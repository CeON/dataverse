package edu.harvard.iq.dataverse.license;

import edu.harvard.iq.dataverse.persistence.datafile.license.FileTermsOfUse;
import edu.harvard.iq.dataverse.persistence.datafile.license.LicenseRepository;
import edu.harvard.iq.dataverse.persistence.datafile.license.TermsOfUseForm;
import org.omnifaces.cdi.ViewScoped;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.faces.model.SelectItem;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.util.List;


@ViewScoped
@Named("editTermsOfUseDialog")
public class EditTermsOfUseDialog implements Serializable {

    @EJB
    private LicenseRepository licenseRepository;

    @EJB
    private TermsOfUseFactory termsOfUseFactory;

    @EJB
    private TermsOfUseFormMapper termsOfUseFormMapper;

    @Inject
    private TermsOfUseSelectItemsFactory termsOfUseSelectItemsFactory;


    private TermsOfUseForm termsOfUseForm;

    private List<SelectItem> termsOfUseSelectItems;


    @PostConstruct
    public void postConstruct() {
        FileTermsOfUse termsOfUse = termsOfUseFactory.createTermsOfUse();
        termsOfUseForm = termsOfUseFormMapper.mapToForm(termsOfUse);

        termsOfUseSelectItems = termsOfUseSelectItemsFactory.buildLicenseSelectItems();
    }


    // -------------------- GETTERS --------------------

    public TermsOfUseForm getTermsOfUseForm() {
        return termsOfUseForm;
    }


    public List<SelectItem> getTermsOfUseSelectItems() {
        return termsOfUseSelectItems;
    }

}
