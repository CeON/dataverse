package edu.harvard.iq.dataverse.citation;

import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.Locale;

@Stateless
public class CitationFactory {

    private CitationDataExtractor dataExtractor;

    private CitationFormatsConverter converter;

    // -------------------- CONSTRUCTORS --------------------

    public CitationFactory() { }

    @Inject
    public CitationFactory(CitationDataExtractor dataExtractor, CitationFormatsConverter converter) {
        this.dataExtractor = dataExtractor;
        this.converter = converter;
    }

    // -------------------- LOGIC --------------------

    public Citation create(DatasetVersion datasetVersion) {
        Locale currentLocale = BundleUtil.getCurrentLocale();
        return new Citation(dataExtractor.create(datasetVersion, currentLocale), converter, currentLocale);
    }

    public Citation create(DatasetVersion datasetVersion, Locale locale) {
        return new Citation(dataExtractor.create(datasetVersion, locale), converter, locale);
    }

    public Citation create(FileMetadata fileMetadata, boolean direct) {
        Locale currentLocale = BundleUtil.getCurrentLocale();
        return new Citation(dataExtractor.create(fileMetadata, direct, currentLocale), converter, currentLocale);
    }

    public Citation create(FileMetadata fileMetadata) {
        return create(fileMetadata, false);
    }
}
