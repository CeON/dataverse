package edu.harvard.iq.dataverse.ingest;

import edu.harvard.iq.dataverse.persistence.datafile.ingest.IngestError;

import javax.ejb.ApplicationException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@ApplicationException(rollback = true)
public class IngestException extends RuntimeException {

    private IngestError errorKey;
    private List<String> bundleArguments = new ArrayList<>();

    // -------------------- CONSTRUCTORS --------------------

    public IngestException(IngestError errorKey) {
        super("");
        this.errorKey = errorKey;
    }

    public IngestException(IngestError errorKey, Throwable cause) {
        super("", cause);
        this.errorKey = errorKey;
    }

    public IngestException(IngestError errorKey, String... bundleArguments) {
        super("");
        this.errorKey = errorKey;
        this.bundleArguments = Arrays.asList(bundleArguments);
    }

    // -------------------- GETTERS --------------------

    public IngestError getErrorKey() {
        return errorKey;
    }

    public List<String> getBundleArguments() {
        return bundleArguments;
    }
}
