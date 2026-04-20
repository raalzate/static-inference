package com.extractor.utils;

import com.extractor.model.SecretLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Scans a project's configuration files for credentials or tokens and
 * emits {@link SecretLocation} records pointing at the offending line —
 * never the raw value (FR-009).
 *
 * <p>Each detection pattern maps to a well-known {@code kind} from the
 * contract: {@code JDBC_PASSWORD}, {@code API_KEY}, {@code OAUTH_SECRET},
 * {@code GENERIC}. The scanner is regex-based and intentionally
 * conservative to keep the JSON envelope small.
 */
public class SecretLocationScanner {
    private static final Logger logger = LoggerFactory.getLogger(SecretLocationScanner.class);

    private static final int MAX_BYTES_PER_FILE = 512 * 1024;

    private record Detector(Pattern pattern, String kind) {}

    private static final List<Detector> DETECTORS = List.of(
        // persistence.xml JDBC password property.
        new Detector(Pattern.compile(
            "<property[^>]*name\\s*=\\s*\"(?:javax|jakarta)\\.persistence\\.jdbc\\.password\"",
            Pattern.CASE_INSENSITIVE), "JDBC_PASSWORD"),
        new Detector(Pattern.compile(
            "<property[^>]*name\\s*=\\s*\"hibernate\\.connection\\.password\"",
            Pattern.CASE_INSENSITIVE), "JDBC_PASSWORD"),
        // Generic JDBC password keys in .properties / .yml / .yaml / .json.
        new Detector(Pattern.compile(
            "(?:^|[\\s_\\-\\.])(?:jdbc|datasource|db|spring\\.datasource)[._\\-]?password\\s*[:=]",
            Pattern.CASE_INSENSITIVE), "JDBC_PASSWORD"),
        new Detector(Pattern.compile(
            "(?:^|[\\s_\\-\\.])oauth[._\\-]?(?:client[._\\-]?)?secret\\s*[:=]",
            Pattern.CASE_INSENSITIVE), "OAUTH_SECRET"),
        new Detector(Pattern.compile(
            "(?:^|[\\s_\\-\\.])(?:api[._\\-]?key|apikey|x-api-key)\\s*[:=]",
            Pattern.CASE_INSENSITIVE), "API_KEY"),
        new Detector(Pattern.compile(
            "AKIA[0-9A-Z]{16}"), "API_KEY"),
        new Detector(Pattern.compile(
            "(?:^|[\\s_\\-\\.])(?:password|passwd|secret|token)\\s*[:=]\\s*[\"'][^\"']+[\"']",
            Pattern.CASE_INSENSITIVE), "GENERIC")
    );

    private static final Pattern YAML_DATASOURCE_SECTION = Pattern.compile(
        "^\\s*(?:datasource|db|spring|jdbc)\\s*:\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern YAML_PASSWORD_KEY = Pattern.compile(
        "^\\s+password\\s*[:=]", Pattern.CASE_INSENSITIVE);

    private static final List<String> SCAN_GLOBS = List.of(
        "persistence.xml", "web.xml", "hibernate.cfg.xml",
        "application.properties", "application.yml", "application.yaml",
        "bootstrap.properties", "bootstrap.yml",
        ".env", "config.json"
    );

    public List<SecretLocation> scan(Path projectRoot) {
        List<SecretLocation> hits = new ArrayList<>();
        if (projectRoot == null || !Files.isDirectory(projectRoot)) {
            return hits;
        }
        try (Stream<Path> files = Files.walk(projectRoot)) {
            files.filter(Files::isRegularFile)
                 .filter(this::isScannable)
                 .forEach(file -> scanFile(projectRoot, file, hits));
        } catch (IOException e) {
            logger.warn("SecretLocationScanner aborted on {}: {}", projectRoot, e.getMessage());
        }
        return hits;
    }

    private boolean isScannable(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (SCAN_GLOBS.contains(name)) return true;
        return name.endsWith(".properties") || name.endsWith(".yml") || name.endsWith(".yaml")
                || name.endsWith(".xml") || name.endsWith(".json") || name.endsWith(".env");
    }

    private void scanFile(Path projectRoot, Path file, List<SecretLocation> hits) {
        try {
            if (Files.size(file) > MAX_BYTES_PER_FILE) return;
            List<String> lines = Files.readAllLines(file);
            String relative = projectRoot.relativize(file).toString();
            boolean inDatasourceSection = false;
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                boolean matched = false;
                for (Detector d : DETECTORS) {
                    Matcher m = d.pattern().matcher(line);
                    if (m.find()) {
                        hits.add(new SecretLocation(relative, i + 1, d.kind()));
                        matched = true;
                        break;
                    }
                }
                // YAML-indent state machine: `datasource:` on one line, then `password:` indented.
                if (!matched) {
                    if (YAML_DATASOURCE_SECTION.matcher(line).find()) {
                        inDatasourceSection = true;
                    } else if (inDatasourceSection && YAML_PASSWORD_KEY.matcher(line).find()) {
                        hits.add(new SecretLocation(relative, i + 1, "JDBC_PASSWORD"));
                    } else if (!line.isBlank() && !line.startsWith(" ") && !line.startsWith("\t")) {
                        inDatasourceSection = false;
                    }
                }
            }
        } catch (IOException e) {
            logger.debug("SecretLocationScanner skipped {}: {}", file, e.getMessage());
        }
    }
}
