/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.harvard.iq.dataverse.settings;

import edu.harvard.iq.dataverse.util.SystemConfig;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.ejb.EJB;
import javax.faces.view.ViewScoped;
import javax.inject.Named;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 *
 * @author gdurand
 */
@ViewScoped
@Named
public class SettingsWrapper implements java.io.Serializable {

    @EJB
    SettingsServiceBean settingService;

    @EJB
    SystemConfig systemConfig;

    private Map<String, String> settingsMap;

    public String get(String settingKey) {
        if (settingsMap == null) {
            initSettingsMap();
        }
        
        return settingsMap.get(settingKey);
    }

    private void initSettingsMap() {
        // initialize settings map
        settingsMap = settingService.listAll();
    }

    public boolean isPublicInstall(){
        return systemConfig.isPublicInstall();
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
    
    public boolean isHTTPUpload(){
        return systemConfig.isHTTPUpload();
    }
    
    public boolean isDataFilePIDSequentialDependent(){
        return systemConfig.isDataFilePIDSequentialDependent();
    }
    
    public Integer getUploadMethodsCount() {
        return systemConfig.getUploadMethodCount();
    }
    
    public String getDropBoxKey() {

        String configuredDropBoxKey = System.getProperty("dataverse.dropbox.key");
        if (configuredDropBoxKey != null) {
            return configuredDropBoxKey;
        }
        return "";
    }
    
    public Boolean isHasDropBoxKey() {

        return !getDropBoxKey().isEmpty();
    }
    
    // Language Locales Configuration: 
    
    // Map from locale to display name eg     en -> English
    private Map<String, String> configuredLocales;
    
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
    
    private void initLocaleSettings() {
        
        configuredLocales = new LinkedHashMap<>();
        
        try {
            JSONArray entries = new JSONArray(get(SettingsServiceBean.Key.Languages.toString()));
            for (Object obj : entries) {
                JSONObject entry = (JSONObject) obj;
                String locale = entry.getString("locale");
                String title = entry.getString("title");

                configuredLocales.put(locale, title);
            }
        } catch (JSONException e) {
            //e.printStackTrace();
            // do we want to know? - probably not
        }
    }
}

