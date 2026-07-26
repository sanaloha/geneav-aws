package com.geneav.scan.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A predefined question offered in the chat widget, shipped with its own answer.
 *
 * <p>The answer travels with the question deliberately: picking a suggestion is
 * answered from configuration, without calling the model at all, which is what
 * keeps these one-click questions free (GN-20).
 */
@Schema(description = "A predefined question and its canned answer")
public record ChatSuggestion(

        @Schema(description = "Stable identifier for the suggestion", example = "file-size")
        String id,

        @Schema(description = "Question shown on the chip", example = "What is the maximum file size?")
        String question,

        @Schema(description = "Answer rendered when the chip is picked, costing no model tokens",
                example = "Up to 25 MB per upload. Anything larger returns 413.")
        String answer
) {
}
