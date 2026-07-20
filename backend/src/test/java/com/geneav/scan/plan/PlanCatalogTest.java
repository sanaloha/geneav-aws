package com.geneav.scan.plan;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlanCatalogTest {

    private PlanProperties.Plan plan(long quota, double rpm, long burst) {
        PlanProperties.Plan p = new PlanProperties.Plan();
        p.setMonthlyScanQuota(quota);
        p.setRatePerMinute(rpm);
        p.setBurst(burst);
        return p;
    }

    @Test
    void resolvesKnownPlan() {
        PlanProperties props = new PlanProperties();
        props.setDefaultPlan("free");
        props.getDefinitions().put("free", plan(100, 10, 10));
        props.getDefinitions().put("pro", plan(100000, 120, 60));
        PlanCatalog catalog = new PlanCatalog(props);

        assertThat(catalog.resolve("pro").getMonthlyScanQuota()).isEqualTo(100000);
        assertThat(catalog.defaultPlanKey()).isEqualTo("free");
    }

    @Test
    void unknownPlanFallsBackToDefault() {
        PlanProperties props = new PlanProperties();
        props.setDefaultPlan("free");
        props.getDefinitions().put("free", plan(100, 10, 10));
        PlanCatalog catalog = new PlanCatalog(props);

        assertThat(catalog.resolve("enterprise").getMonthlyScanQuota()).isEqualTo(100);
    }

    @Test
    void emptyCatalogYieldsSafeFloor() {
        PlanCatalog catalog = new PlanCatalog(new PlanProperties());

        PlanProperties.Plan floor = catalog.resolve("anything");

        assertThat(floor.getMonthlyScanQuota()).isEqualTo(100);
        assertThat(floor.getRatePerMinute()).isEqualTo(10);
    }
}
