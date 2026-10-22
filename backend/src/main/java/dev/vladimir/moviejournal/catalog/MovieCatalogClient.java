package dev.vladimir.moviejournal.catalog;

import java.util.List;

public interface MovieCatalogClient {

    List<MovieSearchResult> search(String query);

    CatalogMovie getMovie(String id);
}
