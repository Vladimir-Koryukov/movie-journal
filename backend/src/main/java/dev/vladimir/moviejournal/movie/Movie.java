package dev.vladimir.moviejournal.movie;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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

    protected Movie() {
    }

    public Movie(String title, Integer releaseYear) {
        applyDetails(title, releaseYear);
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

    public void updateDetails(String title, Integer releaseYear) {
        applyDetails(title, releaseYear);
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

        this.title = title;
        this.releaseYear = releaseYear;
    }
}