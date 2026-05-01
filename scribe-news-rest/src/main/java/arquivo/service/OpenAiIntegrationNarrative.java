package arquivo.service;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.errors.RateLimitException;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class OpenAiIntegrationNarrative {

    private static final Logger LOG = LoggerFactory.getLogger(OpenAiIntegrationNarrative.class);

    /** Maximum number of retry attempts on a 429 before giving up. */
    private static final int MAX_RETRIES = 4;
    /** Base delay in milliseconds for the first retry. Doubles each attempt. */
    private static final long BASE_DELAY_MS = 1_000;
    /** Cap on the calculated backoff delay, regardless of attempt number. */
    private static final long MAX_DELAY_MS = 32_000;

    private static final Random JITTER = new Random();

    private final OpenAIClient client;

    private final String promptNarrative = """
        You are a professional journalist and historian specialized in analyzing historical newspaper archives.
        
        Your task is to synthesize a set of newspaper article summaries into a single coherent narrative, written in a journalistic style.
        The goal is to reconstruct the story or historical context that emerges from these articles.
        
        Core rules — READ CAREFULLY:
        - Use ONLY information explicitly stated in the summaries. Never infer, assume, or extend beyond what is written.
        - A person criticizing an event is NOT the same as being present at that event. Do not conflate the two.
        - A person commenting on another person's actions is NOT the same as being involved in those actions.
        - Do NOT invent facts, locations, presences, or relationships that are not explicitly stated in the summaries.
        - Not every article needs to appear in the narrative. Only include an article if it genuinely contributes to the story.
          Forcing unrelated articles in will break the narrative's coherence.
        - Write in a neutral, informative journalistic tone.
        - The narrative must be written in Portuguese.
        
        Citation rules:
        - When referencing information from an article, add a citation marker like [1], [2], etc., immediately after the relevant sentence.
        - Reference numbers start at 1 and each number corresponds to exactly one article.
        - The same article may be cited multiple times using the same number.
        - Only assign a reference number to articles that are actually used in the narrative.
        
        Narrative quality rules:
        - Identify the central thread: what is the main event or development? Build the narrative around it.
        - Secondary articles should add context to the main thread, not be forced in as separate paragraphs.
        - If two articles describe reactions to the same event, present them as reactions — not as separate events.
        - Prefer depth and accuracy over breadth. A focused narrative about fewer articles is better than a disjointed summary of all of them.
        
        Output format — return ONLY valid JSON with this structure:
        {
          "text": "Narrative text in Portuguese with citation markers like [1], [2]",
          "references": {
            "1": "article_id",
            "2": "article_id"
          }
        }
        
        - The "text" field contains only the narrative.
        - The "references" field maps each reference number used in the text to the corresponding article ID.
        - Do not include any explanation, commentary, or text outside the JSON.
        """;


    private final String templatePromptStart = """
                   Below is a list of newspaper articles related to the same topic.
            
                   Each article contains:
                   - id: the unique article identifier
                   - date: publication date
                   - summary: summary of the article
            
                   Articles:
            """;

    private final String templatePromptEnd = """
        
        Write a unified narrative focused on the main event or development that connects the majority of these articles.
        Build outward from the most prominent thread — do not force every article in.
        Be precise: only state what is explicitly written in each summary.
        
        Remember:
        - Cite only the articles you actually use, with [n] markers.
        - A reaction to an event ≠ presence at that event.
        - Return only valid JSON following the specified schema.
        """;


    public OpenAiIntegrationNarrative(String apiKey) {
        client = OpenAIOkHttpClient.builder().apiKey(apiKey).build();
    }

    public String createNarrative(String articleListJson) {
        StringBuilder sb = new StringBuilder();
        sb.append(promptNarrative);
        sb.append(templatePromptStart);
        sb.append(articleListJson);
        sb.append(templatePromptEnd);
        final ResponseCreateParams params = ResponseCreateParams.builder()
                .model("gpt-4o")
                .input(sb.toString())
                .build();

        RateLimitException lastException = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                final Response response = client.responses().create(params);
                return response.output().getFirst().message().get().content().getFirst().asOutputText().text()
                        .replaceAll("^```json\\s*|```\\s*$", "").trim();
            } catch (RateLimitException e) {
                lastException = e;
                if (attempt == MAX_RETRIES) {
                    LOG.warn("[Narrative] OpenAI rate limit hit after {} retries, giving up.", attempt);
                    break;
                }
                long delayMs = retryDelayMs(e, attempt);
                LOG.warn("[Narrative] OpenAI rate limit hit (attempt {}/{}), retrying in {} ms...",
                        attempt + 1, MAX_RETRIES, delayMs);
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw lastException;
    }

    /**
     * Computes the delay before the next retry.
     * Honours the {@code Retry-After} header when OpenAI provides one;
     * otherwise falls back to truncated exponential backoff with jitter.
     */
    private long retryDelayMs(RateLimitException e, int attempt) {
        // OpenAI sometimes embeds "Please try again in Xs." in the message
        try {
            String msg = e.getMessage();
            if (msg != null) {
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("try again in (\\d+(?:\\.\\d+)?)s")
                        .matcher(msg);
                if (m.find()) {
                    long serverMs = (long) (Double.parseDouble(m.group(1)) * 1_000);
                    // Add a small jitter on top of the server hint
                    return serverMs + JITTER.nextInt(500);
                }
            }
        } catch (Exception ignored) { }

        // Truncated exponential backoff: BASE * 2^attempt, capped at MAX_DELAY, plus ±25% jitter
        long backoff = Math.min(BASE_DELAY_MS * (1L << attempt), MAX_DELAY_MS);
        long jitter   = (long) (backoff * 0.25 * (JITTER.nextDouble() * 2 - 1)); // ±25%
        return Math.max(BASE_DELAY_MS, backoff + jitter);
    }

}
