package com.geneav.scan.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

/** Request/response payloads for account signup and API-key management. */
public final class AccountDtos {

    private AccountDtos() {
    }

    public record SignupRequest(
            @NotBlank(message = "email is required") @Email(message = "a valid email is required") String email) {
    }

    /** Returned once at signup; {@code apiKey} is the plaintext secret, shown only here. */
    public record SignupResponse(UUID accountId, String email, String plan, String apiKey, String keyPrefix) {
    }

    public record CreateKeyRequest(String name) {
    }

    /** Returned once when a key is created; carries the plaintext {@code apiKey}. */
    public record CreatedKeyResponse(UUID id, String name, String apiKey, String keyPrefix) {
    }

    /** Safe key listing — never includes the secret. */
    public record KeySummary(UUID id, String name, String keyPrefix, String lastFour,
                             String status, Instant createdAt, Instant lastUsedAt) {
    }

    public record UsageResponse(String plan, String period, long scansUsed, long scansQuota, long scansRemaining) {
    }

    public record SignupPasswordRequest(
            @NotBlank(message = "email is required") @Email(message = "a valid email is required") String email,
            @NotBlank(message = "password is required") String password) {
    }

    public record LoginRequest(
            @NotBlank(message = "email is required") String email,
            @NotBlank(message = "password is required") String password) {
    }

    /** The currently signed-in account (dashboard session). */
    public record MeResponse(String email, String plan, String authProvider) {
    }

    public record ForgotPasswordRequest(
            @NotBlank(message = "email is required") String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank(message = "token is required") String token,
            @NotBlank(message = "password is required") String password) {
    }
}
