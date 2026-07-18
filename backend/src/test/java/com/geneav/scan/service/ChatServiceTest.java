package com.geneav.scan.service;

import com.geneav.scan.dto.ChatMessage;
import com.geneav.scan.dto.ChatReply;
import com.geneav.scan.web.ScanException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatServiceTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    // --- Guardrails that never reach the network ---

    @Test
    void isConfiguredReflectsApiKeyPresence() {
        assertThat(newService("", "http://localhost:1").isConfigured()).isFalse();
        assertThat(newService("   ", "http://localhost:1").isConfigured()).isFalse();
        assertThat(newService("sk-test", "http://localhost:1").isConfigured()).isTrue();
    }

    @Test
    void answerWithoutApiKeyThrows503() {
        ChatService service = newService("", "http://localhost:1");

        assertThatThrownBy(() -> service.answer(List.of(new ChatMessage("user", "hi"))))
                .isInstanceOf(ScanException.class)
                .hasMessageContaining("OPENAI_API_KEY")
                .extracting(e -> ((ScanException) e).getStatus())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void overlongMessageThrows400() {
        ChatService service = newService("sk-test", "http://localhost:1"); // key set, but fails before any HTTP call
        String tooLong = "x".repeat(5000); // default max-chars is 4000

        assertThatThrownBy(() -> service.answer(List.of(new ChatMessage("user", tooLong))))
                .isInstanceOf(ScanException.class)
                .extracting(e -> ((ScanException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void blankOnlyConversationThrows400() {
        ChatService service = newService("sk-test", "http://localhost:1");

        assertThatThrownBy(() -> service.answer(List.of(new ChatMessage("user", "   "))))
                .isInstanceOf(ScanException.class)
                .extracting(e -> ((ScanException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // --- Real HTTP round-trip against an in-process stub ---

    @Test
    void answerCallsOpenAiAndReturnsReply() throws IOException {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        AtomicReference<String> capturedAuth = new AtomicReference<>();
        String baseUrl = startStub(exchange -> {
            capturedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = ("{\"choices\":[{\"message\":{\"role\":\"assistant\","
                    + "\"content\":\"geneav scans PDFs and Office docs.\"}}]}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        ChatService service = newService("sk-test", baseUrl);
        ChatReply reply = service.answer(List.of(new ChatMessage("user", "What file types can it scan?")));

        assertThat(reply.reply()).isEqualTo("geneav scans PDFs and Office docs.");
        assertThat(reply.model()).isEqualTo("gpt-4o-mini");
        // The auth header carries the key, and the system prompt + user turn are sent.
        assertThat(capturedAuth.get()).isEqualTo("Bearer sk-test");
        assertThat(capturedBody.get()).contains("geneav assistant"); // from the system prompt
        assertThat(capturedBody.get()).contains("What file types can it scan?");
    }

    @Test
    void openAiErrorMapsTo502() throws IOException {
        String baseUrl = startStub(exchange -> {
            byte[] body = "{\"error\":{\"message\":\"invalid api key\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        ChatService service = newService("sk-bad", baseUrl);

        assertThatThrownBy(() -> service.answer(List.of(new ChatMessage("user", "hi"))))
                .isInstanceOf(ScanException.class)
                .extracting(e -> ((ScanException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    // --- helpers ---

    private ChatService newService(String apiKey, String baseUrl) {
        return new ChatService(apiKey, baseUrl, "gpt-4o-mini", 5000, 20, 4000);
    }

    /** Starts a local HTTP stub serving /chat/completions and returns its base URL. */
    private String startStub(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", handler);
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
