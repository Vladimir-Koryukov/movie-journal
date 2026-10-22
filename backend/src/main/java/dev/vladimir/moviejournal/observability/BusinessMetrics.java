package dev.vladimir.moviejournal.observability;

import dev.vladimir.moviejournal.diary.DiaryStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Component
public class BusinessMetrics {

    private static final String NONE = "none";
    private static final String UNKNOWN = "unknown";

    private final MeterRegistry registry;

    public BusinessMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordRegistrationSuccess() {
        registrationCounter("success", NONE).increment();
    }

    public void recordRegistrationRejected(String reason) {
        registrationCounter("rejected", reason).increment();
    }

    public void recordLoginSuccess() {
        loginCounter("success").increment();
    }

    public void recordLoginInvalidCredentials() {
        loginCounter("invalid_credentials").increment();
    }

    public void recordLoginError() {
        loginCounter("error").increment();
    }

    public void recordManualDiaryEntryCreated(DiaryStatus status) {
        diaryEntryCreatedCounter("manual", status).increment();
    }

    public void recordCatalogDiaryEntryCreated(DiaryStatus status) {
        diaryEntryCreatedCounter("catalog", status).increment();
    }

    public void recordDiaryEntryUpdated(DiaryStatus status) {
        diaryEntryUpdatedCounter("success", statusValue(status))
                .increment();
    }

    public void recordDiaryEntryUpdateNotFound() {
        diaryEntryUpdatedCounter("not_found", UNKNOWN).increment();
    }

    public void recordDiaryEntryDeleted() {
        diaryEntryDeletedCounter("success").increment();
    }

    public void recordDiaryEntryDeleteNotFound() {
        diaryEntryDeletedCounter("not_found").increment();
    }

    public void recordCatalogSearch(
            int resultCount,
            long durationNanos
    ) {
        String outcome = resultCount == 0 ? "empty" : "success";

        catalogSearchCounter(outcome).increment();
        catalogSearchResults(outcome).record(resultCount);
        catalogRequestTimer("search", outcome)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordCatalogSearchFailure(long durationNanos) {
        catalogSearchCounter("error").increment();
        catalogRequestTimer("search", "error")
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordCatalogDetailsSuccess(long durationNanos) {
        catalogDetailsCounter("success").increment();
        catalogRequestTimer("details", "success")
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordCatalogDetailsFailure(long durationNanos) {
        catalogDetailsCounter("error").increment();
        catalogRequestTimer("details", "error")
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    private Counter registrationCounter(String outcome, String reason) {
        return Counter.builder("movie_journal.user.registrations")
                .description("User registration attempts")
                .tag("outcome", outcome)
                .tag("reason", reason)
                .register(registry);
    }

    private Counter loginCounter(String outcome) {
        return Counter.builder("movie_journal.user.logins")
                .description("User login attempts")
                .tag("outcome", outcome)
                .register(registry);
    }

    private Counter diaryEntryCreatedCounter(
            String source,
            DiaryStatus status
    ) {
        return Counter.builder("movie_journal.diary.entries.added")
                .description("Successfully created diary entries")
                .tag("source", source)
                .tag("status", statusValue(status))
                .register(registry);
    }

    private Counter diaryEntryUpdatedCounter(
            String outcome,
            String status
    ) {
        return Counter.builder("movie_journal.diary.entries.updated")
                .description("Diary entry update attempts")
                .tag("outcome", outcome)
                .tag("status", status)
                .register(registry);
    }

    private Counter diaryEntryDeletedCounter(String outcome) {
        return Counter.builder("movie_journal.diary.entries.deleted")
                .description("Diary entry deletion attempts")
                .tag("outcome", outcome)
                .register(registry);
    }

    private Counter catalogSearchCounter(String outcome) {
        return Counter.builder("movie_journal.catalog.searches")
                .description("Movie catalog searches")
                .tag("provider", "omdb")
                .tag("outcome", outcome)
                .register(registry);
    }

    private Counter catalogDetailsCounter(String outcome) {
        return Counter.builder("movie_journal.catalog.details")
                .description("Movie catalog details requests")
                .tag("provider", "omdb")
                .tag("outcome", outcome)
                .register(registry);
    }

    private DistributionSummary catalogSearchResults(String outcome) {
        return DistributionSummary.builder(
                        "movie_journal.catalog.search.results")
                .description("Movies returned by catalog searches")
                .baseUnit("movies")
                .tag("provider", "omdb")
                .tag("outcome", outcome)
                .register(registry);
    }

    private Timer catalogRequestTimer(String operation, String outcome) {
        return Timer.builder("movie_journal.catalog.request.duration")
                .description("Movie catalog request duration")
                .publishPercentileHistogram()
                .tag("provider", "omdb")
                .tag("operation", operation)
                .tag("outcome", outcome)
                .register(registry);
    }

    private String statusValue(DiaryStatus status) {
        return status.name().toLowerCase(Locale.ROOT);
    }
}
