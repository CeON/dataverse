/*
   Copyright (C) 2005-2012, by the President and Fellows of Harvard College.

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
   Version 3.0.
*/

package edu.harvard.iq.dataverse.ingest;

import edu.harvard.iq.dataverse.DataFileServiceBean;
import edu.harvard.iq.dataverse.DatasetDao;
import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.common.files.mime.ApplicationMimeType;
import edu.harvard.iq.dataverse.common.files.mime.TextMimeType;
import edu.harvard.iq.dataverse.dataaccess.DataAccess;
import edu.harvard.iq.dataverse.dataaccess.StorageIO;
import edu.harvard.iq.dataverse.dataaccess.StorageIOConstants;
import edu.harvard.iq.dataverse.dataaccess.StorageIOUtils;
import edu.harvard.iq.dataverse.dataaccess.TabularSubsetGenerator;
import edu.harvard.iq.dataverse.datafile.FileTypeDetector;
import edu.harvard.iq.dataverse.datavariable.VariableServiceBean;
import edu.harvard.iq.dataverse.ingest.metadataextraction.FileMetadataExtractor;
import edu.harvard.iq.dataverse.ingest.metadataextraction.FileMetadataIngest;
import edu.harvard.iq.dataverse.ingest.metadataextraction.impl.plugins.fits.FITSFileMetadataExtractor;
import edu.harvard.iq.dataverse.ingest.tabulardata.TabularDataFileReader;
import edu.harvard.iq.dataverse.ingest.tabulardata.TabularDataIngest;
import edu.harvard.iq.dataverse.ingest.tabulardata.impl.plugins.csv.CSVFileReader;
import edu.harvard.iq.dataverse.ingest.tabulardata.impl.plugins.csv.CSVFileReaderSpi;
import edu.harvard.iq.dataverse.ingest.tabulardata.impl.plugins.dta.DTAFileReader;
import edu.harvard.iq.dataverse.ingest.tabulardata.impl.plugins.dta.DTAFileReaderSpi;
import edu.harvard.iq.dataverse.ingest.tabulardata.impl.plugins.dta.NewDTAFileReader;
import edu.harvard.iq.dataverse.ingest.tabulardata.impl.plugins.por.PORFileReader;
import edu.harvard.iq.dataverse.ingest.tabulardata.impl.plugins.por.PORFileReaderSpi;
import edu.harvard.iq.dataverse.ingest.tabulardata.impl.plugins.rdata.RDATAFileReader;
import edu.harvard.iq.dataverse.ingest.tabulardata.impl.plugins.rdata.RDATAFileReaderSpi;
import edu.harvard.iq.dataverse.ingest.tabulardata.impl.plugins.sav.SAVFileReader;
import edu.harvard.iq.dataverse.ingest.tabulardata.impl.plugins.sav.SAVFileReaderSpi;
import edu.harvard.iq.dataverse.ingest.tabulardata.impl.plugins.xlsx.XLSXFileReader;
import edu.harvard.iq.dataverse.ingest.tabulardata.impl.plugins.xlsx.XLSXFileReaderSpi;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.DataFileCategory;
import edu.harvard.iq.dataverse.persistence.datafile.DataTable;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.persistence.datafile.datavariable.DataVariable;
import edu.harvard.iq.dataverse.persistence.datafile.datavariable.SummaryStatistic;
import edu.harvard.iq.dataverse.persistence.datafile.datavariable.VariableCategory;
import edu.harvard.iq.dataverse.persistence.datafile.ingest.IngestError;
import edu.harvard.iq.dataverse.persistence.datafile.ingest.IngestException;
import edu.harvard.iq.dataverse.persistence.datafile.ingest.IngestReport;
import edu.harvard.iq.dataverse.persistence.datafile.ingest.IngestRequest;
import edu.harvard.iq.dataverse.persistence.dataset.ControlledVocabularyValue;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetField;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldType;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetLock;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.dataset.MetadataBlock;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.util.FileUtil;
import edu.harvard.iq.dataverse.util.SumStatCalculator;
import edu.harvard.iq.dataverse.util.SystemConfig;
import io.vavr.control.Option;
import org.apache.commons.lang3.StringUtils;
import org.dataverse.unf.UNFUtil;
import org.dataverse.unf.UnfException;

import javax.ejb.Asynchronous;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.enterprise.event.Event;
import javax.faces.application.FacesMessage;
import javax.faces.bean.ManagedBean;
import javax.inject.Inject;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * @author Leonid Andreev
 * dataverse 4.0
 * New service for handling ingest tasks
 */
@Stateless
@ManagedBean
public class IngestServiceBean {
    private static final Logger logger = Logger.getLogger(IngestServiceBean.class.getCanonicalName());

    private static String dateTimeFormat_ymdhmsS = "yyyy-MM-dd HH:mm:ss.SSS";
    private static String dateFormat_ymd = "yyyy-MM-dd";

    @EJB
    private DatasetDao datasetDao;
    @EJB
    private DataFileServiceBean fileService;
    @EJB
    private SystemConfig systemConfig;

    @Inject
    private SettingsServiceBean settingsService;

    @Inject
    private FileTypeDetector fileTypeDetector;

    @Inject
    private Event<IngestMessageSendEvent> ingestMessageSendEventEvent;

    @Inject
    private FinalizeIngestService finalizeIngestService;

    private DataAccess dataAccess = DataAccess.dataAccess();

    // -------------------- LOGIC --------------------

