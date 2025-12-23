package arquivo.utils;

public class UrlValidator {

    public static boolean isValid(String normalizedUrl) {
        if (normalizedUrl == null) return false;
        return !normalizedUrl.endsWith("/");
    }
}
