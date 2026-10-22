package dev.vladimir.moviejournal.config;

import dev.vladimir.moviejournal.observability.BusinessMetrics;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    private static final Logger log =
            LoggerFactory.getLogger(SecurityConfiguration.class);

    private final ObjectMapper objectMapper;
    private final BusinessMetrics metrics;

    public SecurityConfiguration(
            ObjectMapper objectMapper,
            BusinessMetrics metrics
    ) {
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) throws Exception {
        var authenticationProvider =
                new DaoAuthenticationProvider(userDetailsService);
        authenticationProvider.setPasswordEncoder(passwordEncoder);

        http
                .authenticationProvider(authenticationProvider)
                .csrf(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionFixation(fixation ->
                                fixation.changeSessionId())
                )
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/auth/csrf",
                                "/actuator/health",
                                "/actuator/health/liveness",
                                "/actuator/health/readiness",
                                "/actuator/prometheus"
                        )
                        .permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/register",
                                "/api/auth/login"
                        )
                        .permitAll()

                        .requestMatchers("/api/**")
                        .authenticated()

                        .anyRequest()
                        .denyAll()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                writeProblem(
                                        response,
                                        HttpStatus.UNAUTHORIZED,
                                        "Для продолжения выполните вход"
                                )
                        )
                        .accessDeniedHandler((request, response, exception) ->
                                writeProblem(
                                        response,
                                        HttpStatus.FORBIDDEN,
                                        "Недостаточно прав для выполнения действия"
                                )
                        )
                )
                .formLogin(form -> form
                        .loginPage("/api/auth/login")
                        .loginProcessingUrl("/api/auth/login")
                        .usernameParameter("login")
                        .passwordParameter("password")
                        .successHandler((request, response, authentication) -> {
                            log.atInfo()
                                    .addKeyValue("event_name", "user_login")
                                    .addKeyValue("outcome", "success")
                                    .log("User logged in");
                            metrics.recordLoginSuccess();

                            response.setStatus(HttpStatus.NO_CONTENT.value());
                        })
                        .failureHandler((request, response, exception) -> {
                            boolean serviceFailure =
                                    exception instanceof AuthenticationServiceException;

                            log.atWarn()
                                    .addKeyValue("event_name", "user_login")
                                    .addKeyValue(
                                            "outcome",
                                            serviceFailure
                                                    ? "error"
                                                    : "invalid_credentials"
                                    )
                                    .log("Login failed");

                            if (serviceFailure) {
                                metrics.recordLoginError();
                            } else {
                                metrics.recordLoginInvalidCredentials();
                            }

                            writeProblem(
                                    response,
                                    serviceFailure
                                            ? HttpStatus.SERVICE_UNAVAILABLE
                                            : HttpStatus.UNAUTHORIZED,
                                    serviceFailure
                                            ? "Сервис авторизации временно недоступен"
                                            : "Неверное имя пользователя или пароль"
                            );
                        })
                )
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .logoutSuccessHandler((request, response, authentication) -> {
                            if (authentication != null) {
                                log.atInfo()
                                        .addKeyValue("event_name", "user_logout")
                                        .addKeyValue("outcome", "success")
                                        .log("User logged out");
                            }

                            response.setStatus(HttpStatus.NO_CONTENT.value());
                        })
                );

        return http.build();
    }

    private void writeProblem(
            HttpServletResponse response,
            HttpStatus status,
            String detail
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        objectMapper.writeValue(
                response.getOutputStream(),
                ProblemDetail.forStatusAndDetail(status, detail)
        );
    }
}
