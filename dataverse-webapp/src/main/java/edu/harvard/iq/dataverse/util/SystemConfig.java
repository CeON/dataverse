package edu.harvard.iq.dataverse.util;

import edu.harvard.iq.dataverse.authorization.providers.oauth2.DevOAuthAccountType;
import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean.Key;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.control.Try;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.inject.Named;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * System-wide configuration
 */
@Stateless
@Named
public class SystemConfig {

    private static final Logger logger = Logger.getLogger(SystemConfig.class.getCanonicalName());

    private static final String VERSION_PROPERTIES_CLASSPATH = "/config/version.properties";
    private static final String VERSION_PROPERTIES_KEY = "dataverse.version";
    private static final String VERSION_COMMIT_ID = "git.commit.id.full";
    private static final String VERSION_PLACEHOLDER = "${project.version}";
    private static final String VERSION_FALLBACK = "4.0";

    public static final String DATAVERSE_PATH = "/dataverse/";

    /**
     * A JVM option for where files are stored on the file system.
     */
    public static final String FILES_DIRECTORY = "dataverse.files.directory";

    @Inject
    private SettingsServiceBean settingsService;


    private static String appVersionWithBuild = null;
    private static String appVersion = null;

    public String getVersionWithBuild() {

        if (appVersionWithBuild == null) {

            // We'll rely on Maven placing the version number into the
            // version.properties file using resource filtering

            Try<Tuple2<String, String>> appVersionTry = Try
                    .withResources(() -> getClass().getResourceAsStream(VERSION_PROPERTIES_CLASSPATH))
                    .of(is -> {
                        Properties properties = new Properties();
                        properties.load(is);
                        return properties;
                    })
                    .map(p -> Tuple.of(p.getProperty(VERSION_PROPERTIES_KEY), p.getProperty(VERSION_COMMIT_ID)));

            if (appVersionTry.isFailure()) {
                appVersionWithBuild = VERSION_FALLBACK;
                logger.warning("Failed to read the " + VERSION_PROPERTIES_CLASSPATH + " file");

            } else if (StringUtils.equals(appVersionTry.get()._1(), VERSION_PLACEHOLDER)) {
                appVersionWithBuild = VERSION_FALLBACK;
                logger.warning(VERSION_PROPERTIES_CLASSPATH + " was not filtered by maven (check your pom.xml configuration)");

            } else {
                appVersionWithBuild = appVersionTry.get()._1() + "-" + appVersionTry.get()._2();
            }
        }

        return appVersionWithBuild;
    }

    public String getVersion() {

        if (appVersion == null) {
            Try<String> appVersionTry = Try
                    .withResources(() -> getClass().getResourceAsStream(VERSION_PROPERTIES_CLASSPATH))
                    .of(is -> {
                        Properties properties = new Properties();
                        properties.load(is);
                        return properties;
                    })
                    .map(p -> p.getProperty(VERSION_PROPERTIES_KEY));

            if (appVersionTry.isFailure()) {
                appVersion = VERSION_FALLBACK;
                logger.warning("Failed to read the " + VERSION_PROPERTIES_CLASSPATH + " file");

            } else if (StringUtils.equals(appVersionTry.get(), VERSION_PLACEHOLDER)) {
                appVersion = VERSION_FALLBACK;
                logger.warning(VERSION_PROPERTIES_CLASSPATH + " was not filtered by maven (check your pom.xml configuration)");

            } else {
                appVersion = appVersionTry.get();
            }
        }

        return appVersion;
    }

    public int getMinutesUntilPasswordResetTokenExpires() {
        return settingsService.getValueForKeyAsInt(SettingsServiceBean.Key.MinutesUntilPasswordResetTokenExpires);
    }

    public String getDataverseSiteUrl() {
        return settingsService.getValueForKey(SettingsServiceBean.Key.SiteUrl);
    }

    public boolean isReadonlyMode() {
        return settingsService.isTrueForKey(SettingsServiceBean.Key.ReadonlyMode);
    }