    // This method tries to permanently store new files on the filesystem.
    // Then it adds the files that *have been successfully saved* to the
    // dataset (by attaching the DataFiles to the Dataset, and the corresponding
    // FileMetadatas to the DatasetVersion). It also tries to ensure that none
    // of the parts of the DataFiles that failed to be saved (if any) are still
    // attached to the Dataset via some cascade path (for example, via
    // DataFileCategory objects, if any were already assigned to the files).
    // It must be called before we attempt to permanently save the files in
    // the database by calling the Save command on the dataset and/or version.
    public List<DataFile> saveAndAddFilesToDataset(DatasetVersion version, List<DataFile> newFiles) {
        List<DataFile> result = new ArrayList<>();

        if (newFiles == null || newFiles.isEmpty()) {
            return result;
        }
        // final check for duplicate file names;
        // we tried to make the file names unique on upload, but then
        // the user may have edited them on the "add files" page, and
        // renamed FOOBAR-1.txt back to FOOBAR.txt...

        IngestUtil.checkForDuplicateFileNamesFinal(version, newFiles);

        Dataset dataset = version.getDataset();

        for (DataFile dataFile : newFiles) {

            Path tempLocationPath = Paths.get(FileUtil.getFilesTempDirectory() + "/" + dataFile.getStorageIdentifier());

            boolean unattached = false;
            boolean savedSuccess = false;
            StorageIO<DataFile> storageIO = null;

            try {
                logger.fine("Attempting to create a new storageIO object for " + dataFile.getStorageIdentifier());
                if (dataFile.getOwner() == null) {
                    unattached = true;
                    dataFile.setOwner(dataset);
                }
                dataFile.setStorageIdentifier(null);
                storageIO = dataAccess.createNewStorageIO(dataFile);

                logger.fine("Successfully created a new storageIO object.");

                storageIO.savePath(tempLocationPath);

                dataFile.setFilesize(storageIO.getSize());
                savedSuccess = true;
                logger.fine("Success: permanently saved file " + dataFile.getFileMetadata().getLabel());

            } catch (IOException ioex) {
                logger.warning("Failed to save the file, storage id " + dataFile.getStorageIdentifier() + " (" + ioex.getMessage() + ")");
            }

            // Since we may have already spent some CPU cycles scaling down image thumbnails,
            // we may as well save them, by moving these generated images to the permanent
            // dataset directory. We should also remember to delete any such files in the
            // temp directory:
            List<Path> generatedTempFiles = listGeneratedTempFiles(Paths.get(FileUtil.getFilesTempDirectory()),
                                                                   dataFile.getStorageIdentifier());
            if (generatedTempFiles != null) {
                for (Path generated : generatedTempFiles) {
                    if (savedSuccess) { // no need to try to save this aux file permanently, if we've failed to save the main file!
                        logger.fine("(Will also try to permanently save generated thumbnail file " + generated.toString() + ")");
                        try {
                            int i = generated.toString().lastIndexOf("thumb");
                            if (i > 1) {
                                String extensionTag = generated.toString().substring(i);
                                storageIO.savePathAsAux(generated, extensionTag);
                                logger.fine(
                                        "Saved generated thumbnail as aux object. \"preview available\" status: "
                                                + dataFile.isPreviewImageAvailable());
                            } else {
                                logger.warning("Generated thumbnail file name does not match the expected pattern: "
                                        + generated.toString());
                            }

                        } catch (IOException ioex) {
                            logger.warning("Failed to save generated file " + generated.toString());
                        }
                    }

                    // ... but we definitely want to delete it:
                    try {
                        Files.delete(generated);
                    } catch (IOException ioex) {
                        logger.warning("Failed to delete generated file " + generated.toString());
                    }
                }
            }

            // ... and let's delete the main temp file:
            try {
                logger.fine("Will attempt to delete the temp file " + tempLocationPath.toString());
                Files.delete(tempLocationPath);
            } catch (IOException ex) {
                // (non-fatal - it's just a temp file.)
                logger.warning("Failed to delete temp file " + tempLocationPath.toString());
            }

            if (unattached) {
                dataFile.setOwner(null);
            }
            // Any necessary post-processing:
            // performPostProcessingTasks(dataFile);

            if (!savedSuccess) {
                continue;
            }

            // These are all brand new files, so they should all have
            // one filemetadata total. -- L.A.
            FileMetadata fileMetadata = dataFile.getFileMetadatas().get(0);
            String fileName = fileMetadata.getLabel();

            boolean metadataExtracted = false;
            if (FileUtil.canIngestAsTabular(dataFile)) {
                /*
                 * Note that we don't try to ingest the file right away -
                 * instead we mark it as "scheduled for ingest", then at
                 * the end of the save process it will be queued for async.
                 * ingest in the background. In the meantime, the file
                 * will be ingested as a regular, non-tabular file, and
                 * appear as such to the user, until the ingest job is
                 * finished with the Ingest Service.
                 */
                dataFile.SetIngestScheduled();
            } else if (fileMetadataExtractable(dataFile)) {

                try {
                    // FITS is the only type supported for metadata
                    // extraction, as of now. -- L.A. 4.0
                    metadataExtracted = extractMetadata(tempLocationPath.toString(), dataFile, version);
                } catch (IOException mex) {
                    logger.severe("Caught exception trying to extract indexable metadata from file " + fileName + ",  " + mex.getMessage());
                }
                logger.fine(metadataExtracted
                        ? "Successfully extracted indexable metadata from file " + fileName
                        : "Failed to extract indexable metadata from file " + fileName);
            }
            // Make sure the file is attached to the dataset and to the version, if this
            // hasn't been done yet:
            if (dataFile.getOwner() == null) {
                dataFile.setOwner(dataset);

                version.addFileMetadata(dataFile.getFileMetadata());
                dataFile.getFileMetadata().setDatasetVersion(version);
                dataset.getFiles().add(dataFile);

                if (dataFile.getFileMetadata().getCategories() != null) {
                    ListIterator<DataFileCategory> dfcIt = dataFile.getFileMetadata().getCategories().listIterator();

                    while (dfcIt.hasNext()) {
                        DataFileCategory dataFileCategory = dfcIt.next();

                        if (dataFileCategory.getDataset() != null) {
                            continue;
                        }
                        DataFileCategory newCategory = dataset.getCategoryByName(dataFileCategory.getName());
                        if (newCategory != null) {
                            newCategory.addFileMetadata(dataFile.getFileMetadata());
                            dfcIt.set(newCategory);
                        } else {
                            dfcIt.remove();
                        }
                    }
                }
            }
            result.add(dataFile);
        }
        logger.fine("Done! Finished saving new files in permanent storage and adding them to the dataset.");
        return result;
    }

