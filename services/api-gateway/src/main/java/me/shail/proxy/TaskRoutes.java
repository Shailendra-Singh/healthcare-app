package me.shail.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * task-generation routes that depend on who is asking. A role only sees and changes tasks of its task types; a task
 * of another type answers 404, as if it did not exist. Task changes are always recorded under the logged-in user,
 * whatever {@code actor} the client sent.
 */
@ApplicationScoped
public class TaskRoutes {

    static final String SERVICE = "task-generation";
    private static final Pattern TASK_LIST = Pattern.compile("/api/v1/tasks");
    private static final Pattern ONE_TASK = Pattern.compile("/api/v1/tasks/[0-9]+");
    private static final Pattern PATIENT_TASKS = Pattern.compile("/api/v1/patients/[^/]+/tasks");
    private static final DownstreamResponse NOT_FOUND = new DownstreamResponse(404, null, new byte[0]);

    @Inject
    DownstreamClient downstream;

    @Inject
    ObjectMapper json;

    /**
     * @param visibleTypes the caller's task types; empty for every type
     * @return empty when this is not a task route, for plain forwarding
     */
    public Optional<DownstreamResponse> handle(String method, String path, String query, String contentType, byte[] body,
            String username, Optional<Set<String>> visibleTypes) {
        if (method.equals("PATCH") && ONE_TASK.matcher(path).matches()) {
            if (visibleTypes.isPresent() && !visible(get(path, null), visibleTypes.get())) {
                return Optional.of(NOT_FOUND);
            }
            return Optional.of(downstream.send(SERVICE, method, path, query, "application/json", withActor(body, username)));
        }
        if (!method.equals("GET") || visibleTypes.isEmpty()) {
            return Optional.empty();
        }
        Set<String> types = visibleTypes.get();
        if (TASK_LIST.matcher(path).matches()) {
            return Optional.of(list(path, query, types));
        }
        if (ONE_TASK.matcher(path).matches()) {
            DownstreamResponse task = get(path, query);
            return Optional.of(!task.ok() || visible(task, types) ? task : NOT_FOUND);
        }
        if (PATIENT_TASKS.matcher(path).matches()) {
            return Optional.of(filtered(get(path, query), types));
        }
        return Optional.empty();
    }

    /** Narrows the {@code taskType} filter to the caller's types; nothing visible means an empty list. */
    private DownstreamResponse list(String path, String query, Set<String> visibleTypes) {
        List<String[]> params = parse(query);
        Set<String> requested = new LinkedHashSet<>();
        params.stream().filter(p -> p[0].equals("taskType")).forEach(p -> requested.add(p[1]));
        Set<String> effective = new LinkedHashSet<>(requested.isEmpty() ? visibleTypes : requested);
        effective.retainAll(visibleTypes);
        if (effective.isEmpty()) {
            return DownstreamResponse.json(200, "[]");
        }
        List<String[]> rewritten = new ArrayList<>(params.stream().filter(p -> !p[0].equals("taskType")).toList());
        effective.forEach(type -> rewritten.add(new String[] {"taskType", type}));
        return get(path, format(rewritten));
    }

    private DownstreamResponse filtered(DownstreamResponse response, Set<String> visibleTypes) {
        if (!response.ok()) {
            return response;
        }
        JsonNode tasks = read(response);
        ArrayNode visible = json.createArrayNode();
        tasks.forEach(task -> {
            if (visibleTypes.contains(task.path("taskType").asText())) {
                visible.add(task);
            }
        });
        return DownstreamResponse.json(response.status(), visible.toString());
    }

    private boolean visible(DownstreamResponse task, Set<String> visibleTypes) {
        return task.ok() && visibleTypes.contains(read(task).path("taskType").asText());
    }

    /** The change with {@code actor} set to the logged-in user. */
    private byte[] withActor(byte[] body, String username) {
        try {
            JsonNode change = body == null || body.length == 0 ? json.createObjectNode() : json.readTree(body);
            if (!change.isObject()) {
                return body;
            }
            ((ObjectNode) change).put("actor", username);
            return json.writeValueAsBytes(change);
        } catch (IOException e) {
            // Not JSON: forward as is and let task-generation reject it
            return body;
        }
    }

    private DownstreamResponse get(String path, String query) {
        return downstream.send(SERVICE, "GET", path, query, null, null);
    }

    private JsonNode read(DownstreamResponse response) {
        try {
            return json.readTree(response.body());
        } catch (IOException e) {
            throw new IllegalStateException("task-generation returned invalid JSON", e);
        }
    }

    private static List<String[]> parse(String query) {
        List<String[]> params = new ArrayList<>();
        if (query == null || query.isEmpty()) {
            return params;
        }
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String name = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            params.add(new String[] {name, value});
        }
        return params;
    }

    private static String format(List<String[]> params) {
        return String.join("&", params.stream()
                .map(p -> URLEncoder.encode(p[0], StandardCharsets.UTF_8) + "=" + URLEncoder.encode(p[1], StandardCharsets.UTF_8))
                .toList());
    }
}