    public boolean isUnconfirmedMailRestrictionModeEnabled() {
        return settingsService.isTrueForKey(Key.UnconfirmedMailRestrictionModeEnabled);
    }

    public boolean isSignupAllowed() {
        if (isReadonlyMode()) {
            return false;
        }
        return settingsService.isTrueForKey(SettingsServiceBean.Key.AllowSignUp);
    }

    public String getFilesDirectory() {
        return getFilesDirectoryStatic();
    }

    public static String getFilesDirectoryStatic() {
        String filesDirectory = System.getProperty(SystemConfig.FILES_DIRECTORY);
        if (StringUtils.isEmpty(filesDirectory)) {
            filesDirectory = "/tmp/files";
        }
        return filesDirectory;
    }

    /**
     * The "official" server's fully-qualified domain name:
     */
    public String getDataverseServer() {
        try {
            return new URL(settingsService.getValueForKey(SettingsServiceBean.Key.SiteUrl)).getHost();
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return "localhost";
        }
    }

    public String getSiteName(Locale locale) {
        return getLocalizedProperty(Key.SiteName, locale);
    }

    public String getSiteFullName(Locale locale) {
        return getLocalizedProperty(Key.SiteFullName, locale);
    }

    public boolean isSuperiorLogoDefined(Locale locale) {
        return !getLocalizedProperty(Key.SuperiorLogoPath, locale).isEmpty() || !getLocalizedProperty(Key.SuperiorLogoResponsivePath, locale).isEmpty();
    }

    public String getSuperiorLogoLink(Locale locale) {
        return getLocalizedProperty(Key.SuperiorLogoLink, locale);
    }

    public String getSuperiorLogoPath(Locale locale) {
        return getLocalizedProperty(Key.SuperiorLogoPath, locale);
    }

    public String getSuperiorLogoResponsivePath(Locale locale) {
        return getLocalizedProperty(Key.SuperiorLogoResponsivePath, locale);
    }

    public String getSuperiorLogoContrastPath(Locale locale) {
        return getLocalizedProperty(Key.SuperiorLogoContrastPath, locale);
    }

    public String getSuperiorLogoContrastResponsivePath(Locale locale) {
        return getLocalizedProperty(Key.SuperiorLogoContrastResponsivePath, locale);
    }

    public String getSuperiorLogoAlt(Locale locale) {
        return getLocalizedProperty(Key.SuperiorLogoAlt, locale);
    }

    public String getGuidesBaseUrl(Locale locale) {
        String guidesBaseUrl = settingsService.getValueForKey(SettingsServiceBean.Key.GuidesBaseUrl);
        return guidesBaseUrl + "/" + locale;
    }

    public String getGuidesVersion() {
        String guidesVersion = settingsService.getValueForKey(SettingsServiceBean.Key.GuidesVersion);

        return guidesVersion.equals(StringUtils.EMPTY) ? getVersion() : guidesVersion;
    }

    public boolean isRserveConfigured() {
        return settingsService.isTrueForKey(SettingsServiceBean.Key.RserveConfigured);
    }

    public long getUploadLogoSizeLimit() {
        return 500000;
    }

    // TODO: (?)
    // create sensible defaults for these things? -- 4.2.2
    public long getThumbnailSizeLimitImage() {
        return getThumbnailSizeLimit("Image");
    }

    public long getThumbnailSizeLimitPDF() {
        return getThumbnailSizeLimit("PDF");
    }

    private long getThumbnailSizeLimit(String type) {
        if (isReadonlyMode()) {
            return -1;
        }
        String option = null;

        //get options via jvm options

        if ("Image".equals(type)) {
            option = System.getProperty("dataverse.dataAccess.thumbnail.image.limit");
        } else if ("PDF".equals(type)) {
            option = System.getProperty("dataverse.dataAccess.thumbnail.pdf.limit");
        }

        return NumberUtils.toLong(option, 10_000_000);
    }

    private boolean isThumbnailGenerationDisabledForType(String type) {
        return getThumbnailSizeLimit(type) == -1l;
    }

    public boolean isThumbnailGenerationDisabledForImages() {
        return isThumbnailGenerationDisabledForType("Image");
    }

