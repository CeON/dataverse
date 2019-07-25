package edu.harvard.iq.dataverse.dataset;

import edu.harvard.iq.dataverse.DataFile;
import edu.harvard.iq.dataverse.thumbnail.InputStreamWrapper;

public interface ThumbnailSourceFileProvider {

    boolean isApplicable(DataFile dataFile);
    
    InputStreamWrapper obtainThumbnailSourceFile(DataFile dataFile);
}
