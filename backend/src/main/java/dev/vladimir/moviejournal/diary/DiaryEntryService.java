package dev.vladimir.moviejournal.diary;

import dev.vladimir.moviejournal.catalog.CatalogMovie;
import dev.vladimir.moviejournal.diary.api.CreateDiaryEntryRequest;
import dev.vladimir.moviejournal.diary.api.DiaryEntryResponse;
import dev.vladimir.moviejournal.diary.api.ImportDiaryEntryRequest;
import dev.vladimir.moviejournal.diary.api.UpdateDiaryEntryRequest;
import dev.vladimir.moviejournal.movie.Genre;
import dev.vladimir.moviejournal.movie.GenreService;
import dev.vladimir.moviejournal.movie.Movie;
import dev.vladimir.moviejournal.movie.MovieCastMember;
import dev.vladimir.moviejournal.movie.MovieRepository;
import dev.vladimir.moviejournal.observability.BusinessMetrics;
import dev.vladimir.moviejournal.user.AppUser;
import dev.vladimir.moviejournal.user.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class DiaryEntryService {

    private static final Logger log =
            LoggerFactory.getLogger(DiaryEntryService.class);

    private final MovieRepository movieRepository;
    private final DiaryEntryRepository diaryEntryRepository;
    private final AppUserRepository appUserRepository;
    private final GenreService genreService;
    private final BusinessMetrics metrics;

    public DiaryEntryService(
            MovieRepository movieRepository,
            DiaryEntryRepository diaryEntryRepository,
            AppUserRepository appUserRepository,
            GenreService genreService,
            BusinessMetrics metrics
    ) {
        this.movieRepository = movieRepository;
        this.diaryEntryRepository = diaryEntryRepository;
        this.appUserRepository = appUserRepository;
        this.genreService = genreService;
        this.metrics = metrics;
    }

    @Transactional
    public DiaryEntryResponse create(
            String ownerLogin,
            CreateDiaryEntryRequest request
    ) {
        AppUser owner = requireOwner(ownerLogin);
        Movie movie = new Movie(request.title(), request.releaseYear());
        DiaryEntry savedEntry = saveEntry(
                owner,
                movie,
                request.status(),
                request.rating(),
                request.notes()
        );

        logCreated(savedEntry, "manual");
        metrics.recordManualDiaryEntryCreated(savedEntry.getStatus());
        return toResponse(savedEntry);
    }

    @Transactional
    public DiaryEntryResponse createFromCatalog(
            String ownerLogin,
            ImportDiaryEntryRequest request,
            CatalogMovie catalogMovie
    ) {
        AppUser owner = requireOwner(ownerLogin);
        List<MovieCastMember> cast = catalogMovie.cast().stream()
                .map(member -> new MovieCastMember(member.name()))
                .toList();
        Set<Genre> genres = genreService.resolve(catalogMovie.genres());

        Movie movie = new Movie(
                catalogMovie.title(),
                catalogMovie.releaseYear(),
                catalogMovie.provider(),
                catalogMovie.id(),
                catalogMovie.overview(),
                catalogMovie.posterUrl(),
                cast,
                genres
        );

        DiaryEntry savedEntry = saveEntry(
                owner,
                movie,
                request.status(),
                request.rating(),
                request.notes()
        );

        logCreated(savedEntry, catalogMovie.provider());
        metrics.recordCatalogDiaryEntryCreated(savedEntry.getStatus());
        return toResponse(savedEntry);
    }

    @Transactional(readOnly = true)
    public Optional<DiaryEntryResponse> findById(
            String ownerLogin,
            Long id
    ) {
        return diaryEntryRepository.findByIdAndOwner_Login(id, ownerLogin)
                .map(this::toResponse);
    }

    @Transactional
    public Optional<DiaryEntryResponse> update(
            String ownerLogin,
            Long id,
            UpdateDiaryEntryRequest request
    ) {
        Optional<DiaryEntry> found =
                diaryEntryRepository.findByIdAndOwner_Login(id, ownerLogin);

        if (found.isEmpty()) {
            logNotFound("diary_entry_updated", id);
            metrics.recordDiaryEntryUpdateNotFound();
            return Optional.empty();
        }

        DiaryEntry entry = found.get();
        entry.getMovie().updateDetails(
                request.title(),
                request.releaseYear()
        );
        entry.updateDetails(
                request.status(),
                request.rating(),
                request.notes()
        );

        logChanged("diary_entry_updated", entry);
        metrics.recordDiaryEntryUpdated(entry.getStatus());
        return Optional.of(toResponse(entry));
    }

    @Transactional
    public boolean delete(String ownerLogin, Long id) {
        Optional<DiaryEntry> entryOptional =
                diaryEntryRepository.findByIdAndOwner_Login(id, ownerLogin);

        if (entryOptional.isEmpty()) {
            logNotFound("diary_entry_deleted", id);
            metrics.recordDiaryEntryDeleteNotFound();
            return false;
        }

        DiaryEntry entry = entryOptional.get();
        Movie movie = entry.getMovie();

        diaryEntryRepository.delete(entry);
        movieRepository.delete(movie);
        logChanged("diary_entry_deleted", entry);
        metrics.recordDiaryEntryDeleted();

        return true;
    }

    @Transactional(readOnly = true)
    public List<DiaryEntryResponse> findAll(
            String ownerLogin,
            DiaryStatus status
    ) {
        Sort sort = Sort.by(Sort.Direction.DESC, "id");

        List<DiaryEntry> entries = status == null
                ? diaryEntryRepository.findAllByOwner_Login(ownerLogin, sort)
                : diaryEntryRepository.findAllByOwner_LoginAndStatus(
                        ownerLogin,
                        status,
                        sort
                );

        return entries.stream()
                .map(this::toResponse)
                .toList();
    }

    private AppUser requireOwner(String ownerLogin) {
        return appUserRepository.findByLogin(ownerLogin)
                .orElseThrow(() ->
                        new AuthenticationCredentialsNotFoundException(
                                "Для продолжения выполните вход"));
    }

    private DiaryEntry saveEntry(
            AppUser owner,
            Movie movie,
            DiaryStatus status,
            Short rating,
            String notes
    ) {
        DiaryEntry entry = new DiaryEntry(
                owner,
                movie,
                status,
                rating,
                notes
        );

        movieRepository.save(movie);
        return diaryEntryRepository.save(entry);
    }

    private DiaryEntryResponse toResponse(DiaryEntry entry) {
        Movie movie = entry.getMovie();
        List<DiaryEntryResponse.CastMemberResponse> cast =
                movie.getCastMembers().stream()
                        .map(member ->
                                new DiaryEntryResponse.CastMemberResponse(
                                        member.getName()
                                ))
                        .toList();
        List<String> genres = movie.getGenres().stream()
                .map(Genre::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        return new DiaryEntryResponse(
                entry.getId(),
                movie.getTitle(),
                movie.getReleaseYear(),
                entry.getStatus(),
                entry.getRating(),
                entry.getNotes(),
                movie.getCatalogProvider(),
                movie.getCatalogId(),
                movie.getOverview(),
                movie.getPosterUrl(),
                genres,
                cast
        );
    }

    private void logCreated(DiaryEntry entry, String source) {
        var event = log.atInfo()
                .addKeyValue("event_name", "diary_entry_created")
                .addKeyValue("outcome", "success")
                .addKeyValue("source", source)
                .addKeyValue("user_id", entry.getOwner().getId())
                .addKeyValue("entry_id", entry.getId());

        if (entry.getMovie().getCatalogId() != null) {
            event
                    .addKeyValue(
                            "catalog_provider",
                            entry.getMovie().getCatalogProvider()
                    )
                    .addKeyValue(
                            "catalog_id",
                            entry.getMovie().getCatalogId()
                    );
        }

        event.log("Diary entry created");
    }

    private void logChanged(String eventName, DiaryEntry entry) {
        log.atInfo()
                .addKeyValue("event_name", eventName)
                .addKeyValue("outcome", "success")
                .addKeyValue("user_id", entry.getOwner().getId())
                .addKeyValue("entry_id", entry.getId())
                .log("Diary entry changed");
    }

    private void logNotFound(String eventName, Long entryId) {
        log.atWarn()
                .addKeyValue("event_name", eventName)
                .addKeyValue("outcome", "not_found")
                .addKeyValue("entry_id", entryId)
                .log("Diary entry was not found");
    }
}
