package com.geneav.scan.account;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Where an account came from, captured once at signup and never updated.
 *
 * <p>Every value here originates in the visitor's browser — query parameters
 * and {@code document.referrer} — so none of it is trustworthy input. The
 * guarantee this class makes is that <strong>it can never fail a signup</strong>:
 * {@link #of} trims, discards blanks and truncates to the column widths rather
 * than rejecting anything. Losing the tail of a hand-edited campaign string is a
 * trivial cost; losing the customer because their URL had junk in it is not.
 *
 * <p>Hibernate maps an all-null embeddable back as {@code null}, so
 * {@link Account#getAttribution()} returns null for accounts created before this
 * existed and for anyone who arrived with no attribution at all.
 */
@Embeddable
public class SignupAttribution {

    /** Must match the VARCHAR widths in V5__signup_attribution.sql. */
    private static final int UTM_MAX = 128;
    private static final int URL_MAX = 512;

    @Column(name = "utm_source", length = UTM_MAX)
    private String utmSource;

    @Column(name = "utm_medium", length = UTM_MAX)
    private String utmMedium;

    @Column(name = "utm_campaign", length = UTM_MAX)
    private String utmCampaign;

    @Column(name = "utm_term", length = UTM_MAX)
    private String utmTerm;

    @Column(name = "utm_content", length = UTM_MAX)
    private String utmContent;

    /** Full URL of the external page that linked here. */
    @Column(name = "referrer", length = URL_MAX)
    private String referrer;

    /** Path of the first geneav page the visitor saw, without host or query. */
    @Column(name = "landing_path", length = URL_MAX)
    private String landingPath;

    protected SignupAttribution() {
        // for JPA
    }

    /**
     * Builds a sanitised instance, or null if nothing usable was supplied.
     *
     * <p>Returning null for an empty result matters: it keeps "arrived with no
     * attribution" and "arrived from a campaign" distinguishable in the database
     * instead of writing a row of empty strings that reads as neither.
     */
    public static SignupAttribution of(String utmSource, String utmMedium, String utmCampaign,
                                       String utmTerm, String utmContent,
                                       String referrer, String landingPath) {
        SignupAttribution a = new SignupAttribution();
        a.utmSource = clean(utmSource, UTM_MAX);
        a.utmMedium = clean(utmMedium, UTM_MAX);
        a.utmCampaign = clean(utmCampaign, UTM_MAX);
        a.utmTerm = clean(utmTerm, UTM_MAX);
        a.utmContent = clean(utmContent, UTM_MAX);
        a.referrer = clean(referrer, URL_MAX);
        a.landingPath = clean(landingPath, URL_MAX);
        return a.isEmpty() ? null : a;
    }

    private static String clean(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private boolean isEmpty() {
        return utmSource == null && utmMedium == null && utmCampaign == null
                && utmTerm == null && utmContent == null
                && referrer == null && landingPath == null;
    }

    public String getUtmSource() {
        return utmSource;
    }

    public String getUtmMedium() {
        return utmMedium;
    }

    public String getUtmCampaign() {
        return utmCampaign;
    }

    public String getUtmTerm() {
        return utmTerm;
    }

    public String getUtmContent() {
        return utmContent;
    }

    public String getReferrer() {
        return referrer;
    }

    public String getLandingPath() {
        return landingPath;
    }
}
