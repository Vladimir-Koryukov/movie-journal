package dev.vladimir.moviejournal.movie;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GenreServiceTest {

    private final GenreRepository repository = mock(GenreRepository.class);
    private final GenreService service = new GenreService(repository);

    @Test
    void normalizesDuplicatesAndReusesExistingGenres() {
        Genre existingDrama = new Genre("Drama");

        when(repository.findByNameIgnoreCase("Drama"))
                .thenReturn(Optional.of(existingDrama));
        when(repository.findByNameIgnoreCase("Sci-Fi"))
                .thenReturn(Optional.empty());
        when(repository.save(any(Genre.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.resolve(List.of(
                " Drama ",
                "drama",
                "Sci-Fi",
                " "
        ));

        assertThat(result)
                .extracting(Genre::getName)
                .containsExactly("Drama", "Sci-Fi");
        verify(repository).findByNameIgnoreCase("Drama");
        verify(repository).findByNameIgnoreCase("Sci-Fi");
        verify(repository, never()).findByNameIgnoreCase("drama");
    }
}
