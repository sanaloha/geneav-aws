package com.geneav.scan.account;

import com.geneav.scan.web.ScanException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordResetServiceTest {

    private static final String STRONG = "Br4nd-new-pass!";

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final AuthProperties props = new AuthProperties();
    private final PasswordResetService service =
            new PasswordResetService(accounts, tokens, encoder, new PasswordPolicy(), props);

    private Account account(String email) {
        Account account = new Account(UUID.randomUUID(), email, "free", "active", Instant.now());
        account.setPasswordHash(encoder.encode("Original-pass1!"));
        return account;
    }

    // ---- request ------------------------------------------------------------

    @Test
    void requestIssuesTokenForKnownAccount() {
        Account account = account("a@b.com");
        when(accounts.findByEmail("a@b.com")).thenReturn(Optional.of(account));

        Optional<PasswordResetService.IssuedReset> issued = service.request("A@B.com "); // normalized

        assertThat(issued).isPresent();
        assertThat(issued.get().token()).isNotBlank();

        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokens).save(saved.capture());
        PasswordResetToken token = saved.getValue();
        assertThat(token.getTokenHash())
                .isNotEqualTo(issued.get().token())          // stored hashed, never plaintext
                .isEqualTo(ApiKeyService.sha256Hex(issued.get().token()));
        // 10-minute TTL per GN-8, allowing for clock drift across the two calls.
        Instant expected = Instant.now().plus(props.getResetTokenTtl());
        assertThat(token.getExpiresAt())
                .isAfter(expected.minusSeconds(5))
                .isBefore(expected.plusSeconds(5));
    }

    @Test
    void requestIsSilentForUnknownEmail() {
        when(accounts.findByEmail("nobody@b.com")).thenReturn(Optional.empty());

        assertThat(service.request("nobody@b.com")).isEmpty();
        verify(tokens, never()).save(any());
    }

    @Test
    void requestIsSilentForPasswordlessAccount() {
        Account oauthOnly = new Account(UUID.randomUUID(), "a@b.com", "free", "active", Instant.now());
        when(accounts.findByEmail("a@b.com")).thenReturn(Optional.of(oauthOnly));

        // Resetting would silently turn a Google account into a password account.
        assertThat(service.request("a@b.com")).isEmpty();
        verify(tokens, never()).save(any());
    }

    @Test
    void requestInvalidatesEarlierTokens() {
        Account account = account("a@b.com");
        when(accounts.findByEmail("a@b.com")).thenReturn(Optional.of(account));

        service.request("a@b.com");

        verify(tokens).invalidateOutstanding(eq(account.getId()), any());
    }

    @Test
    void eachRequestProducesADifferentToken() {
        Account account = account("a@b.com");
        when(accounts.findByEmail("a@b.com")).thenReturn(Optional.of(account));

        String first = service.request("a@b.com").orElseThrow().token();
        String second = service.request("a@b.com").orElseThrow().token();

        assertThat(first).isNotEqualTo(second);
    }

    // ---- reset --------------------------------------------------------------

    private PasswordResetToken storedToken(Account account, String plaintext, Instant expiresAt) {
        return new PasswordResetToken(UUID.randomUUID(), account.getId(),
                ApiKeyService.sha256Hex(plaintext), Instant.now(), expiresAt);
    }

    @Test
    void resetSetsNewPasswordAndBurnsToken() {
        Account account = account("a@b.com");
        PasswordResetToken token = storedToken(account, "tok", Instant.now().plusSeconds(600));
        when(tokens.findByTokenHash(ApiKeyService.sha256Hex("tok"))).thenReturn(Optional.of(token));
        when(accounts.findById(account.getId())).thenReturn(Optional.of(account));

        Account result = service.reset("tok", STRONG);

        assertThat(result).isSameAs(account);
        assertThat(encoder.matches(STRONG, account.getPasswordHash())).isTrue();
        assertThat(token.getUsedAt()).isNotNull();
        verify(tokens).invalidateOutstanding(eq(account.getId()), any());
    }

    @Test
    void resetRejectsExpiredToken() {
        Account account = account("a@b.com");
        PasswordResetToken expired = storedToken(account, "tok", Instant.now().minusSeconds(1));
        when(tokens.findByTokenHash(ApiKeyService.sha256Hex("tok"))).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.reset("tok", STRONG))
                .isInstanceOf(ScanException.class)
                .extracting(e -> ((ScanException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void resetRejectsAlreadyUsedToken() {
        Account account = account("a@b.com");
        PasswordResetToken used = storedToken(account, "tok", Instant.now().plusSeconds(600));
        used.markUsed(Instant.now());
        when(tokens.findByTokenHash(ApiKeyService.sha256Hex("tok"))).thenReturn(Optional.of(used));

        assertThatThrownBy(() -> service.reset("tok", STRONG)).isInstanceOf(ScanException.class);
    }

    @Test
    void resetRejectsUnknownToken() {
        when(tokens.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reset("nope", STRONG)).isInstanceOf(ScanException.class);
    }

    @Test
    void resetEnforcesPasswordPolicy() {
        Account account = account("a@b.com");
        PasswordResetToken token = storedToken(account, "tok", Instant.now().plusSeconds(600));
        when(tokens.findByTokenHash(ApiKeyService.sha256Hex("tok"))).thenReturn(Optional.of(token));
        when(accounts.findById(account.getId())).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.reset("tok", "weak"))
                .isInstanceOf(ScanException.class)
                .hasMessageContaining("at least 12 characters");

        // The token survives a rejected password, so the user can retry the same link.
        assertThat(token.getUsedAt()).isNull();
    }

    // ---- cooldown -----------------------------------------------------------

    @Test
    void requestIsSuppressedInsideTheCooldown() {
        Account account = account("a@b.com");
        when(accounts.findByEmail("a@b.com")).thenReturn(Optional.of(account));
        // Issued 5s ago, cooldown is 60s.
        when(tokens.findTopByAccountIdOrderByCreatedAtDesc(account.getId()))
                .thenReturn(Optional.of(new PasswordResetToken(UUID.randomUUID(), account.getId(),
                        "hash", Instant.now().minusSeconds(5), Instant.now().plusSeconds(600))));

        // Silent, so the caller still returns 202 and reveals nothing.
        assertThat(service.request("a@b.com")).isEmpty();
        verify(tokens, never()).save(any());
    }

    @Test
    void requestIsAllowedOnceTheCooldownHasPassed() {
        Account account = account("a@b.com");
        when(accounts.findByEmail("a@b.com")).thenReturn(Optional.of(account));
        when(tokens.findTopByAccountIdOrderByCreatedAtDesc(account.getId()))
                .thenReturn(Optional.of(new PasswordResetToken(UUID.randomUUID(), account.getId(),
                        "hash", Instant.now().minusSeconds(120), Instant.now().minusSeconds(60))));

        assertThat(service.request("a@b.com")).isPresent();
        verify(tokens).save(any());
    }

    // ---- session invalidation ----------------------------------------------

    @Test
    void resetStampsCredentialsChangedAt() {
        Account account = account("a@b.com");
        assertThat(account.getCredentialsChangedAt()).isNull();
        PasswordResetToken token = storedToken(account, "tok", Instant.now().plusSeconds(600));
        when(tokens.findByTokenHash(ApiKeyService.sha256Hex("tok"))).thenReturn(Optional.of(token));
        when(accounts.findById(account.getId())).thenReturn(Optional.of(account));

        Instant before = Instant.now().minusSeconds(1);
        service.reset("tok", STRONG);

        // Sessions authenticated before this instant are now rejected.
        assertThat(account.getCredentialsChangedAt()).isNotNull().isAfter(before);
        assertThat(account.acceptsSessionFrom(before)).isFalse();
        assertThat(account.acceptsSessionFrom(Instant.now().plusSeconds(1))).isTrue();
    }

    @Test
    void resetRejectsTokenForDeactivatedAccount() {
        Account account = account("a@b.com");
        account.setStatus("suspended");
        PasswordResetToken token = storedToken(account, "tok", Instant.now().plusSeconds(600));
        when(tokens.findByTokenHash(ApiKeyService.sha256Hex("tok"))).thenReturn(Optional.of(token));
        when(accounts.findById(account.getId())).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.reset("tok", STRONG)).isInstanceOf(ScanException.class);
    }
}
