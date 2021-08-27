package edu.harvard.iq.dataverse;

import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.harvest.client.HarvestingClientDao;
import edu.harvard.iq.dataverse.harvest.server.OAISetServiceBean;
import edu.harvard.iq.dataverse.persistence.datafile.license.LicenseRepository;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetRepository;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.dataverse.DataverseRepository;
import edu.harvard.iq.dataverse.persistence.harvest.HarvestingClient;
import edu.harvard.iq.dataverse.persistence.harvest.OAISet;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.util.SystemConfig;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import org.omnifaces.cdi.ViewScoped;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.inject.Named;
import java.text.NumberFormat;
import java.util.List;
import java.util.logging.Logger;

/**
 * @author Leonid Andreev
 */
@ViewScoped
@Named
public class DashboardPage implements java.io.Serializable {

    private static final Logger logger = Logger.getLogger(DashboardPage.class.getCanonicalName());

    @EJB
    HarvestingClientDao harvestingClientService;
    @EJB
    OAISetServiceBean oaiSetService;
    @EJB
    SystemConfig systemConfig;
    @Inject
    SettingsServiceBean settingsService;

    @Inject
    DatasetRepository datasetRepository;

    @Inject
    DataverseRepository dataverseRepository;


    @Inject
    DataverseSession session;
    @Inject
    NavigationWrapper navigationWrapper;
    @Inject
    private LicenseRepository licenseRepository;

    /*
     in breadcrumbs the dashboard page always appears as if it belongs to the 
     root dataverse ("Root Dataverse -> Dashboard") - because it is for the 
     top-level, site-wide controls only available to the site admin. 
     but it should still be possible to pass the id of the dataverse that was 
     current when the admin chose to go to the dashboard. This way certain values
     can be pre-selected, etc. -- L.A. 4.5
    */
    private Dataverse dataverse;
    private Long dataverseId = null;

    public String init() {
        if (!session.getUser().isSuperuser()) {
            return navigationWrapper.notAuthorized();
        }

        /* 
            use this to add some kind of a tooltip/info message to the top of the page:
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, BundleUtil.getStringFromBundle("dashboard.title"), BundleUtil.getStringFromBundle("dashboard.toptip")));
            - the values for "dashboard.title" and "dashboard.toptip" would need to be added to the resource bundle.
         */
        return null;
    }

    public Dataverse getDataverse() {
        return dataverse;
    }

    public void setDataverse(Dataverse dataverse) {
        this.dataverse = dataverse;
    }

    public Long getDataverseId() {
        return dataverseId;
    }

    public void setDataverseId(Long dataverseId) {
        this.dataverseId = dataverseId;
    }

    public int getNumberOfConfiguredHarvestClients() {
        List<HarvestingClient> configuredHarvestingClients = harvestingClientService.getAllHarvestingClients();
        if (configuredHarvestingClients == null || configuredHarvestingClients.isEmpty()) {
            return 0;
        }

        return configuredHarvestingClients.size();
    }

    public long getNumberOfHarvestedDatasets() {
        List<HarvestingClient> configuredHarvestingClients = harvestingClientService.getAllHarvestingClients();
        if (configuredHarvestingClients == null || configuredHarvestingClients.isEmpty()) {
            return 0L;
        }

        Long numOfDatasets = harvestingClientService.getNumberOfHarvestedDatasetByClients(configuredHarvestingClients);

        if (numOfDatasets != null && numOfDatasets > 0L) {
            return numOfDatasets;
        }

        return 0L;
    }

    public boolean isHarvestServerEnabled() {
        return settingsService.isTrueForKey(SettingsServiceBean.Key.OAIServerEnabled);
    }

    public int getNumberOfOaiSets() {
        List<OAISet> configuredHarvestingSets = oaiSetService.findAll();
        if (configuredHarvestingSets == null || configuredHarvestingSets.isEmpty()) {
            return 0;
        }

        return configuredHarvestingSets.size();
    }

    @Deprecated
    public String getHarvestClientsInfoLabel() {
        List<HarvestingClient> configuredHarvestingClients = harvestingClientService.getAllHarvestingClients();
        if (configuredHarvestingClients == null || configuredHarvestingClients.isEmpty()) {
            return BundleUtil.getStringFromBundle("harvestclients.noClients.label");
        }

        String infoLabel;

        if (configuredHarvestingClients.size() == 1) {
            infoLabel = configuredHarvestingClients.size() + " configured harvesting client; ";
        } else {
            infoLabel = configuredHarvestingClients.size() + " harvesting clients configured; ";
        }

        Long numOfDatasets = harvestingClientService.getNumberOfHarvestedDatasetByClients(configuredHarvestingClients);

        if (numOfDatasets != null && numOfDatasets > 0L) {
            return infoLabel + numOfDatasets + " harvested datasets";
        }
        return infoLabel + "no datasets harvested.";
    }

    @Deprecated
    public String getHarvestServerInfoLabel() {
        if (!settingsService.isTrueForKey(SettingsServiceBean.Key.OAIServerEnabled)) {
            return "OAI server disabled.";
        }

        String infoLabel = "OAI server enabled; ";

        List<OAISet> configuredHarvestingSets = oaiSetService.findAll();
        if (configuredHarvestingSets == null || configuredHarvestingSets.isEmpty()) {
            infoLabel = infoLabel.concat(BundleUtil.getStringFromBundle("harvestserver.service.empty"));
            return infoLabel;
        }

        infoLabel = infoLabel.concat(configuredHarvestingSets.size() + " configured OAI sets. ");
        return infoLabel;
    }

    /**
     * Maximum length in months for embargo on dataset.
     *
     * @return
     */
    public String getMaximumEmbargoLengthString() {
        return NumberFormat.getInstance().format(getMaximumEmbargoLength());
    }

    public int getMaximumEmbargoLength() {
        return settingsService.getValueForKeyAsLong(SettingsServiceBean.Key.MaximumEmbargoLength).intValue();
    }

    public boolean isMaximumEmbargoLengthSet() {
        return settingsService.getValueForKeyAsLong(SettingsServiceBean.Key.MaximumEmbargoLength) > 0;
    }

    /**
     * Calculates and returns count of active and inactive licenses in the whole Dataverse.
     *
     * @return active and inactive licenses count
     */
    public Tuple2<Long, Long> getActiveAndInactiveLicensesCount() {
        return Tuple.of(licenseRepository.countActiveLicenses(), licenseRepository.countInactiveLicenses());
    }

    public Long getTotalNumberOfDatasets() {
        return datasetRepository.countAll();
    }

    public Long getTotalNumberOfDataverses() {
        return dataverseRepository.countAll();
    }

}
