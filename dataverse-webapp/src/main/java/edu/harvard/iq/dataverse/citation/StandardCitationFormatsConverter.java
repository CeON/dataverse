package edu.harvard.iq.dataverse.citation;

import edu.harvard.iq.dataverse.persistence.GlobalId;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetField;
import edu.harvard.iq.dataverse.persistence.dataset.FieldType;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Priority;
import javax.ejb.EJBException;
import javax.ejb.Stateless;
import javax.enterprise.inject.Alternative;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

/**
 * Based on DataCitation class created by
 * @author gdurand, qqmyers
 */
@Alternative @Priority(1)
public class StandardCitationFormatsConverter extends AbstractCitationFormatsConverter {
    private static final Logger logger = LoggerFactory.getLogger(StandardCitationFormatsConverter.class);

    // -------------------- LOGIC --------------------

    @Override
    public String toString(CitationData data, boolean escapeHtml) {
        CitationBuilder citation = new CitationBuilder(escapeHtml);

        citation.addFormatted(data.getAuthorsString())
                .add(data.getYear());
        if (data.getFileTitle() != null && data.isDirect()) {
            citation.addFormatted(data.getFileTitle(), "\"")
                    .addFormatted(data.getTitle(), "<i>", "</i>");
        } else {
            citation.addFormatted(data.getTitle(), "\"");
        }
        if (data.getPersistentId() != null) {
            String url = data.getPersistentId().toURL().toString();
            citation.add(citation.formatURL(url, url));
        }

        String separator = ", ";
        citation.addFormatted(data.getPublisher())
                .add(data.getVersion())
                .join(separator, StringUtils::isNotEmpty);
        if (data.getFileTitle() != null && !data.isDirect()) {
            citation.add("; ", citation.escapeHtmlIfNeeded(data.getFileTitle()), " [fileName]");
        }
        if (isNotEmpty(data.getUNF())) {
            citation.add(separator, data.getUNF(), " [fileUNF]");
        }
        for (DatasetField field : data.getOptionalValues()) {
            String displayName = field.getDatasetFieldType().getDisplayName();
            String displayValue;

            if (FieldType.URL.equals(field.getDatasetFieldType().getFieldType())) {
                displayValue = citation.formatURL(field.getDisplayValue(), field.getDisplayValue());
                if (data.getOptionalURLcount() == 1) {
                    displayName = "URL";
                }
            } else {
                displayValue = citation.escapeHtmlIfNeeded(field.getDisplayValue());
            }
            citation.add(" [", displayName, ": ", displayValue, "]");
        }
        return citation.join("", s -> true);
    }

    @Override
    public String toBibtexString(CitationData data) {
        return writeAndGet(data, this::writeAsBibtexCitation);
    }

    @Override
    public void writeAsBibtexCitation(CitationData data, OutputStream os) throws IOException {
        GlobalId pid = data.getPersistentId();
        BibTeXCitationBuilder bibtex = new BibTeXCitationBuilder()
                .add(data.getFileTitle() != null && data.isDirect() ? "@incollection{" : "@data{")
                .add(pid.getIdentifier() + "_" + data.getYear() + ",\r\n")
                .line("author", String.join(" and ", data.getAuthors()))
                .line("publisher", data.getPublisher());
        if (data.getFileTitle() != null && data.isDirect()) {
            bibtex.line("title", data.getFileTitle())
                    .line("booktitle", data.getTitle());
        } else {
            bibtex.line("title",
                    data.getTitle()
                            .replaceFirst("\"", "``")
                            .replaceFirst("\"", "''"),
                    s -> bibtex.mapValue(s, "\"{", "}\","));
        }
        if (data.getUNF() != null) {
            bibtex.line("UNF", data.getUNF());
        }
        bibtex.line("year", data.getYear())
            .line("version", data.getVersion())
            .line("doi", pid.getAuthority() + "/" + pid.getIdentifier())
            .line("url", pid.toURL().toString(), s -> bibtex.mapValue(s, "{", "}"))
            .add("}\r\n");

        Writer out = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
        out.write(bibtex.toString());
        out.flush();
    }

    @Override
    public String toRISString(CitationData data) {
        return writeAndGet(data, this::writeAsRISCitation);
    }

    @Override
    public void writeAsRISCitation(CitationData data, OutputStream os) throws IOException {
        RISCitationBuilder ris = new RISCitationBuilder();
        ris.line("Provider: " + data.getPublisher())
                .line("Content: text/plain; charset=\"utf-8\"");
        // Using type DATA: see https://github.com/IQSS/dataverse/issues/4816
        if ((data.getFileTitle() != null) && data.isDirect()) {
            ris.line("TY  - DATA")
                    .line("T1", data.getFileTitle())
                    .line("T2", data.getTitle());
        } else {
            ris.line("TY  - DATA")
                    .line("T1", data.getTitle());
        }
        if (data.getSeriesTitle() != null) {
            ris.line("T3", data.getSeriesTitle());
        }
        ris.lines("AU", data.getAuthors())
                .lines("A2", data.getProducers())
                .lines("A4", data.getFunders())
                .lines("C3", data.getKindsOfData())
                .lines("DA", data.getDatesOfCollection());
        if (data.getPersistentId() != null) {
            ris.line("DO", data.getPersistentId().toString());
        }
        ris.line("ET", data.getVersion())
                .lines("KW", data.getKeywords())
                .lines("LA", data.getLanguages())
                .line("PY", data.getYear())
                .lines("RI", data.getSpatialCoverages())
                .line("SE", String.valueOf(data.getDate()))
                .line("UR", data.getPersistentId().toURL().toString())
                .line("PB", data.getPublisher());
        if (data.getFileTitle() != null) {
            if (!data.isDirect()) {
                ris.line("C1", data.getFileTitle());
            }
            if (data.getUNF() != null) {
                ris.line("C2", data.getUNF());
            }
        }
        ris.line("ER", ""); // closing element

        Writer out = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
        out.write(ris.toString());
        out.flush();
    }

