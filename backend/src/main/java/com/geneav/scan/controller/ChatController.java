package com.geneav.scan.controller;

import com.geneav.scan.dto.ChatReply;
import com.geneav.scan.dto.ChatRequest;
import com.geneav.scan.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * A small chatbot that answers questions about geneav, its scans, and the safety
 * it provides. Backed by OpenAI; scope is constrained by the service's system prompt.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Chat", description = "Ask questions about geneav")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
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

    @Operation(summary = "Chat availability", description = "Reports whether the chat assistant is configured and enabled.")
    @GetMapping(value = "/chat/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> chatHealth() {
        boolean enabled = chatService.isConfigured();
        return Map.of("status", enabled ? "UP" : "DISABLED", "enabled", enabled);
    }
}
