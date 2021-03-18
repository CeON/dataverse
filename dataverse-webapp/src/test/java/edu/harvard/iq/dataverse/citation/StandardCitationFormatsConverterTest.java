package edu.harvard.iq.dataverse.citation;

import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Based on original DataCitation test class by
 * @author pkiraly@gwdg.de
 */
class StandardCitationFormatsConverterTest {

    private StandardCitationFormatsConverter converter = new StandardCitationFormatsConverter();

    private CitationDataExtractor dataExtractor = new CitationDataExtractor();

    private CitationTestUtils utils = new CitationTestUtils();

    // -------------------- TESTS --------------------

    @Test
    @DisplayName("Should write BibTeX citation to output stream")
    void writeAsBibtexCitation() throws ParseException, IOException {

        // given
        DatasetVersion datasetVersion = utils.createATestDatasetVersion("Dataset Title", true);
        CitationData citationData = dataExtractor.create(datasetVersion, false);

        // when
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        converter.writeAsBibtexCitation(citationData, os);
        os.close();
        String bibtex = new String(os.toByteArray(), StandardCharsets.UTF_8);

        // then
        assertThat(bibtex).isEqualTo("@data{LK0D1H_1955,\r\n"
                        + "author = {First Last},\r\n"
                        + "publisher = {LibraScholar},\r\n"
                        + "title = \"{Dataset Title}\",\r\n"
                        + "year = {1955},\r\n"
                        + "version = {V1},\r\n"
                        + "doi = {10.5072/FK2/LK0D1H},\r\n"
                        + "url = {https://doi.org/10.5072/FK2/LK0D1H}\r\n"
                        + "}\r\n");
    }

    @Test
    @DisplayName("Should create BibTeX citation")
    void toBibtexString() throws ParseException {

        // given
        DatasetVersion datasetVersion = utils.createATestDatasetVersion("Dataset Title", true);
        CitationData citationData = dataExtractor.create(datasetVersion, false);

        // when

        String bibtex = converter.toBibtexString(citationData);

        // then
        assertThat(bibtex).isEqualTo("@data{LK0D1H_1955,\r\n"
                        + "author = {First Last},\r\n"
                        + "publisher = {LibraScholar},\r\n"
                        + "title = \"{Dataset Title}\",\r\n"
                        + "year = {1955},\r\n"
                        + "version = {V1},\r\n"
                        + "doi = {10.5072/FK2/LK0D1H},\r\n"
                        + "url = {https://doi.org/10.5072/FK2/LK0D1H}\r\n"
                        + "}\r\n");
    }

    @Test
    @DisplayName("Should create BibTeX citation with empty author if no author is provided")
    void toBibtexString__withoutAuthor() throws ParseException {

        // given
        DatasetVersion datasetVersion = utils.createATestDatasetVersion("Dataset Title", false);
        CitationData citationData = dataExtractor.create(datasetVersion, false);

        // when
        String bibtex = converter.toBibtexString(citationData);

        // then
        assertThat(bibtex).isEqualTo("@data{LK0D1H_1955,\r\n"
                        + "author = {},\r\n"
                        + "publisher = {LibraScholar},\r\n"
                        + "title = \"{Dataset Title}\",\r\n"
                        + "year = {1955},\r\n"
                        + "version = {V1},\r\n"
                        + "doi = {10.5072/FK2/LK0D1H},\r\n"
                        + "url = {https://doi.org/10.5072/FK2/LK0D1H}\r\n"
                        + "}\r\n");
    }

    @Test
    @DisplayName("Should create BibTeX citation with empty title if no title is provided")
    void testToBibtexString_withoutTitle() throws ParseException {

        // given
        DatasetVersion datasetVersion = utils.createATestDatasetVersion(null, true);
        CitationData citationData = dataExtractor.create(datasetVersion, false);

        // when
        String bibtex = converter.toBibtexString(citationData);

        // then
        assertThat(bibtex).isEqualTo("@data{LK0D1H_1955,\r\n"
                        + "author = {First Last},\r\n"
                        + "publisher = {LibraScholar},\r\n"
                        + "title = \"{}\",\r\n"
                        + "year = {1955},\r\n"
                        + "version = {V1},\r\n"
                        + "doi = {10.5072/FK2/LK0D1H},\r\n"
                        + "url = {https://doi.org/10.5072/FK2/LK0D1H}\r\n"
                        + "}\r\n");
    }

