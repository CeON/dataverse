package edu.harvard.iq.dataverse.sitemap;

import edu.harvard.iq.dataverse.Dataset;
import edu.harvard.iq.dataverse.Dataverse;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;

import javax.ejb.Asynchronous;
import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.List;

@Stateless
public class SiteMapServiceBean {

    @Inject
    SettingsServiceBean settingsService;

    @Asynchronous
    public void updateSiteMap(List<Dataverse> dataverses, List<Dataset> datasets) {
        SiteMapUtil.updateSiteMap(dataverses, datasets,
                settingsService.getValueForKey(SettingsServiceBean.Key.FQDN),
                settingsService.getValueForKey(SettingsServiceBean.Key.SiteUrl));
    }

}
