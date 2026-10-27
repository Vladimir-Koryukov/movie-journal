package dev.vladimir.moviejournal.diary.api;

import dev.vladimir.moviejournal.diary.DuplicateCatalogMovieException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = DiaryEntryController.class)
public class DiaryEntryExceptionHandler {

    @ExceptionHandler(DuplicateCatalogMovieException.class)
    public ProblemDetail handleDuplicateCatalogMovie(
            DuplicateCatalogMovieException exception
    ) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                exception.getMessage()
        );
    }
}
