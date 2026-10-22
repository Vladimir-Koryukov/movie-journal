package dev.vladimir.moviejournal.diary;

import dev.vladimir.moviejournal.catalog.CatalogCastMember;
import dev.vladimir.moviejournal.catalog.CatalogMovie;
import dev.vladimir.moviejournal.catalog.MovieCatalogClient;
import dev.vladimir.moviejournal.diary.api.ImportDiaryEntryRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogImportServiceTest {

    private final MovieCatalogClient catalog =
            mock(MovieCatalogClient.class);
    private final DiaryEntryService diary =
            mock(DiaryEntryService.class);
    private final CatalogImportService service =
            new CatalogImportService(catalog, diary);

    @Test
    void loadsCatalogDetailsBeforeCreatingDiaryEntry() {
        ImportDiaryEntryRequest request = new ImportDiaryEntryRequest(
                "tt0816692",
                DiaryStatus.PLANNED,
                null,
                "Посмотреть позже"
        );
        CatalogMovie movie = new CatalogMovie(
                "omdb",
                "tt0816692",
                "Interstellar",
                2014,
                "A team travels through a wormhole.",
                "https://example.com/poster.jpg",
                List.of("Adventure", "Drama", "Sci-Fi"),
                List.of(new CatalogCastMember("Matthew McConaughey"))
        );

        when(catalog.getMovie("tt0816692")).thenReturn(movie);

        service.importMovie("vladimir", request);

        verify(catalog).getMovie("tt0816692");
        verify(diary).createFromCatalog("vladimir", request, movie);
    }
}
