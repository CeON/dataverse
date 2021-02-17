package edu.harvard.iq.dataverse.dataset;

import edu.harvard.iq.dataverse.dataaccess.DataAccess;
import edu.harvard.iq.dataverse.dataaccess.ImageThumbConverter;
import edu.harvard.iq.dataverse.dataaccess.StorageIO;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.persistence.datafile.license.FileTermsOfUse.TermsOfUseType;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.util.FileUtil;
import org.apache.commons.io.IOUtils;

import javax.ejb.Stateless;
import javax.imageio.ImageIO;
import javax.inject.Inject;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.logging.Logger;

@Stateless
public class DatasetThumbnailService {

    private static final Logger logger = Logger.getLogger(DatasetThumbnailService.class.getCanonicalName());
    public static String datasetLogoFilenameFinal = "dataset_logo_original";
    public static String datasetLogoThumbnail = "dataset_logo";
    public static String thumb48addedByImageThumbConverter = ".thumb48";
    
    @Inject
    private ImageThumbConverter imageThumbConverter;

    private DataAccess dataAccess = DataAccess.dataAccess();

    
    
    public List<DatasetThumbnail> getThumbnailCandidates(Dataset dataset,
                                                                boolean considerDatasetLogoAsCandidate) {
        List<DatasetThumbnail> thumbnails = new ArrayList<>();
        if (dataset == null) {
            return thumbnails;
        }
        if (considerDatasetLogoAsCandidate) {

            InputStream in = null;
            try {
                StorageIO<Dataset> storageIO = dataAccess.getStorageIO(dataset);
                in = storageIO.getAuxFileAsInputStream(datasetLogoThumbnail + thumb48addedByImageThumbConverter);
            } catch (Exception ioex) {
            }

            if (in != null) {
                logger.fine("Thumbnail created from dataset logo exists!");
                try {
                    byte[] bytes = IOUtils.toByteArray(in);
                    String base64image = Base64.getEncoder().encodeToString(bytes);
                    DatasetThumbnail datasetThumbnail = new DatasetThumbnail(FileUtil.DATA_URI_SCHEME + base64image, null);
                    thumbnails.add(datasetThumbnail);
                } catch (IOException ex) {
                    logger.warning("Unable to rescale image: " + ex);
                }
            } else {
                logger.fine("There is no thumbnail created from a dataset logo");
            }
            IOUtils.closeQuietly(in);
        }
        for (FileMetadata fileMetadata : dataset.getLatestVersion().getFileMetadatas()) {
            DataFile dataFile = fileMetadata.getDataFile();

            if (dataFile != null && imageThumbConverter.isThumbnailAvailable(dataFile)
                    && fileMetadata.getTermsOfUse().getTermsOfUseType() != TermsOfUseType.RESTRICTED) {
                String imageSourceBase64 = null;
                imageSourceBase64 = imageThumbConverter.getImageThumbnailAsBase64(dataFile, ImageThumbConverter.DEFAULT_CARDIMAGE_SIZE);

                if (imageSourceBase64 != null) {
                    DatasetThumbnail datasetThumbnail = new DatasetThumbnail(imageSourceBase64, dataFile);
                    thumbnails.add(datasetThumbnail);
                }
            }
        }
        return thumbnails;
    }

