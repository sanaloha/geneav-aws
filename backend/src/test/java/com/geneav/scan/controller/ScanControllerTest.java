package com.geneav.scan.controller;

import com.geneav.scan.service.ScanEngine;
import com.geneav.scan.service.ScanResult;
import com.geneav.scan.web.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ScanControllerTest {

    private final ScanEngine engine = Mockito.mock(ScanEngine.class);
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ScanController(engine))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void cleanFileReturnsCleanVerdict() throws Exception {
        Mockito.when(engine.scan(any())).thenReturn(ScanResult.clean());

        MockMultipartFile file = new MockMultipartFile(
                "file", "invoice.pdf", "application/pdf", "hello".getBytes());

        mvc.perform(multipart("/api/v1/scan").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("clean"))
                .andExpect(jsonPath("$.fileName").value("invoice.pdf"));
    }

    @Test
    void infectedFileReturnsThreatName() throws Exception {
        Mockito.when(engine.scan(any())).thenReturn(ScanResult.infected("Eicar-Test-Signature"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "virus.txt", "text/plain", "x".getBytes());

        mvc.perform(multipart("/api/v1/scan").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("infected"))
                .andExpect(jsonPath("$.threat").value("Eicar-Test-Signature"));
    }

    @Test
    void unsupportedTypeReturns415() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "image.png", "image/png", "x".getBytes());

        mvc.perform(multipart("/api/v1/scan").file(file))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void emptyFileReturns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]);

        mvc.perform(multipart("/api/v1/scan").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void engineDownReturns503() throws Exception {
        Mockito.when(engine.scan(any())).thenThrow(new IOException("connection refused"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "invoice.pdf", "application/pdf", "hello".getBytes());

        mvc.perform(multipart("/api/v1/scan").file(file))
                .andExpect(status().isServiceUnavailable());
    }

    // Logs outlive the request, so the filename never reaches them — only its
    // extension does. These pin that down; see ScanController.logExtension.

    @Test
    void logExtensionKeepsOnlyTheExtension() {
        assertThat(ScanController.logExtension("Jane_Doe_payslip_2026.pdf")).isEqualTo("pdf");
        assertThat(ScanController.logExtension("report.final.DOCX")).isEqualTo("docx");
    }

    @Test
    void logExtensionHandlesMissingOrUnusableNames() {
        assertThat(ScanController.logExtension(null)).isEqualTo("none");
        assertThat(ScanController.logExtension("no-extension")).isEqualTo("none");
        assertThat(ScanController.logExtension("trailing.")).isEqualTo("none");
    }

    @Test
    void logExtensionRefusesToEchoAnythingUnusual() {
        // A crafted "extension" must not become a way to write arbitrary text
        // into the log line.
        assertThat(ScanController.logExtension("x.pdf%0AFAKE-LOG-ENTRY")).isEqualTo("other");
        assertThat(ScanController.logExtension("x." + "a".repeat(64))).isEqualTo("other");
        assertThat(ScanController.logExtension("secret.name-with-dashes")).isEqualTo("other");
    }
}
