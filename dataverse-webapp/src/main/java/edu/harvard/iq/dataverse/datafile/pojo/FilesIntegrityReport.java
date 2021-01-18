package edu.harvard.iq.dataverse.datafile.pojo;

import java.util.ArrayList;
import java.util.List;

import edu.harvard.iq.dataverse.persistence.datafile.DataFile;

public class FilesIntegrityReport {

    private int checkedCount;
    
    private int skippedChecksumVerification;
    
    private List<FileIntegrityFail> suspicious = new ArrayList<>();


    // -------------------- GETTERS --------------------

    public int getCheckedCount() {
        return checkedCount;
    }

    public int getSkippedChecksumVerification() {
        return skippedChecksumVerification;
    }

    public List<FileIntegrityFail> getSuspicious() {
        return suspicious;
    }

    // -------------------- LOGIC --------------------

    public void addSuspicious(DataFile dataFile, FileIntegrityCheckResult checkResult) {
        suspicious.add(new FileIntegrityFail(dataFile, checkResult));
    }

    public void incrementSkippedChecksumVerification() {
        ++skippedChecksumVerification;
    }

    public String getSummaryInfo() {
        String message = "Found " + checkedCount + " files in repository.";
        message += " Found " + suspicious.size() + " suspicious files.";
        return message;
    }

    // -------------------- SETTERS --------------------

    public void setCheckedCount(int checkedCount) {
        this.checkedCount = checkedCount;
    }
}