    /**
     * Note "datasetVersionId" can be null. If needed, it helps the "efficiency"
     * of "attemptToAutomaticallySelectThumbnailFromDataFiles"
     *
     * @param dataset
     * @param datasetVersion
     * @return
     */
    private DatasetThumbnail getThumbnailInternal(Dataset dataset) {
        if (dataset == null) {
            return null;
        }

        StorageIO<Dataset> storageIO = null;

        try {
            storageIO = dataAccess.getStorageIO(dataset);
        } catch (IOException ioex) {
            logger.warning("getThumbnail(): Failed to initialize dataset StorageIO for " + dataset.getStorageIdentifier() + " (" + ioex.getMessage() + ")");
        }

        InputStream in = null;
        try {
            if (storageIO == null) {
                logger.warning("getThumbnail(): Failed to initialize dataset StorageIO for " + dataset.getStorageIdentifier());
            } else if (storageIO.getAuxFileAsInputStream(datasetLogoThumbnail + thumb48addedByImageThumbConverter) != null) {
                in = storageIO.getAuxFileAsInputStream(datasetLogoThumbnail + thumb48addedByImageThumbConverter);
            }
        } catch (IOException ex) {
            logger.fine("Dataset-level thumbnail file does not exist, or failed to open; will try to find an image file that can be used as the thumbnail.");
        }


        if (in != null) {
            try {
                byte[] bytes = IOUtils.toByteArray(in);
                String base64image = Base64.getEncoder().encodeToString(bytes);
                DatasetThumbnail datasetThumbnail = new DatasetThumbnail(FileUtil.DATA_URI_SCHEME + base64image, null);
                logger.fine("will get thumbnail from dataset logo");
                return datasetThumbnail;
            } catch (IOException ex) {
                logger.fine("Unable to read thumbnail image from file: " + ex);
                return null;
            } finally {
                IOUtils.closeQuietly(in);
            }
        } else {
            DataFile thumbnailFile = dataset.getThumbnailFile();

            if (thumbnailFile == null) {
                if (dataset.isUseGenericThumbnail()) {
                    logger.fine("Dataset (id :" + dataset.getId() + ") does not have a thumbnail and is 'Use Generic'.");
                    return null;
                } else {
                    thumbnailFile = attemptToAutomaticallySelectThumbnailFromDataFiles(dataset);
                    if (thumbnailFile == null) {
                        logger.fine("Dataset (id :" + dataset.getId() + ") does not have a thumbnail available that could be selected automatically.");
                        return null;
                    } else {
                        String imageSourceBase64 = imageThumbConverter.getImageThumbnailAsBase64(thumbnailFile, ImageThumbConverter.DEFAULT_CARDIMAGE_SIZE);
                        DatasetThumbnail defaultDatasetThumbnail = new DatasetThumbnail(imageSourceBase64, thumbnailFile);
                        logger.fine("thumbnailFile (id :" + thumbnailFile.getId() + ") will get thumbnail through automatic selection from DataFile id " + thumbnailFile.getId());
                        return defaultDatasetThumbnail;
                    }
                }
            } else if (thumbnailFile.getFileMetadata().getTermsOfUse().getTermsOfUseType() == TermsOfUseType.RESTRICTED) {
                logger.fine("Dataset (id :" + dataset.getId() + ") has a thumbnail the user selected but the file must have later been restricted. Returning null.");
                return null;
            } else {
                String imageSourceBase64 = imageThumbConverter.getImageThumbnailAsBase64(thumbnailFile, ImageThumbConverter.DEFAULT_CARDIMAGE_SIZE);
                DatasetThumbnail userSpecifiedDatasetThumbnail = new DatasetThumbnail(imageSourceBase64, thumbnailFile);
                logger.fine("Dataset (id :" + dataset.getId() + ")  will get thumbnail the user specified from DataFile id " + thumbnailFile.getId());
                return userSpecifiedDatasetThumbnail;

            }
        }
    }

    public DatasetThumbnail getThumbnail(Dataset dataset) {
        if (dataset == null) {
            return null;
        }
        return getThumbnailInternal(dataset);
    }

    public boolean deleteDatasetLogo(Dataset dataset) {
        if (dataset == null) {
            return false;
        }
        try {
            StorageIO<Dataset> storageIO = dataAccess.getStorageIO(dataset);

            if (storageIO == null) {
                logger.warning("Null storageIO in deleteDatasetLogo()");
                return false;
            }

            storageIO.deleteAuxObject(datasetLogoFilenameFinal);
            storageIO.deleteAuxObject(datasetLogoThumbnail + thumb48addedByImageThumbConverter);

        } catch (IOException ex) {
            logger.info("Failed to delete dataset logo: " + ex.getMessage());
            return false;
        }
        return true;

        //TODO: Is this required? 
//        File originalFile = new File(dataset.getFileSystemDirectory().toString(), datasetLogoFilenameFinal);
//        boolean originalFileDeleted = originalFile.delete();
//        File thumb48 = new File(dataset.getFileSystemDirectory().toString(), File.separator + datasetLogoThumbnail + thumb48addedByImageThumbConverter);
//        boolean thumb48Deleted = thumb48.delete();
//        if (originalFileDeleted && thumb48Deleted) {
//            return true;
//        } else {
//            logger.info("One of the files wasn't deleted. Original deleted: " + originalFileDeleted + ". thumb48 deleted: " + thumb48Deleted + ".");
//            return false;
//        }
    }

    /**
     * Pass an optional datasetVersion in case the file system is checked
     *
     * @param dataset
     * @param datasetVersion
     * @return
     */
    private DataFile attemptToAutomaticallySelectThumbnailFromDataFiles(Dataset dataset) {
        if (dataset == null) {
            return null;
        }

        if (dataset.isUseGenericThumbnail()) {
            logger.fine("Bypassing logic to find a thumbnail because a generic icon for the dataset is desired.");
            return null;
        }

        DatasetVersion datasetVersion = dataset.getReleasedVersion();

        // No published version? - No [auto-selected] thumbnail for you.
        if (datasetVersion == null) {
            return null;
        }

        for (FileMetadata fmd : datasetVersion.getFileMetadatas()) {
            DataFile testFile = fmd.getDataFile();
            // We don't want to use a restricted image file as the dedicated thumbnail:
            if (fmd.getTermsOfUse().getTermsOfUseType() != TermsOfUseType.RESTRICTED && 
                    imageThumbConverter.isThumbnailAvailable(testFile, ImageThumbConverter.DEFAULT_CARDIMAGE_SIZE)) {
                return testFile;
            }
        }
        logger.fine("In attemptToAutomaticallySelectThumbnailFromDataFiles and interated through all the files but couldn't find a thumbnail.");
        return null;
    }

