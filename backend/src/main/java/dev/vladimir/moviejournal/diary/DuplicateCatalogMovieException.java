package dev.vladimir.moviejournal.diary;

public class DuplicateCatalogMovieException extends RuntimeException {

    public DuplicateCatalogMovieException(String movieTitle) {
        super("Фильм «" + movieTitle + "» уже добавлен в вашу коллекцию.");
    }
}
