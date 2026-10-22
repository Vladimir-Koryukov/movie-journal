package dev.vladimir.moviejournal.catalog.api;

import dev.vladimir.moviejournal.catalog.MovieCatalogClient;
import dev.vladimir.moviejournal.catalog.MovieSearchResult;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/catalog/movies")
public class MovieCatalogController {

    private final MovieCatalogClient movieCatalogClient;

    public MovieCatalogController(MovieCatalogClient movieCatalogClient) {
        this.movieCatalogClient = movieCatalogClient;
    }

    @GetMapping
    public List<MovieSearchResult> search(
            @RequestParam("query") String query
    ) {
        String normalizedQuery = query == null ? "" : query.strip();

        if (normalizedQuery.length() < 2
                || normalizedQuery.length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Введите от 2 до 100 символов для поиска"
            );
        }

        return movieCatalogClient.search(normalizedQuery);
    }
}
