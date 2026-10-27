package dev.vladimir.moviejournal.user;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByLogin(String login);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT appUser FROM AppUser appUser WHERE appUser.login = :login")
    Optional<AppUser> findByLoginForUpdate(@Param("login") String login);

    boolean existsByLogin(String login);
}
