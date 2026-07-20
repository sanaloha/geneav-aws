package com.geneav.scan.account;

/**
 * The resolved identity behind an authenticated request: the {@link Account} and
 * the specific {@link ApiKey} that was presented. Stored as a request attribute
 * by the auth filter and read downstream for plan limits, quota, and metering.
 */
public record AuthenticatedClient(Account account, ApiKey apiKey) {

    /** Request attribute key under which this is stored. */
    public static final String ATTRIBUTE = "geneav.authenticatedClient";
}
