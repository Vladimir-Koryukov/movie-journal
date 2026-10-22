package dev.vladimir.moviejournal.user.api;

import dev.vladimir.moviejournal.user.UserRegistrationService;
import dev.vladimir.moviejournal.user.UserRegistrationService.RegisteredUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRegistrationService registrationService;

    public AuthController(UserRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisteredUser register(
            @Valid @RequestBody RegisterUserRequest request
    ) {
        return registrationService.register(
                request.login(),
                request.password()
        );
    }

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken csrfToken) {
        return csrfToken;
    }

    @GetMapping("/me")
    public CurrentUserResponse currentUser(Principal principal) {
        return new CurrentUserResponse(principal.getName());
    }

    public record CurrentUserResponse(String login) {
    }
}
