package edu.harvard.iq.dataverse.dataset.difference;

import com.google.common.collect.Lists;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.persistence.datafile.license.FileTermsOfUse;
import io.vavr.Tuple2;
import org.apache.commons.lang.StringUtils;

import javax.ejb.Stateless;
import java.util.List;

@Stateless
public class LicenseDifferenceFinder {

    // -------------------- CONSTRUCTORS --------------------

    @Deprecated
    public LicenseDifferenceFinder() {
    }

    // -------------------- LOGIC --------------------
    public List<DatasetFileTermDifferenceItem> getLicenseDifference(List<FileMetadata> newVersionFileMetadata, List<FileMetadata> previousVersionFileMetadata) {
        List<Tuple2<String,TermsOfUseDiff>> changedTermsOfUse = getTermsOfUseDiffs(newVersionFileMetadata, previousVersionFileMetadata);

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

    private List<Tuple2<String, TermsOfUseDiff>> getTermsOfUseDiffs(List<FileMetadata> newVersionFileMetadata, List<FileMetadata> previousVersionFileMetadata) {
        List<Tuple2<String, TermsOfUseDiff>> changedTermsOfUse = Lists.newArrayList();

        for (FileMetadata fmdo : previousVersionFileMetadata) {
            for (FileMetadata fmdn : newVersionFileMetadata) {
                if (fmdo.getDataFile().equals(fmdn.getDataFile())) {
                    if (!areFileTermsEqual(fmdo.getTermsOfUse(), fmdn.getTermsOfUse())) {
                        changedTermsOfUse.add(new Tuple2<>(fmdn.getLabel(), new TermsOfUseDiff(fmdo.getTermsOfUse(), fmdn.getTermsOfUse())));
                    }
                    break;
                }
            }
        }
        return changedTermsOfUse;
    }

    private List<DatasetFileTermDifferenceItem> getDatasetFileTermDifferenceItems(List<Tuple2<String, TermsOfUseDiff>> changedTermsOfUse) {
        List<DatasetFileTermDifferenceItem> filesTermDiffList = Lists.newArrayList();

        for (Tuple2<String,TermsOfUseDiff> changedTermsPair : changedTermsOfUse) {
            FileTermsOfUse originalTerms = changedTermsPair._2.getOldValue();
            FileTermsOfUse newTerms = changedTermsPair._2.getNewValue();
            DataFile dataFile = originalTerms.getFileMetadata().getDataFile();
            String fileName = changedTermsPair._1;

            filesTermDiffList.add(new DatasetFileTermDifferenceItem(
                    new FileSummary(dataFile.getId().toString(),
                            dataFile.getChecksumType(),
                            dataFile.getChecksumValue()),
                    fileName,
                    originalTerms, newTerms));
        }
        return filesTermDiffList;
    }

}
