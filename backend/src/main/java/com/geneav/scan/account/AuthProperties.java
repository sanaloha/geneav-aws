package com.geneav.scan.account;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Tunables for password reset. Configurable rather than constant so the TTL can
 * be shortened in a test environment to exercise expiry without waiting.
 */
@ConfigurationProperties(prefix = "geneav.auth")
public class AuthProperties {

    /** How long a reset link stays valid. GN-8 specifies 10 minutes. */
    private Duration resetTokenTtl = Duration.ofMinutes(10);

    /**
     * Minimum gap between reset emails for one account. Blunts mail-bombing a
     * specific address, which a per-IP rate limit alone cannot stop.
     */
    private Duration resetCooldown = Duration.ofSeconds(60);

    /** How long spent/expired tokens are kept before the sweep deletes them. */
    private Duration resetTokenRetention = Duration.ofHours(24);

    public Duration getResetTokenTtl() {
        return resetTokenTtl;
    }

    public void setResetTokenTtl(Duration resetTokenTtl) {
        this.resetTokenTtl = resetTokenTtl;
    }

    public Duration getResetCooldown() {
        return resetCooldown;
    }

    public void setResetCooldown(Duration resetCooldown) {
        this.resetCooldown = resetCooldown;
    }

    public Duration getResetTokenRetention() {
        return resetTokenRetention;
    }

    public void setResetTokenRetention(Duration resetTokenRetention) {
        this.resetTokenRetention = resetTokenRetention;
    }
}
