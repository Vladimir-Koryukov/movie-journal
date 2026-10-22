package dev.vladimir.moviejournal.catalog.api;

import dev.vladimir.moviejournal.catalog.MovieCatalogNotConfiguredException;
import dev.vladimir.moviejournal.catalog.MovieCatalogUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class MovieCatalogExceptionHandler {

    @ExceptionHandler(MovieCatalogNotConfiguredException.class)
    public ProblemDetail handleNotConfigured() {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Ключ OMDb не настроен или отклонён сервисом. "
                        + "Фильм можно добавить вручную."
        );
    }

    @ExceptionHandler(MovieCatalogUnavailableException.class)
    public ProblemDetail handleUnavailable() {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY,
                "OMDb временно недоступен. Повторите поиск позже."
        );
    }
}
