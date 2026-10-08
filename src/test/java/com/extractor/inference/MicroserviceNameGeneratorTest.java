package com.extractor.inference;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Determinism of {@link MicroserviceNameGenerator}. [BUG-004]
 */
class MicroserviceNameGeneratorTest {

    /** [BUG-004] "service" precedes "handler" in the role list, so the token is "order". */
    @Test
    void generateName_withTwoRoleKeywords_usesFirstDeclaredRole() {
        Cluster cluster = new Cluster(1);
        cluster.setMembers(List.of("com.shop.order.OrderServiceHandler"));

        String name = MicroserviceNameGenerator.generateName(Set.of(1), List.of(cluster));

        assertEquals("Componente de Order", name);
    }

    /**
     * [BUG-004] Role keywords must be held in an ordered collection; an unordered
     * immutable set iterates in a per-process randomized order.
     */
    @Test
    void roleKeywords_areDeclaredInOrderedCollection() throws IOException {
        String source = Files.readString(
                Path.of("src/main/java/com/extractor/inference/MicroserviceNameGenerator.java"));
        Matcher m = Pattern.compile("roleKeywords\\s*=\\s*(\\w+)\\.of\\(").matcher(source);

        assertTrue(m.find(), "roleKeywords declaration not found");
        assertEquals("List", m.group(1));
    }
}