    public Dataset persistDatasetLogoToStorageAndCreateThumbnail(Dataset dataset, InputStream inputStream) {
        if (dataset == null) {
            return null;
        }
        File tmpFile = null;
        try {
            tmpFile = FileUtil.inputStreamToFile(inputStream);
        } catch (IOException ex) {
            logger.severe(ex.getMessage());
        }

        StorageIO<Dataset> storageIO = null;

        try {
            storageIO = dataAccess.createNewStorageIO(dataset, "placeholder");
        } catch (IOException ioex) {
            //TODO: Add a suitable waing message
            logger.warning("Failed to save the file, storage id " + dataset.getStorageIdentifier() + " (" + ioex.getMessage() + ")");
        }

        //File originalFile = new File(datasetDirectory.toString(), datasetLogoFilenameFinal);
        try {
            //this goes through Swift API/local storage/s3 to write the dataset thumbnail into a container
            storageIO.savePathAsAux(tmpFile.toPath(), datasetLogoFilenameFinal);
        } catch (IOException ex) {
            logger.severe("Failed to move original file from " + tmpFile.getAbsolutePath() + " to its DataAccess location" + ": " + ex);
        }

        BufferedImage fullSizeImage = null;
        try {
            fullSizeImage = ImageIO.read(tmpFile);
        } catch (IOException ex) {
            logger.severe(ex.getMessage());
            return null;
        }
        if (fullSizeImage == null) {
            logger.fine("fullSizeImage was null!");
            return null;
        }
        int width = fullSizeImage.getWidth();
        int height = fullSizeImage.getHeight();
        FileChannel src = null;
        try {
            src = new FileInputStream(tmpFile).getChannel();
        } catch (FileNotFoundException ex) {
            logger.severe(ex.getMessage());
            return null;
        }
        FileChannel dest = null;
        try {
            dest = new FileOutputStream(tmpFile).getChannel();
        } catch (FileNotFoundException ex) {
            logger.severe(ex.getMessage());
            return null;
        }
        try {
            dest.transferFrom(src, 0, src.size());
        } catch (IOException ex) {
            logger.severe(ex.getMessage());
            return null;
        }
        File tmpFileForResize = null;
        try {
            tmpFileForResize = FileUtil.inputStreamToFile(inputStream);
        } catch (IOException ex) {
            logger.severe(ex.getMessage());
            return null;
        }
        String thumbFileLocation = imageThumbConverter.rescaleImage(fullSizeImage, width, height, ImageThumbConverter.DEFAULT_CARDIMAGE_SIZE, tmpFileForResize.toPath().toString());
        logger.fine("thumbFileLocation = " + thumbFileLocation);
        logger.fine("tmpFileLocation=" + tmpFileForResize.toPath().toString());
        //now we must save the updated thumbnail 
        try {
            storageIO.savePathAsAux(Paths.get(thumbFileLocation), datasetLogoThumbnail + thumb48addedByImageThumbConverter);
        } catch (IOException ex) {
            logger.severe("Failed to move updated thumbnail file from " + tmpFile.getAbsolutePath() + " to its DataAccess location" + ": " + ex);
        }
        //This deletes the tempfiles created for rescaling and encoding
        boolean tmpFileWasDeleted = tmpFile.delete();
        boolean originalTempFileWasDeleted = tmpFileForResize.delete();
        try {
            Files.delete(Paths.get(thumbFileLocation));
        } catch (IOException ioex) {
            logger.fine("Failed to delete temporary thumbnail file");
        }

        logger.fine("Thumbnail saved to " + thumbFileLocation + ". Temporary file deleted : " + tmpFileWasDeleted + ". Original file deleted : " + originalTempFileWasDeleted);
        return dataset;
    }

    public InputStream getThumbnailAsInputStream(Dataset dataset) {
        if (dataset == null) {
            return null;
        }
        DatasetThumbnail datasetThumbnail = getThumbnail(dataset);
        if (datasetThumbnail == null) {
            return null;
        } else {
            String base64Image = datasetThumbnail.getBase64image();
            String leadingStringToRemove = FileUtil.DATA_URI_SCHEME;
            String encodedImg = base64Image.substring(leadingStringToRemove.length());
            byte[] decodedImg = Base64.getDecoder().decode(encodedImg.getBytes(StandardCharsets.UTF_8));
            logger.fine("returning this many bytes for  " + "dataset id: " + dataset.getId() + ", persistentId: " + dataset.getIdentifier() + " :" + decodedImg.length);
            ByteArrayInputStream nonDefaultDatasetThumbnail = new ByteArrayInputStream(decodedImg);
            logger.fine("For dataset id " + dataset.getId() + " a thumbnail was found and is being returned.");
            return nonDefaultDatasetThumbnail;
        }
    }

    /**
     * The dataset logo is the file that a user uploads which is *not* one of
     * the data files. Compare to the datavese logo. We do not save the original
     * file that is uploaded. Rather, we delete it after first creating at least
     * one thumbnail from it.
     */
    public boolean isDatasetLogoPresent(Dataset dataset) {
        if (dataset == null) {
            return false;
        }

        StorageIO<Dataset> storageIO = null;

        try {
            storageIO = dataAccess.getStorageIO(dataset);
            return storageIO.isAuxObjectCached(datasetLogoThumbnail + thumb48addedByImageThumbConverter);
        } catch (IOException ioex) {
        }
        return false;
    }

}
