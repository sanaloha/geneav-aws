package com.geneav.scan.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Deletes spent and expired reset tokens. Without this the table grows forever:
 * every reset request adds a row that is worthless minutes later.
 *
 * <p>Rows are kept for a retention window past expiry rather than deleted the
 * moment they lapse, so a support question about a recent reset can still be
 * answered from the data.
 */
@Component
@EnableConfigurationProperties(AuthProperties.class)
public class PasswordResetCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetCleanupJob.class);

    private final PasswordResetTokenRepository tokens;
    private final AuthProperties props;

    public PasswordResetCleanupJob(PasswordResetTokenRepository tokens, AuthProperties props) {
        this.tokens = tokens;
        this.props = props;
    }

    /**
     * Runs hourly. {@code fixedDelay} rather than {@code fixedRate} so a slow
     * delete never overlaps itself, and the first run is deferred so it does not
     * compete with application startup.
     */
    @Scheduled(fixedDelayString = "${geneav.auth.reset-cleanup-interval-ms:3600000}",
            initialDelayString = "${geneav.auth.reset-cleanup-initial-delay-ms:60000}")
    @Transactional
    public void purgeExpiredTokens() {
        Instant cutoff = Instant.now().minus(props.getResetTokenRetention());
        int deleted = tokens.deleteExpiredBefore(cutoff);
        if (deleted > 0) {
            log.info("Purged {} expired password reset token(s) older than {}", deleted, cutoff);
        }
    }
}
