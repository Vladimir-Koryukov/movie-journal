package dev.vladimir.moviejournal.diary.api;

import dev.vladimir.moviejournal.diary.DiaryStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateDiaryEntryRequest(
        @NotBlank(message = "Укажите название фильма")
        @Size(
                max = 300,
                message = "Название не должно быть длиннее 300 символов"
        )
        String title,

        @Positive(message = "Год выпуска должен быть положительным числом")
        Integer releaseYear,

        @NotNull(message = "Выберите статус фильма")
        DiaryStatus status,

        @Min(value = 1, message = "Оценка должна быть от 1 до 10")
        @Max(value = 10, message = "Оценка должна быть от 1 до 10")
        Short rating,

        String notes
) {
    @AssertTrue(
            message = "Оценку можно поставить только просмотренному фильму"
    )
    public boolean isRatingAllowed() {
        return rating == null || status == DiaryStatus.WATCHED;
    }
}
