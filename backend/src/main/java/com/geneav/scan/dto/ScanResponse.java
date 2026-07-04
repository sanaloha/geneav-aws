package com.geneav.scan.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Result of scanning a single document.
 */
@Schema(description = "Result of scanning a single document")
public record ScanResponse(

        @Schema(description = "Unique id for this scan", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        String scanId,

        @Schema(description = "Overall verdict", example = "clean", allowableValues = {"clean", "infected"})
        String status,

        @Schema(description = "Name of the detected threat, or null when clean", example = "Eicar-Test-Signature", nullable = true)
        String threat,

        @Schema(description = "Original file name", example = "invoice.pdf")
        String fileName,

        @Schema(description = "File size in bytes", example = "48213")
        long fileSize,

        @Schema(description = "Reported content type", example = "application/pdf", nullable = true)
        String contentType,

        @Schema(description = "When the scan completed (UTC)")
        Instant scannedAt
) {
    public static ScanResponse clean(String scanId, String fileName, long fileSize, String contentType) {
        return new ScanResponse(scanId, "clean", null, fileName, fileSize, contentType, Instant.now());
    }

    public static ScanResponse infected(String scanId, String threat, String fileName, long fileSize, String contentType) {
        return new ScanResponse(scanId, "infected", threat, fileName, fileSize, contentType, Instant.now());
    }
}
