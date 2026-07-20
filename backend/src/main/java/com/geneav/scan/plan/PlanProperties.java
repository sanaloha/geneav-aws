package com.geneav.scan.plan;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The commercial plan catalog, defined in configuration so tiers and limits can
 * change without a code change (billing wiring comes in a later slice).
 */
@ConfigurationProperties(prefix = "geneav.plans")
public class PlanProperties {

    /** Plan assigned to a newly signed-up account. */
    private String defaultPlan = "free";

    /** Plan key -> its limits. */
    private Map<String, Plan> definitions = new LinkedHashMap<>();

    public String getDefaultPlan() {
        return defaultPlan;
    }

    public void setDefaultPlan(String defaultPlan) {
        this.defaultPlan = defaultPlan;
    }

    public Map<String, Plan> getDefinitions() {
        return definitions;
    }

    public void setDefinitions(Map<String, Plan> definitions) {
        this.definitions = definitions;
    }

    /** Limits for one tier. */
    public static class Plan {
        /** Billable scans allowed per calendar month (402 once exhausted). */
        private long monthlyScanQuota;
        /** Sustained request rate for this plan's token bucket. */
        private double ratePerMinute;
        /** Burst allowance for this plan's token bucket. */
        private long burst;

        public long getMonthlyScanQuota() {
            return monthlyScanQuota;
        }

        public void setMonthlyScanQuota(long monthlyScanQuota) {
            this.monthlyScanQuota = monthlyScanQuota;
        }

        public double getRatePerMinute() {
            return ratePerMinute;
        }

        public void setRatePerMinute(double ratePerMinute) {
            this.ratePerMinute = ratePerMinute;
        }

        public long getBurst() {
            return burst;
        }

        public void setBurst(long burst) {
            this.burst = burst;
        }
    }
}
