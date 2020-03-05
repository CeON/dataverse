package edu.harvard.iq.dataverse.datafile.file;

import com.google.common.collect.Lists;
import edu.harvard.iq.dataverse.DataverseRequestServiceBean;
import edu.harvard.iq.dataverse.EjbDataverseEngine;
import edu.harvard.iq.dataverse.dataset.difference.DatasetFileTermDifferenceItem;
import edu.harvard.iq.dataverse.dataset.difference.FileSummary;
import edu.harvard.iq.dataverse.dataset.difference.TermsOfUseDiff;
import edu.harvard.iq.dataverse.engine.command.impl.DeleteProvJsonCommand;
import edu.harvard.iq.dataverse.engine.command.impl.PersistProvJsonCommand;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.persistence.datafile.license.FileTermsOfUse;
import edu.harvard.iq.dataverse.provenance.UpdatesEntry;
import io.vavr.control.Option;
import org.apache.commons.lang.StringUtils;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.List;

@Stateless
public class FileMetadataService {

    private EjbDataverseEngine commandEngine;
    private DataverseRequestServiceBean dvRequestService;

    // -------------------- CONSTRUCTORS --------------------

    @Deprecated
    public FileMetadataService() {
    }

    @Inject
    public FileMetadataService(EjbDataverseEngine commandEngine, DataverseRequestServiceBean dvRequestService) {
        this.commandEngine = commandEngine;
        this.dvRequestService = dvRequestService;
    }

    // -------------------- LOGIC --------------------

    /**
     * If file is among provenanceUpdates file will be updated with ProvFreeForm.
     */
    public FileMetadata updateFileMetadataWithProvFreeForm(FileMetadata fileMetadataToUpdate, String provenanceFreeForm) {

        fileMetadataToUpdate.setProvFreeForm(provenanceFreeForm);

        return fileMetadataToUpdate;
    }

    /**
     * Aggregate function that either persists provenance or deletes it.
     */
    public Option<DataFile> manageProvJson(boolean saveContext, UpdatesEntry provenanceUpdate) {

        Option<DataFile> updatedEntry = Option.none();

        if (provenanceUpdate.getDeleteJson()) {
            DataFile updatedDataFile = commandEngine.submit((new DeleteProvJsonCommand(dvRequestService.getDataverseRequest(),
                                                                                       provenanceUpdate.getDataFile(),
                                                                                       saveContext)));
            return Option.of(updatedDataFile);
        } else if (provenanceUpdate.getProvJson().isDefined()) {
            DataFile updatedDataFile = commandEngine.submit(new PersistProvJsonCommand(dvRequestService.getDataverseRequest(),
                                                                                       provenanceUpdate.getDataFile(),
                                                                                       provenanceUpdate.getProvJson().get(),
                                                                                       provenanceUpdate.getDataFile().getProvEntityName(),
                                                                                       saveContext));
            return Option.of(updatedDataFile);
        }

        return updatedEntry;
    }

    public List<DatasetFileTermDifferenceItem> getTermsOfUseDifference(List<FileMetadata> newVersionFileMetadata, List<FileMetadata> previousVersionFileMetadata) {
        List<TermsOfUseDiff> changedTermsOfUse = getTermsOfUseDiffs(newVersionFileMetadata, previousVersionFileMetadata);

        return getDatasetFileTermDifferenceItems(changedTermsOfUse);
    }

    // -------------------- PRIVATE ---------------------
    private boolean areFileTermsEqual(FileTermsOfUse termsOriginal, FileTermsOfUse termsNew) {
        if (termsOriginal.getTermsOfUseType() != termsNew.getTermsOfUseType()) {
            return false;
        }
        if (termsOriginal.getTermsOfUseType() == FileTermsOfUse.TermsOfUseType.LICENSE_BASED) {
            return termsOriginal.getLicense().getId().equals(termsNew.getLicense().getId());
        }
        if (termsOriginal.getTermsOfUseType() == FileTermsOfUse.TermsOfUseType.RESTRICTED) {
            return termsOriginal.getRestrictType() == termsNew.getRestrictType() &&
                    StringUtils.equals(termsOriginal.getRestrictCustomText(), termsNew.getRestrictCustomText());
        }
        return true;
    }

    private List<TermsOfUseDiff> getTermsOfUseDiffs(List<FileMetadata> newVersionFileMetadata, List<FileMetadata> previousVersionFileMetadata) {
        List<TermsOfUseDiff> changedTermsOfUse = Lists.newArrayList();

        for (FileMetadata fmdo : previousVersionFileMetadata) {
            for (FileMetadata fmdn : newVersionFileMetadata) {
                if (fmdo.getDataFile().equals(fmdn.getDataFile())) {
                    if (!areFileTermsEqual(fmdo.getTermsOfUse(), fmdn.getTermsOfUse())) {
                        changedTermsOfUse.add(new TermsOfUseDiff(fmdo.getTermsOfUse(), fmdn.getTermsOfUse()));
                    }
                    break;
                }
            }
        }
        return changedTermsOfUse;
    }

    private List<DatasetFileTermDifferenceItem> getDatasetFileTermDifferenceItems(List<TermsOfUseDiff> changedTermsOfUse) {
        List<DatasetFileTermDifferenceItem> filesTermDiffList = Lists.newArrayList();

        for (TermsOfUseDiff changedTermsPair : changedTermsOfUse) {
            FileTermsOfUse originalTerms = changedTermsPair.getOldValue();
            FileTermsOfUse newTerms = changedTermsPair.getNewValue();
            DataFile dataFile = originalTerms.getFileMetadata().getDataFile();

            filesTermDiffList.add(new DatasetFileTermDifferenceItem(
                    new FileSummary(dataFile.getId().toString(),
                            dataFile.getChecksumType(),
                            dataFile.getChecksumValue()),
                    originalTerms, newTerms));
        }
        return filesTermDiffList;
    }
}
