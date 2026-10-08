package com.extractor.schema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * JSON helpers for contract tests: parsing, dotted-path and JSON Pointer navigation/mutation, and
 * re-serialization with the CLI {@link ObjectMapper} configuration.
 */
public final class JsonDocs {

    private static final ObjectMapper MAPPER = DemoOutputs.cliMapper();

    private JsonDocs() {
    }

    /** Parses JSON text into a mutable tree (field order preserved). */
    public static JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON: " + e.getOriginalMessage(), e);
        }
    }

    /** Serializes a tree exactly like the CLI writes its artifacts (pretty printer, sorted map entries). */
    public static String write(JsonNode node) {
        try {
            return MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Converts a dotted path ({@code meta.schema_version}) into a JSON Pointer ({@code /meta/schema_version}). */
    public static String pointer(String dottedPath) {
        return "/" + dottedPath.replace('.', '/');
    }

    /** Returns the node at a dotted path, or {@code null} when absent. */
    public static JsonNode get(JsonNode root, String dottedPath) {
        JsonNode node = root.at(pointer(dottedPath));
        return node.isMissingNode() ? null : node;
    }

    /** Removes the property at a JSON Pointer (no-op when absent). */
    public static void removeAt(JsonNode root, String jsonPointer) {
        int slash = jsonPointer.lastIndexOf('/');
        JsonNode parent = slash <= 0 ? root : root.at(jsonPointer.substring(0, slash));
        String last = jsonPointer.substring(slash + 1);
        if (parent instanceof ObjectNode object) {
            object.remove(last);
        } else if (parent instanceof ArrayNode array) {
            array.remove(Integer.parseInt(last));
        }
    }

    /** Sets (or replaces) the value at a JSON Pointer whose parent exists. */
    public static void setAt(JsonNode root, String jsonPointer, JsonNode value) {
        int slash = jsonPointer.lastIndexOf('/');
        JsonNode parent = slash <= 0 ? root : root.at(jsonPointer.substring(0, slash));
        String last = jsonPointer.substring(slash + 1);
        if (parent instanceof ObjectNode object) {
            object.set(last, value);
        } else if (parent instanceof ArrayNode array) {
            int index = Integer.parseInt(last);
            if (index < array.size()) {
                array.set(index, value);
            } else {
                array.add(value);
            }
        } else {
            throw new IllegalArgumentException("Parent of " + jsonPointer + " does not exist");
        }
    }

    /** Ordered property names of an object node. */
    public static List<String> fieldNames(JsonNode object) {
        List<String> names = new ArrayList<>();
        Iterator<String> it = object.fieldNames();
        while (it.hasNext()) {
            names.add(it.next());
        }
        return names;
    }

    /** JSON Pointers of every explicit {@code null} value in the tree. */
    public static List<String> nullPointers(JsonNode root) {
        List<String> out = new ArrayList<>();
        collectNulls(root, "", out);
        return out;
    }

    private static void collectNulls(JsonNode node, String path, List<String> out) {
        if (node.isNull()) {
            out.add(path);
        } else if (node.isObject()) {
            for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext();) {
                Map.Entry<String, JsonNode> e = it.next();
                collectNulls(e.getValue(), path + "/" + e.getKey(), out);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                collectNulls(node.get(i), path + "/" + i, out);
            }
        }
    }

    /** Shared mapper (CLI configuration). */
    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
