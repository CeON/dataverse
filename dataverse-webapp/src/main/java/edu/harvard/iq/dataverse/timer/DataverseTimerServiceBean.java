package edu.harvard.iq.dataverse.timer;

import com.google.api.client.util.Preconditions;
import edu.harvard.iq.dataverse.DatasetDao;
import edu.harvard.iq.dataverse.featured.FeaturedDataverseServiceBean;
import edu.harvard.iq.dataverse.authorization.AuthenticationServiceBean;
import edu.harvard.iq.dataverse.datafile.FileIntegrityChecker;
import edu.harvard.iq.dataverse.datafile.pojo.FilesIntegrityReport;
import edu.harvard.iq.dataverse.dataset.DatasetCitationsCountUpdater;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.harvest.client.HarvesterParams;
import edu.harvard.iq.dataverse.harvest.client.HarvestTimerInfo;
import edu.harvard.iq.dataverse.harvest.client.HarvesterServiceBean;
import edu.harvard.iq.dataverse.harvest.client.HarvestingClientDao;
import edu.harvard.iq.dataverse.harvest.server.OAISetServiceBean;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.harvest.HarvestingClient;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.search.index.IndexServiceBean;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean.Key;
import edu.harvard.iq.dataverse.util.SystemConfig;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.DependsOn;
import javax.ejb.EJB;
import javax.ejb.ScheduleExpression;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.Timeout;
import javax.ejb.Timer;
import javax.ejb.TimerConfig;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This is a largely intact DVN3 implementation.
 * original
 *
 * @author roberttreacy
 * ported by
 * @author Leonid Andreev
 */
@Singleton
@Startup
@DependsOn("StartupFlywayMigrator")
public class DataverseTimerServiceBean implements Serializable {
    private static final Logger logger = Logger.getLogger(DataverseTimerServiceBean.class.getCanonicalName());

    @Resource
    javax.ejb.TimerService timerService;

    @EJB
    HarvesterServiceBean harvesterService;
    @EJB
    HarvestingClientDao harvestingClientService;
    @EJB
    AuthenticationServiceBean authSvc;
    @EJB
    OAISetServiceBean oaiSetService;
    @EJB
    SystemConfig systemConfig;
    @Inject
    SettingsServiceBean settingsService;
    @EJB
    FileIntegrityChecker fileIntegrityChecker;
    @Inject
    DatasetCitationsCountUpdater datasetCitationsCountUpdater;
    @Inject
    FeaturedDataverseServiceBean featuredDataverseServiceBean;

    @Inject
    DatasetDao datasetDao;
    
    @Inject
    IndexServiceBean indexServiceBean;

    @PostConstruct
    public void init() {
        logger.info("PostConstruct timer check.");

        if (systemConfig.isReadonlyMode()) {
            logger.info("Timers not allowed in readonly mode");
            return;
        }

        if (systemConfig.isTimerServer()) {
            logger.info("I am the dedicated timer server. Initializing mother timer.");

            removeAllTimers();
            // create mother timer:
            createMotherTimer();
            // And the export timer (there is only one)
            createExportTimer();

            createIntegrityCheckTimer();
            createCitationCountUpdateTimer();
            createFeaturedDataversesSortingUpdateTimer();
            
            createReindexAfterEmbargoTimer();

        } else {
            logger.info("Skipping timer server init (I am not the dedicated timer server)");
        }
    }

    private void createReindexAfterEmbargoTimer() {
        String cronExpression = settingsService.getValueForKey(Key.ReindexAfterEmbargoTimerExpression);

        if (StringUtils.isNotBlank(cronExpression)) {
            ScheduleExpression expression = cronToScheduleExpression(cronExpression);

            TimerConfig timerConfig = new TimerConfig();
            timerConfig.setPersistent(false);
            timerConfig.setInfo(new AfterEmbargoReindexTimerInfo());
            Timer timer = timerService.createCalendarTimer(expression, timerConfig);
            logger.info("ReindexAfterEmbargoTimerExpression: timer created, initial expiration: " + timer.getNextTimeout());
        } else {
            logger.info("ReindexAfterEmbargoTimerExpression is empty. Skipping creation of timer.");
        }
    }

    public void createTimer(Date initialExpiration, long intervalDuration, Serializable info) {
        try {
            logger.log(Level.INFO, "Creating timer on " + InetAddress.getLocalHost().getCanonicalHostName());
        } catch (UnknownHostException ex) {
            Logger.getLogger(DataverseTimerServiceBean.class.getName()).log(Level.SEVERE, null, ex);
        }
        timerService.createIntervalTimer(initialExpiration, intervalDuration, new TimerConfig(info, false));
    }

