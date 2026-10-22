package dev.vladimir.moviejournal.catalog;

import java.util.List;

public record CatalogMovie(
        String provider,
        String id,
        String title,
        Integer releaseYear,
        String overview,
        String posterUrl,
        List<String> genres,
        List<CatalogCastMember> cast
) {
    public CatalogMovie {
        genres = List.copyOf(genres);
        cast = List.copyOf(cast);
    }
}
