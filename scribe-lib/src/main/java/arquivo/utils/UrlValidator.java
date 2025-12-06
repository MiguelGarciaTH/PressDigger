package arquivo.utils;

public class UrlValidator {

    public static boolean isValid(String normalizedUrl) {
        if (normalizedUrl == null) return false;

        // Rule 1: skip URLs ending with "/"
        if (normalizedUrl.endsWith("/")) return false;

        // More rules can be added here
        return true;
    }
}
