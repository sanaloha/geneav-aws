package com.geneav.scan.account;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues, hashes, and authenticates API keys.
 *
 * <p>A key looks like {@code gav_live_<40 url-safe chars>}. Only its SHA-256 hash
 * is persisted, so a database leak never exposes usable credentials; the plaintext
 * is returned to the caller exactly once, at creation.
 */
@Service
public class ApiKeyService {

    public static final String PREFIX = "gav_live_";
    private static final int SECRET_BYTES = 30; // -> 40 base64url chars

    private final ApiKeyRepository apiKeys;
    private final AccountRepository accounts;
    private final SecureRandom random = new SecureRandom();

    public ApiKeyService(ApiKeyRepository apiKeys, AccountRepository accounts) {
        this.apiKeys = apiKeys;
        this.accounts = accounts;
    }

    /** A freshly issued key: the one-time plaintext plus its stored record. */
    public record IssuedKey(String plaintext, ApiKey record) {
    }

    /** Creates and persists a new key for the account, returning the plaintext once. */
    @Transactional
    public IssuedKey issue(UUID accountId, String name) {
        byte[] raw = new byte[SECRET_BYTES];
        random.nextBytes(raw);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        String plaintext = PREFIX + secret;

        String keyPrefix = PREFIX + secret.substring(0, 4);
        String lastFour = secret.substring(secret.length() - 4);

        ApiKey key = new ApiKey(UUID.randomUUID(), accountId, name, sha256Hex(plaintext),
                keyPrefix, lastFour, "active", Instant.now());
        apiKeys.save(key);
        return new IssuedKey(plaintext, key);
    }

    /**
     * Resolves a bearer token to its owner. Returns empty if the token is absent,
     * malformed, unknown, revoked, or belongs to an inactive account.
     */
    @Transactional(readOnly = true)
    public Optional<AuthenticatedClient> authenticate(String bearerToken) {
        if (bearerToken == null || !bearerToken.startsWith(PREFIX)) {
            return Optional.empty();
        }
        return apiKeys.findByKeyHash(sha256Hex(bearerToken))
                .filter(ApiKey::isActive)
                .flatMap(key -> accounts.findById(key.getAccountId())
                        .filter(Account::isActive)
                        .map(account -> new AuthenticatedClient(account, key)));
    }

    /** Best-effort record of when a key was last used to authenticate. */
    @Transactional
    public void touchLastUsed(UUID keyId) {
        apiKeys.touchLastUsed(keyId, Instant.now());
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            // SHA-256 is guaranteed present on every JVM; this cannot happen.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
