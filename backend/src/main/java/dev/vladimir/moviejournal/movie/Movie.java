package dev.vladimir.moviejournal.movie;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "movies")
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(name = "release_year")
    private Integer releaseYear;

    @Column(name = "catalog_provider", length = 40)
    private String catalogProvider;

    @Column(name = "catalog_id", length = 64)
    private String catalogId;

    @Column(columnDefinition = "text")
    private String overview;

    @Column(name = "poster_url", length = 1000)
    private String posterUrl;

    @ElementCollection(fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @CollectionTable(
            name = "movie_cast",
            joinColumns = @JoinColumn(name = "movie_id")
    )
    @OrderColumn(name = "billing_order")
    private List<MovieCastMember> castMembers = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @JoinTable(
            name = "movie_genres",
            joinColumns = @JoinColumn(name = "movie_id"),
            inverseJoinColumns = @JoinColumn(name = "genre_id")
    )
    @OrderBy("name ASC")
    private Set<Genre> genres = new LinkedHashSet<>();

    protected Movie() {
    }

    public Movie(String title, Integer releaseYear) {
        this(title, releaseYear, Set.of());
    }

    public Movie(
            String title,
            Integer releaseYear,
            Set<Genre> genres
    ) {
        applyDetails(title, releaseYear);
        replaceGenres(genres);
    }

    public Movie(
            String title,
            Integer releaseYear,
            String catalogProvider,
            String catalogId,
            String overview,
            String posterUrl,
            List<MovieCastMember> castMembers,
            Set<Genre> genres
    ) {
        applyDetails(title, releaseYear);

        if (catalogProvider == null || catalogProvider.isBlank()) {
            throw new IllegalArgumentException(
                    "Catalog provider must not be blank");
        }
        if (catalogId == null || catalogId.isBlank()) {
            throw new IllegalArgumentException(
                    "Catalog identifier must not be blank");
        }

        this.catalogProvider = catalogProvider.strip();
        this.catalogId = catalogId.strip();
        this.overview = normalizeOptional(overview);
        this.posterUrl = normalizeOptional(posterUrl);
        this.castMembers = new ArrayList<>(List.copyOf(castMembers));
        this.genres = new LinkedHashSet<>(genres);
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Integer getReleaseYear() {
        return releaseYear;
    }

    public String getCatalogProvider() {
        return catalogProvider;
    }

    public String getCatalogId() {
        return catalogId;
    }

    public String getOverview() {
        return overview;
    }

    public String getPosterUrl() {
        return posterUrl;
    }

    public List<MovieCastMember> getCastMembers() {
        return List.copyOf(castMembers);
    }

    public Set<Genre> getGenres() {
        return Set.copyOf(genres);
    }

    public void updateDetails(String title, Integer releaseYear) {
        applyDetails(title, releaseYear);
    }

    public void replaceGenres(Set<Genre> genres) {
        if (genres == null) {
            throw new IllegalArgumentException("Genres must not be null");
        }

        this.genres.clear();
        this.genres.addAll(genres);
    }

    private void applyDetails(String title, Integer releaseYear) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException(
                    "Movie title must not be blank");
        }
        if (title.length() > 300) {
            throw new IllegalArgumentException(
                    "Movie title must not exceed 300 characters");
        }
        if (releaseYear != null && releaseYear <= 0) {
            throw new IllegalArgumentException(
                    "Release year must be positive");
        }

        this.title = title.strip();
        this.releaseYear = releaseYear;
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
