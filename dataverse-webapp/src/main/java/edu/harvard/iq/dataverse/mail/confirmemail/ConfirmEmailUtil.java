package edu.harvard.iq.dataverse.mail.confirmemail;

import edu.harvard.iq.dataverse.common.BundleUtil;

import java.sql.Timestamp;
import java.util.Locale;

public class ConfirmEmailUtil {

    private ConfirmEmailUtil() {
        // prevent instance creation, this class has only static methods anyway.
    }

    private static final Timestamp GRANDFATHERED_TIME = Timestamp.valueOf("2000-01-01 00:00:00.0");

    /**
     * Currently set to Y2K as an easter egg to easily set apart
     * grandfathered accounts from post-launch accounts.
     *
     * @return
     */
    public static Timestamp getGrandfatheredTime() {
        return GRANDFATHERED_TIME;
    }

    public static String friendlyExpirationTime(long expirationLong) {
        String measurement;
        String expirationString;
        boolean hasDecimal = false;
        double expirationDouble = Double.valueOf(expirationLong);

        if (expirationLong == 1) {
            measurement = BundleUtil.getStringFromBundle("minute");
        } else if (expirationLong < 60) {
            measurement = BundleUtil.getStringFromBundle("minutes");
        } else if (expirationLong == 60) {
            expirationLong = expirationLong / 60;
            measurement = BundleUtil.getStringFromBundle("hour");
        } else {
            if (expirationLong % 60 == 0) {
                expirationLong = (long) (expirationLong / 60.0);
            } else {
                expirationDouble /= 60;
                hasDecimal = true;
            }
            measurement = BundleUtil.getStringFromBundle("hours");
        }
        if (hasDecimal == true) {
            expirationString = String.valueOf(expirationDouble);
            return expirationString + " " + measurement;
        } else {
            expirationString = String.valueOf(expirationLong);
            return expirationString + " " + measurement;
        }
    }

    public static String friendlyExpirationTime(long expirationLong, Locale language) {
        String measurement;
        String expirationString;
        boolean hasDecimal = false;
        double expirationDouble = Double.valueOf(expirationLong);

        if (expirationLong == 1) {
            measurement = BundleUtil.getStringFromBundleWithLocale("minute", language);
        } else if (expirationLong < 60) {
            measurement = BundleUtil.getStringFromBundleWithLocale("minutes", language);
        } else if (expirationLong == 60) {
            expirationLong = expirationLong / 60;
            measurement = BundleUtil.getStringFromBundleWithLocale("hour", language);
        } else {
            if (expirationLong % 60 == 0) {
                expirationLong = (long) (expirationLong / 60.0);
            } else {
                expirationDouble /= 60;
                hasDecimal = true;
            }
            measurement = BundleUtil.getStringFromBundleWithLocale("hours", language);
        }
        if (hasDecimal == true) {
            expirationString = String.valueOf(expirationDouble);
            return expirationString + " " + measurement;
        } else {
            expirationString = String.valueOf(expirationLong);
            return expirationString + " " + measurement;
        }
    }

}
