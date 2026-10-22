package dev.vladimir.moviejournal.user;

import dev.vladimir.moviejournal.config.PasswordConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserRegistrationServiceTest {

    private static final String VALID_PASSWORD = "Correct horse 42";

    private final AppUserRepository users =
            mock(AppUserRepository.class);
    private final PasswordEncoder passwordEncoder =
            new PasswordConfiguration().passwordEncoder();
    private final UserRegistrationService service =
            new UserRegistrationService(users, passwordEncoder);

    @Test
    void registersUserWithNormalizedLoginAndHashedPassword() {
        when(users.saveAndFlush(any(AppUser.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserRegistrationService.RegisteredUser actual = service.register(
                " Vladimir ",
                VALID_PASSWORD
        );

        ArgumentCaptor<AppUser> userCaptor =
                ArgumentCaptor.forClass(AppUser.class);
        verify(users).saveAndFlush(userCaptor.capture());

        AppUser storedUser = userCaptor.getValue();
        assertThat(actual.login()).isEqualTo("vladimir");
        assertThat(actual.createdAt()).isNotNull();
        assertThat(storedUser.getPasswordHash())
                .isNotEqualTo(VALID_PASSWORD);
        assertThat(passwordEncoder.matches(
                VALID_PASSWORD,
                storedUser.getPasswordHash()
        )).isTrue();
    }

    @Test
    void rejectsPasswordShorterThanTwelveCharacters() {
        assertThatThrownBy(() -> service.register("vladimir", "short"))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(users);
    }

    @Test
    void rejectsPasswordExceedingSeventyTwoUtf8Bytes() {
        String password = "я".repeat(37);

        assertThatThrownBy(() -> service.register("vladimir", password))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(users);
    }

    @Test
    void rejectsAlreadyRegisteredLoginWithoutCreatingUser() {
        when(users.existsByLogin("vladimir")).thenReturn(true);

        assertThatThrownBy(() ->
                service.register("vladimir", VALID_PASSWORD))
                .isInstanceOf(LoginAlreadyExistsException.class);

        verify(users, never()).saveAndFlush(any(AppUser.class));
    }

    @Test
    void doesNotDisguiseStorageFailureAsLoginConflict() {
        IllegalStateException storageFailure =
                new IllegalStateException("Storage unavailable");

        when(users.saveAndFlush(any(AppUser.class)))
                .thenThrow(storageFailure);

        assertThatThrownBy(() ->
                service.register("vladimir", VALID_PASSWORD))
                .isSameAs(storageFailure);
    }
}
