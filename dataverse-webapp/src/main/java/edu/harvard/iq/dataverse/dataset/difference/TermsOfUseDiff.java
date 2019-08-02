package edu.harvard.iq.dataverse.dataset.difference;

import edu.harvard.iq.dataverse.DatasetVersion;
import edu.harvard.iq.dataverse.license.FileTermsOfUse;

/**
 * Class that contains old and new value of {@link FileTermsOfUse}
 * that is different in file between two {@link DatasetVersion}s
 * 
 * @author madryk
 */
public class TermsOfUseDiff extends ItemDiff<FileTermsOfUse> {

    // -------------------- CONSTRUCTORS --------------------
    
    public TermsOfUseDiff(FileTermsOfUse oldValue, FileTermsOfUse newValue) {
        super(oldValue, newValue);
    }
    
}