package dev.vladimir.moviejournal.diary;

import dev.vladimir.moviejournal.movie.Movie;
import dev.vladimir.moviejournal.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "diary_entries")
public class DiaryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private AppUser owner;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false, unique = true)
    private Movie movie;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DiaryStatus status;

    private Short rating;

    @Column(columnDefinition = "text")
    private String notes;

    protected DiaryEntry() {
    }

    public DiaryEntry(
            AppUser owner,
            Movie movie,
            DiaryStatus status,
            Short rating,
            String notes
    ) {
        if (owner == null) {
            throw new IllegalArgumentException("Owner must not be null");
        }
        if (movie == null) {
            throw new IllegalArgumentException("Movie must not be null");
        }

        applyDetails(status, rating, notes);
        this.owner = owner;
        this.movie = movie;
    }

    public Long getId() {
        return id;
    }

    public AppUser getOwner() {
        return owner;
    }

    public Movie getMovie() {
        return movie;
    }

    public DiaryStatus getStatus() {
        return status;
    }

    public Short getRating() {
        return rating;
    }

    public String getNotes() {
        return notes;
    }

    public void updateDetails(
            DiaryStatus status,
            Short rating,
            String notes
    ) {
        applyDetails(status, rating, notes);
    }

    private void applyDetails(
            DiaryStatus status,
            Short rating,
            String notes
    ) {
        if (status == null) {
            throw new IllegalArgumentException(
                    "Diary status must not be null");
        }

        if (rating != null && (rating < 1 || rating > 10)) {
            throw new IllegalArgumentException(
                    "Rating must be between 1 and 10");
        }

        if (rating != null && status != DiaryStatus.WATCHED) {
            throw new IllegalArgumentException(
                    "Only watched movies can have a rating");
        }

        this.status = status;
        this.rating = rating;
        this.notes = notes;
    }
}