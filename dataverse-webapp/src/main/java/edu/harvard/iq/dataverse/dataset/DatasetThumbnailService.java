package edu.harvard.iq.dataverse.dataset;

import com.google.common.base.Preconditions;
import edu.harvard.iq.dataverse.DataFile;
import edu.harvard.iq.dataverse.Dataset;
import edu.harvard.iq.dataverse.DatasetServiceBean;
import edu.harvard.iq.dataverse.DatasetVersion;
import edu.harvard.iq.dataverse.FileMetadata;
import edu.harvard.iq.dataverse.dataaccess.DataAccess;
import edu.harvard.iq.dataverse.dataaccess.StorageIO;
import edu.harvard.iq.dataverse.dataaccess.StorageIOUtils;
import edu.harvard.iq.dataverse.datafile.DataFileThumbnailService;
import edu.harvard.iq.dataverse.license.FileTermsOfUse.TermsOfUseType;
import edu.harvard.iq.dataverse.qualifiers.ProductionBean;
import edu.harvard.iq.dataverse.thumbnail.InputStreamWrapper;
import edu.harvard.iq.dataverse.thumbnail.Thumbnail;
import edu.harvard.iq.dataverse.thumbnail.ThumbnailGeneratorManager;
import edu.harvard.iq.dataverse.thumbnail.ThumbnailUtil;
import edu.harvard.iq.dataverse.thumbnail.Thumbnail.ThumbnailSize;
import edu.harvard.iq.dataverse.util.FileUtil;
import edu.harvard.iq.dataverse.util.SystemConfig;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import javax.ejb.Stateless;
import javax.inject.Inject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

@Stateless
public class DatasetThumbnailService {
    private static final Logger logger = Logger.getLogger(DatasetThumbnailService.class.getCanonicalName());
    public static String datasetLogoFilenameFinal = "dataset_logo_original";
    public static String datasetLogoThumbnail = "dataset_logo";
    public static String thumb48addedByImageThumbConverter = ".thumb48";
    
    
    @Inject @ProductionBean
    private DatasetServiceBean datasetServiceBean;
    
    @Inject
    private DataFileThumbnailService dataFileThumbnailService;
    
    @Inject
    private ThumbnailGeneratorManager thumbnailGeneratorManager;
    
    @Inject
    private SystemConfig systemConfig;
    
    private DataAccess dataAccess = new DataAccess();
    
    // -------------------- LOGIC --------------------
    
    public Dataset setNonDatasetFileAsThumbnail(Dataset dataset, File logoFile) {
        Preconditions.checkNotNull(dataset);
        Preconditions.checkNotNull(logoFile);
        Preconditions.checkArgument(logoFile.exists());
        Preconditions.checkArgument(logoFile.length() <= systemConfig.getUploadLogoSizeLimit());
        
        dataset = persistDatasetLogoToStorageAndCreateThumbnail(dataset, logoFile);
        dataset.setThumbnailFile(null);
        dataset.setUseGenericThumbnail(false);
        
        return datasetServiceBean.merge(dataset);
    }
    
    public Dataset setDatasetFileAsThumbnail(Dataset dataset, DataFile datasetFileThumbnailToSwitchTo) {
        Preconditions.checkNotNull(dataset);
        Preconditions.checkNotNull(datasetFileThumbnailToSwitchTo);
        
        deleteDatasetLogo(dataset);
        dataset.setThumbnailFile(datasetFileThumbnailToSwitchTo);
        dataset.setUseGenericThumbnail(false);
        
        return datasetServiceBean.merge(dataset);
    }
    
    public Dataset removeDatasetThumbnail(Dataset dataset) {
        Preconditions.checkNotNull(dataset);
        
        deleteDatasetLogo(dataset);
        dataset.setThumbnailFile(null);
        dataset.setUseGenericThumbnail(true);
        
        return datasetServiceBean.merge(dataset);
    }
    
    
    