    public boolean isThumbnailGenerationDisabledForPDF() {
        return isThumbnailGenerationDisabledForType("PDF");
    }

    public String getApplicationTermsOfUse(Locale locale) {
        return getFromBundleIfEmptyLocalizedProperty(SettingsServiceBean.Key.ApplicationTermsOfUse, locale, "system.app.terms");
    }

    public String getApiTermsOfUse() {
        return getFromBundleIfEmptyProperty(SettingsServiceBean.Key.ApiTermsOfUse, "system.api.terms");
    }

    public String getPrivacyPolicy(Locale locale) {
        return getFromBundleIfEmptyLocalizedProperty(SettingsServiceBean.Key.PrivacyPolicy, locale, "system.privacy.policy");
    }

    public String getAccessibilityStatement(Locale locale) {
        return getFromBundleIfEmptyLocalizedProperty(Key.AccessibilityStatement, locale, "system.accessibility.statement");
    }

    public String getLoginInfo(Locale locale) {
        return getLocalizedProperty(SettingsServiceBean.Key.LoginInfo, locale);
    }

    public String getSelectDataverseInfo(Locale locale) {
        return getLocalizedProperty(SettingsServiceBean.Key.SelectDataverseInfo, locale);
    }


    public String getAllowedExternalRedirectionUrl() {
        return settingsService.getValueForKey(SettingsServiceBean.Key.AllowedExternalRedirectionUrlAfterLogin);
    }

    public String getCookieName() {
        return settingsService.getValueForKey(Key.CookieName);
    }

    public String getCookieDomain() {
        return settingsService.getValueForKey(Key.CookieDomain);
    }

    public Boolean getCookieSecure() {
        return Boolean.parseBoolean(settingsService.getValueForKey(Key.CookieSecure));
    }

    public long getTabularIngestSizeLimit() {
        // This method will return the blanket ingestable size limit, if
        // set on the system. I.e., the universal limit that applies to all
        // tabular ingests, regardless of fromat:
        return settingsService.getValueForKeyAsLong(SettingsServiceBean.Key.TabularIngestSizeLimit);
    }

    public long getTabularIngestSizeLimit(String formatName) {
        // This method returns the size limit set specifically for this format name,
        // if available, otherwise - the blanket limit that applies to all tabular
        // ingests regardless of a format.

        if (StringUtils.isEmpty(formatName)) {
            return getTabularIngestSizeLimit();
        }

        String limitEntry = settingsService.get(SettingsServiceBean.Key.TabularIngestSizeLimit.toString() + ":" + formatName);

        if (StringUtils.isNotEmpty(limitEntry)) {
            try {
                Long sizeOption = new Long(limitEntry);
                return sizeOption;
            } catch (NumberFormatException nfe) {
                logger.warning("Invalid value for TabularIngestSizeLimit:" + formatName + "? - " + limitEntry);
            }
        }

        return getTabularIngestSizeLimit();
    }

    public boolean isTimerServer() {
        return settingsService.isTrueForKey(SettingsServiceBean.Key.TimerServer);
    }

    public DevOAuthAccountType getDevOAuthAccountType() {
        DevOAuthAccountType saneDefault = DevOAuthAccountType.PRODUCTION;
        String settingReturned = settingsService.getValueForKey(SettingsServiceBean.Key.DebugOAuthAccountType);
        logger.fine("setting returned: " + settingReturned);
        if (StringUtils.isNotEmpty(settingReturned)) {
            try {
                DevOAuthAccountType parsedValue = DevOAuthAccountType.valueOf(settingReturned);
                return parsedValue;
            } catch (IllegalArgumentException ex) {
                logger.info("Couldn't parse value: " + ex + " - returning a sane default: " + saneDefault);
                return saneDefault;
            }
        } else {
            logger.fine("OAuth dev mode has not been configured. Returning a sane default: " + saneDefault);
            return saneDefault;
        }
    }