    /**
     * This method is called whenever an EJB Timer goes off.
     * Check to see if this is a Harvest Timer, and if it is
     * Run the harvest for the given (scheduled) dataverse
     *
     * @param timer
     */
    @Timeout
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void handleTimeout(Timer timer) {
        // We have to put all the code in a try/catch block because
        // if an exception is thrown from this method, Glassfish will automatically
        // call the method a second time. (The minimum number of re-tries for a Timer method is 1)
        if (systemConfig.isReadonlyMode()) {
            Logger.getLogger(DataverseTimerServiceBean.class.getName()).log(Level.WARNING, null, "handleTimeout() was called in readonly mode - skipping timeout handling");
            return;
        }

        if (!systemConfig.isTimerServer()) {
            //logger.info("I am not the timer server! - bailing out of handleTimeout()");
            Logger.getLogger(DataverseTimerServiceBean.class.getName()).log(Level.WARNING, null, "I am not the timer server! - but handleTimeout() got called. Please investigate!");
        }

        try {
            logger.log(Level.INFO, "Handling timeout on " + InetAddress.getLocalHost().getCanonicalHostName());
        } catch (UnknownHostException ex) {
            Logger.getLogger(DataverseTimerServiceBean.class.getName()).log(Level.SEVERE, null, ex);
        }

        if (timer.getInfo() instanceof MotherTimerInfo) {
            logger.info("Behold! I am the Master Timer, king of all timers! I'm here to create all the lesser timers!");
            removeHarvestTimers();
            for (HarvestingClient client : harvestingClientService.getAllHarvestingClients()) {
                createHarvestTimer(client);
            }
        } else if (timer.getInfo() instanceof HarvestTimerInfo) {
            HarvestTimerInfo info = (HarvestTimerInfo) timer.getInfo();
            try {

                logger.log(Level.INFO, "running a harvesting client: id=" + info.getHarvestingClientId());
                // Timer batch jobs are run by the main Admin user.
                // TODO: revisit how we retrieve the superuser here.
                // Should it be configurable somewhere, which superuser
                // runs these jobs? Should there be a central mechanism for obtaining
                // the "major", builtin superuser for this Dataverse instance?
                // -- L.A. 4.5, Aug. 2016
                AuthenticatedUser adminUser = authSvc.getAdminUser(); // getAuthenticatedUser("admin");
                if (adminUser == null) {
                    logger.info("Scheduled harvest: failed to locate the admin user! Exiting.");
                    throw new IOException("Scheduled harvest: failed to locate the admin user");
                }
                logger.info("found admin user " + adminUser.getName());
                DataverseRequest dataverseRequest = new DataverseRequest(adminUser, (HttpServletRequest) null);
                harvesterService.doHarvest(dataverseRequest, info.getHarvestingClientId(), HarvesterParams.empty());

            } catch (Throwable e) {
                // Harvester Service should be handling any error notifications,
                // if/when things go wrong.
                // (TODO: -- verify this logic; harvesterService may still be able
                // to throw an IOException, if it could not run the harvest at all,
                // or could not for whatever reason modify the database record...
                // in this case we should, probably, log the error and try to send
                // a mail notification. -- L.A. 4.4)
                //dataverseService.setHarvestResult(info.getHarvestingDataverseId(), harvesterService.HARVEST_RESULT_FAILED);
                //mailService.sendHarvestErrorNotification(dataverseService.find().getSystemEmail(), dataverseService.find().getName());
                logException(e, logger);
            }
        } else if (timer.getInfo() instanceof ExportTimerInfo) {
            try {
                ExportTimerInfo info = (ExportTimerInfo) timer.getInfo();
                logger.info("Timer Service: Running a scheduled export job.");

                // and update all oai sets:
                oaiSetService.exportAllSets();
            } catch (Throwable e) {
                logException(e, logger);
            }
        } else if (timer.getInfo() instanceof FilesIntegrityCheckTimerInfo) {
            FilesIntegrityReport report = fileIntegrityChecker.checkFilesIntegrity();

            logger.info(report.getSummaryInfo());
        } else if (timer.getInfo() instanceof CitationCountUpdateTimerInfo) {
            datasetCitationsCountUpdater.updateCitationCount();
        } else if (timer.getInfo() instanceof AfterEmbargoReindexTimerInfo) {
            reindexAfterEmbargo();
        } else if (timer.getInfo() instanceof FeaturedDataversesSortingUpdateTimerInfo) {
            featuredDataverseServiceBean.refreshFeaturedDataversesAutomaticSorting();
        }

    }

