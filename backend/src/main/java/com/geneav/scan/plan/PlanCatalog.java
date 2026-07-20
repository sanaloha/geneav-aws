package com.geneav.scan.plan;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/** Resolves an account's plan key to its configured limits. */
@Service
@EnableConfigurationProperties(PlanProperties.class)
public class PlanCatalog {

    private final PlanProperties props;

    public PlanCatalog(PlanProperties props) {
        this.props = props;
    }

    /** The plan key new accounts receive. */
    public String defaultPlanKey() {
        return props.getDefaultPlan();
    }

    /**
     * Limits for {@code planKey}, falling back to the default plan (and finally a
     * safe hard-coded floor) if the key is unknown or the catalog is empty — so a
     * misconfigured account can never bypass limits entirely.
     */
    public PlanProperties.Plan resolve(String planKey) {
        PlanProperties.Plan plan = props.getDefinitions().get(planKey);
        if (plan != null) {
            return plan;
        }
        PlanProperties.Plan fallback = props.getDefinitions().get(props.getDefaultPlan());
        if (fallback != null) {
            return fallback;
        }
        PlanProperties.Plan floor = new PlanProperties.Plan();
        floor.setMonthlyScanQuota(100);
        floor.setRatePerMinute(10);
        floor.setBurst(10);
        return floor;
    }
}
