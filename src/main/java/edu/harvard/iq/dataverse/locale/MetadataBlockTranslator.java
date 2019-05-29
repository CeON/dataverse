package edu.harvard.iq.dataverse.locale;

import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.metadata.DefaultMetadataBlocks;
import edu.harvard.iq.dataverse.settings.BundleSettings;
import edu.harvard.iq.dataverse.util.BundleUtil;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.inject.Named;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Logger;

/**
 * Responsible for translating stuff regarding metadata block.
 */
@Stateless
@Named
public class MetadataBlockTranslator {

    private static final Logger logger = Logger.getLogger(MetadataBlockTranslator.class.getCanonicalName());

    @Inject
    private DataverseSession dataverseSession;

    // -------------------- LOGIC --------------------

    /**
     * Gets display name for specified metadata block. If it is external metadata block,
     * method tries to access external directory #{@link BundleSettings#EXTERNAL_DIR_CUSTOM_BUNDLE_LOCATION}
     * where bundles are kept and return the dispaly name.
     * <p>
     * If it is default metadata block #{@link DefaultMetadataBlocks#METADATA_BLOCK_NAMES}
     * method tried to get the name from default bundle otherwise it returns defaultDisplayName.
     */
    public String fetchMetadataBlockDisplayName(String defaultDisplayName, String metadataBlockName) {
        Optional<String> displayNameFromExternalBundle = Optional.empty();

        if (!DefaultMetadataBlocks.METADATA_BLOCK_NAMES.contains(metadataBlockName)) {
            displayNameFromExternalBundle = getDisplayNameFromExternalBundle(metadataBlockName);
        }

        return displayNameFromExternalBundle.orElseGet(() -> getDisplayNameFromDefaultBundle(defaultDisplayName, metadataBlockName));
    }

    // -------------------- PRIVATE --------------------

    private String getDisplayNameFromDefaultBundle(String defaultDisplayName, String metadataBlockName) {
        try {
            return BundleUtil.getStringFromPropertyFile("metadatablock.displayName", metadataBlockName);
        } catch (MissingResourceException ex) {
            logger.warning(ex.getMessage());
            return defaultDisplayName;
        }
    }

    private Optional<String> getDisplayNameFromExternalBundle(String metadataBlockName) {
        try {
            URL customBundlesDir = Paths.get(BundleSettings.EXTERNAL_DIR_CUSTOM_BUNDLE_LOCATION).toUri().toURL();
            URLClassLoader externalBundleDirURL = new URLClassLoader(new URL[]{customBundlesDir});

            ResourceBundle resourceBundle =
                    ResourceBundle.getBundle(metadataBlockName, dataverseSession.getLocale(), externalBundleDirURL);

            return Optional.of(resourceBundle.getString("metadatablock.displayName"));
        } catch (MalformedURLException ex) {
            logger.warning(ex.getMessage());
            return Optional.empty();
        }
    }
}
