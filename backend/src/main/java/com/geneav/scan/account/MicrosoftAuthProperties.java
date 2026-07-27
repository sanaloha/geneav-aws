package com.geneav.scan.account;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * "Sign in with Microsoft" (Entra ID) configuration. Off by default: with no
 * client credentials no OAuth2 client registration exists, Spring's OAuth2
 * login routes are absent, and the login page shows no Microsoft button.
 */
@ConfigurationProperties(prefix = "geneav.auth.microsoft")
public class MicrosoftAuthProperties {

    private boolean enabled = false;

    /** The MULTI-tenant sign-in app registration (App A), not the fulfillment app. */
    private String clientId = "";

    private String clientSecret = "";

    /** Where the browser lands after a completed Microsoft sign-in. */
    private String postLoginUrl = "http://localhost:3000/login";

    public boolean isConfigured() {
        return enabled && !clientId.isBlank() && !clientSecret.isBlank();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId == null ? "" : clientId.trim();
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret == null ? "" : clientSecret.trim();
    }

    public String getPostLoginUrl() {
        return postLoginUrl;
    }

    public void setPostLoginUrl(String postLoginUrl) {
        this.postLoginUrl = postLoginUrl;
    }
}