    public List<Path> listGeneratedTempFiles(Path tempDirectory, String baseName) {
        List<Path> generatedFiles = new ArrayList<>();

        if (StringUtils.isEmpty(baseName)) {
            return null;
        }

        DirectoryStream.Filter<Path> filter = file ->
                file.getFileName() != null
                && file.getFileName().toString().startsWith(baseName + ".thumb");

        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(tempDirectory, filter)) {
            dirStream.forEach(generatedFiles::add);
        } catch (IOException ioe) {
            logger.log(Level.WARNING, "Exception encountered: ", ioe);
        }
        return generatedFiles;
    }

    // TODO: consider creating a version of this method that would take
    // datasetversion as the argument.
    // -- L.A. 4.6
    public void startIngestJobsForDataset(Dataset dataset, AuthenticatedUser user) {
        List<DataFile> scheduledFiles = new ArrayList<>();

        for (DataFile dataFile : dataset.getFiles()) {
            if (dataFile.isIngestScheduled()) {
                // todo: investigate why when calling save with the file object
                // gotten from the loop, the roles assignment added at create is removed
                // (switching to refinding via id resolves that)
                dataFile = fileService.find(dataFile.getId());
                scheduledFiles.add(dataFile);
            }
        }
        startIngestJobs(scheduledFiles, user);
    }

    public String startIngestJobs(List<DataFile> dataFiles, AuthenticatedUser user) {

        IngestMessage ingestMessage;
        StringBuilder sb = new StringBuilder();

        List<DataFile> scheduledFiles = new ArrayList<>();
        for (DataFile dataFile : dataFiles) {
            if (dataFile.isIngestScheduled()) {

                // refresh the copy of the DataFile:
                dataFile = fileService.find(dataFile.getId());

                long ingestSizeLimit = -1;
                try {
                    ingestSizeLimit = systemConfig.getTabularIngestSizeLimit(getTabDataReaderByMimeType(dataFile.getContentType()).getFormatName());
                } catch (IOException ioex) {
                    logger.warning(
                            "IO Exception trying to retrieve the ingestable format identifier from the plugin for type " + dataFile.getContentType() + " (non-fatal);");
                }

                if (ingestSizeLimit == -1 || dataFile.getFilesize() < ingestSizeLimit) {
                    dataFile.SetIngestInProgress();
                    scheduledFiles.add(dataFile);
                } else {
                    dataFile.setIngestDone();
                    String message = "Skipping tabular ingest of the file " + dataFile.getFileMetadata().getLabel() + ", because of the size limit (set to " + ingestSizeLimit + " bytes); ";
                    logger.info(message);
                    sb.append(message);
                }
                dataFile = fileService.save(dataFile);
            } else {
                String message = "(Re)ingest queueing request submitted on a file not scheduled for ingest! (" + dataFile.getFileMetadata().getLabel() + "); ";
                logger.warning(message);
                sb.append(message);
            }
        }

        int count = scheduledFiles.size();

        if (count > 0) {
            String info = "Ingest of " + count + " tabular data file(s) is in progress.";
            logger.info(info);
            datasetDao.addDatasetLock(scheduledFiles.get(0).getOwner().getId(),
                                      DatasetLock.Reason.Ingest,
                                      (user != null) ? user.getId() : null,
                                      info);

            // Sort ingest jobs by file size:
            DataFile[] scheduledFilesArray = scheduledFiles.toArray(new DataFile[count]);
            scheduledFiles = null;

            Arrays.sort(scheduledFilesArray, Comparator.comparingLong(DataFile::getFilesize));

            ingestMessage = new IngestMessage(IngestMessage.INGEST_MESAGE_LEVEL_INFO);
            for (int i = 0; i < count; i++) {
                ingestMessage.addFileId(scheduledFilesArray[i].getId());
            }

            ingestMessageSendEventEvent.fire(new IngestMessageSendEvent(ingestMessage));
        }

        return sb.toString();
    }

    public static void produceFrequencyStatistics(String[][] table, DataFile dataFile) throws IOException {
        List<DataVariable> vars = dataFile.getDataTable().getDataVariables();
        produceFrequencies(table, vars);
    }

    public static void produceFrequencies(File generatedTabularFile, List<DataVariable> vars) throws IOException {
        if (vars.isEmpty()) {
            return;
        }
        DataTable dataTable = vars.get(0).getDataTable();
        String[][] table = TabularSubsetGenerator.readFileIntoTable(dataTable, generatedTabularFile);
        produceFrequencies(table, vars);
    }

    public void recalculateDatasetVersionUNF(DatasetVersion version) {
        IngestUtil.recalculateDatasetVersionUNF(version);
    }

    public void sendFailNotification() {
        new FacesMessage(BundleUtil.getStringFromBundle("ingest.failed"));
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public boolean ingestAsTabular(Long datafile_id) {
        DataFile dataFile = fileService.find(datafile_id);
        IngestRequest ingestRequest = dataFile.getIngestRequest();

        boolean forceTypeCheck = ingestRequest != null && ingestRequest.isForceTypeCheck();

        // Locate ingest plugin for the file format by looking
        // it up with the Ingest Service Provider Registry:
        String fileName = dataFile.getFileMetadata().getLabel();
        TabularDataFileReader ingestPlugin = getTabDataReaderByMimeType(dataFile.getContentType());
        logger.fine("Found ingest plugin: " + (ingestPlugin != null ? ingestPlugin.getClass() : "NONE"));

        if (!forceTypeCheck && ingestPlugin == null) {
            // If this is a reingest request, we'll still have a chance
            // to find an ingest plugin for this file, once we try
            // to identify the file type again.
            // Otherwise, we can give up - there is no point in proceeding to
            // the next step if no ingest plugin is available.

            dataFile.SetIngestProblem();
            dataFile.setIngestReport(
                    IngestReport.createIngestFailureReport(dataFile, IngestError.NOPLUGIN, dataFile.getContentType()));
            fileService.saveInNewTransaction(dataFile);
            logger.warning("Ingest failure.");
            return false;
        }

        File additionalData = null;
        Optional<File> localFile;
        StorageIO<DataFile> storageIO;
        try {
            storageIO = dataAccess.getStorageIO(dataFile);
            localFile = Optional.of(StorageIOUtils.obtainAsLocalFile(storageIO, storageIO.isRemoteFile()));
        } catch (IOException ioEx) {
            dataFile.SetIngestProblem();

            dataFile.setIngestReport(IngestReport.createIngestFailureReport(dataFile, IngestError.UNKNOWN_ERROR));
            fileService.saveInNewTransaction(dataFile);

            logger.warning("Ingest failure (No file produced).");
            return false;
        }

        if (forceTypeCheck) {
            FileTypeDetector fileTypeDetector = new FileTypeDetector();
            String newType = fileTypeDetector.detectTabularFileType(localFile.get(), dataFile.getContentType());

            ingestPlugin = getTabDataReaderByMimeType(newType);
            logger.fine("Re-tested file type: " + newType +
                    "; Using ingest plugin " + (ingestPlugin != null ? ingestPlugin.getClass() : "NONE"));

            // check again:
            if (ingestPlugin == null) {
                // If it's still null - give up!

                dataFile.SetIngestProblem();
                dataFile.setIngestReport(IngestReport.createIngestFailureReport(
                        dataFile, IngestError.NOPLUGIN, dataFile.getContentType()));
                fileService.saveInNewTransaction(dataFile);
                logger.warning("Ingest failure: failed to detect ingest plugin (file type check forced)");
                return false;
            }

            dataFile.setContentType(newType);
        }

        if (ingestRequest != null) {
            if (ingestRequest.getTextEncoding() != null && !"".equals(ingestRequest.getTextEncoding())) {
                logger.fine("Setting language encoding to " + ingestRequest.getTextEncoding());
                ingestPlugin.setDataLanguageEncoding(ingestRequest.getTextEncoding());
            }
            if (ingestRequest.getLabelsFile() != null) {
                additionalData = new File(ingestRequest.getLabelsFile());
            }
        }

        TabularDataIngest tabDataIngest;
        try (BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(localFile.get()))) {
            tabDataIngest = ingestPlugin.read(inputStream, additionalData);
        } catch (IngestException ex) {
            dataFile.SetIngestProblem();

            dataFile.setIngestReport(IngestReport.createIngestFailureReport(dataFile, ex));
            logger.log(Level.WARNING, "Ingest failure.", ex);
            fileService.saveInNewTransaction(dataFile);
            return false;
        } catch (Exception ingestEx) {
            dataFile.SetIngestProblem();
            dataFile.setIngestReport(IngestReport.createIngestFailureReport(dataFile, IngestError.UNKNOWN_ERROR));
            fileService.saveInNewTransaction(dataFile);

            logger.log(Level.WARNING, "Ingest failure.", ingestEx);
            return false;
        } finally {
            if (storageIO.isRemoteFile()) {
                localFile.ifPresent(File::delete);
            }
        }

        String originalContentType = dataFile.getContentType();
        String originalFileName = dataFile.getFileMetadata().getLabel();
        long originalFileSize = dataFile.getFilesize();

        File tabFile = tabDataIngest.getTabDelimitedFile();

        if (tabDataIngest.getDataTable() == null || tabFile == null || !tabFile.exists()) {
            return false;
        }

        logger.info("Tabular data successfully ingested; DataTable with " +
                tabDataIngest.getDataTable().getVarQuantity() + " variables produced. " +
                "Tab-delimited file produced: " + tabFile.getAbsolutePath());

        dataFile.setFilesize(tabFile.length());

        // and change the mime type to "Tabular Data" on the final datafile,
        // and replace (or add) the extension ".tab" to the filename:
        dataFile.setContentType(TextMimeType.TSV_ALT.getMimeValue());
        IngestUtil.modifyExistingFilename(
                dataFile.getOwner().getLatestVersion(), dataFile.getFileMetadata(), FileUtil.replaceExtension(fileName, "tab"));

        tabDataIngest.getDataTable().setOriginalFileFormat(originalContentType);
        tabDataIngest.getDataTable().setOriginalFileSize(originalFileSize);

        dataFile.setDataTable(tabDataIngest.getDataTable());
        tabDataIngest.getDataTable().setDataFile(dataFile);

        try {
            String[][] table = TabularSubsetGenerator.readFileIntoTable(dataFile.getDataTable(), tabFile);
            produceSummaryStatistics(table, dataFile);
            produceFrequencyStatistics(table, dataFile);
        } catch (IOException postIngestEx) {

            dataFile.SetIngestProblem();
            dataFile.setIngestReport(
                    IngestReport.createIngestFailureReport(dataFile, IngestError.STATS_OR_SIGNATURE_FAILURE, postIngestEx.getMessage()));

            restoreIngestedDataFile(dataFile, tabDataIngest, originalFileSize, originalFileName, originalContentType);
            fileService.saveInNewTransaction(dataFile);

            logger.warning("Ingest failure: post-ingest tasks.");
            return false;
        }

        try {
            return finalizeIngestService.finalizeIngest(dataFile, additionalData, tabDataIngest, tabFile);
        } finally {
            tabFile.delete();
        }
    }

    public TabularDataFileReader getTabDataReaderByMimeType(String mimeType) {
        /*
         * Same as the comment above; since we don't have any ingest plugins loadable
         * in real times yet, we can select them by a fixed list of mime types.
         * -- L.A. 4.0 beta.
         */

        if (mimeType == null) {
            return null;
        }

        TabularDataFileReader ingestPlugin = null;

        if (mimeType.equals(ApplicationMimeType.STATA.getMimeValue())) {
            ingestPlugin = new DTAFileReader(new DTAFileReaderSpi());
        } else if (mimeType.equals(ApplicationMimeType.STATA13.getMimeValue())) {
            ingestPlugin = new NewDTAFileReader(new DTAFileReaderSpi(), 117);
        } else if (mimeType.equals(ApplicationMimeType.STATA14.getMimeValue())) {
            ingestPlugin = new NewDTAFileReader(new DTAFileReaderSpi(), 118);
        } else if (mimeType.equals(ApplicationMimeType.STATA15.getMimeValue())) {
            ingestPlugin = new NewDTAFileReader(new DTAFileReaderSpi(), 119);
        } else if (mimeType.equals(ApplicationMimeType.RDATA.getMimeValue())) {
            ingestPlugin = new RDATAFileReader(new RDATAFileReaderSpi(),
                                               settingsService.getValueForKey(SettingsServiceBean.Key.RserveHost),
                                               settingsService.getValueForKey(SettingsServiceBean.Key.RserveUser),
                                               settingsService.getValueForKey(SettingsServiceBean.Key.RservePassword),
                                               settingsService.getValueForKeyAsInt(SettingsServiceBean.Key.RservePort));
        } else if (mimeType.equals(TextMimeType.CSV.getMimeValue()) || mimeType.equals(TextMimeType.CSV_ALT.getMimeValue())) {
            ingestPlugin = new CSVFileReader(new CSVFileReaderSpi(), ',');
        } else if (mimeType.equals(TextMimeType.TSV.getMimeValue()) || mimeType.equals(TextMimeType.TSV_ALT.getMimeValue())) {
            ingestPlugin = new CSVFileReader(new CSVFileReaderSpi(), '\t');
        } else if (mimeType.equals(ApplicationMimeType.XLSX.getMimeValue())) {
            ingestPlugin = new XLSXFileReader(new XLSXFileReaderSpi());
        } else if (mimeType.equals(ApplicationMimeType.SPSS_SAV.getMimeValue())) {
            ingestPlugin = new SAVFileReader(new SAVFileReaderSpi());
        } else if (mimeType.equals(ApplicationMimeType.SPSS_POR.getMimeValue())) {
            ingestPlugin = new PORFileReader(new PORFileReaderSpi());
        }

        return ingestPlugin;
    }

    public boolean fileMetadataExtractable(DataFile dataFile) {
        /*
         * Eventually we'll be consulting the Ingest Service Provider Registry
         * to see if there is a plugin for this type of file;
         * for now - just a hardcoded list of mime types:
         *  -- L.A. 4.0 beta
         */
        return dataFile.getContentType() != null && dataFile.getContentType().equals(ApplicationMimeType.FITS.getMimeValue());
    }

    /*
     * extractMetadata:
     * framework for extracting metadata from uploaded files. The results will
     * be used to populate the metadata of the Dataset to which the file belongs.
     */
    public boolean extractMetadata(String tempFileLocation, DataFile dataFile, DatasetVersion editVersion) throws IOException {
        boolean ingestSuccessful = false;

        FileInputStream tempFileInputStream = null;

        try {
            tempFileInputStream = new FileInputStream(new File(tempFileLocation));
        } catch (FileNotFoundException notfoundEx) {
            throw new IOException("Could not open temp file " + tempFileLocation);
        }

        // Locate metadata extraction plugin for the file format by looking
        // it up with the Ingest Service Provider Registry:
        //FileMetadataExtractor extractorPlugin = IngestSP.getMetadataExtractorByMIMEType(dfile.getContentType());
        FileMetadataExtractor extractorPlugin = new FITSFileMetadataExtractor();

        FileMetadataIngest extractedMetadata = extractorPlugin.ingest(new BufferedInputStream(tempFileInputStream));
        Map<String, Set<String>> extractedMetadataMap = extractedMetadata.getMetadataMap();

        // Store the fields and values we've gathered for safe-keeping:
        // from 3.6:
        // attempt to ingest the extracted metadata into the database;
        // TODO: this should throw an exception if anything goes wrong.
        FileMetadata fileMetadata = dataFile.getFileMetadata();

        if (extractedMetadataMap != null) {
            logger.fine("Ingest Service: Processing extracted metadata;");
            if (extractedMetadata.getMetadataBlockName() != null) {
                logger.fine("Ingest Service: This metadata belongs to the " + extractedMetadata.getMetadataBlockName() + " metadata block.");
                processDatasetMetadata(extractedMetadata, editVersion);
            }

            processFileLevelMetadata(extractedMetadata, fileMetadata);

        }

        ingestSuccessful = true;

        return ingestSuccessful;
    }

    // This method takes a list of file ids, checks the format type of the ingested
    // original, and attempts to fix it if it's missing.
    // Note the @Asynchronous attribute - this allows us to just kick off and run this
    // (potentially large) job in the background.
    // The method is called by the "fixmissingoriginaltypes" /admin api call.
    @Asynchronous
    public void fixMissingOriginalTypes(List<Long> datafileIds) {
        for (Long fileId : datafileIds) {
            fixMissingOriginalType(fileId);
        }
        logger.info("Finished repairing tabular data files that were missing the original file format labels.");
    }

    // This method takes a list of file ids and tries to fix the size of the saved
    // original, if present
    // Note the @Asynchronous attribute - this allows us to just kick off and run this
    // (potentially large) job in the background.
    // The method is called by the "fixmissingoriginalsizes" /admin api call.
    @Asynchronous
    public void fixMissingOriginalSizes(List<Long> datafileIds) {
        for (Long fileId : datafileIds) {
            fixMissingOriginalSize(fileId);
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Exception encountered: ", e);
            }
        }
        logger.info("Finished repairing tabular data files that were missing the original file sizes.");
    }

    // -------------------- PRIVATE --------------------

    private void restoreIngestedDataFile(DataFile dataFile, TabularDataIngest tabDataIngest, long originalSize, String originalFileName, String originalContentType) {
        dataFile.setDataTable(null);
        if (tabDataIngest != null && tabDataIngest.getDataTable() != null) {
            tabDataIngest.getDataTable().setDataFile(null);
        }
        dataFile.getFileMetadata().setLabel(originalFileName);
        dataFile.setContentType(originalContentType);
        dataFile.setFilesize(originalSize);
    }

    private static void produceFrequencies(String[][] table, List<DataVariable> vars) {
        for (int i = 0; i < vars.size(); i++) {
            Collection<VariableCategory> cats = vars.get(i).getCategories();
            int caseQuantity = vars.get(i).getDataTable().getCaseQuantity().intValue();
            boolean isNumeric = vars.get(i).isTypeNumeric();
            Object[] variableVector;
            if (cats.size() > 0) {
                if (isNumeric) {
                    variableVector = TabularSubsetGenerator.subsetFloatVector(table, i, caseQuantity);
                } else {
                    variableVector = TabularSubsetGenerator.subsetStringVector(table, i, caseQuantity);
                }
                Map<Object, Double> freq = calculateFrequency(variableVector);
                for (VariableCategory cat : cats) {
                    Object catValue;
                    if (isNumeric) {
                        catValue = new Float(cat.getValue());
                    } else {
                        catValue = cat.getValue();
                    }
                    Double numberFreq = freq.get(catValue);
                    if (numberFreq != null) {
                        cat.setFrequency(numberFreq);
                    } else {
                        cat.setFrequency(0D);
                    }
                }
            }
        }
    }

    private static Map<Object, Double> calculateFrequency(Object[] variableVector) {
        Map<Object, Double> frequencies = new HashMap<>();

        for (Object variable : variableVector) {
            if (variable != null) {
                Double freqNum = frequencies.get(variable);
                frequencies.put(variable, freqNum != null ? freqNum + 1 : 1D);
            }
        }
        return frequencies;
    }

    private void processDatasetMetadata(FileMetadataIngest fileMetadataIngest, DatasetVersion editVersion) {

        for (MetadataBlock mdb : editVersion.getDataset().getOwner().getRootMetadataBlocks()) {
            if (!mdb.getName().equals(fileMetadataIngest.getMetadataBlockName())) {
                continue;
            }
            logger.fine("Ingest Service: dataset version has " + mdb.getName() + " metadata block enabled.");

            editVersion.setDatasetFields(editVersion.initDatasetFields());

            Map<String, Set<String>> fileMetadataMap = fileMetadataIngest.getMetadataMap();
            for (DatasetFieldType dsft : mdb.getDatasetFieldTypes()) {
                if (dsft.isPrimitive()) {
                    if (dsft.isHasParent()) {
                        continue;
                    }
                    String dsfName = dsft.getName();
                    // See if the plugin has found anything for this field:
                    if (fileMetadataMap.get(dsfName) != null && !fileMetadataMap.get(dsfName).isEmpty()) {

                        logger.fine("Ingest Service: found extracted metadata for field " + dsfName);
                        // go through the existing fields:
                        for (DatasetField dsf : editVersion.getFlatDatasetFields()) {
                            if (!dsf.getDatasetFieldType().equals(dsft)) {
                                continue;
                            }
                            // yep, this is our field!
                            // let's go through the values that the ingest
                            // plugin found in the file for this field:

                            Set<String> mValues = fileMetadataMap.get(dsfName);

                            // Special rules apply to aggregation of values for
                            // some specific fields - namely, the resolution.*
                            // fields from the Astronomy Metadata block.
                            // TODO: rather than hard-coded, this needs to be
                            // programmatically defined. -- L.A. 4.0
                            if ("resolution.Temporal".equals(dsfName)
                                    || "resolution.Spatial".equals(dsfName)
                                    || "resolution.Spectral".equals(dsfName)) {
                                // For these values, we aggregate the minimum-maximum
                                // pair, for the entire set.
                                // So first, we need to go through the values found by
                                // the plugin and select the min. and max. values of
                                // these:
                                // (note that we are assuming that they all must
                                // validate as doubles!)

                                Double minValue = null;
                                Double maxValue = null;

                                for (String fValue : mValues) {

                                    try {
                                        double thisValue = Double.parseDouble(fValue);

                                        if (minValue == null || Double.compare(thisValue, minValue) < 0) {
                                            minValue = thisValue;
                                        }
                                        if (maxValue == null || Double.compare(thisValue, maxValue) > 0) {
                                            maxValue = thisValue;
                                        }
                                    } catch (NumberFormatException nfe) {
                                        logger.log(Level.FINEST, "Wrong value: ", nfe);
                                    }
                                }

                                // Now let's see what aggregated values we
                                // have stored already:

                                // (all of these resolution.* fields have allowedMultiple set to FALSE,
                                // so there can be only one!)
                                //logger.fine("Min value: "+minValue+", Max value: "+maxValue);
                                if (minValue == null) {
                                    // Ouch.
                                    continue;
                                }
                                Double storedMinValue;
                                Double storedMaxValue;

                                String storedValue = "";

                                if (dsf.getFieldValue().isDefined()) {
                                    storedValue = dsf.getFieldValue().get();

                                    if (StringUtils.isNotEmpty(storedValue)) {
                                        try {
                                            boolean isRange = storedValue.contains(" - ");
                                            storedMinValue = isRange
                                                    ? Double.parseDouble(storedValue.substring(0, storedValue.indexOf(" - ")))
                                                    : Double.parseDouble(storedValue);
                                            storedMaxValue = isRange
                                                    ? Double.parseDouble(storedValue.substring(storedValue.indexOf(" - ") + 3))
                                                    : storedMinValue;
                                            if (storedMinValue.compareTo(minValue) < 0) {
                                                minValue = storedMinValue;
                                            }
                                            if (storedMaxValue.compareTo(maxValue) > 0) {
                                                maxValue = storedMaxValue;
                                            }
                                        } catch (NumberFormatException e) {
                                            logger.log(Level.FINE, "Wrong value: ", e);
                                        }
                                    } else {
                                        storedValue = "";
                                    }
                                }

                                String newAggregateValue = minValue.equals(maxValue)
                                        ? minValue.toString()
                                        : minValue.toString() + " - " + maxValue.toString();

                                // finally, compare it to the value we have now:
                                if (!storedValue.equals(newAggregateValue)) {
                                    dsf.setFieldValue(newAggregateValue);
                                }
                            } else {
                                // Other fields are aggregated simply by
                                // collecting a list of *unique* values encountered
                                // for this Field throughout the dataset.
                                // This means we need to only add the values *not yet present*.
                                // (the implementation below may be inefficient - ?)

                                for (String fValue : mValues) {
                                    if (!dsft.isControlledVocabulary()) {
                                        for (DatasetField dsfv : dsf.getDatasetFieldsChildren()) {
                                            if (!fValue.equals(dsfv.getValue())) {
                                                logger.fine("Creating a new value for field " + dsfName + ": " + fValue);
                                                dsfv.setFieldValue(fValue);
                                            }
                                        }
                                    } else {
                                        // A controlled vocabulary entry:
                                        // first, let's see if it's a legit control vocab. entry:
                                        ControlledVocabularyValue legitControlledVocabularyValue = null;
                                        Collection<ControlledVocabularyValue> definedVocabularyValues
                                                = dsft.getControlledVocabularyValues();
                                        if (definedVocabularyValues != null) {
                                            for (ControlledVocabularyValue definedVocabValue : definedVocabularyValues) {
                                                if (fValue.equals(definedVocabValue.getStrValue())) {
                                                    logger.fine("Yes, " + fValue
                                                            + " is a valid controlled vocabulary value for the field " + dsfName);
                                                    legitControlledVocabularyValue = definedVocabValue;
                                                    break;
                                                }
                                            }
                                        }
                                        if (legitControlledVocabularyValue == null) {
                                            continue;
                                        }
                                        // Only need to add the value if it is new,
                                        // i.e. if it does not exist yet:
                                        boolean valueExists = false;

                                        List<ControlledVocabularyValue> existingControlledVocabValues
                                                = dsf.getControlledVocabularyValues();
                                        if (existingControlledVocabValues != null) {
                                            for (ControlledVocabularyValue cvv : existingControlledVocabValues) {
                                                if (fValue.equals(cvv.getStrValue())) {
                                                    // or should I use if (legitControlledVocabularyValue.equals(cvv)) ?
                                                    logger.fine("Controlled vocab. value " + fValue
                                                            + " already exists for field " + dsfName);
                                                    valueExists = true;
                                                    break;
                                                }
                                            }
                                        }

                                        if (!valueExists) {
                                            logger.fine("Adding controlled vocabulary value " + fValue + " to field " + dsfName);
                                            dsf.getControlledVocabularyValues().add(legitControlledVocabularyValue);
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // A compound field:
                    // See if the plugin has found anything for the fields that
                    // make up this compound field; if we find at least one
                    // of the child values in the map of extracted values, we'll
                    // create a new compound field value and its child
                    //
                    List<DatasetField> missingFields = new ArrayList<>();
                    int nonEmptyFields = 0;
                    for (DatasetFieldType cdsft : dsft.getChildDatasetFieldTypes()) {
                        String dsfName = cdsft.getName();
                        if (fileMetadataMap.get(dsfName) == null || fileMetadataMap.get(dsfName).isEmpty()) {
                            continue;
                        }
                        logger.fine("Ingest Service: found extracted metadata for field " + dsfName + ", part of the compound field " + dsft.getName());

                        // probably an unnecessary check - child fields
                        // of compound fields are always primitive...
                        // but maybe it'll change in the future.
                        if (!cdsft.isPrimitive() || cdsft.isControlledVocabulary()) {
                            continue;
                        }

                        // TODO: can we have controlled vocabulary
                        // sub-fields inside compound fields?

                        DatasetField childDsf = new DatasetField();
                        childDsf.setDatasetFieldType(cdsft);
                        childDsf.setFieldValue((String) fileMetadataMap.get(dsfName).toArray()[0]);

                        missingFields.add(childDsf);

                        nonEmptyFields++;
                    }

                    if (nonEmptyFields > 0) {
                        // let's go through this dataset's fields and find the
                        // actual parent for this sub-field:
                        for (DatasetField dsf : editVersion.getDatasetFields()) {
                            if (!dsf.getDatasetFieldType().equals(dsft)) {
                                continue;
                            }

                            // Now let's check that the dataset version doesn't already have
                            // this compound value - we are only interested in aggregating
                            // unique values. Note that we need to compare compound values
                            // as sets! -- i.e. all the sub fields in 2 compound fields
                            // must match in order for these 2 compounds to be recognized
                            // as "the same":

                            boolean alreadyExists = false;

                            int matches = 0;
                            for (DatasetField dsfcv : dsf.getDatasetFieldsChildren()) {

                                String cdsfName = dsfcv.getDatasetFieldType().getName();
                                Option<String> cdsfValue = dsfcv.getFieldValue();
                                if (cdsfValue.isDefined() && !cdsfValue.isEmpty()) {
                                    String extractedValue = (String) fileMetadataMap.get(cdsfName).toArray()[0];
                                    logger.fine("values: existing: " + cdsfValue + ", extracted: " + extractedValue);
                                    if (cdsfValue.get().equals(extractedValue)) {
                                        matches++;
                                    }
                                }
                                if (matches == nonEmptyFields) {
                                    alreadyExists = true;
                                    break;
                                }
                            }

                            if (!alreadyExists) {
                                // save this compound value, by attaching it to the
                                // version for proper cascading:
                                missingFields.forEach(missingField -> {
                                    missingField.setDatasetFieldParent(dsf);
                                    dsf.getDatasetFieldsChildren().add(missingField);
                                });
                            }
                        }
                    }
                }
            }
        }
    }


    private void processFileLevelMetadata(FileMetadataIngest fileLevelMetadata, FileMetadata fileMetadata) {
        // The only type of metadata that ingest plugins can extract from ingested
        // files (as of 4.0 beta) that *stay* on the file-level is the automatically
        // generated "metadata summary" note. We attach it to the "description"
        // field of the fileMetadata object. -- L.A.

        String metadataSummary = fileLevelMetadata.getMetadataSummary();
        if (StringUtils.isNotEmpty(metadataSummary)) {
            // The file upload page allows a user to enter file description
            // on ingest. We don't want to overwrite whatever they may
            // have entered. Rather, we'll append this generated metadata summary
            // to the existing value.
            String userEnteredFileDescription = fileMetadata.getDescription();
            if (StringUtils.isNotEmpty(userEnteredFileDescription)) {
                metadataSummary = userEnteredFileDescription.concat(";\n" + metadataSummary);
            }
            fileMetadata.setDescription(metadataSummary);
        }
    }

    private void calculateContinuousSummaryStatistics(DataFile dataFile, int varnum, Number[] dataVector) throws IOException {
        double[] sumStats = SumStatCalculator.calculateSummaryStatistics(dataVector);
        assignContinuousSummaryStatistics(dataFile.getDataTable().getDataVariables().get(varnum), sumStats);
    }

    private void assignContinuousSummaryStatistics(DataVariable variable, double[] sumStats) throws IOException {
        if (sumStats == null || sumStats.length != VariableServiceBean.summaryStatisticTypes.length) {
            throw new IOException("Wrong number of summary statistics types calculated! ("
                    + (sumStats == null ? "[null]" : sumStats.length) + ")");
        }

        for (int j = 0; j < VariableServiceBean.summaryStatisticTypes.length; j++) {
            SummaryStatistic ss = new SummaryStatistic();
            ss.setTypeByLabel(VariableServiceBean.summaryStatisticTypes[j]);
            ss.setValue(ss.isTypeMode() ? "." : (new Double(sumStats[j])).toString());
            ss.setDataVariable(variable);
            variable.getSummaryStatistics().add(ss);
        }
    }

    private void calculateUNF(DataFile dataFile, int varnum, Double[] dataVector) {
        String unf = null;
        try {
            unf = UNFUtil.calculateUNF(dataVector);
        } catch (IOException iex) {
            logger.warning(
                    "exception thrown when attempted to calculate UNF signature for (numeric, continuous) variable " + varnum);
        } catch (Exception uex) {
            logger.warning(
                    "UNF Exception: thrown when attempted to calculate UNF signature for (numeric, continuous) variable " + varnum);
        }

        if (unf != null) {
            dataFile.getDataTable().getDataVariables().get(varnum).setUnf(unf);
        } else {
            logger.warning("failed to calculate UNF signature for variable " + varnum);
        }
    }

    private void calculateUNF(DataFile dataFile, int varnum, Long[] dataVector) {
        String unf = null;
        try {
            unf = UNFUtil.calculateUNF(dataVector);
        } catch (IOException iex) {
            logger.warning(
                    "exception thrown when attempted to calculate UNF signature for (numeric, discrete) variable " + varnum);
        } catch (UnfException uex) {
            logger.warning(
                    "UNF Exception: thrown when attempted to calculate UNF signature for (numeric, discrete) variable " + varnum);
        }

        if (unf != null) {
            dataFile.getDataTable().getDataVariables().get(varnum).setUnf(unf);
        } else {
            logger.warning("failed to calculate UNF signature for variable " + varnum);
        }
    }

    private void calculateUNF(DataFile dataFile, int varnum, String[] dataVector) throws IOException {
        String unf = null;

        String[] dateFormats = null;

        // Special handling for Character strings that encode dates and times:

        if ("time".equals(dataFile.getDataTable().getDataVariables().get(varnum).getFormatCategory())) {
            dateFormats = new String[dataVector.length];
            String savedDateTimeFormat = dataFile.getDataTable().getDataVariables().get(varnum).getFormat();
            String timeFormat = StringUtils.isNotEmpty(savedDateTimeFormat)
                    ? savedDateTimeFormat : dateTimeFormat_ymdhmsS;

            /* What follows is special handling of a special case of time values
             * non-uniform precision; specifically, when some have if some have
             * milliseconds, and some don't. (and that in turn is only
             * n issue when the timezone is present... without the timezone
             * the time string would still evaluate to the end, even if the
             * format has the .SSS part and the string does not.
             * This case will be properly handled internally, once we permanently
             * switch to UNF6.
             * -- L.A. 4.0 beta 8
             */
            String simplifiedFormat = null;
            SimpleDateFormat fullFormatParser = null;
            SimpleDateFormat simplifiedFormatParser = null;

            if (timeFormat.matches(".*\\.SSS z$")) {
                simplifiedFormat = timeFormat.replace(".SSS", "");

                fullFormatParser = new SimpleDateFormat(timeFormat);
                simplifiedFormatParser = new SimpleDateFormat(simplifiedFormat);
            }

            for (int i = 0; i < dataVector.length; i++) {
                if (dataVector[i] == null) {
                    continue;
                }

                if (simplifiedFormatParser != null) {
                    // first, try to parse the value against the "full"
                    // format (with the milliseconds part):
                    fullFormatParser.setLenient(false);

                    try {
                        logger.fine("trying the \"full\" time format, with milliseconds: " + timeFormat + ", " + dataVector[i]);
                        fullFormatParser.parse(dataVector[i]);
                    } catch (ParseException ex) {
                        // try the simplified (no time zone) format instead:
                        logger.fine("trying the simplified format: " + simplifiedFormat + ", " + dataVector[i]);
                        simplifiedFormatParser.setLenient(false);
                        try {
                            simplifiedFormatParser.parse(dataVector[i]);
                            timeFormat = simplifiedFormat;
                        } catch (ParseException ex1) {
                            logger.warning("no parseable format found for time value " + i + " - " + dataVector[i]);
                            throw new IOException("no parseable format found for time value " + i + " - " + dataVector[i]);
                        }
                    }

                }
                dateFormats[i] = timeFormat;
            }
        } else if ("date".equals(dataFile.getDataTable().getDataVariables().get(varnum).getFormatCategory())) {
            dateFormats = new String[dataVector.length];
            String savedDateFormat = dataFile.getDataTable().getDataVariables().get(varnum).getFormat();
            for (int i = 0; i < dataVector.length; i++) {
                if (dataVector[i] != null) {
                    dateFormats[i] = StringUtils.isNotEmpty(savedDateFormat)
                            ? savedDateFormat : dateFormat_ymd;
                }
            }
        }

        try {
            if (dateFormats == null) {
                logger.fine("calculating the UNF value for string vector; first value: " + dataVector[0]);
                unf = UNFUtil.calculateUNF(dataVector);
            } else {
                unf = UNFUtil.calculateUNF(dataVector, dateFormats);
            }
        } catch (IOException iex) {
            logger.warning("IO exception thrown when attempted to calculate UNF signature for (character) variable " + varnum);
        } catch (UnfException uex) {
            logger.warning("UNF Exception: thrown when attempted to calculate UNF signature for (character) variable " + varnum);
        }

        if (unf != null) {
            dataFile.getDataTable().getDataVariables().get(varnum).setUnf(unf);
        } else {
            logger.warning("failed to calculate UNF signature for variable " + varnum);
        }
    }

    // Calculating UNFs from *floats*, not *doubles* - this is to test dataverse
    // 4.0 Ingest against DVN 3.*; because of the nature of the UNF bug, reading
    // the tab file entry with 7+ digits of precision as a Double will result
    // in a UNF signature *different* from what was produced by the v. 3.* ingest,
    // from a STATA float value directly.
    // TODO: remove this from the final production 4.0!
    // -- L.A., Jul 2014

    private void calculateUNF(DataFile dataFile, int varnum, Float[] dataVector) {
        String unf = null;
        try {
            unf = UNFUtil.calculateUNF(dataVector);
        } catch (IOException iex) {
            logger.warning("exception thrown when attempted to calculate UNF signature for numeric, \"continuous\" " +
                    "(float) variable " + varnum);
        } catch (UnfException uex) {
            logger.warning("UNF Exception: thrown when attempted to calculate UNF signature for numeric, \"continuous\" " +
                    "(float) variable" + varnum);
        }

        if (unf != null) {
            dataFile.getDataTable().getDataVariables().get(varnum).setUnf(unf);
        } else {
            logger.warning("failed to calculate UNF signature for variable " + varnum);
        }
    }

    private void produceSummaryStatistics(String[][] table, DataFile dataFile) throws IOException {
        produceDiscreteNumericSummaryStatistics(table, dataFile);
        produceContinuousSummaryStatistics(table, dataFile);
        produceCharacterSummaryStatistics(table, dataFile);

        recalculateDataFileUNF(dataFile);
        recalculateDatasetVersionUNF(dataFile.getFileMetadata().getDatasetVersion());
    }

    private void produceDiscreteNumericSummaryStatistics(String[][] table, DataFile dataFile) throws IOException {
        DataTable dataTable = dataFile.getDataTable();
        int numberOfCases = dataTable.getCaseQuantity().intValue();
        for (int i = 0; i < dataTable.getVarQuantity(); i++) {
            Long[] vector = TabularSubsetGenerator.subsetLongVector(table, i, numberOfCases);
            calculateContinuousSummaryStatistics(dataFile, i, vector);
            calculateUNF(dataFile, i, vector);
        }
    }

    private void produceContinuousSummaryStatistics(String[][] table, DataFile dataFile) throws IOException {
        DataTable dataTable = dataFile.getDataTable();
        int numberOfCases = dataTable.getCaseQuantity().intValue();
        for (int i = 0; i < dataTable.getVarQuantity(); i++) {
            if (dataTable.getDataVariables().get(i).isIntervalContinuous()) {
                if ("float".equals(dataTable.getDataVariables().get(i).getFormat())) {
                    Float[] variableVector = TabularSubsetGenerator.subsetFloatVector(table, i, numberOfCases);
                    calculateContinuousSummaryStatistics(dataFile, i, variableVector);
                    calculateUNF(dataFile, i, variableVector);
                } else {
                    Double[] variableVector = TabularSubsetGenerator.subsetDoubleVector(table, i, numberOfCases);
                    calculateContinuousSummaryStatistics(dataFile, i, variableVector);
                    calculateUNF(dataFile, i, variableVector);
                }
            }
        }
    }

    /*
    At this point it's still not clear what kinds of summary stats we
    want for character types. Though we are pretty confident we don't
    want to keep doing what we used to do in the past, i.e. simply
    store the total counts for all the unique values; even if it's a
    very long vector, and *every* value in it is unique. (As a result
    of this, our Categorical Variable Value table is the single
    largest in the production database. With no evidence whatsoever,
    that this information is at all useful.
        -- L.A. Jul. 2014
    */
    private void produceCharacterSummaryStatistics(String[][] table, DataFile dataFile) throws IOException {
        DataTable dataTable = dataFile.getDataTable();
        int numberOfCases = dataTable.getCaseQuantity().intValue();
        for (int i = 0; i < dataTable.getVarQuantity(); i++) {
            if (dataTable.getDataVariables().get(i).isTypeCharacter()) {
                String[] variableVector = TabularSubsetGenerator.subsetStringVector(table, i, numberOfCases);
                calculateUNF(dataFile, i, variableVector);
            }
        }
    }

    private void recalculateDataFileUNF(DataFile dataFile) {
        String[] unfValues = new String[dataFile.getDataTable().getVarQuantity().intValue()];
        String fileUnfValue = null;

        for (int i = 0; i < dataFile.getDataTable().getVarQuantity(); i++) {
            String varunf = dataFile.getDataTable().getDataVariables().get(i).getUnf();
            unfValues[i] = varunf;
        }

        try {
            fileUnfValue = UNFUtil.calculateUNF(unfValues);
        } catch (IOException ex) {
            logger.warning("Failed to recalculate the UNF for the datafile id=" + dataFile.getId());
        } catch (UnfException uex) {
            logger.warning("UNF Exception: Failed to recalculate the UNF for the dataset version id=" + dataFile.getId());
        }

        if (fileUnfValue != null) {
            dataFile.getDataTable().setUnf(fileUnfValue);
        }
    }

    // This method fixes a datatable object that's missing the format type of
    // the ingested original. It will check the saved original file to
    // determine the type.
    private void fixMissingOriginalType(long fileId) {
        DataFile dataFile = fileService.find(fileId);

        if (dataFile != null && dataFile.isTabularData()) {
            String originalFormat = dataFile.getDataTable().getOriginalFileFormat();
            Long datatableId = dataFile.getDataTable().getId();
            if (StringUtils.isEmpty(originalFormat) || originalFormat.equals(TextMimeType.TSV_ALT.getMimeValue())) {

                // We need to determine the mime type of the saved original
                // and save it in the database.
                //
                // First, we need access to the file. Note that the code below
                // works with any supported StorageIO driver (although, as of now
                // all the production installations out there are only using filesystem
                // access; but just in case)
                // The FileUtil method that determines the type takes java.io.File
                // as an argument. So for StorageIO drivers that provide local
                // file access, we'll just go directly to the stored file. For
                // s3 and similar implementations, we'll read the saved aux
                // channel and save it as a local temp file.

                String fileTypeDetermined;
                long savedOriginalFileSize;
                Optional<File> tmpFile = Optional.empty();

                try {
                    StorageIO<DataFile> storageIO = dataAccess.getStorageIO(dataFile);
                    File savedOriginalFile = StorageIOUtils.obtainAuxAsLocalFile(storageIO, StorageIOConstants.SAVED_ORIGINAL_FILENAME_EXTENSION, storageIO.isRemoteFile());

                    tmpFile = storageIO.isRemoteFile() ? Optional.of(savedOriginalFile) : Optional.empty();

                    savedOriginalFileSize = savedOriginalFile.length();
                    fileTypeDetermined = fileTypeDetector.determineFileType(savedOriginalFile, "");

                } catch (Exception ex) {
                    logger.warning("Exception " + ex.getClass() + " caught trying to determine file type; (datafile id=" + fileId + ", datatable id=" + datatableId + "): " + ex.getMessage());
                    return;
                } finally {
                    tmpFile.ifPresent(File::delete);
                }

                if (fileTypeDetermined == null) {
                    logger.warning("Failed to determine preserved original file type. (datafile id=" + fileId + ", datatable id=" + datatableId + ")");
                    return;
                }
                // adjust the final result:
                // we know that this file has been successfully ingested;
                // so if the FileUtil is telling us it's a "plain text" file at this point,
                // it really means it must be a CSV file.
                if (fileTypeDetermined.startsWith("text/plain")) {
                    fileTypeDetermined = TextMimeType.CSV.getMimeValue();
                }
                // and, finally, if it is still "application/octet-stream", it must be Excel:
                if (ApplicationMimeType.UNDETERMINED_DEFAULT.getMimeValue().equals(fileTypeDetermined)) {
                    fileTypeDetermined = ApplicationMimeType.XLSX.getMimeValue();
                }
                logger.info("Original file type determined: " + fileTypeDetermined + " (file id=" + fileId + ", datatable id=" + datatableId + ")");

                // save permanently in the database:
                dataFile.getDataTable().setOriginalFileFormat(fileTypeDetermined);
                dataFile.getDataTable().setOriginalFileSize(savedOriginalFileSize);
                fileService.saveDataTable(dataFile.getDataTable());

            } else {
                logger.info("DataFile id=" + fileId + "; original type already present: " + originalFormat);
            }
        } else {
            logger.warning("DataFile id=" + fileId + ": No such DataFile!");
        }
    }

    // This method fixes a datatable object that's missing the size of the
    // ingested original.
    private void fixMissingOriginalSize(long fileId) {
        DataFile dataFile = fileService.find(fileId);

        if (dataFile != null && dataFile.isTabularData()) {
            Long savedOriginalFileSize = dataFile.getDataTable().getOriginalFileSize();
            Long datatableId = dataFile.getDataTable().getId();

            if (savedOriginalFileSize == null) {
                StorageIO<DataFile> storageIO;
                try {
                    storageIO = dataAccess.getStorageIO(dataFile);
                    storageIO.open();
                    savedOriginalFileSize = storageIO.getAuxObjectSize(StorageIOConstants.SAVED_ORIGINAL_FILENAME_EXTENSION);

                } catch (Exception ex) {
                    logger.warning("Exception " + ex.getClass() + " caught trying to look up the size of the saved original; (datafile id=" + fileId + ", datatable id=" + datatableId + "): " + ex.getMessage());
                    return;
                }

                // save permanently in the database:
                dataFile.getDataTable().setOriginalFileSize(savedOriginalFileSize);
                fileService.saveDataTable(dataFile.getDataTable());
            } else {
                logger.info("DataFile id=" + fileId + "; original file size already present: " + savedOriginalFileSize);
            }
        } else {
            logger.warning("DataFile id=" + fileId + ": No such DataFile!");
        }
    }
}
