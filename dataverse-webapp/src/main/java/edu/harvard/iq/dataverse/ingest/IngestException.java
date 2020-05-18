package edu.harvard.iq.dataverse.ingest;

import com.google.common.collect.Lists;
import io.vavr.control.Option;

import javax.ejb.ApplicationException;
import java.util.ArrayList;
import java.util.List;

@ApplicationException(rollback = true)
public class IngestException extends RuntimeException {

    private String bundleKey;
    private List<String> bundleArguments = new ArrayList<>();

    // -------------------- CONSTRUCTORS --------------------

    public IngestException(String message) {
        super(message);
    }

    public IngestException(String message, String bundleKey) {
        super(message);
        this.bundleKey = bundleKey;
    }

    public IngestException(String message, String bundleKey, String... bundleArguments) {
        super(message);
        this.bundleKey = bundleKey;
        this.bundleArguments = Lists.newArrayList(bundleArguments);
    }

    public IngestException(String message, String bundleKey, Throwable cause, String... bundleArguments) {
        super(message,cause);
        this.bundleKey = bundleKey;
        this.bundleArguments = Lists.newArrayList(bundleArguments);
    }

    // -------------------- GETTERS --------------------

    public Option<String> getBundleKey() {
        return Option.of(bundleKey);
    }

    public List<String> getBundleArguments() {
        return bundleArguments;
    }
}
