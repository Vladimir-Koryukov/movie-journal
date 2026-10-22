package dev.vladimir.moviejournal.catalog.omdb;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.omdb")
public record OmdbProperties(
        String baseUrl,
        String apiKey,
        int maxResults,
        int maxCastMembers
) {
    public OmdbProperties {
        baseUrl = valueOrDefault(baseUrl, "https://www.omdbapi.com");
        apiKey = apiKey == null ? "" : apiKey.strip();
        maxResults = maxResults > 0 ? maxResults : 8;
        maxCastMembers = maxCastMembers > 0 ? maxCastMembers : 8;
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}
