package edu.harvard.iq.dataverse.persistence.datafile.dto;

import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.license.FileTermsOfUse;
import io.vavr.control.Option;

public class FileMetadataRestrictionDTO {

    private long dsvId;
    private FileTermsOfUse.TermsOfUseType termsOfUseType;
    private FileTermsOfUse.RestrictType restrictType;
    private DataFile dataFile;

    // -------------------- CONSTRUCTORS --------------------

    public FileMetadataRestrictionDTO(long dsvId,
                                      FileTermsOfUse.TermsOfUseType termsOfUseType,
                                      DataFile dataFile) {
        this.dsvId = dsvId;
        this.termsOfUseType = termsOfUseType;
        this.dataFile = dataFile;
    }

    public FileMetadataRestrictionDTO(long dsvId,
                                      FileTermsOfUse.TermsOfUseType termsOfUseType,
                                      FileTermsOfUse.RestrictType restrictType,
                                      DataFile dataFile) {
        this.dsvId = dsvId;
        this.termsOfUseType = termsOfUseType;
        this.restrictType = restrictType;
        this.dataFile = dataFile;
    }

    // -------------------- GETTERS --------------------

    public long getDsvId() {
        return dsvId;
    }

    public FileTermsOfUse.TermsOfUseType getTermsOfUseType() {
        return termsOfUseType;
    }

    public Option<FileTermsOfUse.RestrictType> getRestrictType() {
        return Option.of(restrictType);
    }

    public DataFile getDataFile() {
        return dataFile;
    }
}
