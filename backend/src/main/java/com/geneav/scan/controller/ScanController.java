package com.geneav.scan.controller;

import com.geneav.scan.dto.ScanResponse;
import com.geneav.scan.service.ScanEngine;
import com.geneav.scan.service.ScanResult;
import com.geneav.scan.web.ScanException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Scan", description = "Document malware scanning")
public class ScanController {

    private static final Logger log = LoggerFactory.getLogger(ScanController.class);

    /**
     * Allowed document content types. Kept intentionally small for v1; extend as needed.
     */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "text/csv",
            "application/rtf",
            "application/zip",
            "application/octet-stream"
    );

    private final ScanEngine scanEngine;

    public ScanController(ScanEngine scanEngine) {
        this.scanEngine = scanEngine;
    }

    @Operation(
            summary = "Scan a document",
            description = "Uploads a single document and returns a malware verdict (clean/infected)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Scan completed",
                    content = @Content(schema = @Schema(implementation = ScanResponse.class))),
            @ApiResponse(responseCode = "400", description = "Missing or empty file", content = @Content),
            @ApiResponse(responseCode = "413", description = "File too large", content = @Content),
            @ApiResponse(responseCode = "415", description = "Unsupported file type", content = @Content),
            @ApiResponse(responseCode = "503", description = "Scan engine unavailable", content = @Content)
    })
    @PostMapping(value = "/scan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ScanResponse scan(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ScanException(HttpStatus.BAD_REQUEST, "No file provided. Send a multipart 'file' field.");
        }

        String contentType = file.getContentType();
        if (contentType != null && !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ScanException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Unsupported content type: " + contentType);
        }

        String scanId = UUID.randomUUID().toString();
        try {
            ScanResult result = scanEngine.scan(file.getInputStream());
            if (result.infected()) {
                log.info("scan {} -> INFECTED ({}) file='{}'", scanId, result.threat(), file.getOriginalFilename());
                return ScanResponse.infected(scanId, result.threat(),
                        file.getOriginalFilename(), file.getSize(), contentType);
            }
            log.info("scan {} -> clean file='{}'", scanId, file.getOriginalFilename());
            return ScanResponse.clean(scanId, file.getOriginalFilename(), file.getSize(), contentType);
        } catch (IOException e) {
            log.error("scan {} failed: engine unreachable - {}", scanId, e.getMessage());
            throw new ScanException(HttpStatus.SERVICE_UNAVAILABLE, "Scan engine unavailable: " + e.getMessage());
        }
    }

    @Operation(summary = "Health check", description = "Reports whether the API and scan engine are ready.")
    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> health() {
        boolean engineUp = scanEngine.isHealthy();
        return Map.of(
                "status", engineUp ? "UP" : "DEGRADED",
                "engine", engineUp ? "UP" : "DOWN",
                "checks", List.of(Map.of("name", "clamav", "status", engineUp ? "UP" : "DOWN"))
        );
    }
}
