package edu.harvard.iq.dataverse.dataset.difference;

import edu.harvard.iq.dataverse.DatasetVersion;
import edu.harvard.iq.dataverse.FileMetadata;

/**
 * Class that contains old and new value of {@link FileMetadata}
 * that is different between two {@link DatasetVersion}s
 * 
 * @author madryk
 */
public class FileMetadataDiff extends ItemDiff<FileMetadata> {

    // -------------------- CONSTRUCTORS -------------------

    public FileMetadataDiff(FileMetadata oldValue, FileMetadata newValue) {
        super(oldValue, newValue);
    }
    
}