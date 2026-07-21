package com.geneav.scan.account;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordResetCleanupJobTest {

    private final PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
    private final AuthProperties props = new AuthProperties();
    private final PasswordResetCleanupJob job = new PasswordResetCleanupJob(tokens, props);

    @Test
    void deletesTokensThatExpiredBeforeTheRetentionCutoff() {
        props.setResetTokenRetention(Duration.ofHours(24));
        when(tokens.deleteExpiredBefore(any())).thenReturn(3);

        job.purgeExpiredTokens();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(tokens).deleteExpiredBefore(cutoff.capture());

        Instant expected = Instant.now().minus(Duration.ofHours(24));
        assertThat(cutoff.getValue())
                .isAfter(expected.minusSeconds(5))
                .isBefore(expected.plusSeconds(5));
    }

    @Test
    void retentionWindowIsConfigurable() {
        props.setResetTokenRetention(Duration.ofMinutes(30));

        job.purgeExpiredTokens();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(tokens).deleteExpiredBefore(cutoff.capture());
        assertThat(cutoff.getValue()).isAfter(Instant.now().minus(Duration.ofMinutes(31)));
    }

    @Test
    void deletingNothingIsNotAnError() {
        when(tokens.deleteExpiredBefore(any())).thenReturn(0);

        job.purgeExpiredTokens();

        verify(tokens).deleteExpiredBefore(any());
    }
}
