package arquivo.utils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UrlNormalizer {

    private static final Pattern ARQUIVO_PATTERN =
            Pattern.compile("/wayback/\\d+/(https?://.+)$");

    public static String normalize(String url) {
        if (url == null) return null;

        // 1. Extract inner URL from arquivo.pt wrapper
        Matcher m = ARQUIVO_PATTERN.matcher(url);
        if (m.find()) {
            url = m.group(1);
        }

        try {
            URI uri = new URI(url);

            String host = uri.getHost();
            if (host == null) return null;

            // 🔥 Always force https (http = https)
            String scheme = "https";

            // 2. Normalize path
            String path = uri.getPath();
            if (path == null) path = "";

            // Remove AMP suffixes
            path = path.replaceAll("/amp/?$", "");

            // Remove .html or .htm
            path = path.replaceAll("\\.html?$", "");

            // Remove index.<ext>
            path = path.replaceAll("/index\\.(html|htm|php)$", "");

            // Remove trailing slash if not root
            if (path.endsWith("/") && path.length() > 1) {
                path = path.substring(0, path.length() - 1);
            }

            // 3. Normalize query
            String query = uri.getQuery();
            if (query != null) {
                // Remove AMP query key
                query = query.replaceAll("\\bamp(=1)?\\b", "")
                        .replaceAll("&{2,}", "&")
                        .replaceAll("^&", "")
                        .replaceAll("&$", "");

                if (query.isBlank()) query = null;
            }

            // 4. Construct normalized URI
            URI normalized = new URI(
                    scheme,
                    host,
                    path.isEmpty() ? "" : path,
                    query,
                    null
            );

            return normalized.toString();

        } catch (URISyntaxException e) {
            return null;
        }
    }
}
