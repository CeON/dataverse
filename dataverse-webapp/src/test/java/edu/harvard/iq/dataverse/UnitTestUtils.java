package edu.harvard.iq.dataverse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;

public class UnitTestUtils {
    /**
     * Gets the contents of a classpath resource as a String using UTF-8 encoding.
     * 
     * <p>
     * The path to unit test resources should be relative, because the path changes when the application
     * is packaged into distributed format (e.g. JAR or WAR).
     * </p>
     * 
     * @param relResourcePath relative path of the desired resource
     * @return the requested String
     * @throws IOException
     */
    public static String readFileToString(String relResourcePath) throws IOException {
        if (relResourcePath.startsWith("src/") || relResourcePath.startsWith("./src/")) {
            throw new IllegalArgumentException("Resource path must be relative");
        }

        return IOUtils.resourceToString(relResourcePath, StandardCharsets.UTF_8, UnitTestUtils.class.getClassLoader());
    }
}