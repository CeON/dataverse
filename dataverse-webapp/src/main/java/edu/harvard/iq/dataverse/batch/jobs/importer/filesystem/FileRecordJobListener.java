/*
   Copyright (C) 2005-2017, by the President and Fellows of Harvard College.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

   Dataverse Network - A web application to share, preserve and analyze research data.
   Developed at the Institute for Quantitative Social Science, Harvard University.
*/

package edu.harvard.iq.dataverse.batch.jobs.importer.filesystem;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.harvard.iq.dataverse.DataFileServiceBean;
import edu.harvard.iq.dataverse.DatasetDao;
import edu.harvard.iq.dataverse.PermissionServiceBean;
import edu.harvard.iq.dataverse.actionlogging.ActionLogServiceBean;
import edu.harvard.iq.dataverse.authorization.AuthenticationServiceBean;
import edu.harvard.iq.dataverse.batch.entities.JobExecutionEntity;
import edu.harvard.iq.dataverse.batch.jobs.importer.ImportMode;
import edu.harvard.iq.dataverse.batch.util.LoggingUtil;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDatasetVersionCommand;
import edu.harvard.iq.dataverse.notification.NotificationObjectType;
import edu.harvard.iq.dataverse.notification.UserNotificationService;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetLock;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.persistence.user.NotificationType;
import edu.harvard.iq.dataverse.persistence.user.Permission;
import edu.harvard.iq.dataverse.util.SystemConfig;
import org.apache.commons.io.IOUtils;

import javax.batch.api.BatchProperty;
import javax.batch.api.chunk.listener.ItemReadListener;
import javax.batch.api.listener.JobListener;
import javax.batch.api.listener.StepListener;
import javax.batch.operations.JobOperator;
import javax.batch.operations.JobSecurityException;
import javax.batch.operations.NoSuchJobExecutionException;
import javax.batch.runtime.BatchRuntime;
import javax.batch.runtime.BatchStatus;
import javax.batch.runtime.JobExecution;
import javax.batch.runtime.StepExecution;
import javax.batch.runtime.context.JobContext;
import javax.ejb.EJB;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

@Dependent
public class FileRecordJobListener implements ItemReadListener, StepListener, JobListener {

    public static final String SEP = System.getProperty("file.separator");

    private static final String notifyType = NotificationType.FILESYSTEMIMPORT;

    @Inject
    private JobContext jobContext;

    @Inject
    private SystemConfig systemConfig;

    @EJB
    UserNotificationService userNotificationService;

    @EJB
    AuthenticationServiceBean authenticationServiceBean;

    @EJB
    ActionLogServiceBean actionLogServiceBean;

    @EJB
    DatasetDao datasetDao;

    @EJB
    DataFileServiceBean dataFileServiceBean;

    @EJB
    PermissionServiceBean permissionServiceBean;

    @Inject
    @BatchProperty
    String checksumManifest;

    @Inject
    @BatchProperty
    String checksumType;

    Properties jobParams;
    Dataset dataset;
    String mode;
    String uploadFolder;
    AuthenticatedUser user;

    @Override
    public void afterStep() throws Exception {
        // no-op
    }

    @Override
    public void beforeStep() throws Exception {
        // no-op
    }

    @Override
    public void beforeRead() throws Exception {
        // no-op
    }

    @Override
    public void afterRead(Object item) throws Exception {
        // no-op
    }

    @Override
    public void onReadError(Exception ex) throws Exception {
        // no-op
    }

