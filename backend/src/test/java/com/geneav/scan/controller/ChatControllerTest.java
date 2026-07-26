package com.geneav.scan.controller;

import com.geneav.scan.dto.ChatReply;
import com.geneav.scan.dto.ChatSuggestion;
import com.geneav.scan.service.ChatProperties;
import com.geneav.scan.service.ChatService;
import com.geneav.scan.web.ApiExceptionHandler;
import com.geneav.scan.web.ScanException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatControllerTest {

    private static final ChatSuggestion SUGGESTION =
            new ChatSuggestion("file-size", "What is the maximum file size?", "Up to 25 MB per upload.");

    private final ChatService chatService = Mockito.mock(ChatService.class);
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ChatController(chatService, suggesting(SUGGESTION)))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    private static ChatProperties suggesting(ChatSuggestion... suggestions) {
        ChatProperties props = new ChatProperties();
        props.setSuggestions(List.of(suggestions));
        return props;
    }

    @Test
    void validConversationReturnsReply() throws Exception {
        Mockito.when(chatService.answer(anyList()))
                .thenReturn(new ChatReply("geneav scans documents for malware.", "gpt-4o-mini"));

        mvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messages\":[{\"role\":\"user\",\"content\":\"What is geneav?\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("geneav scans documents for malware."))
                .andExpect(jsonPath("$.model").value("gpt-4o-mini"));
    }

    @Test
    void emptyMessagesReturns400() throws Exception {
        mvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messages\":[]}"))
                .andExpect(status().isBadRequest());

        Mockito.verify(chatService, Mockito.never()).answer(anyList());
    }

    @Test
    void malformedBodyReturns400() throws Exception {
        mvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unconfiguredServiceReturns503() throws Exception {
        Mockito.when(chatService.answer(anyList()))
                .thenThrow(new ScanException(HttpStatus.SERVICE_UNAVAILABLE, "Chat is not configured."));

        mvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Chat is not configured."));
    }

    @Test
    void upstreamErrorReturns502() throws Exception {
        Mockito.when(chatService.answer(anyList()))
                .thenThrow(new ScanException(HttpStatus.BAD_GATEWAY, "Chat service error. Please try again."));

        mvc.perform(post("/api/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void healthReportsEnabledWhenConfigured() throws Exception {
        Mockito.when(chatService.isConfigured()).thenReturn(true);

        mvc.perform(get("/api/v1/chat/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void healthReportsDisabledWhenUnconfigured() throws Exception {
        Mockito.when(chatService.isConfigured()).thenReturn(false);

        mvc.perform(get("/api/v1/chat/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.status").value("DISABLED"));
    }

    @Test
    void suggestionsAreReturnedWithTheirAnswers() throws Exception {
        mvc.perform(get("/api/v1/chat/suggestions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("file-size"))
                .andExpect(jsonPath("$[0].question").value("What is the maximum file size?"))
                .andExpect(jsonPath("$[0].answer").value("Up to 25 MB per upload."));
    }

    /**
     * The reason these chips exist: serving one must not touch the model. If this
     * ever fails, predefined questions have started costing tokens.
     */
    @Test
    void suggestionsNeverReachTheChatService() throws Exception {
        mvc.perform(get("/api/v1/chat/suggestions")).andExpect(status().isOk());

        Mockito.verifyNoInteractions(chatService);
    }

    @Test
    void suggestionsAreCacheable() throws Exception {
        mvc.perform(get("/api/v1/chat/suggestions"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=3600, public"));
    }

    /** Canned answers cost nothing, so they stay useful even with no API key set. */
    @Test
    void suggestionsAreServedWhenChatIsUnconfigured() throws Exception {
        Mockito.when(chatService.isConfigured()).thenReturn(false);

        mvc.perform(get("/api/v1/chat/suggestions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void noConfiguredSuggestionsYieldsAnEmptyList() throws Exception {
        MockMvcBuilders.standaloneSetup(new ChatController(chatService, suggesting()))
                .build()
                .perform(get("/api/v1/chat/suggestions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
