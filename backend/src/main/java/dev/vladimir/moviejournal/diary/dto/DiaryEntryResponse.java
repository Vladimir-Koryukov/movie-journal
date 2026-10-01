package dev.vladimir.moviejournal.diary.dto;

import dev.vladimir.moviejournal.diary.DiaryStatus;

public record DiaryEntryResponse(
        Long id,
        String title,
        Integer releaseYear,
        DiaryStatus status,
        Short rating,
        String notes
) {
}