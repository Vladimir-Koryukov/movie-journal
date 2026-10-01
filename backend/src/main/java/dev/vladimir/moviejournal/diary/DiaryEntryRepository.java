package dev.vladimir.moviejournal.diary;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DiaryEntryRepository
        extends JpaRepository<DiaryEntry, Long> {

    @Override
    @EntityGraph(attributePaths = "movie")
    List<DiaryEntry> findAll(Sort sort);

    @Override
    @EntityGraph(attributePaths = "movie")
    Optional<DiaryEntry> findById(Long id);

    @EntityGraph(attributePaths = "movie")
    List<DiaryEntry> findAllByStatus(DiaryStatus status, Sort sort);
}