package dev.vladimir.moviejournal.movie;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class GenreService {

    private final GenreRepository genreRepository;

    public GenreService(GenreRepository genreRepository) {
        this.genreRepository = genreRepository;
    }

    public Set<Genre> resolve(List<String> names) {
        LinkedHashMap<String, String> uniqueNames = new LinkedHashMap<>();

        if (names == null) {
            return Set.of();
        }

        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }

            String normalized = name.strip();
            uniqueNames.putIfAbsent(
                    normalized.toLowerCase(Locale.ROOT),
                    normalized
            );
        }

        Set<Genre> genres = new LinkedHashSet<>();

        for (String name : uniqueNames.values()) {
            Genre genre = genreRepository.findByNameIgnoreCase(name)
                    .orElseGet(() -> genreRepository.save(new Genre(name)));
            genres.add(genre);
        }

        return genres;
    }
}
