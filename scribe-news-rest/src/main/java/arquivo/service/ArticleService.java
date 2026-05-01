package arquivo.service;

import arquivo.exceptions.ResourceNotFoundException;
import arquivo.model.Article;
import arquivo.model.Site;
import arquivo.model.User;
import arquivo.repository.ArticleRepository;
import arquivo.repository.SiteRepository;
import arquivo.repository.UserRepository;
import arquivo.services.CohereEmbeddingClient;
import arquivo.services.CohereRerankClient;
import arquivo.services.EmbeddingClient;
import arquivo.services.OpenAiEmbeddingClient;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ArticleService {

    private static final Logger LOG = LoggerFactory.getLogger(ArticleService.class);

    private final ArticleRepository articleRepository;
    private final UserRepository userRepository;
    private final EmbeddingClient embeddingClient;
    private final CohereRerankClient cohereRerankClient;
    private final OpenAiIntegrationNarrative openAiIntegrationNarrative;
    private final SiteRepository siteRepository;
    private final ObjectMapper objectMapper;
    private final boolean useCohere;
    private final boolean rerankEnabled;

    @Value("${scribe-ref.arquivo.scribe-news-rest.open-ai.max-usage}")
    private int maxOpenAIUsage;

    @Value("${scribe-ref.arquivo.scribe-news-rest.open-ai.max-usage-period}")
    private Duration maxOpenAIUsagePeriod;

    @Value("${scribe-ref.rerank.min-score:0.0}")
    private double rerankMinScore;

    public ArticleService(ArticleRepository articleRepository,
                          UserRepository userRepository,
                          SiteRepository siteRepository,
                          @Value("${scribe-ref.arquivo.scribe-news-rest.open-ai.api-key}") String openAiApiKey,
                          @Value("${scribe-ref.arquivo.scribe-news-rest.cohere.api-key:}") String cohereApiKey,
                          @Value("${scribe-ref.embedding.provider:openai}") String embeddingProvider,
                          @Value("${scribe-ref.rerank.enabled:false}") boolean rerankEnabled) {

        this.articleRepository = articleRepository;
        this.userRepository = userRepository;
        this.siteRepository = siteRepository;
        this.useCohere = "cohere".equalsIgnoreCase(embeddingProvider);
        this.rerankEnabled = rerankEnabled && this.useCohere;
        if (this.useCohere) {
            this.embeddingClient = new CohereEmbeddingClient(cohereApiKey);
            this.cohereRerankClient = rerankEnabled ? new CohereRerankClient(cohereApiKey) : null;
        } else {
            this.embeddingClient = new OpenAiEmbeddingClient(openAiApiKey);
            this.cohereRerankClient = null;
        }
        this.openAiIntegrationNarrative = new OpenAiIntegrationNarrative(openAiApiKey);
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
    }

    @Transactional(readOnly = true)
    public Article getArticle(int articleId) {
        Article article = articleRepository.findById(articleId).orElse(null);
        if (article == null) {
            throw new ResourceNotFoundException("article not found with id: " + articleId);
        }
        return article;
    }

    @Transactional(readOnly = true)
    public Page<Article> search(String inputText, List<Integer> siteIds, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        String normalizedText = inputText.trim().replaceAll("[.,;:!?]+$", "");

        float[] vector = embeddingClient.getQueryEmbedding(normalizedText);
        String pgVector = embeddingClient.toPgVectorLiteral(vector);

        // Search always uses the RRF hybrid query (vector + BM25) — rerank is reserved for
        // narrative generation where result ordering directly affects LLM output quality.
        LOG.info("[Search] Using hybrid RRF search path (Cohere={})", useCohere);

        if (useCohere) {
            return articleRepository.searchByTextCohere(siteIds, startDate, endDate, pgVector, inputText, pageable);
        }
        return articleRepository.searchByText(siteIds, startDate, endDate, pgVector, inputText, pageable);
    }

    @Transactional
    public NarrativeResult createNarrative(String googleId, String inputText, List<Integer> siteIds, LocalDateTime startDate, LocalDateTime endDate) {
        User user = userRepository.findByGoogleId(googleId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!canUseOpenAI(user)) {
            final LocalDateTime retryAt = user.getOpenaiUsageLastTimestamp().plus(maxOpenAIUsagePeriod);
            final String retryAtStr = retryAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "User's narrative usage weekly limit reached. You can use it again after " + retryAtStr);
        }

        final String normalizedText = inputText.trim().replaceAll("[.,;:!?]+$", "");
        float[] vector = embeddingClient.getQueryEmbedding(normalizedText);
        String pgVector = embeddingClient.toPgVectorLiteral(vector);

        LOG.info("[Narrative] query='{}' sites={} start={} end={}",
                normalizedText, siteIds, startDate, endDate);

        List<Article> articles;
        if (useCohere) {
            articles = new ArrayList<>(articleRepository.searchByTextToNarrativeCohere(siteIds, startDate, endDate, pgVector, inputText, 30, 20));
            if (articles.size() < 3) {
                LOG.info("[Narrative] Day-window returned only {} articles, falling back to top-K cosine", articles.size());
                articles = new ArrayList<>(articleRepository.searchByTextToNarrativeCohereNoWindow(siteIds, startDate, endDate, pgVector, 20));
            }
        } else {
            articles = new ArrayList<>(articleRepository.searchByTextToNarrative(siteIds, startDate, endDate, pgVector, inputText, 30, 20));
            if (articles.size() < 3) {
                LOG.info("[Narrative] Day-window returned only {} articles, falling back to top-K cosine", articles.size());
                articles = new ArrayList<>(articleRepository.searchByTextToNarrativeNoWindow(siteIds, startDate, endDate, pgVector, 20));
            }
        }

        LOG.info("[Narrative] Found {} articles (need >= 3)", articles.size());
        articles.forEach(a -> LOG.info("[Narrative]   id={} date={} distance_title='{}'",
                a.getId(), a.getPublishedDate(), a.getTitle()));

        if (articles.size() < 3) {
            LOG.warn("[Narrative] Not enough articles: {} found for query='{}' sites={} start={} end={}",
                    articles.size(), normalizedText, siteIds, startDate, endDate);
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Not enough relevant articles found to create a narrative. Try broadening your search criteria or date range.");
        }

        // Rerank narrative candidates so the most relevant articles are fed to the LLM first
        if (rerankEnabled && articles.size() > 3) {
            articles = applyRerank(inputText, articles);
        }

        String articlesJsonText = "";
        for (Article article : articles) {
            String articleJson = String.format("""
                    {
                      "id": %d,
                      "date": "%s",
                      "summary": "%s"
                    }
                    """, article.getId(), article.getPublishedDate(), article.getSummary().replaceAll("\"", "\\\\\"").replaceAll("\n", "\\\\n"));
            if (articlesJsonText.isBlank()) {
                articlesJsonText = "[" + articleJson;
            } else {
                articlesJsonText += "," + articleJson;
            }
        }
        articlesJsonText += "]";

        if (articlesJsonText.isBlank()) {
            return null;
        }

        JsonNode openIaResult = null;
        try {
            String narrativeJson = openAiIntegrationNarrative.createNarrative(articlesJsonText);
            openIaResult = objectMapper.readTree(narrativeJson);
            // Only count usage when the API call actually succeeded
            recordOpenAIUsage(user);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        if (openIaResult == null || !openIaResult.has("text") || !openIaResult.has("references")) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid response from OpenAI. Please try again later.");
        }

        final String text = openIaResult.get("text").asText();
        final JsonNode referencesNode = openIaResult.get("references");
        final List<ArticleReference> references = new java.util.ArrayList<>(openIaResult.get("references").size());
        referencesNode.fields().forEachRemaining(entry -> {
            String marker = entry.getKey();
            int articleId = entry.getValue().asInt();
            articleRepository.findById(articleId).ifPresent(article -> {
                String siteName = siteRepository.findById(article.getSite().getId())
                        .map(Site::getName)
                        .orElse("Unknown");
                references.add(new ArticleReference(marker, article, siteName));
            });
        });
        return new NarrativeResult(text, references);
    }

    /**
     * Re-ranks a list of candidate articles against the original query using the
     * Cohere cross-encoder (rerank-multilingual-v3.0). Documents are scored on
     * title + summary so the ranker sees the full article context.
     * Falls back to the original order if reranking fails.
     */
    private List<Article> applyRerank(String query, List<Article> candidates) {
        List<String> documents = candidates.stream()
                .map(a -> {
                    String title = a.getTitle() != null ? a.getTitle().trim() : "";
                    String summary = a.getSummary() != null ? a.getSummary().trim() : "";
                    return title.isBlank() ? summary : title + "\n" + summary;
                })
                .collect(Collectors.toList());

        List<CohereRerankClient.RerankResult> results;
        try {
            results = cohereRerankClient.rerank(query, documents, candidates.size());
        } catch (Exception e) {
            LOG.warn("[Rerank] Reranking failed, falling back to original vector order: {}", e.getMessage());
            return candidates;
        }

        List<Article> reranked = results.stream()
                .filter(r -> r.relevanceScore() >= rerankMinScore)
                .map(r -> candidates.get(r.index()))
                .collect(Collectors.toList());

        LOG.info("[Rerank] After min-score filter (>= {}): {}/{} articles kept",
                rerankMinScore, reranked.size(), candidates.size());

        return reranked;
    }

    @Transactional(readOnly = true)
    public NarrativeUsageResult getNarrativeUsage(String googleId) {
        User user = userRepository.findByGoogleId(googleId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        final LocalDateTime windowStart = user.getOpenaiUsageLastTimestamp();
        final int usageCount = user.getOpenaiUsageCount();
        final boolean canUse = canUseOpenAI(user);
        final LocalDateTime resetOn = windowStart != null ? windowStart.plus(maxOpenAIUsagePeriod) : LocalDateTime.now(ZoneOffset.UTC);
        return new NarrativeUsageResult(usageCount, windowStart, canUse, resetOn);
    }

    @Transactional(readOnly = true)
    public long count() {
        return articleRepository.count();
    }

    public record NarrativeUsageResult(int usageCount, LocalDateTime windowStart, boolean canUse, LocalDateTime resetOn) {
    }

    public record NarrativeResult(String text, List<ArticleReference> references) {
    }

    record ArticleReference(String marker, String title, int articleId, String author, LocalDate publishedDate,
                            String summary, String linkToArchive, String site) {
        ArticleReference(String marker, Article article, String site) {
            this(marker, article.getTitle(), article.getId(), article.getAuthor() != null ? article.getAuthor().getName() : null, article.getPublishedDate(), article.getSummary(), article.getLinkToArchive(), site);
        }
    }

    private boolean canUseOpenAI(User user) {
        final LocalDateTime windowStart = user.getOpenaiUsageLastTimestamp();
        if (windowStart == null || LocalDateTime.now(ZoneOffset.UTC).isAfter(windowStart.plus(maxOpenAIUsagePeriod))) {
            return true;
        }
        return user.getOpenaiUsageCount() < maxOpenAIUsage;
    }

    private void recordOpenAIUsage(User user) {
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        final LocalDateTime windowStart = user.getOpenaiUsageLastTimestamp();

        if (windowStart == null || now.isAfter(windowStart.plus(maxOpenAIUsagePeriod))) {
            user.setOpenaiUsageCount(1);
            user.setOpenaiUsageLastTimestamp(now);
        } else {
            user.setOpenaiUsageCount(user.getOpenaiUsageCount() + 1);
        }

        userRepository.save(user);
    }
}