    private void reindexAfterEmbargo() {
        List<Dataset> datasetsAfterEmbargo = datasetDao.findNotIndexedAfterEmbargo();
        for (Dataset dataset:datasetsAfterEmbargo) {
            indexServiceBean.indexDataset(dataset, true);
        }
    }

    public void removeAllTimers() {
        logger.info("Removing ALL existing timers.");

        int i = 0;

        for (Iterator it = timerService.getTimers().iterator(); it.hasNext(); ) {

            Timer timer = (Timer) it.next();

            logger.info("Removing timer " + i + ";");
            timer.cancel();

            i++;
        }
        logger.info("Done!");
    }

    public void removeHarvestTimers() {
        // Remove all the harvest timers, if exist:
        //
        // (the logging messages below are set to level INFO; it's ok,
        // since this code is only called on startup of the application,
        // and it may be useful to know what existing timers were encountered).

        logger.log(Level.INFO, "Removing existing harvest timers..");

        int i = 1;
        for (Iterator it = timerService.getTimers().iterator(); it.hasNext(); ) {

            Timer timer = (Timer) it.next();
            logger.log(Level.INFO, "HarvesterService: checking timer " + i);

            if (timer.getInfo() instanceof HarvestTimerInfo) {
                logger.log(Level.INFO, "HarvesterService: timer " + i + " is a harvesting one; removing.");
                timer.cancel();
            }

            i++;
        }
    }

    public void createMotherTimer() {
        MotherTimerInfo info = new MotherTimerInfo();
        Calendar initExpiration = Calendar.getInstance();
        long intervalDuration = 60 * 60 * 1000; // every hour
        initExpiration.set(Calendar.MINUTE, 50);
        initExpiration.set(Calendar.SECOND, 0);

        Date initExpirationDate = initExpiration.getTime();
        Date currTime = new Date();
        if (initExpirationDate.before(currTime)) {
            initExpirationDate.setTime(initExpiration.getTimeInMillis() + intervalDuration);
        }

        logger.info("Setting the \"Mother Timer\", initial expiration: " + initExpirationDate);
        createTimer(initExpirationDate, intervalDuration, info);
    }

    public void createHarvestTimer(HarvestingClient harvestingClient) {

        if (harvestingClient.isScheduled()) {
            long intervalDuration = 0;
            Calendar initExpiration = Calendar.getInstance();
            initExpiration.set(Calendar.MINUTE, 0);
            initExpiration.set(Calendar.SECOND, 0);
            if (harvestingClient.getSchedulePeriod().equals(HarvestingClient.SCHEDULE_PERIOD_DAILY)) {
                intervalDuration = 1000 * 60 * 60 * 24;
                initExpiration.set(Calendar.HOUR_OF_DAY, harvestingClient.getScheduleHourOfDay());

            } else if (harvestingClient.getSchedulePeriod().equals(HarvestingClient.SCHEDULE_PERIOD_WEEKLY)) {
                intervalDuration = 1000 * 60 * 60 * 24 * 7;
                initExpiration.set(Calendar.HOUR_OF_DAY, harvestingClient.getScheduleHourOfDay());
                initExpiration.set(Calendar.DAY_OF_WEEK, harvestingClient.getScheduleDayOfWeek() + 1); //(saved as zero-based array but Calendar is one-based.)

            } else {
                logger.log(Level.WARNING, "Could not set timer for harvesting client id=" + harvestingClient.getId() + ", unknown schedule period: " + harvestingClient.getSchedulePeriod());
                return;
            }
            Date initExpirationDate = initExpiration.getTime();
            Date currTime = new Date();
            if (initExpirationDate.before(currTime)) {
                initExpirationDate.setTime(initExpiration.getTimeInMillis() + intervalDuration);
            }
            logger.log(Level.INFO, "Setting timer for harvesting client " + harvestingClient.getName() + ", initial expiration: " + initExpirationDate);
            createTimer(initExpirationDate, intervalDuration, new HarvestTimerInfo(harvestingClient.getId()));
        }
    }