    public String getOAuth2CallbackUrl() {
        String saneDefault = getDataverseSiteUrl() + "/oauth2/callback.xhtml";
        String settingReturned = settingsService.getValueForKey(SettingsServiceBean.Key.OAuth2CallbackUrl);
        logger.fine("getOAuth2CallbackUrl setting returned: " + settingReturned);
        if (StringUtils.isNotEmpty(settingReturned)) {
            return settingReturned;
        }
        return saneDefault;
    }

    /**
     * Below are three related enums having to do with big data support:
     * <p>
     * - FileUploadMethods
     * <p>
     * - FileDownloadMethods
     * <p>
     * - TransferProtocols
     * <p>
     * There is a good chance these will be consolidated in the future.
     */
    public enum FileUploadMethods {

        /**
         * DCM stands for Data Capture Module. Right now it supports upload over
         * rsync+ssh but DCM may support additional methods in the future.
         */
        RSYNC("dcm/rsync+ssh"),
        /**
         * Traditional Dataverse file handling, which tends to involve users
         * uploading and downloading files using a browser or APIs.
         */
        NATIVE("native/http");


        private final String text;

        FileUploadMethods(final String text) {
            this.text = text;
        }

        public static FileUploadMethods fromString(String text) {
            if (text != null) {
                for (FileUploadMethods fileUploadMethods : FileUploadMethods.values()) {
                    if (text.equals(fileUploadMethods.text)) {
                        return fileUploadMethods;
                    }
                }
            }
            throw new IllegalArgumentException("FileUploadMethods must be one of these values: " + Arrays.asList(FileUploadMethods
                                                                                                                         .values()) + ".");
        }

        @Override
        public String toString() {
            return text;
        }


    }

    /**
     * See FileUploadMethods.
     * <p>
     * TODO: Consider if dataverse.files.s3-download-redirect belongs here since
     * it's a way to bypass Glassfish when downloading.
     */
    public enum FileDownloadMethods {
        /**
         * RSAL stands for Repository Storage Abstraction Layer. Downloads don't
         * go through Glassfish.
         */
        RSYNC("rsal/rsync"),
        NATIVE("native/http");
        private final String text;

        FileDownloadMethods(final String text) {
            this.text = text;
        }

        public static FileUploadMethods fromString(String text) {
            if (text != null) {
                for (FileUploadMethods fileUploadMethods : FileUploadMethods.values()) {
                    if (text.equals(fileUploadMethods.text)) {
                        return fileUploadMethods;
                    }
                }
            }
            throw new IllegalArgumentException("FileDownloadMethods must be one of these values: " + Arrays.asList(FileDownloadMethods
                                                                                                                           .values()) + ".");
        }

        @Override
        public String toString() {
            return text;
        }

    }

    public enum DataFilePIDFormat {
        DEPENDENT("DEPENDENT"),
        INDEPENDENT("INDEPENDENT");
        private final String text;

        public String getText() {
            return text;
        }

        DataFilePIDFormat(final String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }

    }

    /**
     * See FileUploadMethods.
     */
    public enum TransferProtocols {

        RSYNC("rsync"),
        /**
         * POSIX includes NFS. This is related to Key.LocalDataAccessPath in
         * SettingsServiceBean.
         */
        POSIX("posix"),
        GLOBUS("globus");

        private final String text;

        TransferProtocols(final String text) {
            this.text = text;
        }

        public static TransferProtocols fromString(String text) {
            if (text != null) {
                for (TransferProtocols transferProtocols : TransferProtocols.values()) {
                    if (text.equals(transferProtocols.text)) {
                        return transferProtocols;
                    }
                }
            }
            throw new IllegalArgumentException("TransferProtocols must be one of these values: " + Arrays.asList(TransferProtocols
                                                                                                                         .values()) + ".");
        }

        @Override
        public String toString() {
            return text;
        }

    }

    public boolean isRsyncUpload() {
        return getUploadMethodAvailable(SystemConfig.FileUploadMethods.RSYNC.toString());
    }

    // Controls if HTTP upload is enabled for both GUI and API.
    public boolean isHTTPUpload() {
        return getUploadMethodAvailable(SystemConfig.FileUploadMethods.NATIVE.toString());
    }

