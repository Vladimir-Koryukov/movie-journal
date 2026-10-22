package dev.vladimir.moviejournal.catalog;

public record MovieSearchResult(
        String id,
        String title,
        Integer releaseYear,
        String posterUrl
) {
}
