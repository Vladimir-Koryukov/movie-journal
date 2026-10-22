package dev.vladimir.moviejournal.diary;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DiaryEntryRepository
        extends JpaRepository<DiaryEntry, Long> {

    @EntityGraph(attributePaths = "movie")
    Optional<DiaryEntry> findByIdAndOwner_Login(
            Long id,
            String ownerLogin
    );

    @EntityGraph(attributePaths = "movie")
    List<DiaryEntry> findAllByOwner_Login(
            String ownerLogin,
            Sort sort
    );

    @EntityGraph(attributePaths = "movie")
    List<DiaryEntry> findAllByOwner_LoginAndStatus(
            String ownerLogin,
            DiaryStatus status,
            Sort sort
    );
}