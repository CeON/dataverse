package edu.harvard.iq.dataverse.api.datadeposit;

import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.util.SystemConfig;
import org.swordapp.server.SwordConfiguration;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.io.File;

/**
 * Factory of {@link SwordConfigurationImpl} objects
 * @author dbojanek
 */
@Stateless
public class SwordConfigurationFactory {

    @Inject
    SystemConfig systemConfig;
    @Inject
    SettingsServiceBean settingsService;

    private SwordConfiguration swordConfiguration;

    // -------------------- CONSTRUCTORS --------------------


    public SwordConfigurationFactory() {
        this.swordConfiguration = createSwordConfiguration();
    }

    // -------------------- GETTERS --------------------
    public SwordConfiguration getSwordConfiguration() {
        return swordConfiguration;
    }

    // -------------------- LOGIC --------------------

    /**
     * Creates new {@link SwordConfigurationImpl} object
     * @return object created
     */
    public SwordConfiguration createSwordConfiguration() {
        SwordConfigurationImpl swordConf = new SwordConfigurationImpl();
        swordConf.setSwordDirString(createTempDirectory());
        swordConf.setMaxUploadSize(calculateMaxUploadSize());
        swordConf.setErrorBody(calculateErrorBody());
        swordConf.setDepositReceipt(calculateDepositReceipt());
        swordConf.setErrorBody(calculateErrorBody());
        swordConf.setStackTraceInError(calculateStackTraceInError());
        swordConf.setGeneratorUrl(createGeneratorUrl());
        swordConf.setGeneratorVersion(createGeneratorVersion());
        swordConf.setAdministratorEmail(createAdministratorEmail());
        swordConf.setAuthType(createAuthType());
        swordConf.setStoreAndCheckBinary(calculateStoreAndCheckBinary());
        swordConf.setAlternateUrl(createAlternateUrl());
        swordConf.setAlternateUrlContentType(createAlternateUrlContentType());
        swordConf.setAllowUnauthenticatedMediaAccess(calculateAllowUnauthenticatedMediaAccess());

        return swordConf;
    }

    // -------------------- PRIVATE ---------------------

    /**
     * @return temporary Sword directory, based on system config
     */
    private String createTempDirectory() {
        String tmpFileDir = systemConfig.getFilesDirectoryProperty();
        if (tmpFileDir != null) {
            String swordDirString = tmpFileDir + File.separator + "sword";
            File swordDirFile = new File(swordDirString);
            /**
             * @todo Do we really need this check? It seems like we do because
             * if you create a dataset via the native API and then later try to
             * upload a file via SWORD, the directory defined by
             * dataverse.files.directory may not exist and we get errors deep in
             * the SWORD library code. Could maybe use a try catch in the doPost
             * method of our SWORDv2MediaResourceServlet.
             */
            if (swordDirFile.exists()) {
                return swordDirString;
            } else {
                boolean mkdirSuccess = swordDirFile.mkdirs();
                if (mkdirSuccess) {
                    return swordDirString;
                } else {
                    String msgForSwordUsers = ("Could not determine or create SWORD temp directory. Check logs for details.");
                    // sadly, must throw RunTimeException to communicate with SWORD user
                    throw new RuntimeException(msgForSwordUsers);
                }
            }
        } else {
            String msgForSwordUsers = ("JVM option \"" + SystemConfig.FILES_DIRECTORY + "\" not defined. Check logs for details.");
            // sadly, must throw RunTimeException to communicate with SWORD user
            throw new RuntimeException(msgForSwordUsers);
        }
    }

    /**
     * @return calculated maximum upload size
     */
    private int calculateMaxUploadSize() {
        int unlimited = -1;

        Long maxUploadInBytes = settingsService.getValueForKeyAsLong(SettingsServiceBean.Key.MaxFileUploadSizeInBytes);

        if (maxUploadInBytes == null) {
            // (a) No setting, return unlimited
            return unlimited;

        } else if (maxUploadInBytes > Integer.MAX_VALUE) {
            // (b) setting returns the limit of int, return max int value  (BUG)
            return Integer.MAX_VALUE;

        } else {
            // (c) Return the setting as an int
            return maxUploadInBytes.intValue();

        }
    }

    private boolean calculateErrorBody() {
        return true;
    }

    private boolean calculateDepositReceipt() {
        return true;
    }

    private boolean calculateStackTraceInError() {
        /**
         * @todo make this a JVM option Or better - a SettingsServiceBean option
         *
         * Do this at the same time as SWORD: implement equivalent of
         * dvn.dataDeposit.maxUploadInBytes
         * https://github.com/IQSS/dataverse/issues/1043
         */
        return false;
    }

    private String createGeneratorUrl() {
        return "http://www.swordapp.org/";
    }

    private String createGeneratorVersion() {
        return "2.0";
    }

    private String createAdministratorEmail() {
        return null;
    }

    private String createAuthType() {
        // using "Basic" here to match what's in SwordAPIEndpoint
        return "Basic";
    }

    private boolean calculateStoreAndCheckBinary() {
        return true;
    }

    private String createAlternateUrl() {
        return null;
    }

    private String createAlternateUrlContentType() {
        return null;
    }

    private boolean calculateAllowUnauthenticatedMediaAccess() {
        return false;
    }
}