    @Override
    public void beforeJob() throws Exception {
        Logger jobLogger;

        // initialize logger
        // (the beforeJob() method gets executed before anything else; so we 
        // initialize the logger here. everywhere else will be retrieving 
        // it with Logger.getLogger(byname) - that should be giving us the 
        // same instance, created here - and not creating a new logger)
        jobLogger = LoggingUtil.getJobLogger(Long.toString(jobContext.getInstanceId()));

        // update job properties to be used elsewhere to determine dataset, user and mode
        JobOperator jobOperator = BatchRuntime.getJobOperator();
        jobParams = jobOperator.getParameters(jobContext.getInstanceId());

        // log job info
        jobLogger.log(Level.INFO, "Job ID = {0}", jobContext.getExecutionId());
        jobLogger.log(Level.INFO, "Job Name = {0}", jobContext.getJobName());
        jobLogger.log(Level.INFO, "Job Status = {0}", jobContext.getBatchStatus());

        jobParams.setProperty("datasetGlobalId", getDatasetGlobalId());

        if (dataset == null) {
            getJobLogger().log(Level.SEVERE, "Can't find dataset.");
            jobContext.setExitStatus("FAILED");
            throw new IOException("Can't find dataset.");
        }

        if (!dataset.isLockedFor(DatasetLock.Reason.DcmUpload)) {
            getJobLogger().log(Level.SEVERE, "Dataset {0} is not locked for DCM upload. Exiting", dataset.getGlobalId());
            jobContext.setExitStatus("FAILED");
            throw new IOException("Dataset " + dataset.getGlobalId() + " is not locked for DCM upload");
        }

        jobParams.setProperty("userId", getUserId());
        jobParams.setProperty("mode", getMode());

        uploadFolder = jobParams.getProperty("uploadFolder");

        // check constraints for running the job
        if (canRunJob()) {
            // if mode = REPLACE, remove all filemetadata from the dataset version and start fresh
            if (mode.equalsIgnoreCase(ImportMode.REPLACE.name())) {
                try {
                    DatasetVersion workingVersion = dataset.getEditVersion();
                    List<FileMetadata> fileMetadataList = workingVersion.getFileMetadatas();
                    jobLogger.log(Level.INFO, "Removing any existing file metadata since mode = REPLACE");
                    for (FileMetadata fmd : fileMetadataList) {
                        dataFileServiceBean.deleteFromVersion(workingVersion, fmd.getDataFile());
                    }
                } catch (Exception e) {
                    jobLogger.log(Level.SEVERE, "Removing existing file metadata in REPLACE mode: " + e.getMessage());
                }
            }
            // load the checksum manifest
            loadChecksumManifest();
        } else {
            jobContext.setExitStatus("FAILED");
        }
    }

    /**
     * After the job is done, generate a report
     */
    @Override
    public void afterJob() throws Exception {

        //TODO add notifications to job failure?
        if (jobContext.getExitStatus() != null && jobContext.getExitStatus().equals("FAILED")) {
            getJobLogger().log(Level.SEVERE, "Job Failed. See Log for more information.");
            closeJobLoggerHandlers();
            return;
        }

        // run reporting and notifications
        doReport();

        // report any unused checksums
        HashMap checksumHashMap = (HashMap<String, String>) jobContext.getTransientUserData();
        for (Object key : checksumHashMap.keySet()) {
            getJobLogger().log(Level.SEVERE, "File listed in checksum manifest not found: " + key);
        }

        // job step info
        JobOperator jobOperator = BatchRuntime.getJobOperator();
        StepExecution step = jobOperator.getStepExecutions(jobContext.getInstanceId()).get(0);
        getJobLogger().log(Level.INFO, "Job start = " + step.getStartTime());
        getJobLogger().log(Level.INFO, "Job end   = " + step.getEndTime());
        getJobLogger().log(Level.INFO, "Job exit status = " + step.getExitStatus());

        closeJobLoggerHandlers();

    }

    private void closeJobLoggerHandlers() {
        // close the job logger handlers
        for (Handler h : getJobLogger().getHandlers()) {
            h.close();
        }
    }

    /**
     * Check current constraints:
     * 1. user has edit dataset permission
     * 2. only one dataset version
     * 3. dataset version is draft
     */
    private boolean canRunJob() {

        boolean canIssueCommand = permissionServiceBean
                .requestOn(new DataverseRequest(user, (HttpServletRequest) null), dataset)
                .canIssue(UpdateDatasetVersionCommand.class);
        if (!canIssueCommand) {
            getJobLogger().log(Level.SEVERE, "User doesn't have permission to import files into this dataset.");
            return false;
        }

//        if (!permissionServiceBean.userOn(user, dataset.getOwner()).has(Permission.EditDataset)) {
//            getJobLogger().log(Level.SEVERE, "User doesn't have permission to import files into this dataset.");
//            return false;
//        }

        if (dataset.getVersions().size() != 1) {
            getJobLogger().log(Level.SEVERE, "File system import is currently only supported for datasets with one version.");
            return false;
        }

        if (dataset.getLatestVersion().getVersionState() != DatasetVersion.VersionState.DRAFT) {
            getJobLogger().log(Level.SEVERE, "File system import is currently only supported for DRAFT versions.");
            return false;
        }
        return true;
    }

