package dev.vladimir.moviejournal.diary.api;

import dev.vladimir.moviejournal.diary.DiaryStatus;

import java.util.List;

public record DiaryEntryResponse(
        Long id,
        String title,
        Integer releaseYear,
        DiaryStatus status,
        Short rating,
        String notes,
        String catalogProvider,
        String catalogId,
        String overview,
        String posterUrl,
        List<String> genres,
        List<CastMemberResponse> cast
) {
    public record CastMemberResponse(String name) {
    }
}