    @Test
    @DisplayName("Should create BibTeX citation with empty author and title if none of these is specified")
    void toBibtexString__withoutTitleAndAuthor() throws ParseException {

        // given
        DatasetVersion datasetVersion = utils.createATestDatasetVersion(null, false);
        CitationData citationData = dataExtractor.create(datasetVersion, false);

        // when
        String bibtex = converter.toBibtexString(citationData);

        // then
        assertThat(bibtex).isEqualTo("@data{LK0D1H_1955,\r\n"
                        + "author = {},\r\n"
                        + "publisher = {LibraScholar},\r\n"
                        + "title = \"{}\",\r\n"
                        + "year = {1955},\r\n"
                        + "version = {V1},\r\n"
                        + "doi = {10.5072/FK2/LK0D1H},\r\n"
                        + "url = {https://doi.org/10.5072/FK2/LK0D1H}\r\n"
                        + "}\r\n");
    }

    @Test
    @DisplayName("Should create BibTeX citation with inner quotes in title")
    void toBibtexCitation__titleWithQuotes() throws ParseException {

        // given
        DatasetVersion datasetVersion = utils.createATestDatasetVersion("This Title \"Has Quotes\" In It", true);
        CitationData citationData = dataExtractor.create(datasetVersion, false);

        // when
        String bibtex = converter.toBibtexString(citationData);

        // then
        assertThat(bibtex).isEqualTo("@data{LK0D1H_1955,\r\n"
                + "author = {First Last},\r\n"
                + "publisher = {LibraScholar},\r\n"
                + "title = \"{This Title ``Has Quotes'' In It}\",\r\n"
                + "year = {1955},\r\n"
                + "version = {V1},\r\n"
                + "doi = {10.5072/FK2/LK0D1H},\r\n"
                + "url = {https://doi.org/10.5072/FK2/LK0D1H}\r\n"
                + "}\r\n");
    }

    @Test
    @DisplayName("Should create RIS citation with title and author")
    void toRISString__withTitleAndAuthor() throws ParseException {

        // given
        DatasetVersion datasetVersion = utils.createATestDatasetVersion("Dataset Title", true);
        CitationData citationData = dataExtractor.create(datasetVersion, false);

        // when
        String ris = converter.toRISString(citationData);

        // then
        assertThat(ris).isEqualTo("Provider: LibraScholar\r\n" +
                        "Content: text/plain; charset=\"utf-8\"\r\n" +
                        "TY  - DATA\r\n" +
                        "T1  - Dataset Title\r\n" +
                        "AU  - First Last\r\n" +
                        "DO  - doi:10.5072/FK2/LK0D1H\r\n" +
                        "ET  - V1\r\n" +
                        "PY  - 1955\r\n" +
                        "SE  - 1955-11-05 00:00:00.0\r\n" +
                        "UR  - https://doi.org/10.5072/FK2/LK0D1H\r\n" +
                        "PB  - LibraScholar\r\n" +
                        "ER  - \r\n");
    }

    @Test
    @DisplayName("Should create RIS citation without title and author")
    void testToRISString_withoutTitleAndAuthor() throws ParseException {

        // given
        DatasetVersion datasetVersion = utils.createATestDatasetVersion(null, false);
        CitationData citationData = dataExtractor.create(datasetVersion, false);

        // when
        String ris = converter.toRISString(citationData);

        // then
        assertThat(ris).isEqualTo("Provider: LibraScholar\r\n" +
                        "Content: text/plain; charset=\"utf-8\"\r\n" +
                        "TY  - DATA\r\n" +
                        "T1  - \r\n" +
                        "DO  - doi:10.5072/FK2/LK0D1H\r\n" +
                        "ET  - V1\r\n" +
                        "PY  - 1955\r\n" +
                        "SE  - 1955-11-05 00:00:00.0\r\n" +
                        "UR  - https://doi.org/10.5072/FK2/LK0D1H\r\n" +
                        "PB  - LibraScholar\r\n" +
                        "ER  - \r\n");
    }

