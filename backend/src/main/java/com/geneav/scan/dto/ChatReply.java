package com.geneav.scan.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The assistant's answer to a {@link ChatRequest}.
 */
@Schema(description = "The assistant's reply")
public record ChatReply(

        @Schema(description = "Assistant's answer", example = "geneav scans documents such as PDFs, Office files, text, and ZIPs for malware.")
        String reply,

        @Schema(description = "Model that produced the reply", example = "gpt-4o-mini")
        String model
) {
}
