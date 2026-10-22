package dev.vladimir.moviejournal.user.api;

import dev.vladimir.moviejournal.config.PasswordConfiguration;
import dev.vladimir.moviejournal.config.SecurityConfiguration;
import dev.vladimir.moviejournal.observability.BusinessMetrics;
import dev.vladimir.moviejournal.user.AppUser;
import dev.vladimir.moviejournal.user.AppUserRepository;
import dev.vladimir.moviejournal.user.UserRegistrationService;
import dev.vladimir.moviejournal.user.AppUserDetailsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({
        SecurityConfiguration.class,
        PasswordConfiguration.class,
        AppUserDetailsService.class
})
class AuthControllerTest {

    private static final String PASSWORD = "Correct horse 42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UserRegistrationService registrationService;

    @MockitoBean
    private AppUserRepository userRepository;

    @MockitoBean
    private BusinessMetrics metrics;

    @Test
    void providesCsrfTokenWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void rejectsCurrentUserRequestWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/logout"
    })
    void rejectsPostWithoutCsrfToken(String path) throws Exception {
        mockMvc.perform(post(path))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userRepository, registrationService);
    }

    @Test
    void rejectsIncorrectPassword() throws Exception {
        stubRegisteredUser();

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("login", "vladimir")
                        .param("password", "incorrect password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail")
                        .value("Неверное имя пользователя или пароль"))
                .andExpect(unauthenticated());
    }

    @Test
    void rejectsMalformedLoginWithoutServerError() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("login", "!!!")
                        .param("password", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail")
                        .value("Неверное имя пользователя или пароль"))
                .andExpect(unauthenticated());

        verifyNoInteractions(userRepository);
    }

    @Test
    void explainsInvalidRegistrationPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "login": "vladimir",
                                  "password": "short"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("Проверьте заполнение полей"))
                .andExpect(jsonPath("$.errors[0].field")
                        .value("password"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value(
                                "Пароль должен содержать "
                                        + "от 12 до 72 символов"));

        verifyNoInteractions(registrationService);
    }

    @Test
    void maintainsAuthenticatedSessionAndInvalidatesItOnLogout()
            throws Exception {
        stubRegisteredUser();

        var loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("login", " Vladimir ")
                        .param("password", PASSWORD))
                .andExpect(status().isNoContent())
                .andExpect(authenticated().withUsername("vladimir"))
                .andReturn();

        var session = (MockHttpSession) loginResult
                .getRequest()
                .getSession(false);

        assertNotNull(session);

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("vladimir"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mockMvc.perform(post("/api/auth/logout")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(unauthenticated());

        assertTrue(session.isInvalid());

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "я"})
    void rejectsPasswordSuffixBeyondSeventyTwoBytes(String symbol)
            throws Exception {
        int bytesPerSymbol = symbol.getBytes(StandardCharsets.UTF_8).length;
        String password = symbol.repeat(72 / bytesPerSymbol);

        AppUser user = new AppUser(
                "vladimir",
                passwordEncoder.encode(password)
        );

        when(userRepository.findByLogin("vladimir"))
                .thenReturn(Optional.of(user));

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("login", "vladimir")
                        .param("password", password + "x"))
                .andExpect(status().isUnauthorized())
                .andExpect(unauthenticated());

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("login", "vladimir")
                        .param("password", password))
                .andExpect(status().isNoContent())
                .andExpect(authenticated().withUsername("vladimir"));
    }

    private void stubRegisteredUser() {
        AppUser user = new AppUser(
                "vladimir",
                passwordEncoder.encode(PASSWORD)
        );

        when(userRepository.findByLogin("vladimir"))
                .thenReturn(Optional.of(user));
    }
}