    public boolean isRsyncOnly() {
        String downloadMethods = settingsService.getValueForKey(SettingsServiceBean.Key.DownloadMethods);
        if (StringUtils.isEmpty(downloadMethods)) {
            return false;
        }
        if (!downloadMethods.toLowerCase().equals(SystemConfig.FileDownloadMethods.RSYNC.toString())) {
            return false;
        }
        String uploadMethods = settingsService.getValueForKey(SettingsServiceBean.Key.UploadMethods);
        if (StringUtils.isEmpty(uploadMethods)) {
            return false;
        } else {
            return Arrays.asList(uploadMethods.toLowerCase().split("\\s*,\\s*")).size() == 1 && uploadMethods
                    .toLowerCase()
                    .equals(SystemConfig.FileUploadMethods.RSYNC.toString());
        }
    }

    public boolean isRsyncDownload() {
        String downloadMethods = settingsService.getValueForKey(SettingsServiceBean.Key.DownloadMethods);
        return downloadMethods != null && downloadMethods
                .toLowerCase()
                .contains(SystemConfig.FileDownloadMethods.RSYNC.toString());
    }

    public boolean isHTTPDownload() {
        String downloadMethods = settingsService.getValueForKey(SettingsServiceBean.Key.DownloadMethods);
        logger.fine("Download Methods:" + downloadMethods);
        return downloadMethods != null && downloadMethods
                .toLowerCase()
                .contains(SystemConfig.FileDownloadMethods.NATIVE.toString());
    }

    private Boolean getUploadMethodAvailable(String method) {
        String uploadMethods = settingsService.getValueForKey(SettingsServiceBean.Key.UploadMethods);
        if (StringUtils.isEmpty(uploadMethods)) {
            return false;
        } else {
            return Arrays.asList(uploadMethods.toLowerCase().split("\\s*,\\s*")).contains(method);
        }
    }

    public Integer getUploadMethodCount() {
        String uploadMethods = settingsService.getValueForKey(SettingsServiceBean.Key.UploadMethods);
        if (StringUtils.isEmpty(uploadMethods)) {
            return 0;
        } else {
            return Arrays.asList(uploadMethods.toLowerCase().split("\\s*,\\s*")).size();
        }
    }

    public Map<String, String> getConfiguredLocales() {
        Map<String, String> configuredLocales = new LinkedHashMap<>();

        JSONArray entries = new JSONArray(settingsService.getValueForKey(SettingsServiceBean.Key.Languages));
        for (Object obj : entries) {
            JSONObject entry = (JSONObject) obj;
            String locale = entry.getString("locale");
            String title = entry.getString("title");

            configuredLocales.put(locale, title);
        }

        return configuredLocales;
    }

    public boolean isShowPrivacyPolicyFooterLinkRendered() {
        return settingsService.isTrueForKey(Key.ShowPrivacyPolicyFooterLink);
    }

    public boolean isShowTermsOfUseFooterLinkRendered() {
        return settingsService.isTrueForKey(Key.ShowTermsOfUseFooterLink);
    }

    public boolean isShowAccessibilityStatementFooterLinkRendered() {
        return settingsService.isTrueForKey(Key.ShowAccessibilityStatementFooterLink);
    }

    private String getFromBundleIfEmptyLocalizedProperty(Key key, Locale locale, String bundleKey) {
        String result = getLocalizedProperty(key, locale);

        return result.isEmpty() ? getFromBundleIfEmptyProperty(key, bundleKey) : result;
    }

    private String getFromBundleIfEmptyProperty(SettingsServiceBean.Key key, String bundleKey) {
        String result = settingsService.getValueForKey(key);

        return result.equals(StringUtils.EMPTY) ? BundleUtil.getStringFromBundle(bundleKey) : result;
    }

    private String getLocalizedProperty(Key key, Locale locale) {
        String result = settingsService.getValueForKeyWithPostfix(key, locale.toLanguageTag());

        return result.isEmpty() ? settingsService.getValueForKey(key) : result;
    }

}
