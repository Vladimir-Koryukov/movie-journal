package dev.vladimir.moviejournal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.jdbc.PostgreSqlJdbcIndexedSessionRepositoryCustomizer;

@Configuration(proxyBeanMethods = false)
public class SessionConfiguration {

    @Bean
    public PostgreSqlJdbcIndexedSessionRepositoryCustomizer
    postgresSessionRepositoryCustomizer() {
        return new PostgreSqlJdbcIndexedSessionRepositoryCustomizer();
    }
}