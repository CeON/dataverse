package edu.harvard.iq.dataverse.export;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RelatedIdentifierTypeConstants {
    private static Map<String, String> alternativeToMainIdTypeIndex = Initializer.createAlternativeToMainIdTypeIndex();

    // -------------------- LOGIC --------------------

    public static Map<String, String> getAlternativeToMainIdTypeIndex() {
        return Collections.unmodifiableMap(alternativeToMainIdTypeIndex);
    }

    // -------------------- INNER CLASSES --------------------

    private static class Initializer {
        public static Map<String, String> createAlternativeToMainIdTypeIndex() {
            return Stream.of("ARK", "arXiv", "bibcode", "DOI", "EAN13", "EISSN", "Handle", "ISBN", "ISSN", "ISTC",
                                "LISSN", "LSID", "PISSN", "PMID", "PURL", "UPC", "URL", "URN", "WOS")
                    .collect(Collectors.toMap(String::toLowerCase, v -> v));
        }
    }
}
