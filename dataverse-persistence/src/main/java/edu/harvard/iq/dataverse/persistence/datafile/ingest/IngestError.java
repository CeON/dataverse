package edu.harvard.iq.dataverse.persistence.datafile.ingest;

import edu.harvard.iq.dataverse.common.BundleUtil;

import java.util.List;

/**
 * Enum representing error keys, which translates to bundle key with prefix.
 */
public enum IngestError {
    NOPLUGIN,
    STATS_OR_SIGNATURE_FAILURE,
    DB_FAIL_WITH_TAB_PRODUCED,
    DB_FAIL,
    PLUGIN_RAW_FILES,
    UNZIP_FAIL,
    UNZIP_SIZE_FAIL,
    WRONG_HEADER,
    INVALID_HEADER,
    RECORD_MISMATCH,
    EXCEL_PARSE,
    EXCEL_NO_ROWS,
    EXCEL_ONLY_ONE_ROW,
    EXCEL_READ_FAIL,
    EXCEL_MISMATCH,
    EXCEL_LINE_COUNT,
    EXCEL_NUMERIC_PARSE,
    RTAB_FAIL,
    RTAB_MISMATCH,
    RTAB_BOOLEAN_FAIL,
    RTAB_UNREDABLE_BOOLEAN,
    RTAB_VARQNTY_MISSING,
    RTAB_VARQNTY_ZERO,
    UNKNOWN_ERROR;

    public static String ERROR_KEY_PREFIX = "ingest.error.";

    // -------------------- LOGIC --------------------

    public static String getErrorMessage(IngestError error) {
        return BundleUtil.getStringFromBundle(ERROR_KEY_PREFIX + error.toString());
    }

    public static String getErrorMessage(IngestError error, List<String> arguments) {
        return BundleUtil.getStringFromBundle(ERROR_KEY_PREFIX + error.toString(), arguments);
    }

    public static String getDefaultErrorMessage() {
        return BundleUtil.getStringFromBundle(ERROR_KEY_PREFIX + UNKNOWN_ERROR.toString());
    }

}
