package com.geneav.scan.account;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rule behind "signed out everywhere after a password change". Sessions are
 * in-memory, so validity is decided by comparing timestamps rather than by
 * purging a session store.
 */
class AccountSessionValidityTest {

    private Account account() {
        return new Account(UUID.randomUUID(), "a@b.com", "free", "active", Instant.now());
    }

    @Test
    void everySessionIsValidWhenThePasswordHasNeverChanged() {
        Account account = account();

        assertThat(account.acceptsSessionFrom(Instant.now())).isTrue();
        // Even a session with no recorded stamp — these predate the feature and
        // must not be logged out by merely deploying it.
        assertThat(account.acceptsSessionFrom(null)).isTrue();
    }

    @Test
    void sessionsOlderThanThePasswordChangeAreRejected() {
        Account account = account();
        Instant changed = Instant.now();
        account.setCredentialsChangedAt(changed);

        assertThat(account.acceptsSessionFrom(changed.minusSeconds(1))).isFalse();
    }

    @Test
    void theSessionThatPerformedTheChangeIsAccepted() {
        Account account = account();
        Instant changed = Instant.now();
        account.setCredentialsChangedAt(changed);

        assertThat(account.acceptsSessionFrom(changed)).isTrue();
        assertThat(account.acceptsSessionFrom(changed.plusSeconds(1))).isTrue();
    }

    @Test
    void unstampedSessionsAreRejectedOnceThePasswordChanges() {
        Account account = account();
        account.setCredentialsChangedAt(Instant.now());

        // Cannot prove it is newer than the change, so it must not be trusted.
        assertThat(account.acceptsSessionFrom(null)).isFalse();
    }
}
