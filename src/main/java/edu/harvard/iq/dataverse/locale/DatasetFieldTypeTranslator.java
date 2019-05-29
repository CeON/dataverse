package edu.harvard.iq.dataverse.locale;

import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.metadata.DefaultMetadataBlocks;
import edu.harvard.iq.dataverse.settings.BundleSettings;
import edu.harvard.iq.dataverse.util.BundleUtil;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Logger;

@Stateless
public class DatasetFieldTypeTranslator {

    private static final Logger logger = Logger.getLogger(DatasetFieldTypeTranslator.class.getCanonicalName());

    @Inject
    private DataverseSession dataverseSession;

    // -------------------- LOGIC --------------------

    public String fetchDatasetFieldTypeTitle(String defaultDisplayName, String metadataBlockName, String dsftName) {
        String dsftBundleKey = "datasetfieldtype." + dsftName + ".title";
        Optional<String> displayNameFromExternalBundle = Optional.empty();

        if (!DefaultMetadataBlocks.METADATA_BLOCK_NAMES.contains(metadataBlockName)) {
            displayNameFromExternalBundle = getDisplayNameFromExternalBundle(metadataBlockName,
                    dsftBundleKey);
        }

        return displayNameFromExternalBundle.orElseGet(() -> getDisplayNameFromDefaultBundle(defaultDisplayName, metadataBlockName, dsftBundleKey));
    }

    public String fetchDatasetFieldTypeDescription(String defaultDisplayName, String metadataBlockName, String dsftName) {
        String dsftBundleKey = "datasetfieldtype." + dsftName + ".description";
        Optional<String> displayNameFromExternalBundle = Optional.empty();

        if (!DefaultMetadataBlocks.METADATA_BLOCK_NAMES.contains(metadataBlockName)) {
            displayNameFromExternalBundle = getDisplayNameFromExternalBundle(metadataBlockName,
                    dsftBundleKey);
        }

        return displayNameFromExternalBundle.orElseGet(() -> getDisplayNameFromDefaultBundle(defaultDisplayName, metadataBlockName, dsftBundleKey));
    }

    public String fetchDatasetFieldTypeWatermark(String defaultDisplayName, String metadataBlockName, String dsftName) {
        String dsftBundleKey = "datasetfieldtype." + dsftName + ".watermark";
        Optional<String> displayNameFromExternalBundle = Optional.empty();

        if (!DefaultMetadataBlocks.METADATA_BLOCK_NAMES.contains(metadataBlockName)) {
            displayNameFromExternalBundle = getDisplayNameFromExternalBundle(metadataBlockName,
                    dsftBundleKey);
        }

        return displayNameFromExternalBundle.orElseGet(() -> getDisplayNameFromDefaultBundle(defaultDisplayName, metadataBlockName, dsftBundleKey));
    }

    // -------------------- PRIVATE --------------------

    private String getDisplayNameFromDefaultBundle(String defaultDisplayName, String metadataBlockName, String bundleKey) {
        try {
            return BundleUtil.getStringFromPropertyFile(bundleKey, metadataBlockName);
        } catch (MissingResourceException ex) {
            logger.warning(ex.getMessage());
            return defaultDisplayName;
        }
    }

    private Optional<String> getDisplayNameFromExternalBundle(String metadataBlockName, String bundleKey) {
        try {
            URL customBundlesDir = Paths.get(BundleSettings.EXTERNAL_DIR_CUSTOM_BUNDLE_LOCATION).toUri().toURL();
            URLClassLoader externalBundleDirURL = new URLClassLoader(new URL[]{customBundlesDir});

            ResourceBundle resourceBundle =
                    ResourceBundle.getBundle(metadataBlockName, dataverseSession.getLocale(), externalBundleDirURL);

            return Optional.of(resourceBundle.getString(bundleKey));
        } catch (MalformedURLException ex) {
            logger.warning(ex.getMessage());
            return Optional.empty();
        }
    }
}
