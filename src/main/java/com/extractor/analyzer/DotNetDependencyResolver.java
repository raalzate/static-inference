package com.extractor.analyzer;

import com.extractor.utils.DependencyResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves NuGet dependencies by parsing .csproj files for PackageReference elements.
 * Extends the base {@link DependencyResolver} to provide .NET-specific dependency loading.
 */
public class DotNetDependencyResolver extends DependencyResolver {

    private static final Logger logger = LoggerFactory.getLogger(DotNetDependencyResolver.class);

    /**
     * Load dependencies from .csproj files in the project, parsing
     * {@code <PackageReference Include="..." Version="..." />} elements.
     * Overrides the Maven/Gradle loading from the base class.
     */
    @Override
    public void loadDependencies(Path projectRoot) {
        logger.info("Loading NuGet dependencies from .csproj files...");

        try {
            Files.walk(projectRoot)
                    .filter(path -> path.getFileName().toString().endsWith(".csproj"))
                    .forEach(this::parseCsprojFile);
        } catch (IOException e) {
            logger.warn("Error walking project tree for .csproj files: {}", e.getMessage());
        }

        logger.info("Loaded {} NuGet dependencies", getAllDependencies().size());
    }

    /**
     * Parse a single .csproj file and extract PackageReference elements.
     */
    private void parseCsprojFile(Path csprojPath) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Disable external entities for security
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(csprojPath.toFile());

            NodeList packageRefs = document.getElementsByTagName("PackageReference");

            for (int i = 0; i < packageRefs.getLength(); i++) {
                Element element = (Element) packageRefs.item(i);

                String include = element.getAttribute("Include");
                String version = element.getAttribute("Version");

                if (include != null && !include.isEmpty()) {
                    // Store as packageId:version (mimicking Maven groupId:artifactId:version)
                    String dependencyString = include;
                    if (version != null && !version.isEmpty()) {
                        dependencyString += ":" + version;
                    }

                    getAllDependencies().put(include, dependencyString);
                    logger.debug("Found NuGet dependency: {} version {}", include, version);
                }
            }

            logger.debug("Parsed .csproj file: {}", csprojPath);

        } catch (Exception e) {
            logger.warn("Error parsing .csproj at {}: {}", csprojPath, e.getMessage());
        }
    }
}
