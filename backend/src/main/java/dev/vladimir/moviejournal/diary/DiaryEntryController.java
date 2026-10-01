package dev.vladimir.moviejournal.diary;

import dev.vladimir.moviejournal.diary.dto.UpdateDiaryEntryRequest;
import dev.vladimir.moviejournal.diary.dto.CreateDiaryEntryRequest;
import dev.vladimir.moviejournal.diary.dto.DiaryEntryResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/diary-entries")
public class DiaryEntryController {

    private final DiaryEntryService diaryEntryService;

    public DiaryEntryController(DiaryEntryService diaryEntryService) {
        this.diaryEntryService = diaryEntryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DiaryEntryResponse create(
            @Valid @RequestBody CreateDiaryEntryRequest request
    ) {
        return diaryEntryService.create(request);
    }

    @GetMapping
    public List<DiaryEntryResponse> findAll(
            @RequestParam(name = "status", required = false)
            DiaryStatus status
    ) {
        return diaryEntryService.findAll(status);
    }

    @GetMapping("/{id}")
    public DiaryEntryResponse findById(@PathVariable("id") Long id) {
        return diaryEntryService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Diary entry not found"
                ));
    }

    @PutMapping("/{id}")
    public DiaryEntryResponse update(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateDiaryEntryRequest request
    ) {
        return diaryEntryService.update(id, request)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Diary entry not found"
                ));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") Long id) {
        boolean deleted = diaryEntryService.delete(id);

        if (!deleted) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Diary entry not found"
            );
        }
    }
}