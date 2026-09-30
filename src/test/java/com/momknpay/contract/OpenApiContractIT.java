package com.momknpay.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.momknpay.TestcontainersConfiguration;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.ObjectMapper;

/**
 * The spec generated from code ({@code /v3/api-docs}) must match the frozen contract ({@code
 * docs/openapi.yaml}) for every operation implemented so far: same operations, success and error
 * status codes, header parameters, and request/response property names and leaf types (NFR-DOC-1,
 * M2-S5). Contract operations not implemented yet are listed, not failed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenApiContractIT {

    private static final String PREFIX = "/v1";
    private static final Set<String> METHODS = Set.of("get", "post", "put", "patch", "delete");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;

    private Map<String, Object> contract;
    private Map<String, Object> generated;

    @BeforeAll
    void loadBothSpecs() throws Exception {
        try (Reader reader =
                Files.newBufferedReader(Path.of("docs/openapi.yaml"), StandardCharsets.UTF_8)) {
            contract = new Yaml().load(reader);
        }
        String json =
                mvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
        generated = objectMapper.readValue(json, Map.class);
    }

    @Test
    void everyImplementedOperationExistsInTheContract() {
        Set<String> missing = new TreeSet<>(implementedOperations());
        missing.removeAll(contractOperations());

        assertThat(missing).as("implemented but not in docs/openapi.yaml").isEmpty();
    }

    @Test
    void implementedOperationsMatchTheContract() {
        List<String> mismatches = new ArrayList<>();
        for (String key : implementedOperations()) {
            String[] parts = key.split(" ", 2);
            Map<String, Object> mine = operation(generated, PREFIX + parts[1], parts[0]);
            Map<String, Object> theirs = operation(contract, parts[1], parts[0]);
            if (theirs == null) {
                continue; // reported by the other test
            }
            compareStatusCodes(key, mine, theirs, mismatches);
            compareHeaders(key, mine, theirs, mismatches);
            compareBodies(key, mine, theirs, mismatches);
        }

        assertThat(mismatches).as("differences from docs/openapi.yaml").isEmpty();
    }

    @Test
    void notYetImplementedOperationsAreReported() {
        Set<String> pending = new TreeSet<>(contractOperations());
        pending.removeAll(implementedOperations());
        System.out.println("Contract operations not implemented yet: " + pending);
        assertThat(pending).allMatch(op -> op.contains("/payments/"));
    }

    // ---- comparisons -------------------------------------------------------------------------

    private void compareStatusCodes(
            String op, Map<String, Object> mine, Map<String, Object> theirs, List<String> out) {
        Set<String> a = new TreeSet<>(map(mine.get("responses")).keySet());
        Set<String> b = new TreeSet<>(map(theirs.get("responses")).keySet());
        if (!a.equals(b)) {
            out.add(op + ": status codes " + a + " vs contract " + b);
        }
    }

    private void compareHeaders(
            String op, Map<String, Object> mine, Map<String, Object> theirs, List<String> out) {
        Set<String> a = headerNames(generated, mine);
        Set<String> b = headerNames(contract, theirs);
        if (!a.equals(b)) {
            out.add(op + ": headers " + a + " vs contract " + b);
        }
    }

    private void compareBodies(
            String op, Map<String, Object> mine, Map<String, Object> theirs, List<String> out) {
        Object myRequest = jsonSchema(mine.get("requestBody"));
        Object theirRequest = jsonSchema(theirs.get("requestBody"));
        compareSchema(op + " request", myRequest, theirRequest, out);

        for (String status : map(theirs.get("responses")).keySet()) {
            if (status.startsWith("2")) {
                Object a = jsonSchema(map(mine.get("responses")).get(status));
                Object b = jsonSchema(map(theirs.get("responses")).get(status));
                compareSchema(op + " " + status, a, b, out);
            }
        }
    }

    /** Property names recursively; leaf types where both sides state a single type. */
    private void compareSchema(String where, Object mine, Object theirs, List<String> out) {
        Map<String, Object> a = flatten(generated, mine);
        Map<String, Object> b = flatten(contract, theirs);
        if (a.isEmpty() && b.isEmpty()) {
            return;
        }
        Map<String, Object> aProps = map(a.get("properties"));
        Map<String, Object> bProps = map(b.get("properties"));
        if (!aProps.isEmpty() || !bProps.isEmpty()) {
            if (!aProps.keySet().equals(bProps.keySet())) {
                out.add(
                        where
                                + ": properties "
                                + new TreeSet<>(aProps.keySet())
                                + " vs contract "
                                + new TreeSet<>(bProps.keySet()));
                return;
            }
            for (String name : aProps.keySet()) {
                compareSchema(where + "." + name, aProps.get(name), bProps.get(name), out);
            }
            return;
        }
        if (a.containsKey("items") || b.containsKey("items")) {
            compareSchema(where + "[]", a.get("items"), b.get("items"), out);
            return;
        }
        if (a.get("type") instanceof String ta
                && b.get("type") instanceof String tb
                && !ta.equals(tb)) {
            out.add(where + ": type " + ta + " vs contract " + tb);
        }
        if (a.get("enum") != null
                && b.get("enum") != null
                && !new TreeSet<>(list(a.get("enum"))).equals(new TreeSet<>(list(b.get("enum"))))) {
            out.add(where + ": enum " + a.get("enum") + " vs contract " + b.get("enum"));
        }
    }

    // ---- spec navigation ---------------------------------------------------------------------

    private Set<String> implementedOperations() {
        Set<String> ops = new TreeSet<>();
        map(generated.get("paths"))
                .forEach(
                        (path, item) -> {
                            if (path.startsWith(PREFIX + "/")
                                    && !path.startsWith(PREFIX + "/test/")) {
                                map(item).keySet().stream()
                                        .filter(METHODS::contains)
                                        .forEach(
                                                m ->
                                                        ops.add(
                                                                m
                                                                        + " "
                                                                        + path.substring(
                                                                                PREFIX.length())));
                            }
                        });
        return ops;
    }

    private Set<String> contractOperations() {
        Set<String> ops = new TreeSet<>();
        map(contract.get("paths"))
                .forEach(
                        (path, item) ->
                                map(item).keySet().stream()
                                        .filter(METHODS::contains)
                                        .forEach(m -> ops.add(m + " " + path)));
        return ops;
    }

    private static Map<String, Object> operation(
            Map<String, Object> spec, String path, String method) {
        Map<String, Object> item = map(map(spec.get("paths")).get(path));
        Object op = item.get(method);
        return op == null ? null : map(op);
    }

    private static Set<String> headerNames(
            Map<String, Object> spec, Map<String, Object> operation) {
        Set<String> names = new TreeSet<>();
        for (Object raw : list(operation.get("parameters"))) {
            Map<String, Object> parameter = resolve(spec, raw);
            if ("header".equals(parameter.get("in"))) {
                names.add(String.valueOf(parameter.get("name")));
            }
        }
        return names;
    }

    private static Object jsonSchema(Object bodyOrResponse) {
        Map<String, Object> content = map(map(bodyOrResponse).get("content"));
        Map<String, Object> media = map(content.get("application/json"));
        if (media.isEmpty() && !content.isEmpty()) {
            media = map(content.values().iterator().next()); // e.g. */*
        }
        return media.get("schema");
    }

    /** Resolves $ref and merges allOf into one schema map. */
    private static Map<String, Object> flatten(Map<String, Object> spec, Object schema) {
        Map<String, Object> resolved = resolve(spec, schema);
        if (!resolved.containsKey("allOf")) {
            return resolved;
        }
        Map<String, Object> merged = new LinkedHashMap<>(resolved);
        Map<String, Object> properties = new LinkedHashMap<>();
        merged.remove("allOf");
        for (Object part : list(resolved.get("allOf"))) {
            Map<String, Object> flat = flatten(spec, part);
            properties.putAll(map(flat.get("properties")));
            flat.forEach(
                    (k, v) -> {
                        if (!"properties".equals(k)) merged.putIfAbsent(k, v);
                    });
        }
        if (!properties.isEmpty()) {
            merged.put("properties", properties);
        }
        return merged;
    }

    private static Map<String, Object> resolve(Map<String, Object> spec, Object node) {
        Map<String, Object> current = map(node);
        while (current.get("$ref") instanceof String ref) {
            Object target = spec;
            for (String segment : ref.substring(2).split("/")) {
                target = map(target).get(segment);
            }
            current = map(target);
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static List<?> list(Object value) {
        return value instanceof List<?> l ? l : List.of();
    }
}
