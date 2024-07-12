package edu.harvard.iq.dataverse.harvest.client;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Parameters used by the datacite DOI harvester.
 */
public class DataciteHarvesterParams extends HarvesterParams {

    private final static String DOI_PART_SEPARATOR = "/";

    private final List<DOIValue> doiImport;

    // -------------------- CONSTRUCTORS --------------------

    public DataciteHarvesterParams(List<DOIValue> doi) {
        this.doiImport = doi;
    }

    // -------------------- GETTERS --------------------

    public List<DOIValue> getDoiImport() {
        return doiImport;
    }

    // -------------------- LOGIC --------------------

    public static DataciteHarvesterParams fromFullDOIList(List<String> fullDOI) {
        return new DataciteHarvesterParams(fullDOI.stream().map(DOIValue::parseFullDOI).collect(Collectors.toList()));
    }

    // -------------------- INNER CLASSES --------------------

    static public class DOIValue {
        private final String authority;
        private final String id;

        // -------------------- CONSTRUCTORS --------------------

        public DOIValue(String authority, String id) {
            this.authority = authority;
            this.id = id;
        }

        // -------------------- GETTERS --------------------

        public String getAuthority() {
            return authority;
        }

        public String getId() {
            return id;
        }

        public String getFull() {
            return authority + DOI_PART_SEPARATOR + id;
        }

        // -------------------- LOGIC --------------------

        public static DOIValue parseFullDOI(String fullDOI) {
            String[] doiParts = fullDOI.split(DOI_PART_SEPARATOR);
            if (doiParts.length != 2) {
                throw new IllegalArgumentException("Invalid DOI: " + fullDOI);
            }
            return new DOIValue(doiParts[0], doiParts[1]);
        }

    }
}
