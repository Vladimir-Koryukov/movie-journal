package dev.vladimir.moviejournal.diary;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DiaryEntryRepository
        extends JpaRepository<DiaryEntry, Long> {

    @EntityGraph(attributePaths = {
            "movie",
            "movie.genres",
            "movie.castMembers"
    })
    Optional<DiaryEntry> findByIdAndOwner_Login(
            Long id,
            String ownerLogin
    );

    @Query("""
            SELECT CASE WHEN COUNT(entry) > 0 THEN TRUE ELSE FALSE END
            FROM DiaryEntry entry
            WHERE entry.owner.login = :ownerLogin
              AND entry.movie.catalogProvider = :catalogProvider
              AND entry.movie.catalogId = :catalogId
            """)
    boolean existsCatalogMovie(
            @Param("ownerLogin") String ownerLogin,
            @Param("catalogProvider") String catalogProvider,
            @Param("catalogId") String catalogId
    );

    @EntityGraph(attributePaths = {
            "movie",
            "movie.genres",
            "movie.castMembers"
    })
    @Query("""
            SELECT entry
            FROM DiaryEntry entry
            JOIN entry.movie movie
            WHERE entry.owner.login = :ownerLogin
              AND (:status IS NULL OR entry.status = :status)
              AND (
                    :queryEnabled = FALSE
                    OR LOWER(movie.title) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR EXISTS (
                        SELECT 1
                        FROM Movie castMovie
                        JOIN castMovie.castMembers castMember
                        WHERE castMovie = movie
                          AND LOWER(castMember.name)
                              LIKE LOWER(CONCAT('%', :query, '%'))
                    )
                    OR EXISTS (
                        SELECT 1
                        FROM Movie genreMovie
                        JOIN genreMovie.genres genre
                        WHERE genreMovie = movie
                          AND LOWER(genre.name)
                              LIKE LOWER(CONCAT('%', :query, '%'))
                    )
              )
              AND (:yearFrom IS NULL OR movie.releaseYear >= :yearFrom)
              AND (:yearTo IS NULL OR movie.releaseYear <= :yearTo)
              AND (:ratingFrom IS NULL OR entry.rating >= :ratingFrom)
              AND (:ratingTo IS NULL OR entry.rating <= :ratingTo)
            ORDER BY
              CASE WHEN :sort = 'ADDED_ASC' THEN entry.id END ASC,
              CASE WHEN :sort = 'YEAR_DESC' THEN movie.releaseYear END DESC NULLS LAST,
              CASE WHEN :sort = 'YEAR_ASC' THEN movie.releaseYear END ASC NULLS LAST,
              CASE WHEN :sort = 'RATING_DESC' THEN entry.rating END DESC NULLS LAST,
              CASE WHEN :sort = 'RATING_ASC' THEN entry.rating END ASC NULLS LAST,
              CASE WHEN :sort = 'TITLE_ASC' THEN LOWER(movie.title) END ASC,
              entry.id DESC
            """)
    List<DiaryEntry> search(
            @Param("ownerLogin") String ownerLogin,
            @Param("status") DiaryStatus status,
            @Param("queryEnabled") boolean queryEnabled,
            @Param("query") String query,
            @Param("yearFrom") Integer yearFrom,
            @Param("yearTo") Integer yearTo,
            @Param("ratingFrom") Short ratingFrom,
            @Param("ratingTo") Short ratingTo,
            @Param("sort") String sort
    );
}
