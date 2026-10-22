package dev.vladimir.moviejournal.catalog.omdb;

import dev.vladimir.moviejournal.catalog.CatalogMovie;
import dev.vladimir.moviejournal.catalog.MovieCatalogNotConfiguredException;
import dev.vladimir.moviejournal.catalog.MovieSearchResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OmdbMovieCatalogClientTest {

    private final HttpClient httpClient = mock(HttpClient.class);

    @SuppressWarnings("unchecked")
    private final HttpResponse<String> response = mock(HttpResponse.class);

    @Test
    void mapsSearchResultsAndSendsApiKey() throws Exception {
        OmdbMovieCatalogClient client = client("test-key", 8, 8);
        stubResponse("""
                {
                  "Search": [
                    {
                      "Title": "Interstellar",
                      "Year": "2014",
                      "imdbID": "tt0816692",
                      "Type": "movie",
                      "Poster": "https://example.com/poster.jpg"
                    }
                  ],
                  "totalResults": "1",
                  "Response": "True"
                }
                """);

        List<MovieSearchResult> results = client.search("Inter stellar");

        assertThat(results).singleElement().satisfies(movie -> {
            assertThat(movie.id()).isEqualTo("tt0816692");
            assertThat(movie.title()).isEqualTo("Interstellar");
            assertThat(movie.releaseYear()).isEqualTo(2014);
            assertThat(movie.posterUrl())
                    .isEqualTo("https://example.com/poster.jpg");
        });

        ArgumentCaptor<HttpRequest> requestCaptor =
                ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(
                requestCaptor.capture(),
                org.mockito.ArgumentMatchers
                        .<HttpResponse.BodyHandler<String>>any()
        );

        assertThat(requestCaptor.getValue().uri().toString())
                .contains("apikey=test-key")
                .contains("s=Inter+stellar")
                .contains("type=movie");
    }

    @Test
    void mapsMovieDetailsGenresAndLimitedCast() throws Exception {
        OmdbMovieCatalogClient client = client("test-key", 8, 2);
        stubResponse("""
                {
                  "Title": "Interstellar",
                  "Year": "2014",
                  "imdbID": "tt0816692",
                  "Plot": "A team travels through a wormhole.",
                  "Poster": "https://example.com/poster.jpg",
                  "Genre": "Adventure, Drama, Sci-Fi",
                  "Actors": "Matthew McConaughey, Anne Hathaway, Jessica Chastain",
                  "Response": "True"
                }
                """);

        CatalogMovie movie = client.getMovie("tt0816692");

        assertThat(movie.provider()).isEqualTo("omdb");
        assertThat(movie.genres())
                .containsExactly("Adventure", "Drama", "Sci-Fi");
        assertThat(movie.cast())
                .extracting(actor -> actor.name())
                .containsExactly("Matthew McConaughey", "Anne Hathaway");
    }

    @Test
    void returnsEmptyListWhenMovieIsNotFound() throws Exception {
        OmdbMovieCatalogClient client = client("test-key", 8, 8);
        stubResponse("""
                {"Response":"False","Error":"Movie not found!"}
                """);

        assertThat(client.search("does-not-exist")).isEmpty();
    }

    @Test
    void returnsEmptyListWhenSearchQueryIsTooBroad() throws Exception {
        OmdbMovieCatalogClient client = client("test-key", 8, 8);
        stubResponse("""
                {"Response":"False","Error":"Too many results."}
                """);

        assertThat(client.search("1+1")).isEmpty();
    }

    @Test
    void rejectsSearchWhenApiKeyIsMissing() throws Exception {
        OmdbMovieCatalogClient client = client("", 8, 8);

        assertThatThrownBy(() -> client.search("Interstellar"))
                .isInstanceOf(MovieCatalogNotConfiguredException.class);

        verify(httpClient, never()).send(
                any(HttpRequest.class),
                org.mockito.ArgumentMatchers
                        .<HttpResponse.BodyHandler<String>>any()
        );
    }

    @Test
    void reportsRejectedApiKeyAsConfigurationProblem() throws Exception {
        OmdbMovieCatalogClient client = client("invalid-key", 8, 8);

        when(response.statusCode()).thenReturn(401);
        when(httpClient.send(
                any(HttpRequest.class),
                org.mockito.ArgumentMatchers
                        .<HttpResponse.BodyHandler<String>>any()
        )).thenReturn(response);

        assertThatThrownBy(() -> client.search("Interstellar"))
                .isInstanceOf(MovieCatalogNotConfiguredException.class);
    }

    private OmdbMovieCatalogClient client(
            String apiKey,
            int maxResults,
            int maxCastMembers
    ) {
        return new OmdbMovieCatalogClient(
                httpClient,
                JsonMapper.builder().build(),
                new OmdbProperties(
                        "https://www.omdbapi.com",
                        apiKey,
                        maxResults,
                        maxCastMembers
                )
        );
    }

    private void stubResponse(String body) throws Exception {
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(body);
        when(httpClient.send(
                any(HttpRequest.class),
                org.mockito.ArgumentMatchers
                        .<HttpResponse.BodyHandler<String>>any()
        )).thenReturn(response);
    }
}
