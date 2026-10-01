package dev.vladimir.moviejournal;

import dev.vladimir.moviejournal.admin.MigrationConfiguration;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer;

@SpringBootApplication
public class MovieJournalApplication {

	public static void main(String[] args) {
		var arguments = new DefaultApplicationArguments(args);

		if (arguments.containsOption("migrate")) {
			runMigrations(args);
			return;
		}

		SpringApplication.run(MovieJournalApplication.class, args);
	}

	private static void runMigrations(String[] args) {
		var application = new SpringApplication(
				MigrationConfiguration.class
		);

		application.setWebApplicationType(WebApplicationType.NONE);
		application.setAdditionalProfiles("migration");

		try (var context = application.run(args)) {
			context.getBean(FlywayMigrationInitializer.class);
		}
	}
}