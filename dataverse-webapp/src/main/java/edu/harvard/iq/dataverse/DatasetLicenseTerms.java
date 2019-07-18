package edu.harvard.iq.dataverse;

import javax.faces.view.ViewScoped;
import javax.inject.Named;

@ViewScoped
@Named("DatasetLicenseTerms")
public class DatasetLicenseTerms {

    private Dataset dataset;
    private Guestbook selectedGuestbook;

    // -------------------- GETTERS --------------------
    public Dataset getDataset() {
        return dataset;
    }

    public Guestbook getSelectedGuestbook() {
        return selectedGuestbook;
    }

    // -------------------- LOGIC --------------------
    public void init(Dataset dataset) {
        if(dataset != null) {
            this.dataset = dataset;
            selectedGuestbook = dataset.getGuestbook();
        }
    }

    public void reset() {
        dataset.setGuestbook(null);
    }

    public void viewSelectedGuestbook(Guestbook selectedGuestbook) {
        this.selectedGuestbook = selectedGuestbook;
    }

    // -------------------- SETTERS --------------------
    public void setDataset(Dataset dataset) {
        this.dataset = dataset;
    }

    public void setSelectedGuestbook(Guestbook selectedGuestbook) {
        this.selectedGuestbook = selectedGuestbook;
    }
}
