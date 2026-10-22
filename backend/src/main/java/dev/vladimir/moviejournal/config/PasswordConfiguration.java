package dev.vladimir.moviejournal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;

@Configuration(proxyBeanMethods = false)
public class PasswordConfiguration {

    private static final int MAX_PASSWORD_BYTES = 72;

    @Bean
    public PasswordEncoder passwordEncoder() {
        PasswordEncoder delegate =
                PasswordEncoderFactories.createDelegatingPasswordEncoder();

        return new PasswordEncoder() {

            @Override
            public String encode(CharSequence rawPassword) {
                return delegate.encode(rawPassword);
            }

            @Override
            public boolean matches(
                    CharSequence rawPassword,
                    String encodedPassword
            ) {
                if (rawPassword == null) {
                    return false;
                }

                int byteCount = rawPassword.toString()
                        .getBytes(StandardCharsets.UTF_8)
                        .length;

                return byteCount <= MAX_PASSWORD_BYTES
                        && delegate.matches(rawPassword, encodedPassword);
            }

            @Override
            public boolean upgradeEncoding(String encodedPassword) {
                return delegate.upgradeEncoding(encodedPassword);
            }
        };
    }
}