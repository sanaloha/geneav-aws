package com.geneav.scan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * A chat request carrying the full conversation so far. The final message is
 * expected to be the user's latest question; earlier messages provide context.
 */
@Schema(description = "A chat conversation to continue")
public record ChatRequest(

        @Schema(description = "Conversation history, oldest first; the last entry is the user's new question")
        @NotEmpty(message = "messages must not be empty")
        List<ChatMessage> messages
) {
}
