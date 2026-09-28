package me.shail.security;

import static me.shail.security.Roles.ADMIN;
import static me.shail.security.Roles.CLINICAL_TEAM;
import static me.shail.security.Roles.SCHEDULER;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import me.shail.config.GatewayConfig;

/**
 * Who may call what through the gateway. Deny by default: {@code admin} may call every API of every service; the
 * other roles only what a rule below grants them. Which task types a role sees within the task routes is decided
 * separately by {@link #visibleTaskTypes}.
 */
@ApplicationScoped
public class AccessPolicy {

    private static final Set<String> ALL_ROLES = Set.of(ADMIN, SCHEDULER, CLINICAL_TEAM);
    private static final Set<String> CLINICAL = Set.of(ADMIN, CLINICAL_TEAM);

    /** One grant: these roles may call {@code method} on paths matching {@code path}, on {@code service}. */
    record Rule(String service, String method, Pattern path, Set<String> roles) {

        static Rule of(String service, String method, String pathRegex, Set<String> roles) {
            return new Rule(service, method, Pattern.compile(pathRegex), roles);
        }

        boolean matches(String service, String method, String path) {
            return this.service.equals(service) && this.method.equals(method) && this.path.matcher(path).matches();
        }
    }

    static final List<Rule> RULES = List.of(
            // clinical-data: schedulers need demographics and encounters to book, not diagnoses or labs
            Rule.of("clinical-data", "GET", "/api/v1/patients(/[0-9]+|/source/[^/]+)?", ALL_ROLES),
            Rule.of("clinical-data", "GET", "/api/v1/patients/[0-9]+/encounters(/.*)?", ALL_ROLES),
            Rule.of("clinical-data", "GET", "/api/v1/patients/[0-9]+/(diagnoses|lab-results)(/.*)?", CLINICAL),
            Rule.of("clinical-data", "GET", "/api/v1/(languages|specialties|lab-tests|providers|condition-groups|diagnosis-codes)(/.*)?", ALL_ROLES),
            // rules-engine: read-only for the clinical team; starting evaluations is admin only
            Rule.of("rules-engine", "GET", "/api/v1/(programs|care-needs|evaluations)(/.*)?", CLINICAL),
            Rule.of("rules-engine", "GET", "/api/v1/patients/[^/]+/programs", CLINICAL),
            // task-generation: tasks for every role, narrowed to the role's task types; runs are clinical/admin
            Rule.of("task-generation", "GET", "/api/v1/tasks(/[0-9]+)?", ALL_ROLES),
            Rule.of("task-generation", "PATCH", "/api/v1/tasks/[0-9]+", ALL_ROLES),
            Rule.of("task-generation", "GET", "/api/v1/patients/[^/]+/tasks", ALL_ROLES),
            Rule.of("task-generation", "GET", "/api/v1/task-types", ALL_ROLES),
            Rule.of("task-generation", "GET", "/api/v1/generation-runs(/.*)?", CLINICAL));

    @Inject
    GatewayConfig config;

    /** Whether a user with {@code roles} may call {@code method path} on {@code service}. */
    public boolean allows(String service, String method, String path, Set<String> roles) {
        if (roles.contains(ADMIN)) {
            return true;
        }
        return RULES.stream().anyMatch(rule -> rule.matches(service, method, path)
                && rule.roles().stream().anyMatch(roles::contains));
    }

    /**
     * The task types a user with {@code roles} may see and change: empty for every type (admin), otherwise the union
     * of their roles' {@code gateway.task-access} entries (possibly none).
     */
    public Optional<Set<String>> visibleTaskTypes(Set<String> roles) {
        if (roles.contains(ADMIN)) {
            return Optional.empty();
        }
        Set<String> types = new TreeSet<>();
        roles.forEach(role -> types.addAll(config.taskAccess().getOrDefault(role, List.of())));
        return Optional.of(types);
    }
}
