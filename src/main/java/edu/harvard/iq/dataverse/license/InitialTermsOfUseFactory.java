package edu.harvard.iq.dataverse.license;

import javax.ejb.Stateless;
import javax.inject.Inject;


/**
 * Factory of {@link TermsOfUse} objects.
 * 
 * @author madryk
 */
@Stateless
public class InitialTermsOfUseFactory {

    @Inject
    private LicenseDAO licenseDao;
    
    
    // -------------------- LOGIC --------------------
    
    /**
     * Returns new instance of license based {@link TermsOfUse}
     * with license set to first active one.
     */
    public TermsOfUse createTermsOfUse() {
        
        License defaultLicense = licenseDao.findFirstActive();
        
        TermsOfUse termsOfUse = new TermsOfUse();
        termsOfUse.setLicense(defaultLicense);
        
        return termsOfUse;
    }
}