    /**
     * Generate all the job reports and user notifications.
     */
    private void doReport() {

        try {

            String jobJson;
            String jobId = Long.toString(jobContext.getInstanceId());
            JobOperator jobOperator = BatchRuntime.getJobOperator();

            if (user == null) {
                getJobLogger().log(Level.SEVERE, "Cannot find authenticated user.");
                return;
            }
            if (dataset == null) {
                getJobLogger().log(Level.SEVERE, "Cannot find dataset.");
                return;
            }

            long datasetVersionId = dataset.getLatestVersion().getId();

            JobExecution jobExecution = jobOperator.getJobExecution(jobContext.getInstanceId());
            if (jobExecution != null) {

                Date date = new Date();
                Timestamp timestamp = new Timestamp(date.getTime());

                JobExecutionEntity jobExecutionEntity = JobExecutionEntity.create(jobExecution);
                jobExecutionEntity.setExitStatus("COMPLETED");
                jobExecutionEntity.setStatus(BatchStatus.COMPLETED);
                jobExecutionEntity.setEndTime(date);
                jobJson = new ObjectMapper().writeValueAsString(jobExecutionEntity);

                String logDir = System.getProperty("com.sun.aas.instanceRoot") + SEP + "logs" + SEP + "batch-jobs" + SEP;

                // [1] save json log to file
                LoggingUtil.saveJsonLog(jobJson, logDir, jobId);
                // [2] send user notifications - to all authors
                userNotificationService.sendNotificationWithEmail(user, timestamp, notifyType, datasetVersionId, NotificationObjectType.DATASET_VERSION);
                Map<String, AuthenticatedUser> distinctAuthors = permissionServiceBean.getDistinctUsersWithPermissionOn(Permission.EditDataset, dataset);
                distinctAuthors.values().forEach((value) -> {
                    userNotificationService.sendNotificationWithEmail(value, new Timestamp(new Date().getTime()), notifyType, datasetVersionId, NotificationObjectType.DATASET_VERSION);
                });
                // [3] send SuperUser notification
                List<AuthenticatedUser> superUsers = authenticationServiceBean.findSuperUsers();
                if (superUsers != null && !superUsers.isEmpty()) {
                    superUsers.forEach((au) -> {
                        userNotificationService.sendNotificationWithEmail(au, timestamp, notifyType, datasetVersionId, NotificationObjectType.DATASET_VERSION);
                    });
                }
                // [4] action log: store location of the full log to avoid truncation issues
                actionLogServiceBean.log(LoggingUtil.getActionLogRecord(user.getIdentifier(), jobExecution,
                                                                        logDir + "job-" + jobId + ".log", jobId));

            } else {
                getJobLogger().log(Level.SEVERE, "Job execution is null");
            }

        } catch (NoSuchJobExecutionException | JobSecurityException | JsonProcessingException e) {
            getJobLogger().log(Level.SEVERE, "Creating job json: " + e.getMessage());
        }
    }

    /**
     * Get the dataset based on the job parameter: datasetId or datasetPrimaryKey.
     *
     * @return dataset global identifier
     */
    private String getDatasetGlobalId() {
        if (jobParams.containsKey("datasetId")) {

            String datasetId = jobParams.getProperty("datasetId");

            dataset = datasetDao.find(new Long(datasetId));

            if (dataset != null) {
                getJobLogger().log(Level.INFO, "Dataset Identifier (datasetId=" + datasetId + "): " + dataset.getIdentifier());
                return dataset.getGlobalId().asString();
            }
        }
        if (jobParams.containsKey("datasetPrimaryKey")) {
            long datasetPrimaryKey = Long.parseLong(jobParams.getProperty("datasetPrimaryKey"));
            dataset = datasetDao.find(datasetPrimaryKey);
            if (dataset != null) {
                getJobLogger().log(Level.INFO, "Dataset Identifier (datasetPrimaryKey=" + datasetPrimaryKey + "): "
                        + dataset.getIdentifier());

                return dataset.getGlobalId().asString();
            }
        }

        dataset = null;
        return null;
    }

