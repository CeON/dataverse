package edu.harvard.iq.dataverse.citation;

import edu.harvard.iq.dataverse.persistence.GlobalId;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetField;
import edu.harvard.iq.dataverse.persistence.dataset.FieldType;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.EJBException;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Based on DataCitation class created by
 * @author gdurand, qqmyers
 */
public class Citation {
    private static final Logger logger = LoggerFactory.getLogger(Citation.class);

    private CitationData data;

    // -------------------- CONSTRUCTORS --------------------

    public Citation(CitationData citationData) {
        this.data = citationData;
    }

    // -------------------- GETTERS --------------------

    public CitationData getCitationData() {
        return data;
    }

    // -------------------- LOGIC --------------------

    @Override
    public String toString() {
        return toString(false);
    }

    public String toString(boolean html) {
        String separator = ", ";
        List<String> citationList = new ArrayList<>();

        citationList.add(formatString(data.getAuthorsString(), html));
        citationList.add(data.getYear());
        if ((data.getFileTitle() != null) && data.isDirect()) {
            citationList.add(formatString(data.getFileTitle(), html, "\""));
            citationList.add(formatString(data.getTitle(), html, "<i>", "</i>"));
        } else {
            citationList.add(formatString(data.getTitle(), html, "\""));
        }

        if (data.getPersistentId() != null) {
            // always show url format
            citationList.add(formatURL(
                    data.getPersistentId().toURL().toString(), data.getPersistentId().toURL().toString(), html));
        }
        citationList.add(formatString(data.getPublisher(), html));
        citationList.add(data.getVersion());

        StringBuilder citation = new StringBuilder(citationList.stream()
                .filter(StringUtils::isNotEmpty)
                .collect(Collectors.joining(separator)));

        if ((data.getFileTitle() != null) && !data.isDirect()) {
            citation.append("; ")
                    .append(formatString(data.getFileTitle(), html, ""))
                    .append(" [fileName]");
        }
        if (!StringUtils.isEmpty(data.getUNF())) {
            citation.append(separator)
                    .append(data.getUNF())
                    .append(" [fileUNF]");
        }

        for (DatasetField dsf : data.getOptionalValues()) {
            String displayName = dsf.getDatasetFieldType().getDisplayName();
            String displayValue;

            if (dsf.getDatasetFieldType().getFieldType().equals(FieldType.URL)) {
                displayValue = formatURL(dsf.getDisplayValue(), dsf.getDisplayValue(), html);
                if (data.getOptionalURLcount() == 1) {
                    displayName = "URL";
                }
            } else {
                displayValue = formatString(dsf.getDisplayValue(), html);
            }
            citation.append(" [")
                    .append(displayName)
                    .append(": ")
                    .append(displayValue)
                    .append("]");
        }
        return citation.toString();
    }

