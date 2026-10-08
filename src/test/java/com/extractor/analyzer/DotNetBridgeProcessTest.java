package com.extractor.analyzer;

import com.extractor.model.DependencyGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Subprocess-level tests for {@link DotNetBridgeAnalyzer} using fake shell
 * analyzers. [BUG-002]
 */
@DisabledOnOs(OS.WINDOWS)
class DotNetBridgeProcessTest {

    private static final String MINIMAL_GRAPH =
            "{\"components\":[],\"edges\":[],\"meta\":{\"source\":\"roslyn\"}}";

    @TempDir
    Path tempDir;

    private Path fakeAnalyzer(String body) throws IOException {
        Path script = tempDir.resolve("dotnet-analyzer");
        Files.writeString(script, "#!/bin/sh\n" + body + "\n");
        assertTrue(script.toFile().setExecutable(true));
        return script;
    }

    /**
     * [BUG-002] Analyzer writes ~1 MB to stderr before its JSON on stdout; the bridge
     * must not deadlock on a full stderr pipe.
     */
    @Test
    void analyzeProject_withLargeStderrBeforeStdout_completes() throws Exception {
        fakeAnalyzer("head -c 1048576 /dev/zero | tr '\\0' 'x' >&2\n"
                + "printf '%s' '" + MINIMAL_GRAPH + "'");
        DotNetBridgeAnalyzer analyzer =
                new DotNetBridgeAnalyzer(new DotNetToolLocator(tempDir), Duration.ofSeconds(20));

        DependencyGraph graph = assertTimeoutPreemptively(Duration.ofSeconds(30),
                () -> analyzer.analyzeProject(tempDir));

        assertNotNull(graph);
        assertTrue(graph.getComponents().isEmpty());
    }

    /**
     * [BUG-002] Analyzer that never exits must fail with a timeout error within the
     * configured timeout, not hang reading its streams.
     */
    @Test
    void analyzeProject_whenAnalyzerHangs_failsWithTimeout() throws Exception {
        fakeAnalyzer("exec sleep 60");
        DotNetBridgeAnalyzer analyzer =
                new DotNetBridgeAnalyzer(new DotNetToolLocator(tempDir), Duration.ofSeconds(1));

        RuntimeException ex = assertTimeoutPreemptively(Duration.ofSeconds(15),
                () -> assertThrows(RuntimeException.class, () -> analyzer.analyzeProject(tempDir)));

        assertTrue(ex.getMessage().contains("timed out"), ex.getMessage());
    }
}
