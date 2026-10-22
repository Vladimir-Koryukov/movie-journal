package dev.vladimir.moviejournal.diary.api;

import dev.vladimir.moviejournal.diary.DiaryStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ImportDiaryEntryRequest(
        @NotBlank(message = "Выберите фильм из каталога")
        @Pattern(
                regexp = "tt\\d{7,12}",
                message = "Некорректный идентификатор фильма"
        )
        String catalogId,

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
