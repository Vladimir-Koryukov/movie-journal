package dev.vladimir.moviejournal.diary;

import dev.vladimir.moviejournal.diary.dto.UpdateDiaryEntryRequest;
import dev.vladimir.moviejournal.diary.dto.CreateDiaryEntryRequest;
import dev.vladimir.moviejournal.diary.dto.DiaryEntryResponse;
import dev.vladimir.moviejournal.movie.Movie;
import dev.vladimir.moviejournal.movie.MovieRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

@Service
public class DiaryEntryService {

    private final MovieRepository movieRepository;
    private final DiaryEntryRepository diaryEntryRepository;

    public DiaryEntryService(
            MovieRepository movieRepository,
            DiaryEntryRepository diaryEntryRepository
    ) {
        this.movieRepository = movieRepository;
        this.diaryEntryRepository = diaryEntryRepository;
    }

    @Transactional
    public DiaryEntryResponse create(CreateDiaryEntryRequest request) {
        Movie movie = new Movie(
                request.title(),
                request.releaseYear()
        );

        DiaryEntry entry = new DiaryEntry(
                movie,
                request.status(),
                request.rating(),
                request.notes()
        );

        movieRepository.save(movie);
        DiaryEntry savedEntry = diaryEntryRepository.save(entry);

        return toResponse(savedEntry);
    }

    @Transactional(readOnly = true)
    public Optional<DiaryEntryResponse> findById(Long id) {
        return diaryEntryRepository.findById(id)
                .map(this::toResponse);
    }

    @Transactional
    public Optional<DiaryEntryResponse> update(
            Long id,
            UpdateDiaryEntryRequest request
    ) {
        return diaryEntryRepository.findById(id)
                .map(entry -> {
                    entry.getMovie().updateDetails(
                            request.title(),
                            request.releaseYear()
                    );

                    entry.updateDetails(
                            request.status(),
                            request.rating(),
                            request.notes()
                    );

                    return toResponse(entry);
                });
    }

    @Transactional
    public boolean delete(Long id) {
        Optional<DiaryEntry> entryOptional =
                diaryEntryRepository.findById(id);

        if (entryOptional.isEmpty()) {
            return false;
        }

        DiaryEntry entry = entryOptional.get();
        Movie movie = entry.getMovie();

        diaryEntryRepository.delete(entry);
        movieRepository.delete(movie);

        return true;
    }

    @Transactional(readOnly = true)
    public List<DiaryEntryResponse> findAll(DiaryStatus status) {
        Sort sort = Sort.by(Sort.Direction.DESC, "id");

        List<DiaryEntry> entries = status == null
                ? diaryEntryRepository.findAll(sort)
                : diaryEntryRepository.findAllByStatus(status, sort);

        return entries.stream()
                .map(this::toResponse)
                .toList();
    }

    private DiaryEntryResponse toResponse(DiaryEntry entry) {
        Movie movie = entry.getMovie();

        return new DiaryEntryResponse(
                entry.getId(),
                movie.getTitle(),
                movie.getReleaseYear(),
                entry.getStatus(),
                entry.getRating(),
                entry.getNotes()
        );
    }
}