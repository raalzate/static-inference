package com.extractor.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Determinism of {@link DependencyGraph.Meta}. [BUG-001]
 */
class DependencyGraphMetaTest {

    private static final String OPT_IN = "STATIC_INFERENCE_COLLECTED_AT";
    private final ObjectMapper mapper = new ObjectMapper();

    @AfterEach
    void clearOptIn() {
        System.clearProperty(OPT_IN);
    }

    /** [BUG-001] By default no wall-clock timestamp is emitted. */
    @Test
    void meta_byDefault_omitsCollectedAt() throws Exception {
        System.clearProperty(OPT_IN);

        String json = mapper.writeValueAsString(new DependencyGraph.Meta());

        assertFalse(json.contains("collected_at"), json);
    }

    /** [BUG-001] Two default Meta instances serialize identically. */
    @Test
    void meta_byDefault_serializesDeterministically() throws Exception {
        String first = mapper.writeValueAsString(new DependencyGraph.Meta());
        Thread.sleep(5);
        String second = mapper.writeValueAsString(new DependencyGraph.Meta());

        assertEquals(first, second);
    }

    /** [BUG-001] Timestamp is still available when the user explicitly opts in. */
    @Test
    void meta_withOptIn_emitsCollectedAt() throws Exception {
        System.setProperty(OPT_IN, "true");

        String json = mapper.writeValueAsString(new DependencyGraph.Meta());

        assertTrue(json.contains("collected_at"), json);
    }
}
