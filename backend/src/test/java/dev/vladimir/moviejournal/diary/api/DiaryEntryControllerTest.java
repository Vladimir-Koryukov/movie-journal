package dev.vladimir.moviejournal.diary.api;

import dev.vladimir.moviejournal.diary.CatalogImportService;
import dev.vladimir.moviejournal.diary.DiaryEntryService;
import dev.vladimir.moviejournal.diary.DiaryStatus;
import dev.vladimir.moviejournal.observability.BusinessMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import dev.vladimir.moviejournal.config.PasswordConfiguration;
import dev.vladimir.moviejournal.config.SecurityConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import static org.hamcrest.Matchers.hasItem;

@WebMvcTest(DiaryEntryController.class)
@Import({
        SecurityConfiguration.class,
        PasswordConfiguration.class
})
@WithMockUser(username = "vladimir")
class DiaryEntryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DiaryEntryService diaryEntryService;

    @MockitoBean
    private CatalogImportService catalogImportService;

    @MockitoBean
    private BusinessMetrics metrics;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void createsEntryAndReturnsCreated() throws Exception {
        CreateDiaryEntryRequest expectedRequest =
                new CreateDiaryEntryRequest(
                        "Интерстеллар",
                        2014,
                        DiaryStatus.WATCHED,
                        (short) 9,
                        "Отличный фильм"
                );

        DiaryEntryResponse response = new DiaryEntryResponse(
                42L,
                "Интерстеллар",
                2014,
                DiaryStatus.WATCHED,
                (short) 9,
                "Отличный фильм",
                null,
                null,
                null,
                null,
                java.util.List.of(),
                java.util.List.of()
        );

        when(diaryEntryService.create("vladimir", expectedRequest))
                .thenReturn(response);

        mockMvc.perform(post("/api/diary-entries")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Интерстеллар",
                                  "releaseYear": 2014,
                                  "status": "WATCHED",
                                  "rating": 9,
                                  "notes": "Отличный фильм"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.title").value("Интерстеллар"))
                .andExpect(jsonPath("$.releaseYear").value(2014))
                .andExpect(jsonPath("$.status").value("WATCHED"))
                .andExpect(jsonPath("$.rating").value(9))
                .andExpect(jsonPath("$.notes").value("Отличный фильм"));

        verify(diaryEntryService).create("vladimir", expectedRequest);
    }

    @Test
    void importsSelectedCatalogMovie() throws Exception {
        ImportDiaryEntryRequest expectedRequest =
                new ImportDiaryEntryRequest(
                        "tt0816692",
                        DiaryStatus.PLANNED,
                        null,
                        "Посмотреть позже"
                );
        DiaryEntryResponse response = new DiaryEntryResponse(
                43L,
                "Interstellar",
                2014,
                DiaryStatus.PLANNED,
                null,
                "Посмотреть позже",
                "omdb",
                "tt0816692",
                "A team travels through a wormhole.",
                "https://example.com/poster.jpg",
                java.util.List.of("Adventure", "Drama", "Sci-Fi"),
                java.util.List.of()
        );

        when(catalogImportService.importMovie(
                "vladimir",
                expectedRequest
        )).thenReturn(response);

        mockMvc.perform(post("/api/diary-entries/from-catalog")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "catalogId": "tt0816692",
                                  "status": "PLANNED",
                                  "notes": "Посмотреть позже"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(43))
                .andExpect(jsonPath("$.catalogProvider").value("omdb"))
                .andExpect(jsonPath("$.catalogId").value("tt0816692"))
                .andExpect(jsonPath("$.genres[0]").value("Adventure"))
                .andExpect(jsonPath("$.title").value("Interstellar"));

        verify(catalogImportService).importMovie(
                "vladimir",
                expectedRequest
        );
    }

    @Test
    void rejectsRatingForPlannedMovie() throws Exception {
        mockMvc.perform(post("/api/diary-entries")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Интерстеллар",
                                  "status": "PLANNED",
                                  "rating": 9
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[*].field")
                        .value(hasItem("ratingAllowed")))
                .andExpect(jsonPath("$.errors[*].message")
                        .value(hasItem(
                                "Оценку можно поставить только "
                                        + "просмотренному фильму")));

        verifyNoInteractions(diaryEntryService);
    }

    @Test
    void rejectsBlankTitle() throws Exception {
        mockMvc.perform(post("/api/diary-entries")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "   ",
                                  "status": "PLANNED"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail")
                        .value("Проверьте заполнение полей"))
                .andExpect(jsonPath("$.instance")
                        .value("/api/diary-entries"))
                .andExpect(jsonPath("$.errors[*].field")
                        .value(hasItem("title")));

        verifyNoInteractions(diaryEntryService);
    }

    @Test
    void returnsNotFoundWhenEntryIsMissing() throws Exception {
        when(diaryEntryService.findById("vladimir", 999L))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/diary-entries/999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail")
                        .value("Diary entry not found"))
                .andExpect(jsonPath("$.instance")
                        .value("/api/diary-entries/999"));

        verify(diaryEntryService).findById("vladimir", 999L);
    }

    @Test
    void deletesEntryAndReturnsNoContent() throws Exception {
        when(diaryEntryService.delete("vladimir", 42L))
                .thenReturn(true);

        mockMvc.perform(delete("/api/diary-entries/42")
                        .with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(diaryEntryService).delete("vladimir", 42L);
    }
}