    public List<DatasetThumbnail> getThumbnailCandidates(Dataset dataset, boolean considerDatasetLogoAsCandidate) {
        List<DatasetThumbnail> thumbnails = new ArrayList<>();
        if (dataset == null) {
            return thumbnails;
        }
        if (considerDatasetLogoAsCandidate) {
            getThumbnailFromStorage(dataset)
                .ifPresent(thumbnails::add);
        }
        
        List<DataFile> dataFileCandidates = filterToFilesAssignableAsThumbnails(dataset.getLatestVersion().getFileMetadatas());
        
        for (DataFile dataFile: dataFileCandidates) {
            Thumbnail thumbnail = dataFileThumbnailService.getThumbnail(dataFile, ThumbnailSize.CARD);
            String imageSourceBase64 = ThumbnailUtil.thumbnailAsBase64(thumbnail);
            
            DatasetThumbnail datasetThumbnail = new DatasetThumbnail(imageSourceBase64, dataFile, dataFile.getDisplayName());
            thumbnails.add(datasetThumbnail);
        }
        return thumbnails;
    }
    
    public Optional<DatasetThumbnail> getThumbnailBase64(Dataset dataset) {
        Preconditions.checkNotNull(dataset);
        
        if (dataset.isUseGenericThumbnail()) {
            logger.fine("Dataset (id :" + dataset.getId() + ") does not have a thumbnail and is 'Use Generic'.");
            return Optional.empty();
        }

        DataFile thumbnailFile = dataset.getThumbnailFile();
        
        if (thumbnailFile != null) {
            Thumbnail thumbnail = dataFileThumbnailService.getThumbnail(thumbnailFile, ThumbnailSize.CARD);
            String imageSourceBase64 = ThumbnailUtil.thumbnailAsBase64(thumbnail);
            DatasetThumbnail userSpecifiedDatasetThumbnail = new DatasetThumbnail(imageSourceBase64, thumbnailFile,
                    thumbnailFile.getLatestFileMetadata() == null ? StringUtils.EMPTY : thumbnailFile.getDisplayName());
            logger.fine("Dataset (id :" + dataset.getId() + ")  will get thumbnail the user specified from DataFile id " + thumbnailFile.getId());
            return Optional.of(userSpecifiedDatasetThumbnail);
        }
        
        return getThumbnailFromStorage(dataset);

    }
    
    public Dataset autoSelectThumbnailFromDataFiles(Dataset dataset, DatasetVersion datasetVersion) {
        if (dataset == null) {
            return null;
        }

        if (dataset.isUseGenericThumbnail()) {
            logger.fine("Bypassing logic to find a thumbnail because a generic icon for the dataset is desired.");
            return null;
        }

        if (datasetVersion == null) {
            logger.fine("getting a published version of the dataset");
            // We want to use published files only when automatically selecting 
            // dataset thumbnails.
            datasetVersion = dataset.getReleasedVersion();
        }

        // No published version? - No [auto-selected] thumbnail for you.
        if (datasetVersion == null) {
            return null;
        }

        List<DataFile> dataFileCandidates = filterToFilesAssignableAsThumbnails(dataset.getLatestVersion().getFileMetadatas());
        
        if (!dataFileCandidates.isEmpty()) {
            return setDatasetFileAsThumbnail(dataset, dataFileCandidates.get(0));
        }
        logger.fine("In attemptToAutomaticallySelectThumbnailFromDataFiles and interated through all the files but couldn't find a thumbnail.");
        return dataset;
    }
    
    
    
    public boolean isDatasetLogoPresent(Dataset dataset) {
        if (dataset == null) {
            return false;
        }

        StorageIO<Dataset> storageIO = null;

        try {
            storageIO = dataAccess.getStorageIO(dataset);
            return storageIO.isAuxObjectCached(datasetLogoThumbnail + thumb48addedByImageThumbConverter);
        } catch (IOException ioex) {
            throw new RuntimeException("Error while checking if dataset logo is present", ioex);
        }
    }
    
    
    // -------------------- PRIVATE --------------------
    
