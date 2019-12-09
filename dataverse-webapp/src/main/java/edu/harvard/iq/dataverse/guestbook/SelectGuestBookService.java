package edu.harvard.iq.dataverse.guestbook;

import edu.harvard.iq.dataverse.dataset.datasetversion.DatasetVersionServiceBean;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.guestbook.Guestbook;
import io.vavr.control.Option;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.Date;

@Stateless
public class SelectGuestBookService {

    private DatasetVersionServiceBean versionService;

    // -------------------- CONSTRUCTORS --------------------

    @Deprecated
    public SelectGuestBookService() {
    }

    @Inject
    public SelectGuestBookService(DatasetVersionServiceBean versionService) {
        this.versionService = versionService;
    }

    // -------------------- LOGIC --------------------

    public Dataset saveGuestbookChanges(DatasetVersion editedDataset,
                        Option<Guestbook> selectedGuestbook,
                        Option<Guestbook> guestbookBeforeChanges) {

        if (isGuestbookAddedOrRemoved(selectedGuestbook, guestbookBeforeChanges)){
            Dataset dataset = editedDataset.getDataset();
            dataset.setGuestbookChangeTime(new Date());
        }

        Dataset dataset = editedDataset.getDataset();
        dataset.setGuestbook(selectedGuestbook.get());
        return versionService.updateDatasetVersion(editedDataset, true);
    }

    // -------------------- PRIVATE --------------------

    private boolean isGuestbookAddedOrRemoved(Option<Guestbook> selectedGuestbook, Option<Guestbook> guestbookBeforeChanges) {
        return (guestbookBeforeChanges.isEmpty() && selectedGuestbook.isDefined()) ||
                (guestbookBeforeChanges.isDefined() && selectedGuestbook.isEmpty());
    }
}
