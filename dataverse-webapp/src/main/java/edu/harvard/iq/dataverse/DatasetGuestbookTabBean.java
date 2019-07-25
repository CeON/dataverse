package edu.harvard.iq.dataverse;

import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;

@ViewScoped
@Named("DatasetGuestbookTab")
public class DatasetGuestbookTabBean implements Serializable {

    @Inject
    PermissionsWrapper permissionsWrapper;

    private Guestbook guestbook;
    private boolean canIssueUpdateDatasetCommand;
    private int dataverseGuestbooksCount;
    private String ownerName;

    public void init(Dataset dataset) {
        guestbook = dataset.getGuestbook();
        canIssueUpdateDatasetCommand = permissionsWrapper.canIssueUpdateDatasetCommand(dataset);
        dataverseGuestbooksCount = dataset.getDataverseContext().getAvailableGuestbooks().size();
        ownerName = dataset.getOwner().getName();
    }

    // -------------------- GETTERS --------------------

    public Guestbook getGuestbook() {
        return guestbook;
    }

    public boolean isCanIssueUpdateDatasetCommand() {
        return canIssueUpdateDatasetCommand;
    }

    public int getDataverseGuestbooksCount() {
        return dataverseGuestbooksCount;
    }

    public String getOwnerName() {
        return ownerName;
    }
}
