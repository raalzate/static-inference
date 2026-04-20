package com.extractor.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JpaTable} shape and Jackson wire format.
 * Contract: {name, component, source ∈ {ANNOTATION, PERSISTENCE_XML, SQL, DEFAULT}, schema?}.
 */
class JpaTableTest {

    @Test
    void constructor_populatesAllFields() {
        JpaTable t = new JpaTable("PRODUCT", "Product", "ANNOTATION", "public");
        assertEquals("PRODUCT", t.getName());
        assertEquals("Product", t.getComponent());
        assertEquals("ANNOTATION", t.getSource());
        assertEquals("public", t.getSchema());
    }

    @Test
    void jsonSerialization_usesSnakeCaseKeys() throws Exception {
        JpaTable t = new JpaTable("ORDERS", "Order", "DEFAULT", null);
        String json = new ObjectMapper().writeValueAsString(t);

        assertTrue(json.contains("\"name\":\"ORDERS\""));
        assertTrue(json.contains("\"component\":\"Order\""));
        assertTrue(json.contains("\"source\":\"DEFAULT\""));
        // schema is null → must be omitted by @JsonInclude(NON_NULL)
        assertFalse(json.contains("\"schema\""));
    }

    @Test
    void jsonDeserialization_acceptsAllFields() throws Exception {
        String json = "{\"name\":\"USERS\",\"component\":\"User\",\"source\":\"SQL\",\"schema\":\"auth\"}";
        JpaTable t = new ObjectMapper().readValue(json, JpaTable.class);

        assertEquals("USERS", t.getName());
        assertEquals("User", t.getComponent());
        assertEquals("SQL", t.getSource());
        assertEquals("auth", t.getSchema());
    }
}
