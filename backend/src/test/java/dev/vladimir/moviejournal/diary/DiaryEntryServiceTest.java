package dev.vladimir.moviejournal.diary;

import dev.vladimir.moviejournal.catalog.CatalogCastMember;
import dev.vladimir.moviejournal.catalog.CatalogMovie;
import dev.vladimir.moviejournal.diary.api.CreateDiaryEntryRequest;
import dev.vladimir.moviejournal.diary.api.DiaryEntryResponse;
import dev.vladimir.moviejournal.diary.api.ImportDiaryEntryRequest;
import dev.vladimir.moviejournal.diary.api.UpdateDiaryEntryRequest;
import dev.vladimir.moviejournal.movie.Genre;
import dev.vladimir.moviejournal.movie.GenreService;
import dev.vladimir.moviejournal.movie.Movie;
import dev.vladimir.moviejournal.movie.MovieRepository;
import dev.vladimir.moviejournal.observability.BusinessMetrics;
import dev.vladimir.moviejournal.user.AppUser;
import dev.vladimir.moviejournal.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiaryEntryServiceTest {

    private final MovieRepository movies = mock(MovieRepository.class);
    private final DiaryEntryRepository entries =
            mock(DiaryEntryRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final GenreService genres = mock(GenreService.class);
    private final BusinessMetrics metrics = mock(BusinessMetrics.class);

    private final DiaryEntryService service =
            new DiaryEntryService(
                    movies,
                    entries,
                    users,
                    genres,
                    metrics
            );

    @Test
    void assignsAuthenticatedUserToNewEntry() {
        AppUser alice = new AppUser("alice", "test-password-hash");

        when(users.findByLogin("alice")).thenReturn(Optional.of(alice));
        when(genres.resolve(List.of("Science Fiction")))
                .thenReturn(Set.of(new Genre("Science Fiction")));
        when(entries.save(any(DiaryEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.create("alice", new CreateDiaryEntryRequest(
                "Интерстеллар",
                2014,
                DiaryStatus.PLANNED,
                null,
                null,
                List.of("Science Fiction")
        ));

        ArgumentCaptor<DiaryEntry> savedEntry =
                ArgumentCaptor.forClass(DiaryEntry.class);

        verify(entries).save(savedEntry.capture());
        assertThat(savedEntry.getValue().getOwner()).isSameAs(alice);
        assertThat(savedEntry.getValue().getMovie().getGenres())
                .extracting(Genre::getName)
                .containsExactly("Science Fiction");
    }

    @Test
    void savesCatalogMetadataAndCastWithImportedMovie() {
        AppUser alice = new AppUser("alice", "test-password-hash");
        Genre drama = new Genre("Drama");
        CatalogMovie catalogMovie = new CatalogMovie(
                "omdb",
                "tt0816692",
                "Interstellar",
                2014,
                "A team travels through a wormhole.",
                "https://example.com/poster.jpg",
                List.of("Adventure", "Drama", "Sci-Fi"),
                List.of(new CatalogCastMember("Matthew McConaughey"))
        );

        when(users.findByLoginForUpdate("alice"))
                .thenReturn(Optional.of(alice));
        when(genres.resolve(catalogMovie.genres()))
                .thenReturn(Set.of(drama));
        when(entries.save(any(DiaryEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DiaryEntryResponse response = service.createFromCatalog(
                "alice",
                new ImportDiaryEntryRequest(
                        "tt0816692",
                        DiaryStatus.PLANNED,
                        null,
                        null
                ),
                catalogMovie
        );

        ArgumentCaptor<Movie> movieCaptor =
                ArgumentCaptor.forClass(Movie.class);
        verify(movies).save(movieCaptor.capture());

        Movie movie = movieCaptor.getValue();
        assertThat(movie.getCatalogProvider()).isEqualTo("omdb");
        assertThat(movie.getCatalogId()).isEqualTo("tt0816692");
        assertThat(movie.getOverview())
                .isEqualTo("A team travels through a wormhole.");
        assertThat(movie.getGenres())
                .extracting(Genre::getName)
                .containsExactly("Drama");
        assertThat(movie.getCastMembers())
                .extracting(member -> member.getName())
                .containsExactly("Matthew McConaughey");
        assertThat(response.cast())
                .extracting(DiaryEntryResponse.CastMemberResponse::name)
                .containsExactly("Matthew McConaughey");
        assertThat(response.genres()).containsExactly("Drama");
    }

    @Test
    void rejectsDuplicateCatalogMovieForSameOwner() {
        AppUser alice = new AppUser("alice", "test-password-hash");
        CatalogMovie catalogMovie = new CatalogMovie(
                "omdb",
                "tt0816692",
                "Interstellar",
                2014,
                "A team travels through a wormhole.",
                "https://example.com/poster.jpg",
                List.of("Adventure"),
                List.of()
        );

        when(users.findByLoginForUpdate("alice"))
                .thenReturn(Optional.of(alice));
        when(entries.existsCatalogMovie(
                "alice",
                "omdb",
                "tt0816692"
        )).thenReturn(true);

        assertThatThrownBy(() -> service.createFromCatalog(
                "alice",
                new ImportDiaryEntryRequest(
                        "tt0816692",
                        DiaryStatus.PLANNED,
                        null,
                        null
                ),
                catalogMovie
        ))
                .isInstanceOf(DuplicateCatalogMovieException.class)
                .hasMessageContaining("Interstellar")
                .hasMessageContaining("уже добавлен");

        verify(metrics).recordDuplicateCatalogImport("omdb");
        verify(movies, never()).save(any(Movie.class));
        verify(entries, never()).save(any(DiaryEntry.class));
    }

    @Test
    void preventsReadingUpdatingAndDeletingAnotherUsersEntry() {
        DiaryEntry aliceEntry = entry("alice", "Alice movie");

        // The entry exists globally, but belongs only to Alice.
        when(entries.findById(42L))
                .thenReturn(Optional.of(aliceEntry));
        when(entries.findByIdAndOwner_Login(42L, "alice"))
                .thenReturn(Optional.of(aliceEntry));
        when(entries.findByIdAndOwner_Login(42L, "bob"))
                .thenReturn(Optional.empty());

        assertThat(service.findById("alice", 42L)).isPresent();
        assertThat(service.findById("bob", 42L)).isEmpty();

        UpdateDiaryEntryRequest update = new UpdateDiaryEntryRequest(
                "Changed title",
                2020,
                DiaryStatus.PLANNED,
                null,
                "Changed notes",
                List.of("Drama")
        );

        assertThat(service.update("bob", 42L, update)).isEmpty();
        assertThat(service.delete("bob", 42L)).isFalse();

        assertThat(aliceEntry.getMovie().getTitle()).isEqualTo("Alice movie");
        assertThat(aliceEntry.getStatus()).isEqualTo(DiaryStatus.WATCHED);
        assertThat(aliceEntry.getNotes()).isEqualTo("Private notes");

        verify(entries, never()).delete(any(DiaryEntry.class));
        verify(movies, never()).delete(any(Movie.class));
    }

    @Test
    void limitsBothUnfilteredAndFilteredListsToOwner() {
        DiaryEntry aliceEntry = entry("alice", "Alice movie");
        DiaryEntry bobEntry = entry("bob", "Bob movie");

        when(entries.search(
                "bob",
                null,
                false,
                "",
                null,
                null,
                null,
                null,
                "ADDED_DESC"
        )).thenReturn(List.of(bobEntry));
        when(entries.search(
                "bob",
                DiaryStatus.WATCHED,
                true,
                "Bob",
                2000,
                2025,
                (short) 7,
                (short) 10,
                "RATING_DESC"
        )).thenReturn(List.of(bobEntry));

        assertThat(service.findAll(
                "bob",
                null,
                null,
                null,
                null,
                null,
                null,
                DiaryEntrySort.ADDED_DESC
        ))
                .extracting(DiaryEntryResponse::title)
                .containsExactly("Bob movie");

        assertThat(service.findAll(
                "bob",
                DiaryStatus.WATCHED,
                " Bob ",
                2000,
                2025,
                (short) 7,
                (short) 10,
                DiaryEntrySort.RATING_DESC
        ))
                .extracting(DiaryEntryResponse::title)
                .containsExactly("Bob movie");

        verify(metrics).recordDiarySearch(false, 1);
        verify(metrics).recordDiarySearch(true, 1);
    }

    private DiaryEntry entry(String ownerLogin, String title) {
        return new DiaryEntry(
                new AppUser(ownerLogin, "test-password-hash"),
                new Movie(title, 2014),
                DiaryStatus.WATCHED,
                (short) 8,
                "Private notes"
        );
    }
}
