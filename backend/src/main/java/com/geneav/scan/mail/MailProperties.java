package com.geneav.scan.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Outbound mail settings. The SMTP transport itself is configured under the
 * standard {@code spring.mail.*} keys; these are the geneav-specific bits.
 */
@ConfigurationProperties(prefix = "geneav.mail")
public class MailProperties {

    /** When false (the default), mail is logged and dropped instead of sent. */
    private boolean enabled = false;

    /** Envelope sender address. */
    private String from = "no-reply@geneav.com";

    /** Display name shown next to {@link #from}. */
    private String fromName = "geneav";

    /** Deep link included in the signup acknowledgement. */
    private String loginUrl = "https://geneav.com/login";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public String getLoginUrl() {
        return loginUrl;
    }

    public void setLoginUrl(String loginUrl) {
        this.loginUrl = loginUrl;
    }
}
