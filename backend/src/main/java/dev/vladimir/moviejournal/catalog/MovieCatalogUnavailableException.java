package dev.vladimir.moviejournal.catalog;

public class MovieCatalogUnavailableException extends RuntimeException {

    public MovieCatalogUnavailableException(String message) {
        super(message);
    }

    public MovieCatalogUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
