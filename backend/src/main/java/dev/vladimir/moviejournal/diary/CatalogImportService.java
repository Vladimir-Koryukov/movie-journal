package dev.vladimir.moviejournal.diary;

import dev.vladimir.moviejournal.catalog.CatalogMovie;
import dev.vladimir.moviejournal.catalog.MovieCatalogClient;
import dev.vladimir.moviejournal.diary.api.DiaryEntryResponse;
import dev.vladimir.moviejournal.diary.api.ImportDiaryEntryRequest;
import org.springframework.stereotype.Service;

@Service
public class CatalogImportService {

    private final MovieCatalogClient movieCatalogClient;
    private final DiaryEntryService diaryEntryService;

    public CatalogImportService(
            MovieCatalogClient movieCatalogClient,
            DiaryEntryService diaryEntryService
    ) {
        this.movieCatalogClient = movieCatalogClient;
        this.diaryEntryService = diaryEntryService;
    }

    public DiaryEntryResponse importMovie(
            String ownerLogin,
            ImportDiaryEntryRequest request
    ) {
        CatalogMovie movie = movieCatalogClient.getMovie(
                request.catalogId());

        return diaryEntryService.createFromCatalog(
                ownerLogin,
                request,
                movie
        );
    }
}
