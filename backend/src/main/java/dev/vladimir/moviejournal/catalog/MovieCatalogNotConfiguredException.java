package dev.vladimir.moviejournal.catalog;

public class MovieCatalogNotConfiguredException extends RuntimeException {

    public MovieCatalogNotConfiguredException() {
        super("Movie catalog API key is not configured");
    }
}
