package com.geneav.scan.marketplace;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Microsoft Marketplace fulfillment configuration, modelled on
 * {@link com.geneav.scan.plan.PlanProperties}. Holds the Partner Center
 * plan-id mapping — <b>not</b> {@code PlanProperties}, which stays about limits.
 */
@ConfigurationProperties(prefix = "geneav.marketplace")
public class MarketplaceProperties {

    private boolean enabled = false;

    /** Tenant of the single-tenant fulfillment app (App B). */
    private String tenantId = "";

    /** Client id of the fulfillment app — what the webhook JWT's {@code aud} must equal. */
    private String clientId = "";

    private String clientSecret = "";

    private String apiBaseUrl = "https://marketplaceapi.microsoft.com/api/saas";

    private String apiVersion = "2018-08-31";

    /** Microsoft's fixed SaaS fulfillment resource id (token scope + webhook appid/azp). */
    private String resourceId = "20e940b3-4c77-4b0b-9a53-9e16a1b010a7";

    private String loginBaseUrl = "https://login.microsoftonline.com";

    private String publisherId = "";

    private String offerId = "";

    /** Partner Center plan id → geneav plan key. */
    private Map<String, String> planMap = new LinkedHashMap<>();

    private long expirySweepIntervalMs = 3_600_000;

    private long expirySweepInitialDelayMs = 120_000;

    /** Usable only when enabled AND fully credentialed. */
    public boolean isConfigured() {
        return enabled && !tenantId.isBlank() && !clientId.isBlank() && !clientSecret.isBlank();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId == null ? "" : tenantId.trim();
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

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getLoginBaseUrl() {
        return loginBaseUrl;
    }

    public void setLoginBaseUrl(String loginBaseUrl) {
        this.loginBaseUrl = loginBaseUrl;
    }

    public String getPublisherId() {
        return publisherId;
    }

    public void setPublisherId(String publisherId) {
        this.publisherId = publisherId == null ? "" : publisherId.trim();
    }

    public String getOfferId() {
        return offerId;
    }

    public void setOfferId(String offerId) {
        this.offerId = offerId == null ? "" : offerId.trim();
    }

    public Map<String, String> getPlanMap() {
        return planMap;
    }

    public void setPlanMap(Map<String, String> planMap) {
        this.planMap = planMap;
    }

    public long getExpirySweepIntervalMs() {
        return expirySweepIntervalMs;
    }

    public void setExpirySweepIntervalMs(long expirySweepIntervalMs) {
        this.expirySweepIntervalMs = expirySweepIntervalMs;
    }

    public long getExpirySweepInitialDelayMs() {
        return expirySweepInitialDelayMs;
    }

    public void setExpirySweepInitialDelayMs(long expirySweepInitialDelayMs) {
        this.expirySweepInitialDelayMs = expirySweepInitialDelayMs;
    }
}
