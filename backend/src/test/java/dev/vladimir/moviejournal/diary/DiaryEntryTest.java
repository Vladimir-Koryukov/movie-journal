package dev.vladimir.moviejournal.diary;

import dev.vladimir.moviejournal.movie.Movie;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiaryEntryTest {

    @Test
    void allowsEntriesWithoutRating() {
        DiaryEntry planned = new DiaryEntry(
                createMovie(), DiaryStatus.PLANNED, null, "Посмотреть"
        );

        DiaryEntry watched = new DiaryEntry(
                createMovie(), DiaryStatus.WATCHED, null, "Без оценки"
        );

        assertThat(planned.getRating()).isNull();
        assertThat(watched.getRating()).isNull();
    }

    @Test
    void rejectsRatingForPlannedMovie() {
        assertThatThrownBy(() -> new DiaryEntry(
                createMovie(), DiaryStatus.PLANNED, (short) 8, null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsRatingOutsideAllowedRange() {
        assertThatThrownBy(() -> new DiaryEntry(
                createMovie(), DiaryStatus.WATCHED, (short) 0, null
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new DiaryEntry(
                createMovie(), DiaryStatus.WATCHED, (short) 11, null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keepsPreviousStateWhenUpdateFails() {
        DiaryEntry entry = new DiaryEntry(
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

    private Movie createMovie() {
        return new Movie("Интерстеллар", 2014);
    }
}