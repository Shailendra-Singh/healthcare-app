package me.shail.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import me.shail.config.GatewayConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The access matrix, role by role. */
class AccessPolicyTest {

    static final Set<String> ADMIN = Set.of("admin");
    static final Set<String> CLINICAL = Set.of("clinical-team");
    static final Set<String> SCHEDULER = Set.of("scheduler");
    static final Set<String> NO_APP_ROLE = Set.of("offline_access");

    AccessPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new AccessPolicy();
        policy.config = new GatewayConfig() {
            public Map<String, Service> services() {
                return Map.of();
            }

            public Map<String, List<String>> taskAccess() {
                return Map.of("scheduler", List.of("SCHEDULING"), "clinical-team", List.of("SCHEDULING", "REFERRAL"));
            }

            public Keycloak keycloak() {
                return () -> "http://localhost:8180/realms/healthcare";
            }
        };
    }

    @Test
    void adminMayCallEverything() {
        assertTrue(policy.allows("clinical-data", "GET", "/api/v1/etl-runs/latest", ADMIN));
        assertTrue(policy.allows("clinical-data", "GET", "/api/v1/evaluation-inputs", ADMIN));
        assertTrue(policy.allows("rules-engine", "POST", "/api/v1/evaluations", ADMIN));
        assertTrue(policy.allows("task-generation", "POST", "/api/v1/generation-runs", ADMIN));
    }

    @Test
    void clinicalTeamReadsClinicalDataAndRulesButStartsNothing() {
        assertTrue(policy.allows("clinical-data", "GET", "/api/v1/patients/7/diagnoses", CLINICAL));
        assertTrue(policy.allows("clinical-data", "GET", "/api/v1/patients/7/lab-results/latest", CLINICAL));
        assertTrue(policy.allows("rules-engine", "GET", "/api/v1/care-needs", CLINICAL));
        assertTrue(policy.allows("rules-engine", "GET", "/api/v1/patients/P1/programs", CLINICAL));
        assertTrue(policy.allows("rules-engine", "GET", "/api/v1/evaluations/latest", CLINICAL));
        assertTrue(policy.allows("task-generation", "GET", "/api/v1/generation-runs/latest", CLINICAL));
        assertFalse(policy.allows("rules-engine", "POST", "/api/v1/evaluations", CLINICAL));
        assertFalse(policy.allows("task-generation", "POST", "/api/v1/generation-runs", CLINICAL));
        assertFalse(policy.allows("clinical-data", "GET", "/api/v1/etl-runs/latest", CLINICAL));
        assertFalse(policy.allows("clinical-data", "GET", "/api/v1/evaluation-inputs", CLINICAL));
    }

    @Test
    void schedulerSeesDemographicsEncountersAndTasksOnly() {
        assertTrue(policy.allows("clinical-data", "GET", "/api/v1/patients", SCHEDULER));
        assertTrue(policy.allows("clinical-data", "GET", "/api/v1/patients/7", SCHEDULER));
        assertTrue(policy.allows("clinical-data", "GET", "/api/v1/patients/source/P1", SCHEDULER));
        assertTrue(policy.allows("clinical-data", "GET", "/api/v1/patients/7/encounters/upcoming", SCHEDULER));
        assertTrue(policy.allows("clinical-data", "GET", "/api/v1/providers/3", SCHEDULER));
        assertTrue(policy.allows("task-generation", "GET", "/api/v1/tasks", SCHEDULER));
        assertTrue(policy.allows("task-generation", "PATCH", "/api/v1/tasks/12", SCHEDULER));
        assertFalse(policy.allows("clinical-data", "GET", "/api/v1/patients/7/diagnoses", SCHEDULER));
        assertFalse(policy.allows("clinical-data", "GET", "/api/v1/patients/7/lab-results", SCHEDULER));
        assertFalse(policy.allows("rules-engine", "GET", "/api/v1/care-needs", SCHEDULER));
        assertFalse(policy.allows("task-generation", "GET", "/api/v1/generation-runs/latest", SCHEDULER));
    }

    @Test
    void denyByDefault() {
        assertFalse(policy.allows("task-generation", "DELETE", "/api/v1/tasks/12", CLINICAL), "no rule grants DELETE");
        assertFalse(policy.allows("clinical-data", "GET", "/api/v1/patients/7/secrets", CLINICAL), "unknown path");
        assertFalse(policy.allows("clinical-data", "GET", "/api/v1/patients", NO_APP_ROLE), "logged in without an app role");
        assertFalse(policy.allows("task-generation", "GET", "/api/v1/tasks", Set.of()));
    }

    @Test
    void visibleTaskTypesByRole() {
        assertEquals(Optional.empty(), policy.visibleTaskTypes(ADMIN), "admin sees every type");
        assertEquals(Optional.of(Set.of("SCHEDULING")), policy.visibleTaskTypes(SCHEDULER));
        assertEquals(Optional.of(Set.of("SCHEDULING", "REFERRAL")), policy.visibleTaskTypes(CLINICAL));
        assertEquals(Optional.of(Set.of("SCHEDULING", "REFERRAL")), policy.visibleTaskTypes(Set.of("scheduler", "clinical-team")),
                "several roles see the union");
        assertEquals(Optional.of(Set.of()), policy.visibleTaskTypes(NO_APP_ROLE));
    }
}
