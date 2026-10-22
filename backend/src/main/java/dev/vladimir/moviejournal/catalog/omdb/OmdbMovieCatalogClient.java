package dev.vladimir.moviejournal.catalog.omdb;

import dev.vladimir.moviejournal.catalog.CatalogCastMember;
import dev.vladimir.moviejournal.catalog.CatalogMovie;
import dev.vladimir.moviejournal.catalog.MovieCatalogClient;
import dev.vladimir.moviejournal.catalog.MovieCatalogNotConfiguredException;
import dev.vladimir.moviejournal.catalog.MovieCatalogUnavailableException;
import dev.vladimir.moviejournal.catalog.MovieSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class OmdbMovieCatalogClient implements MovieCatalogClient {

    private static final String PROVIDER = "omdb";
    private static final Pattern RELEASE_YEAR = Pattern.compile("\\d{4}");
    private static final Pattern IMDB_ID = Pattern.compile("tt\\d{7,12}");
    private static final Logger log =
            LoggerFactory.getLogger(OmdbMovieCatalogClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final OmdbProperties properties;

    public OmdbMovieCatalogClient(
            HttpClient omdbHttpClient,
            ObjectMapper objectMapper,
            OmdbProperties properties
    ) {
        this.httpClient = omdbHttpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public List<MovieSearchResult> search(String query) {
        long startedAt = System.nanoTime();

        try {
            JsonNode response = get("s=" + encoded(query.strip())
                    + "&type=movie&page=1");

            if (!isSuccessful(response)) {
                String error = apiText(response.path("Error"));

                if (isApiKeyError(error)) {
                    throw new MovieCatalogNotConfiguredException();
                }
                if (isRequestLimitError(error) || error == null) {
                    throw apiError(response);
                }

                logRequest("search", "success", 0, null, startedAt);
                return List.of();
            }

            List<MovieSearchResult> results = new ArrayList<>();

            for (JsonNode movie : response.path("Search")) {
                if (results.size() >= properties.maxResults()) {
                    break;
                }

                String id = apiText(movie.path("imdbID"));
                String title = apiText(movie.path("Title"));

                if (id == null || !IMDB_ID.matcher(id).matches()
                        || title == null) {
                    continue;
                }

                results.add(new MovieSearchResult(
                        id,
                        title,
                        releaseYear(apiText(movie.path("Year"))),
                        apiText(movie.path("Poster"))
                ));
            }

            logRequest(
                    "search",
                    "success",
                    results.size(),
                    null,
                    startedAt
            );
            return List.copyOf(results);
        } catch (RuntimeException exception) {
            logRequest("search", "error", null, null, startedAt);
            throw exception;
        }
    }

    @Override
    public CatalogMovie getMovie(String id) {
        if (id == null || !IMDB_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid IMDb identifier");
        }

        long startedAt = System.nanoTime();

        try {
            JsonNode movie = get("i=" + encoded(id) + "&plot=full");

            if (!isSuccessful(movie)) {
                throw apiError(movie);
            }

            CatalogMovie result = new CatalogMovie(
                    PROVIDER,
                    id,
                    requiredTitle(movie),
                    releaseYear(apiText(movie.path("Year"))),
                    apiText(movie.path("Plot")),
                    apiText(movie.path("Poster")),
                    splitValues(apiText(movie.path("Genre")),
                            Integer.MAX_VALUE),
                    splitValues(
                            apiText(movie.path("Actors")),
                            properties.maxCastMembers()
                    ).stream().map(CatalogCastMember::new).toList()
            );

            logRequest("details", "success", null, id, startedAt);
            return result;
        } catch (RuntimeException exception) {
            logRequest("details", "error", null, id, startedAt);
            throw exception;
        }
    }

    private JsonNode get(String providerQuery) {
        requireConfiguration();

        String baseUrl = properties.baseUrl().replaceAll("/+$", "");
        URI uri = URI.create(baseUrl
                + "/?apikey=" + encoded(properties.apiKey())
                + "&" + providerQuery);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(6))
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() == 401
                    || response.statusCode() == 403) {
                throw new MovieCatalogNotConfiguredException();
            }
            if (response.statusCode() < 200
                    || response.statusCode() >= 300) {
                throw new MovieCatalogUnavailableException(
                        "OMDb returned HTTP " + response.statusCode());
            }

            return objectMapper.readTree(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MovieCatalogUnavailableException(
                    "OMDb request was interrupted",
                    exception
            );
        } catch (IOException exception) {
            throw new MovieCatalogUnavailableException(
                    "OMDb request failed",
                    exception
            );
        } catch (MovieCatalogNotConfiguredException
                 | MovieCatalogUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new MovieCatalogUnavailableException(
                    "OMDb response could not be processed",
                    exception
            );
        }
    }

    private void requireConfiguration() {
        if (!properties.isConfigured()) {
            throw new MovieCatalogNotConfiguredException();
        }
    }

    private MovieCatalogUnavailableException apiError(JsonNode response) {
        String error = apiText(response.path("Error"));
        return new MovieCatalogUnavailableException(
                error == null ? "OMDb rejected the request" : error
        );
    }

    private boolean isSuccessful(JsonNode response) {
        return "true".equalsIgnoreCase(
                response.path("Response").asString(""));
    }

    private boolean isApiKeyError(String error) {
        return containsIgnoreCase(error, "api key");
    }

    private boolean isRequestLimitError(String error) {
        return containsIgnoreCase(error, "request limit");
    }

    private boolean containsIgnoreCase(String value, String expected) {
        return value != null
                && value.toLowerCase(Locale.ROOT).contains(expected);
    }

    private String requiredTitle(JsonNode movie) {
        String title = apiText(movie.path("Title"));
        return title == null ? "Без названия" : title;
    }

    private String apiText(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }

        String value = node.asString("").strip();
        return value.isEmpty() || "N/A".equalsIgnoreCase(value)
                ? null
                : value;
    }

    private Integer releaseYear(String value) {
        if (value == null) {
            return null;
        }

        Matcher matcher = RELEASE_YEAR.matcher(value);
        return matcher.find() ? Integer.valueOf(matcher.group()) : null;
    }

    private List<String> splitValues(String value, int limit) {
        if (value == null) {
            return List.of();
        }

        return Arrays.stream(value.split(","))
                .map(String::strip)
                .filter(item -> !item.isEmpty())
                .distinct()
                .limit(limit)
                .toList();
    }

    private String encoded(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void logRequest(
            String operation,
            String outcome,
            Integer resultCount,
            String catalogId,
            long startedAt
    ) {
        var event = "error".equals(outcome)
                ? log.atWarn()
                : log.atInfo();

        event
                .addKeyValue("event_name", "movie_catalog_request")
                .addKeyValue("provider", PROVIDER)
                .addKeyValue("operation", operation)
                .addKeyValue("outcome", outcome)
                .addKeyValue(
                        "duration_ms",
                        TimeUnit.NANOSECONDS.toMillis(
                                System.nanoTime() - startedAt)
                );

        if (resultCount != null) {
            event.addKeyValue("result_count", resultCount);
        }
        if (catalogId != null) {
            event.addKeyValue("catalog_id", catalogId);
        }

        event.log("Movie catalog request completed");
    }
}
