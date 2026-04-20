package com.extractor.utils;

import com.extractor.model.SecretLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SecretLocationScanner} — FR-009 redaction contract.
 * Validates that the scanner emits pointers (path + line + kind) and NEVER
 * leaks raw credential values.
 */
class SecretLocationScannerTest {

    @TempDir
    Path tempDir;

    @Test
    void scan_emptyDirectory_returnsEmptyList() {
        List<SecretLocation> hits = new SecretLocationScanner().scan(tempDir);
        assertNotNull(hits);
        assertTrue(hits.isEmpty());
    }

    @Test
    void scan_nullProjectRoot_returnsEmptyList() {
        assertTrue(new SecretLocationScanner().scan(null).isEmpty());
    }

    @Test
    void scan_applicationYmlWithJdbcPassword_detectsJdbcPassword() throws IOException {
        Path yml = tempDir.resolve("application.yml");
        Files.writeString(yml,
                "spring:\n" +
                "  datasource:\n" +
                "    password: super-secret-123\n");

        List<SecretLocation> hits = new SecretLocationScanner().scan(tempDir);

        assertFalse(hits.isEmpty());
        SecretLocation hit = hits.get(0);
        assertEquals("application.yml", hit.getPath());
        assertEquals("JDBC_PASSWORD", hit.getKind());
        assertTrue(hit.isRedacted(), "redacted flag must always be true (FR-009)");
    }

    @Test
    void scan_persistenceXmlJdbcPassword_detectsJdbcPassword() throws IOException {
        Path xml = tempDir.resolve("persistence.xml");
        Files.writeString(xml,
                "<persistence>\n" +
                "  <property name=\"javax.persistence.jdbc.password\" value=\"pw1234\"/>\n" +
                "</persistence>\n");

        List<SecretLocation> hits = new SecretLocationScanner().scan(tempDir);

        assertEquals(1, hits.size());
        assertEquals("JDBC_PASSWORD", hits.get(0).getKind());
        assertEquals(2, hits.get(0).getLine());
    }

    @Test
    void scan_applicationPropertiesApiKey_detectsApiKey() throws IOException {
        Path props = tempDir.resolve("application.properties");
        Files.writeString(props, "app.api_key=ABCDEF123456\n");

        List<SecretLocation> hits = new SecretLocationScanner().scan(tempDir);

        assertEquals(1, hits.size());
        assertEquals("API_KEY", hits.get(0).getKind());
    }

    @Test
    void scan_awsAccessKeyPattern_detectsApiKey() throws IOException {
        Path env = tempDir.resolve(".env");
        Files.writeString(env, "AWS_KEY=AKIAIOSFODNN7EXAMPLE\n");

        List<SecretLocation> hits = new SecretLocationScanner().scan(tempDir);

        assertFalse(hits.isEmpty());
        assertEquals("API_KEY", hits.get(0).getKind());
    }

    @Test
    void scan_oauthSecret_detectsOauthSecret() throws IOException {
        Path props = tempDir.resolve("application.properties");
        Files.writeString(props, "oauth.client_secret=xoxb-123\n");

        List<SecretLocation> hits = new SecretLocationScanner().scan(tempDir);

        Set<String> kinds = hits.stream().map(SecretLocation::getKind).collect(Collectors.toSet());
        assertTrue(kinds.contains("OAUTH_SECRET"));
    }

    @Test
    void scan_neverEmitsRawValueInAnyField() throws IOException {
        String rawSecret = "RAW_SECRET_VALUE_MUST_NOT_APPEAR";
        Path props = tempDir.resolve("application.properties");
        Files.writeString(props, "db.password=" + rawSecret + "\n");

        List<SecretLocation> hits = new SecretLocationScanner().scan(tempDir);

        assertFalse(hits.isEmpty());
        for (SecretLocation hit : hits) {
            assertFalse(hit.getPath().contains(rawSecret));
            assertNull(findRawInKind(hit, rawSecret));
            assertTrue(hit.isRedacted());
        }
    }

    @Test
    void scan_dotnetAppsettingsConnectionString_detectsPasswordAndKeys() throws IOException {
        Path settings = tempDir.resolve("appsettings.json");
        Files.writeString(settings,
                "{\n" +
                "  \"ConnectionStrings\": {\n" +
                "    \"Default\": \"Server=localhost;Database=db;User Id=sa;Password=VerySecret123;\"\n" +
                "  },\n" +
                "  \"Messaging\": {\n" +
                "    \"Sb\": \"Endpoint=sb://x;SharedAccessKey=abc123==\"\n" +
                "  }\n" +
                "}\n");

        List<SecretLocation> hits = new SecretLocationScanner().scan(tempDir);

        Set<String> kinds = hits.stream().map(SecretLocation::getKind).collect(Collectors.toSet());
        assertTrue(kinds.contains("JDBC_PASSWORD"),
                "connection-string Password= must be flagged as JDBC_PASSWORD, got " + kinds);
        assertTrue(kinds.contains("API_KEY"),
                "SharedAccessKey must be flagged as API_KEY, got " + kinds);
        for (SecretLocation hit : hits) {
            assertTrue(hit.isRedacted());
            assertFalse(hit.getKind().contains("VerySecret123"));
        }
    }

    @Test
    void scan_webConfigConnectionString_detectsJdbcPassword() throws IOException {
        Path webConfig = tempDir.resolve("web.config");
        Files.writeString(webConfig,
                "<configuration>\n" +
                "  <connectionStrings>\n" +
                "    <add name=\"MyDb\" connectionString=\"Server=x;Database=y;User Id=sa;pwd=LeakedPwd;\" />\n" +
                "  </connectionStrings>\n" +
                "</configuration>\n");

        List<SecretLocation> hits = new SecretLocationScanner().scan(tempDir);

        Set<String> kinds = hits.stream().map(SecretLocation::getKind).collect(Collectors.toSet());
        assertTrue(kinds.contains("JDBC_PASSWORD"),
                "web.config connectionString pwd= must be flagged as JDBC_PASSWORD, got " + kinds);
    }

    @Test
    void scan_skipsLargeFiles() throws IOException {
        Path big = tempDir.resolve("big.properties");
        StringBuilder sb = new StringBuilder();
        while (sb.length() < 600 * 1024) {
            sb.append("filler=placeholder\n");
        }
        sb.append("db.password=leaky\n");
        Files.writeString(big, sb.toString());

        List<SecretLocation> hits = new SecretLocationScanner().scan(tempDir);

        assertTrue(hits.isEmpty(), "files larger than 512 KB must be skipped");
    }

    @Test
    void scan_ignoresNonScannableFiles() throws IOException {
        Path readme = tempDir.resolve("README.md");
        Files.writeString(readme, "db.password=leaky\n");

        assertTrue(new SecretLocationScanner().scan(tempDir).isEmpty());
    }

    private static String findRawInKind(SecretLocation hit, String raw) {
        return hit.getKind() != null && hit.getKind().contains(raw) ? hit.getKind() : null;
    }
}
