package dev.vladimir.moviejournal.diary.api;

import dev.vladimir.moviejournal.diary.CatalogImportService;
import dev.vladimir.moviejournal.diary.DiaryEntryService;
import dev.vladimir.moviejournal.diary.DiaryEntrySort;
import dev.vladimir.moviejournal.diary.DiaryStatus;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/diary-entries")
public class DiaryEntryController {

    private final DiaryEntryService diaryEntryService;
    private final CatalogImportService catalogImportService;

    public DiaryEntryController(
            DiaryEntryService diaryEntryService,
            CatalogImportService catalogImportService
    ) {
        this.diaryEntryService = diaryEntryService;
        this.catalogImportService = catalogImportService;
    }

    @PostMapping("/from-catalog")
    @ResponseStatus(HttpStatus.CREATED)
    public DiaryEntryResponse createFromCatalog(
            @Valid @RequestBody ImportDiaryEntryRequest request,
            Principal principal
    ) {
        return catalogImportService.importMovie(
                principal.getName(),
                request
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DiaryEntryResponse create(
            @Valid @RequestBody CreateDiaryEntryRequest request,
            Principal principal
    ) {
        return diaryEntryService.create(principal.getName(), request);
    }

    @GetMapping
    public List<DiaryEntryResponse> findAll(
            @RequestParam(name = "status", required = false)
            DiaryStatus status,
            @RequestParam(name = "query", required = false)
            String query,
            @RequestParam(name = "yearFrom", required = false)
            Integer yearFrom,
            @RequestParam(name = "yearTo", required = false)
            Integer yearTo,
            @RequestParam(name = "ratingFrom", required = false)
            Short ratingFrom,
            @RequestParam(name = "ratingTo", required = false)
            Short ratingTo,
            @RequestParam(
                    name = "sort",
                    defaultValue = "ADDED_DESC"
            ) DiaryEntrySort sort,
            Principal principal
    ) {
        validateFilters(
                query,
                yearFrom,
                yearTo,
                ratingFrom,
                ratingTo
        );

        return diaryEntryService.findAll(
                principal.getName(),
                status,
                query,
                yearFrom,
                yearTo,
                ratingFrom,
                ratingTo,
                sort
        );
    }

    @GetMapping("/{id}")
    public DiaryEntryResponse findById(
            @PathVariable("id") Long id,
            Principal principal
    ) {
        return diaryEntryService.findById(principal.getName(), id)
                .orElseThrow(this::entryNotFound);
    }

    @PutMapping("/{id}")
    public DiaryEntryResponse update(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateDiaryEntryRequest request,
            Principal principal
    ) {
        return diaryEntryService.update(principal.getName(), id, request)
                .orElseThrow(this::entryNotFound);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable("id") Long id,
            Principal principal
    ) {
        if (!diaryEntryService.delete(principal.getName(), id)) {
            throw entryNotFound();
        }
    }

    private ResponseStatusException entryNotFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Diary entry not found"
        );
    }

    private void validateFilters(
            String query,
            Integer yearFrom,
            Integer yearTo,
            Short ratingFrom,
            Short ratingTo
    ) {
        if (query != null && query.strip().length() > 100) {
            throw invalidFilter("Поисковый запрос не должен быть длиннее 100 символов");
        }
        if (yearFrom != null && yearFrom <= 0
                || yearTo != null && yearTo <= 0) {
            throw invalidFilter("Год должен быть положительным числом");
        }
        if (yearFrom != null && yearTo != null && yearFrom > yearTo) {
            throw invalidFilter("Начальный год не может быть больше конечного");
        }
        if (ratingFrom != null && (ratingFrom < 1 || ratingFrom > 10)
                || ratingTo != null && (ratingTo < 1 || ratingTo > 10)) {
            throw invalidFilter("Оценка должна быть от 1 до 10");
        }
        if (ratingFrom != null && ratingTo != null
                && ratingFrom > ratingTo) {
            throw invalidFilter("Минимальная оценка не может быть больше максимальной");
        }
    }

    private ResponseStatusException invalidFilter(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
