package com.extractor.bdd;

import com.extractor.schema.DemoOutputs;
import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Scenario state shared by the step-definition classes (Cucumber without a DI container: one static
 * holder, reset before every scenario by {@link Hooks}).
 */
final class World {

    static String project;
    static boolean collectedAtOptIn;
    static DemoOutputs.Artifacts artifacts;
    static Path outDir;

    /** Document under mutation/validation and its contract kind. */
    static JsonNode doc;
    static String docKind;
    static List<String> errors = new ArrayList<>();

    /** Raw .NET analyzer output (stdout) and the fixture it came from. */
    static Path dotnetFixture;
    static String rawDotnetGraph;

    private World() {
    }

    static void reset() {
        project = null;
        collectedAtOptIn = false;
        artifacts = null;
        outDir = null;
        doc = null;
        docKind = null;
        errors = new ArrayList<>();
        dotnetFixture = null;
        rawDotnetGraph = null;
    }
}
