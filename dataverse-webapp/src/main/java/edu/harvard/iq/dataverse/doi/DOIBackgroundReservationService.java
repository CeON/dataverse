package edu.harvard.iq.dataverse.doi;

import edu.harvard.iq.dataverse.DatasetDao;
import edu.harvard.iq.dataverse.globalid.DOIDataCiteServiceBean;
import edu.harvard.iq.dataverse.persistence.GlobalId;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetRepository;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import io.vavr.control.Option;
import io.vavr.control.Try;

import javax.annotation.PostConstruct;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

@Startup
@Singleton
class DOIBackgroundReservationService {

    private static final Logger logger = Logger.getLogger(DOIBackgroundReservationService.class.getCanonicalName());

    private SettingsServiceBean settingsServiceBean;
    private DatasetRepository datasetRepository;
    private DatasetDao datasetDao;
    private DOIDataCiteServiceBean doiDataCiteService;
    private Timer timer;

    public DOIBackgroundReservationService() {
    }

    @Inject
    public DOIBackgroundReservationService(SettingsServiceBean settingsServiceBean, DatasetRepository datasetRepository,
                                           DatasetDao datasetDao, DOIDataCiteServiceBean doiDataCiteService) {
        this.settingsServiceBean = settingsServiceBean;
        this.datasetRepository = datasetRepository;
        this.datasetDao = datasetDao;
        this.doiDataCiteService = doiDataCiteService;

        timer = new Timer("Doi background registration");
    }

    @PostConstruct
    void startReservation() {
        registerDoiPeriodically();
    }

    void registerDoiPeriodically() {
        String provider = settingsServiceBean.getValueForKey(SettingsServiceBean.Key.DoiProvider);

        if (provider.equals("DataCite")) {

            Option<Long> intervalInMs = Option
                    .of(settingsServiceBean.getValueForKey(SettingsServiceBean.Key.DoiBackgroundReservationInterval))
                    .map(Long::parseLong);

            if (intervalInMs.isEmpty()) {
                return;
            }

            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    registerDataCiteIdentifier();
                }
            }, 0, intervalInMs.get());
        }

    }

    void registerDataCiteIdentifier() {
        List<Dataset> nonReservedDatasets = datasetRepository.findByNonRegisteredIdentifier();
        int attempts = 0;

        for (Dataset nonReservedDataset : nonReservedDatasets) {

            GlobalId globalId = nonReservedDataset.getGlobalId();

            while (doiDataCiteService.alreadyExists(globalId) && attempts < 10) {
                globalId = new GlobalId(datasetDao.generateDatasetIdentifier(nonReservedDataset));
                attempts++;
            }

            datasetRepository.refresh(nonReservedDataset);
            nonReservedDataset.setIdentifier(globalId.getIdentifier());

            Try.of(() -> doiDataCiteService.createIdentifier(nonReservedDataset))
               .onFailure(throwable -> logger.log(Level.INFO, "Identifier could not be reserved", throwable))
               .onSuccess(s -> {
                   nonReservedDataset.setGlobalIdCreateTime(new Timestamp(new Date().getTime()));
                   nonReservedDataset.setIdentifierRegistered(true);
               });
        }

    }
}