    @Test
    @DisplayName("Should create EndNote citation with title and author")
    void toEndNoteString__withTitleAndAuthor() throws ParseException {

        // given
        DatasetVersion datasetVersion = utils.createATestDatasetVersion("Dataset Title", true);
        CitationData citationData = dataExtractor.create(datasetVersion, false);

        // when
        String endNote = converter.toEndNoteString(citationData);

        // then
        assertThat(endNote).isEqualTo("<?xml version='1.0' encoding='UTF-8'?>" +
                        "<xml>" +
                        "<records>" +
                        "<record>" +
                        "<ref-type name=\"Dataset\">59</ref-type>" +
                        "<contributors>" +
                        "<authors><author>First Last</author></authors>" +
                        "</contributors>" +
                        "<titles><title>Dataset Title</title></titles>" +
                        "<section>1955-11-05</section>" +
                        "<dates><year>1955</year></dates>" +
                        "<edition>V1</edition>" +
                        "<publisher>LibraScholar</publisher>" +
                        "<urls><related-urls><url>https://doi.org/10.5072/FK2/LK0D1H</url></related-urls></urls>" +
                        "<electronic-resource-num>doi/10.5072/FK2/LK0D1H</electronic-resource-num>" +
                        "</record>" +
                        "</records>" +
                        "</xml>");
    }

    @Test
    @DisplayName("Should create EndNote citation without title and author")
    void toEndNoteString__withoutTitleAndAuthor() throws ParseException {

        // given
        DatasetVersion datasetVersion = utils.createATestDatasetVersion(null, false);
        CitationData citationData = dataExtractor.create(datasetVersion, false);

        // when
        String endNote = converter.toEndNoteString(citationData);

        // then
        assertThat(endNote).isEqualTo("<?xml version='1.0' encoding='UTF-8'?>" +
                        "<xml>" +
                        "<records>" +
                        "<record>" +
                        "<ref-type name=\"Dataset\">59</ref-type>" +
                        "<contributors />" +
                        "<titles><title></title></titles>" +
                        "<section>1955-11-05</section>" +
                        "<dates><year>1955</year></dates>" +
                        "<edition>V1</edition>" +
                        "<publisher>LibraScholar</publisher>" +
                        "<urls><related-urls><url>https://doi.org/10.5072/FK2/LK0D1H</url></related-urls></urls>" +
                        "<electronic-resource-num>doi/10.5072/FK2/LK0D1H</electronic-resource-num>" +
                        "</record>" +
                        "</records>" +
                        "</xml>");
    }

    @Test
    @DisplayName("Should create citation with title and author")
    void toString__withTitleAndAuthor() throws ParseException {

        // given
        DatasetVersion datasetVersion = utils.createATestDatasetVersion("Dataset Title", true);
        CitationData citationData = dataExtractor.create(datasetVersion, false);

        // when
        String citation = converter.toString(citationData, false);

        // then
        assertThat(citation)
                .isEqualTo("First Last, 1955, \"Dataset Title\", https://doi.org/10.5072/FK2/LK0D1H, LibraScholar, V1");
    }

    @Test
    @DisplayName("Should create citation without title and author")
    void toString__withoutTitleAndAuthor() throws ParseException {

        // given
        DatasetVersion datasetVersion = utils.createATestDatasetVersion(null, false);
        CitationData citationData = dataExtractor.create(datasetVersion, false);

        // when
        String citation = converter.toString(citationData, false);

        // then
        assertThat(citation)
                .isEqualTo("1955, https://doi.org/10.5072/FK2/LK0D1H, LibraScholar, V1");
    }

    @Test
    @DisplayName("Should create citation with title and author, escaping html")
    void toString__withTitleAndAuthor_escapedHtml() throws ParseException {

        // given
        DatasetVersion datasetVersion = utils.createATestDatasetVersion("Dataset Title", true);
        CitationData citationData = dataExtractor.create(datasetVersion, false);

        // when
        String citation = converter.toString(citationData, true);

        // then
        assertThat(citation).isEqualTo("First Last, 1955, \"Dataset Title\"," +
                        " <a href=\"https://doi.org/10.5072/FK2/LK0D1H\" target=\"_blank\">https://doi.org/10.5072/FK2/LK0D1H</a>," +
                        " LibraScholar, V1");
    }

    @Test
    @DisplayName("Should create citation without title and author, escaping html")
    void toString__withoutTitleAndAuthor_escapedHtml() throws ParseException {

        // given
        DatasetVersion datasetVersion = utils.createATestDatasetVersion(null, false);
        CitationData citationData = dataExtractor.create(datasetVersion, false);

        // when
        String citation = converter.toString(citationData, true);

        // then
        assertThat(citation).isEqualTo("1955," +
                        " <a href=\"https://doi.org/10.5072/FK2/LK0D1H\" target=\"_blank\">https://doi.org/10.5072/FK2/LK0D1H</a>," +
                        " LibraScholar, V1");
    }
}