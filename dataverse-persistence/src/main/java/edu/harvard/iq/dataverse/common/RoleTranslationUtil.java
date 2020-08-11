package edu.harvard.iq.dataverse.common;

import org.apache.commons.lang3.StringUtils;
import java.util.MissingResourceException;

public class RoleTranslationUtil {

    /**
     *
     * @param alias db alias used to check for property
     * @param name default value we want to use in case property based on alias isn't found,
     *             usually original role name should be passed here
     * @return localized Role name if found, or provided in second parameter default
     */
    public static String getLocaleNameFromAlias(String alias, String name) {
        if (alias != null) {
            try {
                String key = "role." + alias.toLowerCase() + ".name";
                String localeName = BundleUtil.getStringFromNonDefaultBundle(key, "BuiltInRoles");
                if (StringUtils.isEmpty(localeName)) {
                    return name;
                } else {
                    return localeName;
                }
            } catch (MissingResourceException mre) {
                return name;
            }
        }
        return name;
    }

    /**
     *
     * @param alias db alias used to check for property
     * @param description default value we want to use in case property based on alias isn't found,
     *             usually original role description should be passed here
     * @return localized Role description if found, or provided in second parameter default
     */
    public static String getLocaleDescriptionFromAlias(String alias, String description) {
        if (alias != null) {
            String key = "role." + alias.toLowerCase() + ".description";
            try {
                String localeDescription = BundleUtil.getStringFromNonDefaultBundle(key, "BuiltInRoles");
                if (StringUtils.isEmpty(localeDescription)) {
                    return description;
                } else {
                    return localeDescription;
                }

            } catch (MissingResourceException mre) {
                return description;
            }

        }
        return description;
    }
}