    public String toBibtexString() {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            writeAsBibtexCitation(buffer);
            return buffer.toString(StandardCharsets.UTF_8.name());
        } catch (IOException ioe) {
            logger.warn("Exception when creating BibTeX citation", ioe);
            return StringUtils.EMPTY;
        }
    }

    public void writeAsBibtexCitation(OutputStream os) throws IOException {
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

    public String toRISString() {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            writeAsRISCitation(buffer);
            return buffer.toString(StandardCharsets.UTF_8.name());
        } catch (IOException ioe) {
            logger.warn("Exception when creating RISS citation", ioe);
            return StringUtils.EMPTY;
        }
    }

    public void writeAsRISCitation(OutputStream os) throws IOException {
        RISSCitationBuilder riss = new RISSCitationBuilder();
        riss.line("Provider: " + data.getPublisher())
                .line("Content: text/plain; charset=\"utf-8\"");
        // Using type DATA: see https://github.com/IQSS/dataverse/issues/4816
        if ((data.getFileTitle() != null) && data.isDirect()) {
            riss.line("TY  - DATA")
                    .line("T1", data.getFileTitle())
                    .line("T2", data.getTitle());
        } else {
            riss.line("TY  - DATA")
                    .line("T1", data.getTitle());
        }
        if (data.getSeriesTitle() != null) {
            riss.line("T3", data.getSeriesTitle());
        }
        riss.lines("AU", data.getAuthors())
                .lines("A2", data.getProducers())
                .lines("A4", data.getFunders())
                .lines("C3", data.getKindsOfData())
                .lines("DA", data.getDatesOfCollection());
        if (data.getPersistentId() != null) {
            riss.line("DO", data.getPersistentId().toString());
        }
        riss.line("ET", data.getVersion())
                .lines("KW", data.getKeywords())
                .lines("LA", data.getLanguages())
                .line("PY", data.getYear())
                .lines("RI", data.getSpatialCoverages())
                .line("SE", String.valueOf(data.getDate()))
                .line("UR", data.getPersistentId().toURL().toString())
                .line("PB", data.getPublisher());
        if (data.getFileTitle() != null) {
            if (!data.isDirect()) {
                riss.line("C1", data.getFileTitle());
            }
            if (data.getUNF() != null) {
                riss.line("C2", data.getUNF());
            }
        }
        riss.line("ER", ""); // closing element

        Writer out = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
        out.write(riss.toString());
        out.flush();
    }

    public String toEndNoteString() {
        try (ByteArrayOutputStream outStream = new ByteArrayOutputStream()) {
            writeAsEndNoteCitation(outStream);
            return outStream.toString(StandardCharsets.UTF_8.name());
        } catch (IOException ioe) {
            logger.warn("Exception when creating EndNote citation", ioe);
            return StringUtils.EMPTY;
        }
    }

    public void writeAsEndNoteCitation(OutputStream os) {

        XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();
        XMLStreamWriter xmlw = null;
        try {
            xmlw = xmlOutputFactory.createXMLStreamWriter(os);
            createEndNoteXML(xmlw);
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

    private void createEndNoteXML(XMLStreamWriter xmlw) throws XMLStreamException {
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



    public Map<String, String> getDataCiteMetadata() {
        Map<String, String> metadata = new HashMap<>();
        String authorString = data.getAuthorsString();

        if (authorString.isEmpty()) {
            authorString = ":unav";
        }
        String producerString = data.getPublisher();

        if (producerString.isEmpty()) {
            producerString = ":unav";
        }

        metadata.put("datacite.creator", authorString);
        metadata.put("datacite.title", data.getTitle());
        metadata.put("datacite.publisher", producerString);
        metadata.put("datacite.publicationyear", data.getYear());
        return metadata;
    }


    // helper methods
    private String formatString(String value, boolean escapeHtml) {
        return formatString(value, escapeHtml, "");
    }

    private String formatString(String value, boolean escapeHtml, String wrapperFront) {
        return formatString(value, escapeHtml, wrapperFront, wrapperFront);
    }

    private String formatString(String value, boolean escapeHtml, String wrapperStart, String wrapperEnd) {
        if (!StringUtils.isEmpty(value)) {
            return wrapperStart
                    + (escapeHtml ? StringEscapeUtils.escapeHtml4(value) : value)
                    + wrapperEnd;
        }
        return null;
    }

    private String formatURL(String text, String url, boolean html) {
        if (text == null) {
            return null;
        }

        if (html && url != null) {
            return "<a href=\"" + url + "\" target=\"_blank\">" + StringEscapeUtils.escapeHtml4(text) + "</a>";
        } else {
            return text;
        }
    }

    // -------------------- INNER CLASSES --------------------

    private static class BibTeXCitationBuilder {
        private StringBuilder sb = new StringBuilder();

        // -------------------- LOGIC --------------------

        public BibTeXCitationBuilder line(String label, String value) {
            return line(label, value, s -> mapValue(s, "{", "},"));
        }

        private BibTeXCitationBuilder line(String label, String value, Function<String, String> valueMapper) {
            sb.append(label)
                    .append(" = ")
                    .append(valueMapper.apply(value))
                    .append("\r\n");
            return this;
        }

        public String mapValue(String value, String startDelimiter, String endDelimiter) {
            return startDelimiter + value + endDelimiter;
        }

        public BibTeXCitationBuilder add(String text) {
            sb.append(text);
            return this;
        }

        @Override
        public String toString() {
            return sb.toString();
        }
    }

    private static class RISSCitationBuilder {
        private StringBuilder sb = new StringBuilder();

        // -------------------- LOGIC --------------------

        public RISSCitationBuilder line(String value) {
            sb.append(value)
                    .append("\r\n");
            return this;
        }

        public RISSCitationBuilder line(String label, String value) {
            sb.append(label)
                    .append("  - ");
            return line(value);
        }

        public RISSCitationBuilder lines(String label, Collection<String> values) {
            values.forEach(v -> line(label, v));
            return this;
        }

        public RISSCitationBuilder add(String text) {
            sb.append(text);
            return this;
        }

        @Override
        public String toString() {
            return sb.toString();
        }
    }

    private static class EndNoteCitationBuilder {
        private XMLStreamWriter writer;

        // -------------------- CONSTRUCTORS --------------------

        public EndNoteCitationBuilder(XMLStreamWriter writer) {
            this.writer = writer;
        }

        // -------------------- LOGIC --------------------

        public EndNoteCitationBuilder start() throws XMLStreamException {
            writer.writeStartDocument();
            return this;
        }

        public void end() throws XMLStreamException {
            writer.writeEndDocument();
            writer = null;
        }

        public EndNoteCitationBuilder addTagWithValue(String tag, String value) throws XMLStreamException {
            writer.writeStartElement(tag);
            writer.writeCharacters(value);
            writer.writeEndElement();
            return this;
        }

        public EndNoteCitationBuilder addTagCollection(String collectionTag, String itemTag, Collection<String> values)
                throws XMLStreamException {
            if (values.isEmpty()) {
                return this;
            }
            if (StringUtils.isNotEmpty(collectionTag)) {
                startTag(collectionTag);
            }
            for (String value : values) {
                addTagWithValue(itemTag, value);
            }
            if (StringUtils.isNotEmpty(collectionTag)) {
                endTag();
            }
            return this;
        }

        public EndNoteCitationBuilder startTag(String tag) throws XMLStreamException {
            writer.writeStartElement(tag);
            return this;
        }

        public EndNoteCitationBuilder addAttribute(String name, String value) throws XMLStreamException {
            writer.writeAttribute(name, value);
            return this;
        }

        public EndNoteCitationBuilder addValue(String value) throws XMLStreamException {
            writer.writeCharacters(value);
            return this;
        }

        public EndNoteCitationBuilder endTag() throws XMLStreamException {
            writer.writeEndElement();
            return this;
        }
    }
}
