package edu.harvard.iq.dataverse.api.datadeposit;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Holds static fields, not required by {@link org.swordapp.server.SwordConfiguration}
 *
 * @author dbojanek
 */
public final class SwordConfigurationConstants {

    private static final String BASE_URL_PATH_V1_DOT1 = "/dvn/api/data-deposit/v1.1/swordv2";
    private static final String BASE_URL_PATH_V1 = "/dvn/api/data-deposit/v1/swordv2";
    private static final String BASE_URL_PATH_CURRENT = BASE_URL_PATH_V1_DOT1;
    private static final List<String> BASE_URL_PATHS_DEPRECATED =
            Collections.unmodifiableList(Arrays.asList(BASE_URL_PATH_V1));
    private static final List<String> BASE_URL_PATHS_VALID =
            Collections.unmodifiableList(Arrays.asList(BASE_URL_PATH_V1, BASE_URL_PATH_V1_DOT1));

    // -------------------- GETTERS --------------------

    public static String getBaseUrlPathV1Dot1() {
        return BASE_URL_PATH_V1_DOT1;
    }

    public static String getBaseUrlPathV1() {
        return BASE_URL_PATH_V1;
    }

    public static String getBaseUrlPathCurrent() {
        return BASE_URL_PATH_CURRENT;
    }

    public static List<String> getBaseUrlPathsDeprecated() {
        return BASE_URL_PATHS_DEPRECATED;
    }

    public static List<String> getBaseUrlPathsValid() {
        return BASE_URL_PATHS_VALID;
    }
}
