package com.geneav.scan.service;

import com.geneav.scan.dto.ChatMessage;
import com.geneav.scan.dto.ChatReply;
import com.geneav.scan.web.ScanException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;

/**
 * Answers user questions using OpenAI's Chat Completions API, constrained by a
 * system prompt to only discuss geneav, the scans it performs, and the safety it
 * provides. The OpenAI API key stays server-side; the browser never sees it.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    /**
     * Keeps the assistant on-topic. It should answer only questions about geneav,
     * its document scanning, and the safety it provides, and politely decline
     * anything else.
     */
    private static final String SYSTEM_PROMPT = """
            You are the geneav assistant, a helpful chatbot embedded on the geneav website.

            geneav is an "antivirus for documents": a marketing website plus a REST API that
            scans uploaded documents for malware. It is backed by ClamAV, a widely used
            open-source antivirus engine with a large, frequently updated signature database.

            How geneav works:
            - Users upload a single document to POST /api/v1/scan (multipart field "file") and
              get a JSON verdict back: "clean" or "infected" (with the detected threat name).
            - There is also GET /api/v1/health for engine readiness and interactive API docs at /docs.
            - The backend streams the file to the ClamAV daemon over ClamAV's native INSTREAM
              protocol. Files are scanned in-stream and are not persisted.
            - Supported document types include PDF, Microsoft Office documents (Word, Excel,
              PowerPoint), plain text, CSV, RTF, and ZIP archives. The upload size limit is 25 MB.
            - Errors are explicit: 400 (missing/empty file), 413 (too large), 415 (unsupported
              type), 503 (scan engine unavailable).

            Safety geneav provides:
            - It detects known malware, viruses, trojans, worms, and malicious documents using
              ClamAV's signature database before those files reach your systems or users.
            - It is a building block for scanning user-uploaded files in any application via a
              simple HTTP request.
            - Note honestly when asked: no antivirus catches 100% of threats; ClamAV is
              signature-based and complements, but does not replace, layered security.

            Rules:
            - ONLY answer questions about geneav, its document scanning, the file types it
              supports, its API, and the safety/protection it provides.
            - If a question is unrelated (general knowledge, coding help, other products, personal
              advice, etc.), politely decline in one sentence and steer the user back to geneav.
            - Do not invent features, pricing, or guarantees that are not described above.
            - Be concise, friendly, and accurate. Prefer a few sentences over long essays.
            """;

    private final String apiKey;
    private final String model;
    private final int maxMessages;
    private final int maxCharsPerMessage;
    private final RestClient client;

    public ChatService(
            @Value("${geneav.openai.api-key:}") String apiKey,
            @Value("${geneav.openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${geneav.openai.model:gpt-4o-mini}") String model,
            @Value("${geneav.openai.timeout-ms:30000}") int timeoutMs,
            @Value("${geneav.openai.max-messages:20}") int maxMessages,
            @Value("${geneav.openai.max-chars-per-message:4000}") int maxCharsPerMessage) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.maxMessages = maxMessages;
        this.maxCharsPerMessage = maxCharsPerMessage;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    /** Whether a usable OpenAI API key is configured. */
    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    /**
     * Produces an answer for the given conversation history (oldest first).
     *
     * @throws ScanException 503 if chat is not configured or OpenAI is unreachable,
     *                       400 for malformed input, 502 if OpenAI returns an error.
     */
    public ChatReply answer(List<ChatMessage> history) {
        if (!isConfigured()) {
            throw new ScanException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Chat is not configured. Set OPENAI_API_KEY on the server to enable it.");
        }

        List<OpenAiMessage> messages = new ArrayList<>();
        messages.add(new OpenAiMessage("system", SYSTEM_PROMPT));

        // Keep only the most recent turns to bound cost and payload size.
        List<ChatMessage> recent = history.size() > maxMessages
                ? history.subList(history.size() - maxMessages, history.size())
                : history;

        for (ChatMessage m : recent) {
            String content = m.content() == null ? "" : m.content().trim();
            if (content.isEmpty()) {
                continue;
            }
            if (content.length() > maxCharsPerMessage) {
                throw new ScanException(HttpStatus.BAD_REQUEST,
                        "Message too long (max " + maxCharsPerMessage + " characters).");
            }
            // Only user/assistant roles are meaningful from the client; default to user.
            String role = "assistant".equals(m.role()) ? "assistant" : "user";
            messages.add(new OpenAiMessage(role, content));
        }

        if (messages.size() == 1) { // only the system prompt survived
            throw new ScanException(HttpStatus.BAD_REQUEST, "No message content provided.");
        }

        OpenAiRequest payload = new OpenAiRequest(model, messages, 0.2, 500);

        try {
            OpenAiResponse response = client.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .body(payload)
                    .retrieve()
                    .body(OpenAiResponse.class);

            String reply = response != null && response.choices() != null && !response.choices().isEmpty()
                    ? response.choices().get(0).message().content()
                    : null;

            if (reply == null || reply.isBlank()) {
                throw new ScanException(HttpStatus.BAD_GATEWAY, "Chat service returned an empty response.");
            }
            return new ChatReply(reply.trim(), model);
        } catch (RestClientResponseException e) {
            // OpenAI answered with a 4xx/5xx (bad key, rate limit, model error, ...).
            log.error("OpenAI error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ScanException(HttpStatus.BAD_GATEWAY, "Chat service error. Please try again.");
        } catch (ResourceAccessException e) {
            // Connect/read timeout or network failure reaching OpenAI.
            log.error("OpenAI unreachable: {}", e.getMessage());
            throw new ScanException(HttpStatus.SERVICE_UNAVAILABLE, "Chat service is temporarily unreachable.");
        }
    }

    // --- Minimal OpenAI Chat Completions wire types (only the fields we use) ---

    private record OpenAiRequest(String model, List<OpenAiMessage> messages, double temperature,
                                 @com.fasterxml.jackson.annotation.JsonProperty("max_tokens") int maxTokens) {
    }

    private record OpenAiMessage(String role, String content) {
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private record OpenAiResponse(List<Choice> choices) {
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private record Choice(OpenAiMessage message) {
    }
}
