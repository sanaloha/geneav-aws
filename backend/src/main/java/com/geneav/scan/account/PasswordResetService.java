package com.geneav.scan.account;

import com.geneav.scan.web.ScanException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Forgot-password: issues a short-lived, single-use token and redeems it for a
 * new password.
 *
 * <p>Requesting a reset never reveals whether an account exists — an unknown
 * address takes the same path and returns the same result as a known one, so the
 * endpoint cannot be used to enumerate customers.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    /** Per GN-8. Short enough to limit exposure of a leaked link, long enough to be usable. */
    public static final Duration TOKEN_TTL = Duration.ofMinutes(10);

    private static final int TOKEN_BYTES = 32; // -> 43 url-safe chars

    private final AccountRepository accounts;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(AccountRepository accounts, PasswordResetTokenRepository tokens,
                                PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
    }

    /** A freshly issued reset: the one-time plaintext token and who it belongs to. */
    public record IssuedReset(String token, Account account) {
    }

    /**
     * Issues a reset token, or returns empty when the address has no usable
     * password account. Callers must respond identically either way.
     */
    @Transactional
    public Optional<IssuedReset> request(String rawEmail) {
        if (rawEmail == null || rawEmail.isBlank()) {
            return Optional.empty();
        }
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        Optional<Account> found = accounts.findByEmail(email).filter(Account::isActive);
        if (found.isEmpty()) {
            log.info("Password reset requested for unknown or inactive address; responding as if sent");
            return Optional.empty();
        }
        Account account = found.get();
        if (account.getPasswordHash() == null) {
            // A federated (e.g. Google) account has no password to reset. Resetting
            // would silently convert it to a password account, so refuse quietly.
            log.info("Password reset requested for a passwordless account {}; responding as if sent", account.getId());
            return Optional.empty();
        }

        Instant now = Instant.now();
        tokens.invalidateOutstanding(account.getId(), now);

        byte[] raw = new byte[TOKEN_BYTES];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        tokens.save(new PasswordResetToken(UUID.randomUUID(), account.getId(),
                ApiKeyService.sha256Hex(token), now, now.plus(TOKEN_TTL)));
        return Optional.of(new IssuedReset(token, account));
    }

    /**
     * Redeems a token and sets the new password.
     *
     * @return the account whose password changed, for the confirmation email
     * @throws ScanException 400 if the token is unknown, already used, or expired,
     *                       or if the new password fails the strength policy
     */
    @Transactional
    public Account reset(String token, String newPassword) {
        ScanException invalid = new ScanException(HttpStatus.BAD_REQUEST,
                "This reset link is invalid or has expired. Please request a new one.");
        if (token == null || token.isBlank()) {
            throw invalid;
        }
        PasswordResetToken stored = tokens.findByTokenHash(ApiKeyService.sha256Hex(token)).orElseThrow(() -> invalid);
        Instant now = Instant.now();
        if (!stored.isRedeemable(now)) {
            throw invalid;
        }
        Account account = accounts.findById(stored.getAccountId()).filter(Account::isActive).orElseThrow(() -> invalid);

        // Validated before the token is burned, so a weak password can be retried
        // with the same link rather than forcing the user to request another.
        passwordPolicy.validate(newPassword, account.getEmail());

        account.setPasswordHash(passwordEncoder.encode(newPassword));
        accounts.save(account);

        stored.markUsed(now);
        tokens.save(stored);
        tokens.invalidateOutstanding(account.getId(), now);

        log.info("Password reset completed for account {}", account.getId());
        return account;
    }
}