    private Dataset persistDatasetLogoToStorageAndCreateThumbnail(Dataset dataset, File logoFile) {
        
        saveLogoInStorage(dataset, logoFile);
        
        Thumbnail thumbnail = generateThumbnailFromLogo(logoFile);
        
        saveThumbnailInStorage(dataset, thumbnail);

        return dataset;
    }
    
    
    private boolean deleteDatasetLogo(Dataset dataset) {
        if (dataset == null) {
            return false;
        }
        try {
            StorageIO<Dataset> storageIO = dataAccess.getStorageIO(dataset);

            storageIO.deleteAuxObject(datasetLogoFilenameFinal);
            storageIO.deleteAuxObject(datasetLogoThumbnail + thumb48addedByImageThumbConverter);

        } catch (IOException ex) {
            logger.info("Failed to delete dataset logo: " + ex.getMessage());
            return false;
        }
        return true;
    }
    
    
    
    private List<DataFile> filterToFilesAssignableAsThumbnails(List<FileMetadata> filesMetadata) {
        List<DataFile> assignableDataFiles = new ArrayList<DataFile>();
        
        for (FileMetadata fmd: filesMetadata) {
            DataFile testFile = fmd.getDataFile();
            if (fmd.getTermsOfUse().getTermsOfUseType() == TermsOfUseType.RESTRICTED) {
                continue;
            }
            if (!dataFileThumbnailService.isThumbnailAvailable(testFile)) {
                continue;
            }
            
            assignableDataFiles.add(testFile);
        }
        
        return assignableDataFiles;
    }
    
    
    private Optional<DatasetThumbnail> getThumbnailFromStorage(Dataset dataset) {
        
        StorageIO<Dataset> storageIO = null;
        
        try {
            storageIO = dataAccess.getStorageIO(dataset);
        } catch (IOException e) {
            throw new RuntimeException("getThumbnail(): Failed to initialize dataset StorageIO for " + dataset.getStorageIdentifier(), e);
        }
        
        try (InputStream in = storageIO.getAuxFileAsInputStream(datasetLogoThumbnail + thumb48addedByImageThumbConverter)) {
            if (in == null) {
                throw new IOException("storageIO.getAuxFileAsInputStream() returned null");
            }
            byte[] bytes = IOUtils.toByteArray(in);
            String base64image = Base64.getEncoder().encodeToString(bytes);
            
            DatasetThumbnail datasetThumbnail = new DatasetThumbnail(FileUtil.DATA_URI_SCHEME + base64image);
            return Optional.of(datasetThumbnail);
            
        } catch (IOException ioex) {
            logger.fine("getThumbnailFromStorage(): Dataset-level thumbnail file does not exist, or failed to open for " + dataset.getStorageIdentifier() + " (" +ioex.getMessage()+ ")");
            logger.fine("assuming that it does not exist");
            return Optional.empty();
        }
    }
    
    private void saveLogoInStorage(Dataset dataset, File logoFile) {
        
        StorageIO<Dataset> storageIO = null;

        try {
            storageIO = dataAccess.createNewStorageIO(dataset, "placeholder");
        } catch (IOException ioex) {
            throw new RuntimeException("Failed to save the file, storage id " + dataset.getStorageIdentifier(), ioex);
        }

        try {
            //this goes through Swift API/local storage/s3 to write the dataset thumbnail into a container
            storageIO.savePathAsAux(logoFile.toPath(), datasetLogoFilenameFinal);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to move original file from " + logoFile.getAbsolutePath() + " to its DataAccess location", ex);
        }
        
    }
    
    private void saveThumbnailInStorage(Dataset dataset, Thumbnail thumbnail) {
        try {
            StorageIO<Dataset> storageIO = dataAccess.getStorageIO(dataset);
            StorageIOUtils.saveBytesAsAuxFile(storageIO, thumbnail.getData(), datasetLogoThumbnail + thumb48addedByImageThumbConverter);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to save thumbnail in storage", ex);
        }
    }
    
    private Thumbnail generateThumbnailFromLogo(File logoFile) {
        try (InputStream newInputStream = new FileInputStream(logoFile)) {
            
            return thumbnailGeneratorManager.generateThumbnail(new InputStreamWrapper(newInputStream, logoFile.length(), "image/png"), ThumbnailSize.CARD);
        } catch (IOException e) {
            throw new RuntimeException("Error generating thumbnail", e);
        }
    }
    
}
