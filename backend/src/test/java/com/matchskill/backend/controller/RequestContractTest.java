package com.matchskill.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.matchskill.backend.dto.feedback.ExchangeFeedbackResponse;
import com.matchskill.backend.exception.GlobalExceptionHandler;
import com.matchskill.backend.service.AvailabilityService;
import com.matchskill.backend.service.ExchangeService;
import com.matchskill.backend.service.FeedbackService;
import com.matchskill.backend.service.MatchService;
import com.matchskill.backend.service.SkillService;
import com.matchskill.backend.service.UserSkillService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class RequestContractTest {
    @Mock private AvailabilityService availability;
    @Mock private UserSkillService mySkills;
    @Mock private SkillService skills;
    @Mock private ExchangeService exchanges;
    @Mock private FeedbackService feedback;
    @Mock private MatchService matches;
    private MockMvc mvc;
    private final UUID userId = UUID.randomUUID();
    private final UsernamePasswordAuthenticationToken principal =
            new UsernamePasswordAuthenticationToken(userId, null, List.of());

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AvailabilityController(availability),
                        new MySkillsController(mySkills), new SkillController(skills),
                        new ExchangeController(exchanges, feedback), new MatchController(matches))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void nestedMissingAndNullAvailabilityValuesReturn400BeforeService() throws Exception {
        for (String json : List.of("{\"windows\":[null]}",
                "{\"windows\":[{\"dayOfWeek\":\"MONDAY\"}]}",
                "{\"windows\":null}", "{\"windows\":[],\"timeZone\":\"Invalid/Zone\"}",
                "{\"windows\":[],\"timeZone\":\"   \"}")) {
            mvc.perform(put("/me/availability").principal(principal)
                            .contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        verifyNoInteractions(availability);
    }

    @Test
    void nullSkillIdReturns400BeforeService() throws Exception {
        mvc.perform(put("/me/skills").principal(principal).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeredSkillIds\":[null],\"wantedSkillIds\":[]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(mySkills);
    }

    @Test
    void malformedJsonUuidEnumAndMissingQueryAreClientErrors() throws Exception {
        mvc.perform(put("/me/availability").principal(principal).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/exchanges/not-a-uuid").principal(principal))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/exchanges?status=UNKNOWN").principal(principal))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/search").principal(principal))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void paginationRejectsInvalidBoundsAndCapsOversizedRequests() throws Exception {
        for (String query : List.of("page=-1", "size=0", "size=-1", "page=2147483647&size=100")) {
            mvc.perform(get("/skills?" + query)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        when(skills.search(any(), any())).thenAnswer(call -> Page.empty(call.getArgument(1)));
        mvc.perform(get("/skills?size=1000000"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void unsupportedMethodRetains405AndJsonEnvelope() throws Exception {
        mvc.perform(post("/skills")).andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("REQUEST_ERROR"));
    }

    @Test
    void availabilityExtensionKeepsArrayAndPassesOptionalZone() throws Exception {
        mvc.perform(put("/me/availability").principal(principal).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"windows\":[],\"timeZone\":\"America/New_York\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
        verify(availability).replaceMyAvailability(userId, List.of(), "America/New_York");
        mvc.perform(put("/me/availability").principal(principal).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"windows\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
        verify(availability).replaceMyAvailability(userId, List.of(), null);
    }

    @Test
    void feedbackStateUsesExplicitNullsAndSubmissionFlag() throws Exception {
        UUID exchangeId = UUID.randomUUID();
        when(feedback.getForExchange(eq(userId), eq(exchangeId)))
                .thenReturn(new ExchangeFeedbackResponse(null, null, true));
        mvc.perform(get("/exchanges/" + exchangeId + "/feedback").principal(principal))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mine").isEmpty())
                .andExpect(jsonPath("$.theirs").isEmpty()).andExpect(jsonPath("$.counterpartSubmitted").value(true));
    }
}
