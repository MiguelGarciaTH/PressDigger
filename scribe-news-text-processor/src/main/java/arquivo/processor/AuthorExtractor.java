package arquivo.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AuthorExtractor {

    private static final Pattern AUTHOR_PATTERN =
            Pattern.compile("(?i)(autor|por|escrito por|opinião de)\\s+([A-ZÁÉÍÓÚÂÊÔÃÕÇ][a-záéíóúâêôãõç]+(?:\\s+[A-ZÁÉÍÓÚÂÊÔÃÕÇ][a-záéíóúâêôãõç]+)+)");

    private final ObjectMapper mapper;

    private final String nerServiceUrl;

    private static class AuthorCandidate {
        String name;
        int score;
        int position; // character offset
        int occurrences;

        AuthorCandidate(String name, int position) {
            this.name = name;
            this.position = position;
            this.score = 0;
        }
    }

    AuthorExtractor(String nerServiceUrl) {
        this.mapper = new ObjectMapper();
        this.nerServiceUrl = nerServiceUrl;
    }

    public Optional<String> extractAuthor(String rawText) {

        // 1. Clean
        List<String> lines = cleanLines(rawText);
        String cleanedText = String.join("\n", lines);

        // Optional optimization: only first 2000 chars
        String truncated = cleanedText.substring(0,
                Math.min(cleanedText.length(), 2000));

        // 2. Pattern candidates
        List<AuthorCandidate> candidates = new ArrayList<>();
        candidates.addAll(extractPatternCandidates(truncated));

        // 3. NER candidates
        candidates.addAll(callNerService(truncated));

        if (candidates.isEmpty())
            return Optional.empty();

        // 4. Score
        scoreCandidates(candidates, truncated);

        // 5. Select
        return selectBest(candidates);
    }

    private Optional<String> selectBest(List<AuthorCandidate> candidates) {
        return candidates.stream()
                .sorted((a, b) -> Integer.compare(b.score, a.score))
                .filter(c -> c.score >= 4)
                .map(c -> c.name)
                .findFirst();
    }

    private List<AuthorCandidate> callNerService(String text) {
        try {
            String jsonPayload = """
                    {
                      "text": %s,
                      "lang": "pt"
                    }
                    """.formatted(
                    "\"" + text.replace("\"", "\\\"") + "\""
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(nerServiceUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response =
                    HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

            return parsePersons(response.body());

        } catch (Exception e) {
            return List.of();
        }
    }

    private List<AuthorCandidate> parsePersons(String json) throws Exception {
        JsonNode root = mapper.readTree(json);
        JsonNode persons = root.get("persons");

        List<AuthorCandidate> candidates = new ArrayList<>();

        for (JsonNode person : persons) {
            String name = person.get("text").asText();
            int start = person.get("start").asInt();
            candidates.add(new AuthorCandidate(name, start));
        }

        return candidates;
    }

    private List<AuthorCandidate> extractPatternCandidates(String text) {
        Matcher matcher = AUTHOR_PATTERN.matcher(text);
        List<AuthorCandidate> candidates = new ArrayList<>();

        while (matcher.find()) {
            String name = matcher.group(2);
            candidates.add(new AuthorCandidate(name, matcher.start(2)));
        }

        return candidates;
    }

    private void scoreCandidates(List<AuthorCandidate> candidates, String text) {
        int totalLength = text.length();

        for (AuthorCandidate c : candidates) {

            double relativePosition = (double) c.position / totalLength;

            // Appears early
            if (relativePosition < 0.2)
                c.score += 3;

            // Count occurrences
            c.occurrences = countOccurrences(text, c.name);

            if (c.occurrences == 1)
                c.score += 2;

            if (c.occurrences > 2)
                c.score -= 3;

            // Context window
            int start = Math.max(0, c.position - 50);
            int end = Math.min(text.length(), c.position + 50);

            String context = text.substring(start, end).toLowerCase();

            if (context.contains("por ") || context.contains("autor"))
                c.score += 5;

            if (context.contains("disse") || context.contains("afirmou"))
                c.score -= 4;
        }
    }

    private int countOccurrences(String text, String name) {
        return text.split(Pattern.quote(name), -1).length - 1;
    }

    private List<String> cleanLines(String raw) {
        return Arrays.stream(raw.split("\n"))
                .map(String::trim)
                .filter(line -> line.length() > 0)
                .filter(line -> line.split("\\s+").length >= 2)
                .filter(line -> !line.matches("^[A-Z\\s]+$"))
                .filter(line -> !line.contains("http"))
                .filter(line -> !line.toLowerCase().contains("newsletter"))
                .collect(Collectors.toList());
    }
}
