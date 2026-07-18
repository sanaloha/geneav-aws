package com.geneav.scan.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A single turn in a chat conversation.
 */
@Schema(description = "A single chat message")
public record ChatMessage(

        @Schema(description = "Who sent the message", example = "user", allowableValues = {"user", "assistant"})
        String role,

        @Schema(description = "Message text", example = "What kinds of files can geneav scan?")
        String content
) {
}
