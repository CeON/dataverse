package edu.harvard.iq.dataverse;

import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;

@ViewScoped
@Named("GuestbookTab")
public class GuestbookTabBackingBean implements Serializable {

    @Inject
    PermissionsWrapper permissionsWrapper;

    private Guestbook selectedGuestbook;
    private boolean canIssueUpdateDatasetCommand;
    private int dataverseGuestbooksCount;
    private String ownerName;

    public void init(Dataset dataset) {
        selectedGuestbook = dataset.getGuestbook();
        canIssueUpdateDatasetCommand = permissionsWrapper.canIssueUpdateDatasetCommand(dataset);
        dataverseGuestbooksCount = dataset.getDataverseContext().getAvailableGuestbooks().size();
        ownerName = dataset.getOwner().getName();
    }

    public Guestbook getSelectedGuestbook() {
        return selectedGuestbook;
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
