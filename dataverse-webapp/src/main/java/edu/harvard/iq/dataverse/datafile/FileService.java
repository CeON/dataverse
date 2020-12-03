package edu.harvard.iq.dataverse.datafile;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.validation.ConstraintViolation;
import javax.validation.ValidationException;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

import edu.harvard.iq.dataverse.DataFileServiceBean;
import edu.harvard.iq.dataverse.DataverseRequestServiceBean;
import edu.harvard.iq.dataverse.EjbDataverseEngine;
import edu.harvard.iq.dataverse.authorization.AuthenticationServiceBean;
import edu.harvard.iq.dataverse.dataaccess.DataAccess;
import edu.harvard.iq.dataverse.dataaccess.StorageIO;
import edu.harvard.iq.dataverse.datacapturemodule.DataCaptureModuleUtil;
import edu.harvard.iq.dataverse.datacapturemodule.ScriptRequestResponse;
import edu.harvard.iq.dataverse.datafile.pojo.RsyncInfo;
import edu.harvard.iq.dataverse.engine.command.exception.UpdateDatasetException;
import edu.harvard.iq.dataverse.engine.command.impl.PersistProvFreeFormCommand;
import edu.harvard.iq.dataverse.engine.command.impl.RequestRsyncScriptCommand;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDatasetVersionCommand;
import edu.harvard.iq.dataverse.mail.EmailContent;
import edu.harvard.iq.dataverse.mail.MailService;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import io.vavr.control.Option;
import io.vavr.control.Try;

@Stateless
public class FileService {

    private static final Logger logger = LoggerFactory.getLogger(FileService.class);

    private static final int CHUNK_SIZE = 2048;
    private static final byte[] INSTREAM = "zINSTREAM\0".getBytes();

    private DataverseRequestServiceBean dvRequestService;
    private EjbDataverseEngine commandEngine;
    private DataFileServiceBean dataFileService;
    private SettingsServiceBean settingsService;
    private AuthenticationServiceBean authSvc;
    private MailService mailService;



    // -------------------- CONSTRUCTORS --------------------

    @Deprecated
    public FileService() {
    }

    @Inject
    public FileService(DataverseRequestServiceBean dvRequestService, EjbDataverseEngine commandEngine, 
                       DataFileServiceBean dataFileServiceBean, SettingsServiceBean settingsService,
                       AuthenticationServiceBean authSvc, MailService mailService) {
        this.dvRequestService = dvRequestService;
        this.commandEngine = commandEngine;
        this.dataFileService = dataFileServiceBean;
        this.settingsService = settingsService;
        this.authSvc = authSvc;
        this.mailService = mailService;
    }

    // -------------------- LOGIC --------------------

    public Set<Dataset> deleteFiles(Collection<FileMetadata> filesToDelete) {

        return filesToDelete.stream()
                .map(this::deleteFile)
                .collect(Collectors.toSet());
    }

    /**
     * If the dataset is realised it creates it's draft version, and then it deletes the file from newly created datasetdraft.
     * Otherwise it deletes the file from current dataset and the actual storage.
     */
    public Dataset deleteFile(FileMetadata fileToDelete) {
        Dataset datasetFileOwner = fileToDelete.getDataFile().getOwner();

        Set<ConstraintViolation> constraintViolations = fileToDelete.getDatasetVersion().validate();

        if (!constraintViolations.isEmpty()) {
            constraintViolations.forEach(constraintViolation -> logger.warn(constraintViolation.getMessage()));
            throw new ValidationException("There was validation error during deletion attempt with the dataFile id: " + fileToDelete.getDataFile().getId());

        }

        if (isFileAThumbnail(fileToDelete, datasetFileOwner)) {
            datasetFileOwner.setThumbnailFile(null);
        }

        Dataset updatedDataset = updateDatasetVersion(Lists.newArrayList(fileToDelete.getDataFile()), datasetFileOwner);

        if (!fileToDelete.getDataFile().isReleased()) {
            deleteFilePhysically(fileToDelete.getDataFile());
        }

        return updatedDataset;
    }

    /**
     * Persists the provenance file and then updates the dataset.
     */
    public Dataset saveProvenanceFileWithDesc(FileMetadata editedFile, DataFile uploadedProvFile, String provenanceDesciption) {
        Dataset datasetFileOwner = editedFile.getDataFile().getOwner();

        editedFile.getDataFile().setProvEntityName(uploadedProvFile.getProvEntityName()); //passing this value into the file being saved here is pretty hacky.

        Try.of(() -> commandEngine.submit(new PersistProvFreeFormCommand(dvRequestService.getDataverseRequest(),
                                                                                                    editedFile.getDataFile(),
                                                                                                    provenanceDesciption)))
        .getOrElseThrow(throwable -> new RuntimeException("There was a problem with persisting provenance file", throwable));

        Set<ConstraintViolation> constraintViolations = editedFile.getDatasetVersion().validate();

        if (!constraintViolations.isEmpty()) {
            constraintViolations.forEach(constraintViolation -> logger.warn(constraintViolation.getMessage()));
            throw new ValidationException("There was validation error during deletion attempt with the dataFile id: " + editedFile.getDataFile().getId());
        }

        updateDatasetVersion(new ArrayList<>(), datasetFileOwner);

        return datasetFileOwner;
    }

