package com.extractor.analyzer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ProjectTypeDetector.
 * Validates detection of project type based on marker files in the project root.
 * [TS-011, TS-012, TS-013, TS-014, TS-015]
 */
class ProjectTypeDetectorTest {

    private ProjectTypeDetector detector;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        detector = new ProjectTypeDetector();
    }

    // --- Java detection [TS-011] ---

    @Test
    void detect_withPomXml_returnsJava() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), "<project></project>");

        ProjectType result = detector.detect(tempDir);

        assertEquals(ProjectType.JAVA, result);
    }

    @Test
    void detect_withBuildGradle_returnsJava() throws IOException {
        Files.writeString(tempDir.resolve("build.gradle"), "plugins {}");

        ProjectType result = detector.detect(tempDir);

        assertEquals(ProjectType.JAVA, result);
    }

    // --- .NET detection [TS-012] ---

    @Test
    void detect_withSlnFile_returnsDotnet() throws IOException {
        Files.writeString(tempDir.resolve("MyApp.sln"), "Microsoft Visual Studio Solution File");

        ProjectType result = detector.detect(tempDir);

        assertEquals(ProjectType.DOTNET, result);
    }

    @Test
    void detect_withCsprojFile_returnsDotnet() throws IOException {
        Files.writeString(tempDir.resolve("MyApp.csproj"), "<Project Sdk=\"Microsoft.NET.Sdk\"></Project>");

        ProjectType result = detector.detect(tempDir);

        assertEquals(ProjectType.DOTNET, result);
    }

    // --- Ambiguous detection [TS-013] ---

    @Test
    void detect_withBothJavaAndDotnet_returnsAmbiguous() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), "<project></project>");
        Files.writeString(tempDir.resolve("MyApp.csproj"), "<Project Sdk=\"Microsoft.NET.Sdk\"></Project>");

        ProjectType result = detector.detect(tempDir);

        assertEquals(ProjectType.AMBIGUOUS, result);
    }

    // --- Unknown detection [TS-014] ---

    @Test
    void detect_withNoMarkers_returnsUnknown() {
        ProjectType result = detector.detect(tempDir);

        assertEquals(ProjectType.UNKNOWN, result);
    }

    // --- .NET Framework detection [TS-015] ---

    @Test
    void detect_withDotnetFrameworkCsproj_returnsDotnet() throws IOException {
        String csprojContent = """
                <Project Sdk="Microsoft.NET.Sdk">
                  <PropertyGroup>
                    <TargetFramework>net48</TargetFramework>
                  </PropertyGroup>
                </Project>
                """;
        Files.writeString(tempDir.resolve("LegacyApp.csproj"), csprojContent);

        ProjectType result = detector.detect(tempDir);

        assertEquals(ProjectType.DOTNET, result);
    }
}
