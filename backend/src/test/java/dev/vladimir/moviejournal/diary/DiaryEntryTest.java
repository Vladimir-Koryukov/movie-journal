package dev.vladimir.moviejournal.diary;

import dev.vladimir.moviejournal.movie.Movie;
import dev.vladimir.moviejournal.user.AppUser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiaryEntryTest {

    private AppUser createOwner() {
        return new AppUser("vladimir", "test-password-hash");
    }

    @Test
    void allowsEntriesWithoutRating() {
        DiaryEntry planned = new DiaryEntry(
                createOwner(),
                createMovie(),
                DiaryStatus.PLANNED,
                null,
                "Посмотреть"
        );

        DiaryEntry watched = new DiaryEntry(
                createOwner(),
                createMovie(),
                DiaryStatus.WATCHED,
                null,
                "Без оценки"
        );

        assertThat(planned.getRating()).isNull();
        assertThat(watched.getRating()).isNull();
    }

    @Test
    void rejectsRatingForPlannedMovie() {
        assertThatThrownBy(() -> new DiaryEntry(
                createOwner(),
                createMovie(),
                DiaryStatus.PLANNED,
                (short) 8,
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsRatingOutsideAllowedRange() {
        assertThatThrownBy(() -> new DiaryEntry(
                createOwner(),
                createMovie(),
                DiaryStatus.WATCHED,
                (short) 0,
                null
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new DiaryEntry(
                createOwner(),
                createMovie(),
                DiaryStatus.WATCHED,
                (short) 11,
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keepsPreviousStateWhenUpdateFails() {
        DiaryEntry entry = new DiaryEntry(
                createOwner(),
                createMovie(),
                DiaryStatus.WATCHED,
                (short) 8,
                "Исходная заметка"
        );

        assertThatThrownBy(() -> entry.updateDetails(
                DiaryStatus.PLANNED,
                (short) 9,
                "Новая заметка"
        )).isInstanceOf(IllegalArgumentException.class);

        assertThat(entry.getStatus()).isEqualTo(DiaryStatus.WATCHED);
        assertThat(entry.getRating()).isEqualTo((short) 8);
        assertThat(entry.getNotes()).isEqualTo("Исходная заметка");
    }

    @Test
    void canMarkPlannedMovieAsWatched() {
        DiaryEntry entry = new DiaryEntry(
                createOwner(),
                createMovie(),
                DiaryStatus.PLANNED,
                null,
                "Посмотреть"
        );

        entry.updateDetails(
                DiaryStatus.WATCHED,
                (short) 10,
                "Понравился саундтрек"
        );

        assertThat(entry.getStatus()).isEqualTo(DiaryStatus.WATCHED);
        assertThat(entry.getRating()).isEqualTo((short) 10);
        assertThat(entry.getNotes()).isEqualTo("Понравился саундтрек");
    }

    @Test
    void rejectsMissingOwner() {
        assertThatThrownBy(() -> new DiaryEntry(
                null,
                createMovie(),
                DiaryStatus.PLANNED,
                null,
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Owner must not be null");
    }

    private Movie createMovie() {
        return new Movie("Интерстеллар", 2014);
    }
}