package dev.vladimir.moviejournal.observability;

import dev.vladimir.moviejournal.diary.DiaryStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessMetricsTest {

    private final SimpleMeterRegistry registry =
            new SimpleMeterRegistry();
    private final BusinessMetrics metrics =
            new BusinessMetrics(registry);

    @Test
    void recordsBoundedBusinessMetrics() {
        metrics.recordRegistrationSuccess();
        metrics.recordRegistrationRejected("duplicate_login");
        metrics.recordLoginSuccess();
        metrics.recordLoginInvalidCredentials();
        metrics.recordManualDiaryEntryCreated(DiaryStatus.PLANNED);
        metrics.recordCatalogDiaryEntryCreated(DiaryStatus.WATCHED);
        metrics.recordDiaryEntryUpdated(DiaryStatus.WATCHED);
        metrics.recordDiaryEntryUpdateNotFound();
        metrics.recordDiaryEntryDeleted();
        metrics.recordDiaryEntryDeleteNotFound();
        metrics.recordCatalogSearch(3, 1_000_000L);
        metrics.recordCatalogSearch(0, 2_000_000L);
        metrics.recordCatalogSearchFailure(3_000_000L);
        metrics.recordCatalogDetailsSuccess(4_000_000L);
        metrics.recordCatalogDetailsFailure(5_000_000L);

        assertThat(registry.get("movie_journal.user.registrations")
                .tags("outcome", "success", "reason", "none")
                .counter().count()).isEqualTo(1);
        assertThat(registry.get("movie_journal.user.logins")
                .tag("outcome", "invalid_credentials")
                .counter().count()).isEqualTo(1);
        assertThat(registry.get("movie_journal.diary.entries.added")
                .tags("source", "catalog", "status", "watched")
                .counter().count()).isEqualTo(1);
        assertThat(registry.get("movie_journal.diary.entries.updated")
                .tags("outcome", "not_found", "status", "unknown")
                .counter().count()).isEqualTo(1);
        assertThat(registry.get("movie_journal.diary.entries.deleted")
                .tag("outcome", "success")
                .counter().count()).isEqualTo(1);
        assertThat(registry.get("movie_journal.catalog.searches")
                .tags("provider", "omdb", "outcome", "success")
                .counter().count()).isEqualTo(1);
        assertThat(registry.get("movie_journal.catalog.search.results")
                .tags("provider", "omdb", "outcome", "success")
                .summary().totalAmount()).isEqualTo(3);
        assertThat(registry.get("movie_journal.catalog.request.duration")
                .tags(
                        "provider", "omdb",
                        "operation", "details",
                        "outcome", "error"
                )
                .timer().count()).isEqualTo(1);
    }
}