    public Option<RsyncInfo> retrieveRsyncScript(Dataset dataset, DatasetVersion workingVersion) {
        ScriptRequestResponse scriptRequestResponse = commandEngine.submit(new RequestRsyncScriptCommand(dvRequestService.getDataverseRequest(), dataset));

        if (StringUtils.isNotEmpty(scriptRequestResponse.getScript())) {
            return Option.of(new RsyncInfo(scriptRequestResponse.getScript(), DataCaptureModuleUtil.getScriptName(workingVersion)));
        }

        return Option.none();
    }

    // -------------------- PRIVATE --------------------

    private Dataset updateDatasetVersion(List<DataFile> filesToDelete, Dataset datasetFileOwner) {
        return Try.of(() -> commandEngine.submit(new UpdateDatasetVersionCommand(datasetFileOwner, dvRequestService.getDataverseRequest(),
                                                                                 filesToDelete)))
                .getOrElseThrow(throwable -> new UpdateDatasetException("Dataset Update failed with dataset id: " + datasetFileOwner.getId(), throwable));
    }

    private boolean isFileAThumbnail(FileMetadata thumbnailFile, Dataset datasetFileOwner) {
        return thumbnailFile.getDataFile().equals(datasetFileOwner.getThumbnailFile());
    }

    private void deleteFilePhysically(DataFile fileToDelete) {
        String fileStorageLocation = dataFileService.getPhysicalFileToDelete(fileToDelete);

        if (fileStorageLocation != null) {
            Try.run(() -> dataFileService.finalizeFileDelete(fileToDelete.getId(), fileStorageLocation, new DataAccess()))
                    .onFailure(throwable -> logger.warn("Failed to delete the physical file associated with the deleted datafile id="
                                                                   + fileToDelete.getId() + ", storage location: " + fileStorageLocation));
        } else {
            throw new IllegalStateException("DataFile with id: " + fileToDelete.getId() + " doesn't have storage location");
        }
    }
    
    public String scan(InputStream fileInput) throws IOException {
        Socket socket = new Socket();

        socket.connect(new InetSocketAddress(settingsService.getValueForKey(SettingsServiceBean.Key.AntivirusScannerSocketAddress),
                                             settingsService.getValueForKeyAsInt(SettingsServiceBean.Key.AntivirusScannerSocketPort)));

        socket.setSoTimeout(settingsService.getValueForKeyAsInt(SettingsServiceBean.Key.AntivirusScannerSocketTimeout));

        DataOutputStream dos = null;
        try {

            dos = new DataOutputStream(socket.getOutputStream());
            dos.write(INSTREAM);
 
            int read = CHUNK_SIZE;
            byte[] buffer = new byte[CHUNK_SIZE];
            while (read == CHUNK_SIZE) {
                    read = fileInput.read(buffer);
 
                if (read > 0) {
                        dos.writeInt(read);
                        dos.write(buffer, 0, read);
                }
            }

            dos.writeInt(0);
            dos.flush();
 
            read = socket.getInputStream().read(buffer);
            return new String(buffer, 0, read);

        } finally {
            if (dos != null) {
                dos.close();
            }
            socket.close();
        }

    }

    public String checkFilesIntegrity() {
        List<DataFile> suspicious = new ArrayList<DataFile>();
        List<DataFile> dataFiles = dataFileService.findAll();

        if (dataFiles.isEmpty()) {
            return "No datafiles found - check database.";
        } else {

            int filesCount = dataFiles.size();
            String message = "Found " + filesCount + " files in repository.";
            for (DataFile dataFile:dataFiles) {
                try {
                    StorageIO<DataFile> storageIO = DataAccess.dataAccess().getStorageIO(dataFile);
                    if (!storageIO.exists() && storageIO.getSize() == dataFile.getFilesize()) {
                        suspicious.add(dataFile);
                    }
                } catch (IOException e) {
                    logger.info(e.getMessage());
                }
            
            }
            
            StringBuffer messageBodyBuffer = new StringBuffer();
            messageBodyBuffer.append("Datafiles integrity check summary: \n");
            messageBodyBuffer.append("Files checked: " + filesCount + "\n");
            messageBodyBuffer.append("Number of files with failures: " + suspicious.size() + "\n\n");
            messageBodyBuffer.append("List of files with failures:\n");
            suspicious.stream().forEach(datafile -> messageBodyBuffer.append("File id: " + datafile.getId() + ", file label: " + datafile.getLatestFileMetadata().getLabel() + "\n"));
            
            String messageSubject = "Dataverse files integrity check report";
            
            authSvc.findAllAuthenticatedUsers().
            stream().
            filter(user -> user.isSuperuser()).
            forEach(user -> mailService.sendMailAsync(user.getEmail(), new EmailContent(messageSubject, messageBodyBuffer.toString(), "")));

            
            message += " Found " + suspicious.size() + " suspicious files.";
            return message;
        }

    }
}
