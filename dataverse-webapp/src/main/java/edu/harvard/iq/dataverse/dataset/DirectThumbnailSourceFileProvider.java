package edu.harvard.iq.dataverse.dataset;

import com.google.common.base.Preconditions;
import edu.harvard.iq.dataverse.DataFile;
import edu.harvard.iq.dataverse.dataaccess.DataAccess;
import edu.harvard.iq.dataverse.dataaccess.StorageIO;
import edu.harvard.iq.dataverse.files.mime.ImageMimeType;
import edu.harvard.iq.dataverse.thumbnail.InputStreamWrapper;

import javax.ejb.Stateless;

import java.io.IOException;

@Stateless
public class DirectThumbnailSourceFileProvider implements ThumbnailSourceFileProvider {

    private DataAccess dataAccess = new DataAccess();
    
    // -------------------- LOGIC --------------------
    
    @Override
    public boolean isApplicable(DataFile dataFile) {
        String contentType = dataFile.getContentType();
        
        // Some browsers (Chrome?) seem to identify FITS files as mime
        // type "image/fits" on upload; this is both incorrect (the official
        // mime type for FITS is "application/fits", and problematic: then
        // the file is identified as an image, and the page will attempt to 
        // generate a preview - which of course is going to fail...
        if (ImageMimeType.FITSIMAGE.getMimeValue().equalsIgnoreCase(contentType)) {
            return false;
        }
        
        return contentType.startsWith("image/") ||
                contentType.equalsIgnoreCase("application/pdf");
    }

    @Override
    public InputStreamWrapper obtainThumbnailSourceFile(DataFile dataFile) {
        Preconditions.checkArgument(isApplicable(dataFile));
        
        StorageIO<DataFile> storageIO = null;
        try {
            storageIO = dataAccess.getStorageIO(dataFile);
            storageIO.open();
            
            return new InputStreamWrapper(storageIO.getInputStream(), dataFile.getFilesize(), dataFile.getContentType());
        } catch (IOException e) {
            throw new RuntimeException("Error while obtaining thumbnail source file", e);
        }
        
    }

}
