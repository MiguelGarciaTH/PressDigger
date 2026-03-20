package arquivo.service;

import arquivo.exceptions.ResourceNotFoundException;
import arquivo.model.Article;
import arquivo.model.Site;
import arquivo.model.User;
import arquivo.repository.ArticleChunkMediumRepository;
import arquivo.repository.ArticleRepository;
import arquivo.repository.SiteRepository;
import arquivo.repository.UserRepository;
import arquivo.services.OpenAiEmbeddingClient;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
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
import java.util.List;

@Service
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final ArticleChunkMediumRepository articleChunkRepository;
    private final UserRepository userRepository;
    private final OpenAiEmbeddingClient embeddingClient; // replaces TextEmbeddingClient
    private final OpenAiIntegrationNarrative openAiIntegrationNarrative;
    private final SiteRepository siteRepository;
    private final ObjectMapper objectMapper;

    @Value("${scribe-ref.arquivo.scribe-news-rest.open-ia.max-usage}")
    private int maxOpenAIUsage;

    @Value("${scribe-ref.arquivo.scribe-news-rest.open-ia.max-usage-period}")
    private Duration maxOpenAIUsagePeriod;

    public ArticleService(Environment environment,
                          ArticleRepository articleRepository,
                          UserRepository userRepository,
                          ArticleChunkMediumRepository articleChunkRepository,
                          SiteRepository siteRepository,
                          @Value("${scribe-ref.arquivo.scribe-news-rest.open-ai.api-key}") String apiKey) {

        this.articleRepository = articleRepository;
        this.userRepository = userRepository;
        this.articleChunkRepository = articleChunkRepository;
        this.siteRepository = siteRepository;
        final String url = environment.getProperty("scribe-ref.arquivo.scribe-rest.embedding-service-url");
        this.embeddingClient = new OpenAiEmbeddingClient(apiKey); // same key, new use
        this.openAiIntegrationNarrative = new OpenAiIntegrationNarrative(apiKey);
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
        // Normalize input text: trim, remove trailing punctuation
        String normalizedText = inputText.trim().replaceAll("[.,;:!?]+$", "");

        float[] vector = embeddingClient.getEmbedding(normalizedText);
        String pgVector = embeddingClient.toPgVectorLiteral(vector);

        // Use original input text for full-text search (it handles punctuation well)
        return articleChunkRepository.searchByText(siteIds, startDate, endDate, pgVector, inputText, pageable);
    }

    @Transactional
    public NarrativeResult createNarrative(String googleId, String inputText, List<Integer> siteIds, LocalDateTime startDate, LocalDateTime endDate) {
        User user = userRepository.findByGoogleId(googleId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with googleId: " + googleId));
        if (!canUseOpenAI(user)) {
            final LocalDateTime retryAt = user.getOpenaiUsageLastTimestamp().plus(maxOpenAIUsagePeriod);
            final String retryAtStr = retryAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "User's narrative usage weekly limit reached. You can use it again after " + retryAtStr);
        }

        final String normalizedText = inputText.trim().replaceAll("[.,;:!?]+$", "");
        float[] vector = embeddingClient.getEmbedding(normalizedText);
        String pgVector = embeddingClient.toPgVectorLiteral(vector);

        final List<Article> articles = articleChunkRepository.searchByTextToNarrative(siteIds, startDate, endDate, pgVector, inputText, 7, 20);
        if (articles.size() < 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not enough relevant articles found to create a narrative. Try broadening your search criteria.");
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
            openIaResult = objectMapper.readTree(openAiIntegrationNarrative.createNarrative(articlesJsonText));
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

    @Transactional(readOnly = true)
    public NarrativeUsageResult getNarrativeUsage(String googleId) {
        User user = userRepository.findByGoogleId(googleId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with googleId: " + googleId));

        final LocalDateTime windowStart = user.getOpenaiUsageLastTimestamp();
        final int usageCount = user.getOpenaiUsageCount();
        final boolean canUse = canUseOpenAI(user);
        final LocalDateTime resetOn = windowStart != null ? windowStart.plus(maxOpenAIUsagePeriod) : LocalDateTime.now(ZoneOffset.UTC);
        return new NarrativeUsageResult(usageCount, windowStart, canUse, resetOn);

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

        // No usage yet, or the window has expired → new window, always allowed
        if (windowStart == null || LocalDateTime.now(ZoneOffset.UTC).isAfter(windowStart.plus(maxOpenAIUsagePeriod))) {
            return true;
        }

        // Within the active window → check against the limit
        return user.getOpenaiUsageCount() < maxOpenAIUsage;
    }


    private void recordOpenAIUsage(User user) {
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        final LocalDateTime windowStart = user.getOpenaiUsageLastTimestamp();

        if (windowStart == null || now.isAfter(windowStart.plus(maxOpenAIUsagePeriod))) {
            // Window expired or first use ever → start a fresh window
            user.setOpenaiUsageCount(1);
            user.setOpenaiUsageLastTimestamp(now);
        } else {
            // Within the active window → just increment
            user.setOpenaiUsageCount(user.getOpenaiUsageCount() + 1);
        }

        userRepository.save(user);
    }
}
