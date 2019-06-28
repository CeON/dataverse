package edu.harvard.iq.dataverse.authorization;

import edu.harvard.iq.dataverse.util.BundleUtil;

import java.util.MissingResourceException;

public class RoleTranslationUtil {

    public static String getNameFromAlias(String alias) {
        if (alias != null) {
            try {
                String key = "role." + alias.toLowerCase() + ".name";
                String _name = BundleUtil.getStringFromPropertyFile(key, "BuiltInRoles");
                if (_name == null) {
                    return null;
                } else {
                    return _name;
                }
            } catch (MissingResourceException mre) {
                return null;
            }
        }
        return null;
    }

    public static String getDescriptionFromAlias(String alias) {
        if (alias != null) {
            String key = "role." + alias.toLowerCase() + ".description";
            try {
                String _description = BundleUtil.getStringFromPropertyFile(key, "BuiltInRoles");
                if (_description == null) {
                    return null;
                } else {
                    return _description;
                }

            } catch (MissingResourceException mre) {
                return null;
            }

        }
        return null;
    }
}
