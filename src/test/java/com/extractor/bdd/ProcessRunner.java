package com.extractor.bdd;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Runs external commands (npm, node, bash) for the scenarios that delegate to other toolchains. */
final class ProcessRunner {

    /** Exit code, merged stdout+stderr and wall-clock duration of a command. */
    record Result(int exitCode, String output, Duration elapsed) {
    }

    private ProcessRunner() {
    }

    static Result run(List<String> command, Path workDir, Map<String, String> env, Duration timeout) {
        long start = System.nanoTime();
        try {
            ProcessBuilder pb = new ProcessBuilder(command).directory(workDir.toFile()).redirectErrorStream(true);
            pb.environment().putAll(env);
            Process process = pb.start();
            process.getOutputStream().close();
            StringBuilder out = new StringBuilder();
            Thread reader = new Thread(() -> {
                try {
                    out.append(new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            reader.start();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                throw new IllegalStateException("Timed out after " + timeout + ": " + command);
            }
            reader.join();
            return new Result(process.exitValue(), out.toString(), Duration.ofNanos(System.nanoTime() - start));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot run " + command, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    static Result run(List<String> command, Path workDir) {
        return run(command, workDir, Map.of(), Duration.ofMinutes(10));
    }
}