    /**
     * Get the authenticated user based on the job parameter: userPrimaryKey or userId.
     *
     * @return user
     */
    private String getUserId() {
        if (jobParams.containsKey("userPrimaryKey")) {
            long userPrimaryKey = Long.parseLong(jobParams.getProperty("userPrimaryKey"));
            user = authenticationServiceBean.findByID(userPrimaryKey);
            getJobLogger().log(Level.INFO, "User Identifier (userPrimaryKey=" + userPrimaryKey + "): " + user.getIdentifier());
            return Long.toString(user.getId());
        }
        if (jobParams.containsKey("userId")) {
            String userId = jobParams.getProperty("userId");
            user = authenticationServiceBean.getAuthenticatedUser(userId);
            getJobLogger().log(Level.INFO, "User Identifier (userId=" + userId + "): " + user.getIdentifier());
            return Long.toString(user.getId());
        }
        getJobLogger().log(Level.SEVERE, "Cannot find authenticated user.");
        user = null;
        return null;
    }

    /**
     * Get the import mode: MERGE (default), UPDATE, REPLACE
     *
     * @return mode
     */
    private String getMode() {
        if (jobParams.containsKey("mode")) {
            mode = jobParams.getProperty("mode").toUpperCase();
        } else {
            mode = ImportMode.MERGE.name();
        }
        getJobLogger().log(Level.INFO, "Import mode =  " + mode);
        return mode;
    }

    /**
     * Load the checksum manifest into an in memory HashMap, available to the step's read-process-write classes via the
     * step context's transientUserData
     */
    private void loadChecksumManifest() {

        // log job checksum type and how it was configured
        if (System.getProperty("checksumType") != null) {
            getJobLogger().log(Level.INFO, "Checksum type = " + System.getProperty("checksumType") + " ('checksumType' System property)");
        } else {
            getJobLogger().log(Level.INFO, "Checksum type = " + checksumType + " (FileSystemImportJob.xml property)");
        }

        // check system property first, otherwise use default property in FileSystemImportJob.xml
        String manifest;
        if (System.getProperty("checksumManifest") != null) {
            manifest = System.getProperty("checksumManifest");
            getJobLogger().log(Level.INFO, "Checksum manifest = " + manifest + " ('checksumManifest' System property)");
        } else {
            manifest = checksumManifest;
            getJobLogger().log(Level.INFO, "Checksum manifest = " + manifest + " (FileSystemImportJob.xml property)");
        }
        // construct full path
        String manifestAbsolutePath = systemConfig.getFilesDirectory()
                + SEP + dataset.getAuthority()
                + SEP + dataset.getIdentifier()
                + SEP + uploadFolder
                + SEP + manifest;
        getJobLogger().log(Level.INFO, "Reading checksum manifest: " + manifestAbsolutePath);
        Scanner scanner = null;
        try {
            scanner = new Scanner(new FileReader(manifestAbsolutePath));
            HashMap<String, String> map = new HashMap<>();
            while (scanner.hasNextLine()) {
                String[] parts = scanner.nextLine().split("\\s+"); // split on any empty space between path and checksum
                if (parts.length == 2) {
                    map.put(parts[1].replaceAll("^\\./", ""), parts[0]); // strip any leading dot-slash
                }
            }
            jobContext.setTransientUserData(map);
            getJobLogger().log(Level.INFO, "Checksums found = " + map.size());
        } catch (IOException ioe) {
            getJobLogger().log(Level.SEVERE, "Unable to load checksum manifest file: " + ioe.getMessage());
            jobContext.setExitStatus("FAILED");
        } finally {
            IOUtils.closeQuietly(scanner);
        }

    }

    private Logger getJobLogger() {
        return Logger.getLogger("job-" + jobContext.getInstanceId());
    }

}
