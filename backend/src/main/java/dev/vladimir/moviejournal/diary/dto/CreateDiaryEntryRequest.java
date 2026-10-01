package dev.vladimir.moviejournal.diary.dto;

import dev.vladimir.moviejournal.diary.DiaryStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;

public record CreateDiaryEntryRequest(
        @NotBlank
        @Size(max = 300)
        String title,

        @Positive
        Integer releaseYear,

        @NotNull
        DiaryStatus status,

        @Min(1)
        @Max(10)
        Short rating,

        String notes
) {
        @AssertTrue(message = "Only watched movies can have a rating")
        public boolean isRatingAllowed() {
                return rating == null || status == DiaryStatus.WATCHED;
        }
}