package me.shail.rules;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parses and validates one care program YAML file. Collects every problem, each with its location
 * (e.g. {@code tiers[1].criteria.lab: unknown key 'withinMonth'}), rather than stopping at the first.
 * Unknown keys are errors, so typos fail loudly instead of silently changing a rule.
 */
public final class ProgramParser {

    private static final YAMLMapper YAML = YAMLMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    private static final Pattern ID = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    private static final Set<String> PRIORITIES = Set.of("normal", "high");

    private final List<String> errors = new ArrayList<>();

    private ProgramParser() {
    }

    /** @throws ProgramDefinitionException listing every problem found */
    public static ProgramDefinition parse(String yaml) {
        return new ProgramParser().program(yaml);
    }

    private ProgramDefinition program(String yaml) {
        JsonNode root;
        try {
            root = YAML.readTree(yaml);
        } catch (JsonProcessingException e) {
            throw new ProgramDefinitionException(List.of("not valid YAML: " + e.getOriginalMessage()));
        }
        if (root == null || !root.isObject()) {
            throw new ProgramDefinitionException(List.of("expected a mapping with id, name, eligibility and tiers"));
        }
        allowOnly(root, "", "id", "name", "shortName", "purpose", "eligibility", "tiers", "tasks");

        String id = id(root.get("id"), "id");
        String name = requiredText(root.get("name"), "name");
        String shortName = optionalText(root.get("shortName"), "shortName");
        String purpose = optionalText(root.get("purpose"), "purpose");
        Condition eligibility = required(root.get("eligibility"), "eligibility") ? condition(root.get("eligibility"), "eligibility") : null;
        List<ProgramDefinition.Tier> tiers = tiers(root.get("tiers"));
        ProgramDefinition.TaskPolicy tasks = root.has("tasks") ? tasks(root.get("tasks"), "tasks") : null;

        if (!errors.isEmpty()) {
            throw new ProgramDefinitionException(errors);
        }
        return new ProgramDefinition(id, name, shortName, purpose, eligibility, tiers, tasks);
    }

