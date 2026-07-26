package com.geneav.scan.controller;

import com.geneav.scan.dto.ChatReply;
import com.geneav.scan.dto.ChatRequest;
import com.geneav.scan.dto.ChatSuggestion;
import com.geneav.scan.service.ChatProperties;
import com.geneav.scan.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * A small chatbot that answers questions about geneav, its scans, and the safety
 * it provides. Backed by OpenAI; scope is constrained by the service's system prompt.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Chat", description = "Ask questions about geneav")
@EnableConfigurationProperties(ChatProperties.class)
public class ChatController {

    /** How long a client may reuse the suggestion list; it only changes on deploy. */
    private static final Duration SUGGESTIONS_MAX_AGE = Duration.ofHours(1);

    private final ChatService chatService;
    private final ChatProperties chatProperties;

    public ChatController(ChatService chatService, ChatProperties chatProperties) {
        this.chatService = chatService;
        this.chatProperties = chatProperties;
    }

    @Operation(
            summary = "Ask the geneav assistant",
            description = "Sends the conversation so far and returns the assistant's reply. "
                    + "The assistant only answers questions about geneav, its document scanning, "
                    + "and the safety it provides."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reply produced",
                    content = @Content(schema = @Schema(implementation = ChatReply.class))),
            @ApiResponse(responseCode = "400", description = "Empty or malformed conversation", content = @Content),
            @ApiResponse(responseCode = "502", description = "Upstream chat service error", content = @Content),
            @ApiResponse(responseCode = "503", description = "Chat not configured or unreachable", content = @Content)
    })
    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ChatReply chat(@Valid @RequestBody ChatRequest request) {
        return chatService.answer(request.messages());
    }

    @Operation(
            summary = "Predefined questions",
            description = "Returns the questions offered as one-click chips in the chat widget, each with the "
                    + "answer to render. Picking one is answered from this list, so it costs no model tokens. "
                    + "Available even when the assistant itself is not configured."
    )
    @ApiResponse(responseCode = "200", description = "Suggestions returned (possibly empty)")
    @GetMapping(value = "/chat/suggestions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<ChatSuggestion>> suggestions() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(SUGGESTIONS_MAX_AGE).cachePublic())
                .body(chatProperties.getSuggestions());
    }

    @Operation(summary = "Chat availability", description = "Reports whether the chat assistant is configured and enabled.")
    @GetMapping(value = "/chat/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> chatHealth() {
        boolean enabled = chatService.isConfigured();
        return Map.of("status", enabled ? "UP" : "DISABLED", "enabled", enabled);
    }
}
