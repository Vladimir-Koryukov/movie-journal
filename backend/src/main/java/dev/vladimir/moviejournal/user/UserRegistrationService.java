package dev.vladimir.moviejournal.user;

import dev.vladimir.moviejournal.observability.BusinessMetrics;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Service
public class UserRegistrationService {

    private static final int MIN_PASSWORD_LENGTH = 12;
    private static final int MAX_PASSWORD_BYTES = 72;
    private static final String LOGIN_UNIQUE_CONSTRAINT =
            "uq_app_users_login";
    private static final Logger log =
            LoggerFactory.getLogger(UserRegistrationService.class);

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BusinessMetrics metrics;

    public UserRegistrationService(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            BusinessMetrics metrics
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.metrics = metrics;
    }

    public RegisteredUser register(String login, String rawPassword) {
        String normalizedLogin = normalizeLogin(login);
        validatePassword(rawPassword);

        if (userRepository.existsByLogin(normalizedLogin)) {
            logRejected("duplicate_login");
            throw new LoginAlreadyExistsException();
        }

        AppUser savedUser;

        try {
            savedUser = userRepository.saveAndFlush(new AppUser(
                    normalizedLogin,
                    passwordEncoder.encode(rawPassword)
            ));
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateLogin(exception)) {
                logRejected("duplicate_login");
                throw new LoginAlreadyExistsException();
            }

            throw exception;
        }

        log.atInfo()
                .addKeyValue("event_name", "user_registration")
                .addKeyValue("outcome", "success")
                .addKeyValue("user_id", savedUser.getId())
                .log("User registered");
        metrics.recordRegistrationSuccess();

        return RegisteredUser.from(savedUser);
    }

    private String normalizeLogin(String login) {
        try {
            return new Login(login).value();
        } catch (IllegalArgumentException exception) {
            logRejected("invalid_login");
            throw exception;
        }
    }

    private void validatePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            logRejected("password_blank");
            throw new IllegalArgumentException("Укажите пароль");
        }

        if (rawPassword.length() < MIN_PASSWORD_LENGTH) {
            logRejected("password_too_short");
            throw new IllegalArgumentException(
                    "Пароль должен содержать не менее 12 символов");
        }

        int byteCount = rawPassword.getBytes(StandardCharsets.UTF_8).length;

        if (byteCount > MAX_PASSWORD_BYTES) {
            logRejected("password_too_long");
            throw new IllegalArgumentException(
                    "Пароль не должен быть длиннее 72 байт");
        }
    }

    private boolean isDuplicateLogin(Throwable exception) {
        Throwable current = exception;

        while (current != null) {
            if (current instanceof ConstraintViolationException violation
                    && LOGIN_UNIQUE_CONSTRAINT.equalsIgnoreCase(
                    violation.getConstraintName())) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }

    private void logRejected(String reason) {
        log.atWarn()
                .addKeyValue("event_name", "user_registration")
                .addKeyValue("outcome", "rejected")
                .addKeyValue("reason", reason)
                .log("User registration rejected");
        metrics.recordRegistrationRejected(reason);
    }

    public record RegisteredUser(Long id, String login, Instant createdAt) {

        private static RegisteredUser from(AppUser user) {
            return new RegisteredUser(
                    user.getId(),
                    user.getLogin(),
                    user.getCreatedAt()
            );
        }
    }
}
