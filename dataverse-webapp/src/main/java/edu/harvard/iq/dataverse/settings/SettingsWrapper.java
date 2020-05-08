package edu.harvard.iq.dataverse.settings;

import edu.harvard.iq.dataverse.util.SystemConfig;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.EJB;
import javax.faces.context.FacesContext;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author gdurand
 */
@ViewScoped
@Named
public class SettingsWrapper implements java.io.Serializable {

    private static final Logger log = LoggerFactory.getLogger(SettingsWrapper.class);

    @Inject
    SettingsServiceBean settingService;

    @EJB
    SystemConfig systemConfig;

    private Map<String, String> settingsMap;
    private Map<String, String> configuredLocales;
    private Map<String, String> configuredAboutUrls;

    // -------------------- GETTERS --------------------

    public boolean isPublicInstall() {
        return settingService.isTrueForKey(SettingsServiceBean.Key.PublicInstall);
    }

    public String getMetricsUrl() {
        return settingService.getValueForKey(SettingsServiceBean.Key.MetricsUrl);
    }

    public boolean isShibPassiveLoginEnabled() {
        return settingService.isTrueForKey(SettingsServiceBean.Key.ShibPassiveLoginEnabled);
    }

    public boolean isProvCollectionEnabled() {
        return settingService.isTrueForKey(SettingsServiceBean.Key.ProvCollectionEnabled);
    }

    public boolean isRsyncUpload() {
        return systemConfig.isRsyncUpload();
    }

    public boolean isRsyncDownload() {
        return systemConfig.isRsyncDownload();
    }

    public boolean isRsyncOnly() {
        return systemConfig.isRsyncOnly();
    }

    public boolean isHTTPUpload() {
        return systemConfig.isHTTPUpload();
    }

    public Integer getUploadMethodsCount() {
        return systemConfig.getUploadMethodCount();
    }

    public String getGuidesBaseUrl() {
        return systemConfig.getGuidesBaseUrl();
    }

    public String getGuidesVersion() {
        return systemConfig.getGuidesVersion();
    }

    public String getDropBoxKey() {
        String configuredDropBoxKey = getSettingValue(SettingsServiceBean.Key.DropboxKey.toString());
        if (configuredDropBoxKey != null) {
            return configuredDropBoxKey;
        }
        return "";
    }

    // -------------------- LOGIC --------------------

    public Boolean isHasDropBoxKey() {
        return !getDropBoxKey().isEmpty();
    }

    public String getEnumSettingValue(SettingsServiceBean.Key key) {
        return getSettingValue(key.toString());
    }

    public String getSettingValue(String settingKey) {
        if (settingsMap == null) {
            initSettingsMap();
        }
        return settingsMap.get(settingKey);
    }

    public boolean isLocalesConfigured() {
        if (configuredLocales == null) {
            initLocaleSettings();
        }
        return configuredLocales.size() > 1;
    }

    public Map<String, String> getConfiguredLocales() {
        if (configuredLocales == null) {
            initLocaleSettings();
        }
        return configuredLocales;
    }

    public String getConfiguredLocaleName(String localeCode) {
        if (configuredLocales == null) {
            initLocaleSettings();
        }
        return configuredLocales.get(localeCode);
    }

    public Map<String, String> getConfiguredAboutUrls() {
        if (configuredAboutUrls == null) {
            initConfiguredAboutUrls();
        }
        return configuredAboutUrls;
    }

    // -------------------- PRIVATE --------------------

    private void initLocaleSettings() {
        configuredLocales = initMapFromJson(SettingsServiceBean.Key.Languages, "locale", "title");
    }

    private void initConfiguredAboutUrls() {
        configuredAboutUrls = initMapFromJson(SettingsServiceBean.Key.NavbarAboutUrl, "url", "title");
    }

    private Map<String, String> initMapFromJson(SettingsServiceBean.Key settingKey, String mapKey, String mapValue) {
        Map<String, String> map = new LinkedHashMap<>();
        try {
            JSONArray entries = new JSONArray(getSettingValue(settingKey.toString()));
            for (Object obj : entries) {
                JSONObject entry = (JSONObject) obj;
                String key = entry.getString(mapKey);
                String value = getLocaleAwareString(entry, mapValue);
                map.put(key, value);
            }
        } catch (JSONException e) {
            log.warn("Error parsing setting " + settingKey + " as JSON", e);
        }
        return map;
    }

    private static String getLocaleAwareString(JSONObject object, String key) {
        String lang = FacesContext.getCurrentInstance().getViewRoot().getLocale().getLanguage();
        String langKey = key + "." + lang;
        if (object.keySet().contains(langKey)) {
            return object.getString(langKey);
        } else {
            return object.getString(key);
        }
    }

    private void initSettingsMap() {
        // initialize settings map
        settingsMap = settingService.listAll();
    }
}