    @Override
    public String toEndNoteString(CitationData data) {
        return writeAndGet(data, this::writeAsEndNoteCitation);
    }

    @Override
    public void writeAsEndNoteCitation(CitationData data, OutputStream os) {
        XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();
        XMLStreamWriter xmlw = null;
        try {
            xmlw = xmlOutputFactory.createXMLStreamWriter(os);
            createEndNoteXML(data, xmlw);
        } catch (XMLStreamException xse) {
            logger.error("", xse);
            throw new EJBException("Error occurred during creating endnote xml.", xse);
        } finally {
            try {
                if (xmlw != null) {
                    xmlw.close();
                }
            } catch (XMLStreamException xse) {
                logger.warn("Exception while closing XMLStreamWriter", xse);
            }
        }
    }

    // -------------------- PRIVATE --------------------

    private void createEndNoteXML(CitationData data, XMLStreamWriter xmlw) throws XMLStreamException {
        EndNoteCitationBuilder xml = new EndNoteCitationBuilder(xmlw);
        xml.start()
                .startTag("xml")
                .startTag("records")
                .startTag("record")

    /*
        "Ref-type" indicates which of the (numerous!) available EndNote schemas this record will be interpreted as.

        This is relatively important. Certain fields with generic names like "custom1" and "custom2" become very
     specific things in specific schemas; for example, custom1 shows as "legal notice" in "Journal Article"
     (ref-type 84), or as "year published" in "Government Document".

        We don't want the UNF to show as a "legal notice"!

        We have found a ref-type that works ok for our purposes - "Dataset" (type 59). In this one, the fields
     Custom1 and Custom2 are not translated and just show as is. And "Custom1" still beats "legal notice".

        -- L.A. 12.12.2014 beta 10 and see https://github.com/IQSS/dataverse/issues/4816
    */

                .startTag("ref-type")
                .addAttribute("name", "Dataset")
                .addValue("59")
                .endTag() // ref-type
                .startTag("contributors")
                .addTagCollection("authors", "author", data.getAuthors())
                .addTagCollection("secondary-authors", "author", data.getProducers())
                .addTagCollection("subsidiary-authors", "author", data.getFunders())
                .endTag(); // contributors

        xml.startTag("titles");
        if ((data.getFileTitle() != null) && data.isDirect()) {
            xml.addTagWithValue("title", data.getFileTitle())
                    .addTagWithValue("secondary-title", data.getTitle());
        } else {
            xml.addTagWithValue("title", data.getTitle());
        }
        if (data.getSeriesTitle() != null) {
            xml.addTagWithValue("tertiary-title", data.getSeriesTitle());
        }
        xml.endTag() // titles
                .addTagWithValue("section", new SimpleDateFormat("yyyy-MM-dd").format(data.getDate()))
                .startTag("dates")
                .addTagWithValue("year", data.getYear())
                .addTagCollection("pub-dates", "date", data.getDatesOfCollection())
                .endTag() // dates
                .addTagWithValue("edition", data.getVersion())
                .addTagCollection("keywords", "keyword", data.getKeywords())
                .addTagCollection(StringUtils.EMPTY, "custom3", data.getKindsOfData())
                .addTagCollection(StringUtils.EMPTY, "language", data.getLanguages())
                .addTagWithValue("publisher", data.getPublisher())
                .addTagCollection(StringUtils.EMPTY, "reviewed-item", data.getSpatialCoverages())
                .startTag("urls")
                .startTag("related-urls")
                .addTagWithValue("url", data.getPersistentId().toURL().toString())
                .endTag() // related-urls
                .endTag(); // urls

        // a DataFile citation also includes the filename and (for Tabular files)
        // the UNF signature, that we put into the custom1 and custom2 fields respectively:
        if (data.getFileTitle() != null) {
            xml.addTagWithValue("custom1", data.getFileTitle());
            if (data.getUNF() != null) {
                xml.addTagWithValue("custom2", data.getUNF());
            }
        }
        if (data.getPersistentId() != null) {
            GlobalId pid = data.getPersistentId();
            xml.addTagWithValue("electronic-resource-num",
                    pid.getProtocol() + "/" + pid.getAuthority() + "/" + pid.getIdentifier());
        }
        xml.endTag() // record
                .endTag() // records
                .endTag() // xml
                .end();
    }
}
