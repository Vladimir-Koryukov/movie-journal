package dev.vladimir.moviejournal.diary;

import dev.vladimir.moviejournal.diary.dto.CreateDiaryEntryRequest;
import dev.vladimir.moviejournal.diary.dto.DiaryEntryResponse;
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

import static org.hamcrest.Matchers.hasItem;

@WebMvcTest(DiaryEntryController.class)
class DiaryEntryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DiaryEntryService diaryEntryService;

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
                "Отличный фильм"
        );

        when(diaryEntryService.create(expectedRequest))
                .thenReturn(response);

        mockMvc.perform(post("/api/diary-entries")
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

        verify(diaryEntryService).create(expectedRequest);
    }

    @Test
    void rejectsRatingForPlannedMovie() throws Exception {
        mockMvc.perform(post("/api/diary-entries")
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
                        .value(hasItem("Only watched movies can have a rating")));

        verifyNoInteractions(diaryEntryService);
    }

    @Test
    void rejectsBlankTitle() throws Exception {
        mockMvc.perform(post("/api/diary-entries")
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
                        .value("Request validation failed"))
                .andExpect(jsonPath("$.instance")
                        .value("/api/diary-entries"))
                .andExpect(jsonPath("$.errors[*].field")
                        .value(hasItem("title")));

        verifyNoInteractions(diaryEntryService);
    }

    @Test
    void returnsNotFoundWhenEntryIsMissing() throws Exception {
        when(diaryEntryService.findById(999L))
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

        verify(diaryEntryService).findById(999L);
    }

    @Test
    void deletesEntryAndReturnsNoContent() throws Exception {
        when(diaryEntryService.delete(42L)).thenReturn(true);

        mockMvc.perform(delete("/api/diary-entries/42"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(diaryEntryService).delete(42L);
    }
}