    public void removeHarvestTimer(HarvestingClient harvestingClient) {
        // Clear dataverse timer, if one exists
        try {
            logger.log(Level.INFO, "Removing harvest timer on " + InetAddress.getLocalHost().getCanonicalHostName());
        } catch (UnknownHostException ex) {
            Logger.getLogger(DataverseTimerServiceBean.class.getName()).log(Level.SEVERE, null, ex);
        }
        for (Iterator it = timerService.getTimers().iterator(); it.hasNext(); ) {
            Timer timer = (Timer) it.next();
            if (timer.getInfo() instanceof HarvestTimerInfo) {
                HarvestTimerInfo info = (HarvestTimerInfo) timer.getInfo();
                if (info.getHarvestingClientId().equals(harvestingClient.getId())) {
                    timer.cancel();
                }
            }
        }
    }

    public void createExportTimer() {
        ExportTimerInfo info = new ExportTimerInfo();
        Calendar initExpiration = Calendar.getInstance();
        long intervalDuration = 24 * 60 * 60 * 1000; // every day
        initExpiration.set(Calendar.MINUTE, 0);
        initExpiration.set(Calendar.SECOND, 0);
        initExpiration.set(Calendar.HOUR_OF_DAY, 2); // 2AM, fixed.


        Date initExpirationDate = initExpiration.getTime();
        Date currTime = new Date();
        if (initExpirationDate.before(currTime)) {
            initExpirationDate.setTime(initExpiration.getTimeInMillis() + intervalDuration);
        }

        logger.info("Setting the Export Timer, initial expiration: " + initExpirationDate);
        createTimer(initExpirationDate, intervalDuration, info);
    }

    public void createIntegrityCheckTimer() {
        String cronExpression = settingsService.getValueForKey(Key.FilesIntegrityCheckTimerExpression);

        if (StringUtils.isNotBlank(cronExpression)) {
            ScheduleExpression expression = cronToScheduleExpression(cronExpression);

            TimerConfig timerConfig = new TimerConfig();
            timerConfig.setInfo(new FilesIntegrityCheckTimerInfo());
            timerConfig.setPersistent(false);
            timerService.createCalendarTimer(expression, timerConfig);
        }
    }

    public void createCitationCountUpdateTimer() {
        String cronExpression = settingsService.getValueForKey(Key.CitationCountUpdateTimerExpression);

        if (StringUtils.isNotBlank(cronExpression)) {
            ScheduleExpression expression = cronToScheduleExpression(cronExpression);

            TimerConfig timerConfig = new TimerConfig();
            timerConfig.setPersistent(false);
            timerConfig.setInfo(new CitationCountUpdateTimerInfo());
            Timer timer = timerService.createCalendarTimer(expression, timerConfig);
            logger.info("CitationCountUpdateTimerExpression: timer created, initial expiration: " + timer.getNextTimeout());
        } else {
            logger.info("CitationCountUpdateTimerExpression is empty. Skipping creation of timer.");
        }

    }

    public void createFeaturedDataversesSortingUpdateTimer() {
        String cronExpression = settingsService.getValueForKey(Key.FeaturedDataversesSortingUpdateTimerExpression);

        if (StringUtils.isNotBlank(cronExpression)) {
            ScheduleExpression expression = cronToScheduleExpression(cronExpression);

            TimerConfig timerConfig = new TimerConfig();
            timerConfig.setPersistent(false);
            timerConfig.setInfo(new FeaturedDataversesSortingUpdateTimerInfo());
            Timer timer = timerService.createCalendarTimer(expression, timerConfig);
            logger.info("FeaturedDataversesSortingUpdateTimerExpression: timer created, initial expiration: " + timer.getNextTimeout());
        } else {
            logger.info("FeaturedDataversesSortingUpdateTimerExpression is empty. Skipping creation of timer.");
        }

    }

    /* Utility methods: */
    private ScheduleExpression cronToScheduleExpression(String cronExpression) {
        final String[] parts = cronExpression.split(" ");
        Preconditions.checkArgument(parts.length == 5, "Invalid cron expression {} Expression should have 5 parts", cronExpression);

        return new ScheduleExpression()
            .minute(parts[0])
            .hour(parts[1])
            .dayOfMonth(parts[2])
            .month(parts[3])
            .dayOfWeek(parts[4]);
    }

    private void logException(Throwable e, Logger logger) {

        boolean cause = false;
        String fullMessage = "";
        do {
            String message = e.getClass().getName() + " " + e.getMessage();
            if (cause) {
                message = "\nCaused By Exception.................... " + e.getClass().getName() + " " + e.getMessage();
            }
            StackTraceElement[] ste = e.getStackTrace();
            message += "\nStackTrace: \n";
            for (int m = 0; m < ste.length; m++) {
                message += ste[m].toString() + "\n";
            }
            fullMessage += message;
            cause = true;
        } while ((e = e.getCause()) != null);
        logger.severe(fullMessage);
    }

}