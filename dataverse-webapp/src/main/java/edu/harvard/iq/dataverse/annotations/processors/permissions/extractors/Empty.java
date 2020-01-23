package edu.harvard.iq.dataverse.annotations.processors.permissions.extractors;

import edu.harvard.iq.dataverse.persistence.DvObject;

public class Empty implements DvObjectExtractor {

    @Override
    public DvObject extract(Object input) {
        throw new IllegalStateException("This extractor should have not been called. Probably erroneous configuration" +
                "of permission annotations for service method.");
    }
}