    private List<ProgramDefinition.Tier> tiers(JsonNode node) {
        List<ProgramDefinition.Tier> tiers = new ArrayList<>();
        if (!required(node, "tiers")) {
            return tiers;
        }
        if (!node.isArray() || node.isEmpty()) {
            errors.add("tiers: expected a non-empty list");
            return tiers;
        }
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < node.size(); i++) {
            String path = "tiers[" + i + "]";
            JsonNode tier = node.get(i);
            if (!tier.isObject()) {
                errors.add(path + ": expected a mapping with id, name, criteria and needs");
                continue;
            }
            allowOnly(tier, path, "id", "name", "criteria", "needs");
            String id = id(tier.get("id"), path + ".id");
            if (id != null && !ids.add(id)) {
                errors.add(path + ".id: duplicate tier id '" + id + "'");
            }
            String name = requiredText(tier.get("name"), path + ".name");
            Condition criteria = null;
            JsonNode criteriaNode = tier.get("criteria");
            if (required(criteriaNode, path + ".criteria")) {
                if (criteriaNode.isTextual() && criteriaNode.asText().equals("otherwise")) {
                    if (i != node.size() - 1) {
                        errors.add(path + ".criteria: 'otherwise' is only allowed on the last tier");
                    }
                    criteria = new Condition.Otherwise();
                } else {
                    criteria = condition(criteriaNode, path + ".criteria");
                }
            }
            tiers.add(new ProgramDefinition.Tier(id, name, criteria, needs(tier.get("needs"), path + ".needs")));
        }
        return tiers;
    }

    private ProgramDefinition.TaskPolicy tasks(JsonNode node, String path) {
        if (!mapping(node, path, "pastCadence and neverSeen")) {
            return null;
        }
        allowOnly(node, path, "pastCadence", "neverSeen");
        return new ProgramDefinition.TaskPolicy(taskType(node.get("pastCadence"), path + ".pastCadence"),
                taskType(node.get("neverSeen"), path + ".neverSeen"));
    }

    private String taskType(JsonNode node, String path) {
        String value = requiredText(node, path);
        if (value != null && !ProgramDefinition.TaskPolicy.VALUES.contains(value)) {
            errors.add(path + ": expected scheduling, referral or none, got '" + value + "'");
            return null;
        }
        return value;
    }

    private List<ProgramDefinition.Need> needs(JsonNode node, String path) {
        List<ProgramDefinition.Need> needs = new ArrayList<>();
        if (!required(node, path)) {
            return needs;
        }
        if (!node.isArray() || node.isEmpty()) {
            errors.add(path + ": expected a non-empty list");
            return needs;
        }
        Set<String> visits = new HashSet<>();
        for (int i = 0; i < node.size(); i++) {
            String needPath = path + "[" + i + "]";
            JsonNode need = node.get(i);
            if (!need.isObject()) {
                errors.add(needPath + ": expected a mapping with visit and everyDays");
                continue;
            }
            allowOnly(need, needPath, "visit", "everyDays", "priority", "note");
            String visit = requiredText(need.get("visit"), needPath + ".visit");
            if (visit != null && !visits.add(visit.toLowerCase(Locale.ROOT))) {
                errors.add(needPath + ".visit: '" + visit + "' is listed twice in this tier");
            }
            Integer everyDays = positiveInt(need.get("everyDays"), needPath + ".everyDays", true);
            String priority = optionalText(need.get("priority"), needPath + ".priority");
            if (priority != null && !PRIORITIES.contains(priority)) {
                errors.add(needPath + ".priority: expected normal or high, got '" + priority + "'");
            }
            String note = optionalText(need.get("note"), needPath + ".note");
            needs.add(new ProgramDefinition.Need(visit, everyDays == null ? 0 : everyDays,
                    priority == null ? "normal" : priority, note));
        }
        return needs;
    }

    private Condition condition(JsonNode node, String path) {
        if (!node.isObject() || node.size() != 1) {
            errors.add(path + ": expected exactly one of all, any, not, age, diagnosis, lab");
            return null;
        }
        Map.Entry<String, JsonNode> entry = node.properties().iterator().next();
        String type = entry.getKey();
        JsonNode value = entry.getValue();
        String childPath = path + "." + type;
        return switch (type) {
            case "all" -> new Condition.All(conditions(value, childPath));
            case "any" -> new Condition.Any(conditions(value, childPath));
            case "not" -> new Condition.Not(condition(value, childPath));
            case "age" -> age(value, childPath);
            case "diagnosis" -> diagnosis(value, childPath);
            case "lab" -> lab(value, childPath);
            default -> {
                errors.add(path + ": unknown condition '" + type + "' (expected all, any, not, age, diagnosis or lab)");
                yield null;
            }
        };
    }

    private List<Condition> conditions(JsonNode node, String path) {
        if (!node.isArray() || node.isEmpty()) {
            errors.add(path + ": expected a non-empty list of conditions");
            return List.of();
        }
        List<Condition> conditions = new ArrayList<>();
        for (int i = 0; i < node.size(); i++) {
            conditions.add(condition(node.get(i), path + "[" + i + "]"));
        }
        return conditions;
    }

    private Condition age(JsonNode node, String path) {
        if (!mapping(node, path, "min and/or max")) {
            return null;
        }
        allowOnly(node, path, "min", "max");
        Integer min = nonNegativeInt(node.get("min"), path + ".min");
        Integer max = nonNegativeInt(node.get("max"), path + ".max");
        if (min == null && max == null) {
            errors.add(path + ": set min, max or both");
        } else if (min != null && max != null && min > max) {
            errors.add(path + ": min " + min + " is greater than max " + max);
        }
        return new Condition.Age(min, max);
    }

    private Condition diagnosis(JsonNode node, String path) {
        if (!mapping(node, path, "chronic, conditionGroups and/or icdPrefixes")) {
            return null;
        }
        allowOnly(node, path, "chronic", "conditionGroups", "icdPrefixes");
        Boolean chronic = null;
        JsonNode chronicNode = node.get("chronic");
        if (chronicNode != null) {
            if (chronicNode.isBoolean()) {
                chronic = chronicNode.asBoolean();
            } else {
                errors.add(path + ".chronic: expected true or false");
            }
        }
        List<String> groups = textList(node.get("conditionGroups"), path + ".conditionGroups");
        List<String> prefixes = textList(node.get("icdPrefixes"), path + ".icdPrefixes");
        if (chronicNode == null && groups == null && prefixes == null) {
            errors.add(path + ": set chronic, conditionGroups or icdPrefixes");
        }
        return new Condition.Diagnosis(chronic, groups, prefixes);
    }

    private Condition lab(JsonNode node, String path) {
        if (!mapping(node, path, "test, withinMonths and latest or exists")) {
            return null;
        }
        allowOnly(node, path, "test", "withinMonths", "latest", "exists");
        String test = requiredText(node.get("test"), path + ".test");
        Integer withinMonths = positiveInt(node.get("withinMonths"), path + ".withinMonths", false);
        Condition.Range latest = node.has("latest") ? range(node.get("latest"), path + ".latest") : null;
        Boolean exists = null;
        JsonNode existsNode = node.get("exists");
        if (existsNode != null) {
            if (existsNode.isBoolean()) {
                exists = existsNode.asBoolean();
            } else {
                errors.add(path + ".exists: expected true or false");
            }
        }
        if (!node.has("latest") && existsNode == null) {
            errors.add(path + ": set latest (a value range) or exists");
        }
        if (latest != null && Boolean.FALSE.equals(exists)) {
            errors.add(path + ": latest cannot be combined with exists: false");
        }
        return new Condition.Lab(test, withinMonths, latest, exists);
    }

    private Condition.Range range(JsonNode node, String path) {
        if (!mapping(node, path, "gt, gte, lt and/or lte")) {
            return null;
        }
        allowOnly(node, path, "gt", "gte", "lt", "lte");
        BigDecimal gt = number(node.get("gt"), path + ".gt");
        BigDecimal gte = number(node.get("gte"), path + ".gte");
        BigDecimal lt = number(node.get("lt"), path + ".lt");
        BigDecimal lte = number(node.get("lte"), path + ".lte");
        if (gt == null && gte == null && lt == null && lte == null) {
            errors.add(path + ": set at least one of gt, gte, lt, lte");
        }
        return new Condition.Range(gt, gte, lt, lte);
    }

    // --- Scalars ---

    private boolean required(JsonNode node, String path) {
        if (node == null || node.isNull()) {
            errors.add(path + ": required");
            return false;
        }
        return true;
    }

    private boolean mapping(JsonNode node, String path, String expected) {
        if (!node.isObject()) {
            errors.add(path + ": expected a mapping with " + expected);
            return false;
        }
        return true;
    }

    private void allowOnly(JsonNode node, String path, String... keys) {
        Set<String> allowed = Set.of(keys);
        for (Iterator<String> names = node.fieldNames(); names.hasNext(); ) {
            String name = names.next();
            if (!allowed.contains(name)) {
                errors.add((path.isEmpty() ? "" : path + ": ") + "unknown key '" + name + "'");
            }
        }
    }

    private String id(JsonNode node, String path) {
        String id = requiredText(node, path);
        if (id != null && !ID.matcher(id).matches()) {
            errors.add(path + ": '" + id + "' must be lowercase letters, digits and dashes (e.g. diabetes-management)");
        }
        return id;
    }

    private String requiredText(JsonNode node, String path) {
        return required(node, path) ? optionalText(node, path) : null;
    }

    private String optionalText(JsonNode node, String path) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isValueNode() || node.isBoolean() || node.asText().isBlank()) {
            errors.add(path + ": expected text");
            return null;
        }
        return node.asText().trim();
    }

    private List<String> textList(JsonNode node, String path) {
        if (node == null) {
            return null;
        }
        if (!node.isArray() || node.isEmpty()) {
            errors.add(path + ": expected a non-empty list");
            return null;
        }
        List<String> values = new ArrayList<>();
        for (int i = 0; i < node.size(); i++) {
            String value = optionalText(node.get(i), path + "[" + i + "]");
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    private Integer positiveInt(JsonNode node, String path, boolean required) {
        if (node == null && !required) {
            return null;
        }
        if (!required(node, path)) {
            return null;
        }
        if (!node.canConvertToExactIntegral() || node.asInt() <= 0) {
            errors.add(path + ": expected a whole number greater than 0");
            return null;
        }
        return node.asInt();
    }

    private Integer nonNegativeInt(JsonNode node, String path) {
        if (node == null) {
            return null;
        }
        if (!node.canConvertToExactIntegral() || node.asInt() < 0) {
            errors.add(path + ": expected a whole number, 0 or more");
            return null;
        }
        return node.asInt();
    }

    private BigDecimal number(JsonNode node, String path) {
        if (node == null) {
            return null;
        }
        if (!node.isNumber()) {
            errors.add(path + ": expected a number");
            return null;
        }
        // 9, 9.0 and 9.00 become the same value, so parsed ranges compare equal
        return node.decimalValue().stripTrailingZeros();
    }
}
