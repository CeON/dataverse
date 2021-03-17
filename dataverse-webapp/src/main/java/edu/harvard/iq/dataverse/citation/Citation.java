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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
            return buffer.toString();
        } catch (IOException ioe) {
            logger.warn("Exception when creating BibTeX citation", ioe);
            return StringUtils.EMPTY;
        }
    }

    public void writeAsBibtexCitation(OutputStream os) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(data.getFileTitle() != null && data.isDirect() ? "@incollection{" : "@data{")
                .append(data.getPersistentId().getIdentifier())
                .append("_")
                .append(data.getYear())
                .append(",\r\n")
                .append(bibtexLine("author", String.join(" and ", data.getAuthors())))
                .append(bibtexLine("publisher", data.getPublisher()));
        if (data.getFileTitle() != null && data.isDirect()) {
            sb.append(bibtexLine("title", data.getFileTitle()))
                .append(bibtexLine("booktitle", data.getTitle()));
        } else {
            sb.append(bibtexLine("title",
                    data.getTitle()
                            .replaceFirst("\"", "``")
                            .replaceFirst("\"", "''"),
                    s -> bibtexValue(s, "\"{", "}\",")));
        }
        if (data.getUNF() != null) {
            sb.append(bibtexLine("UNF", data.getUNF()));
        }
        sb.append(bibtexLine("year", data.getYear()))
            .append(bibtexLine("version", data.getVersion()))
            .append(bibtexLine("doi",
                    data.getPersistentId().getAuthority() + "/" + data.getPersistentId().getIdentifier()))
            .append(bibtexLine("url", data.getPersistentId().toURL().toString(), s -> bibtexValue(s, "{", "}")))
            .append("}\r\n");

        Writer out = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
        out.write(sb.toString());
        out.flush();
    }
    private static class BibtexCitation {

    }
    private String bibtexLine(String label, String value) {
        return bibtexLine(label, value, s -> bibtexValue(s, "{", "},"));
    }

    private String bibtexLine(String label, String value, Function<String, String> valueMapper) {
        return label + " = " + valueMapper.apply(value) + "\r\n";
    }

    private String bibtexValue(String value, String startDelimiter, String endDelimiter) {
        return startDelimiter + value + endDelimiter;
    }

    public String toRISString() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            writeAsRISCitation(buffer);
        } catch (IOException e) {
            e.printStackTrace();
        }
        //Use UTF-8?
        return buffer.toString();
    }

    public void writeAsRISCitation(OutputStream os) throws IOException {
        // Use UTF-8
        Writer out = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
        out.write("Provider: " + data.getPublisher() + "\r\n");
        out.write("Content: text/plain; charset=\"utf-8\"" + "\r\n");
        // Using type "DATA" - see https://github.com/IQSS/dataverse/issues/4816

        if ((data.getFileTitle() != null) && data.isDirect()) {
            out.write("TY  - DATA" + "\r\n");
            out.write("T1  - " + data.getFileTitle() + "\r\n");
            out.write("T2  - " + data.getTitle() + "\r\n");
        } else {
            out.write("TY  - DATA" + "\r\n");
            out.write("T1  - " + data.getTitle() + "\r\n");
        }
        if (data.getSeriesTitle() != null) {
            out.write("T3  - " + data.getSeriesTitle() + "\r\n");
        }
        /* Removing abstract/description per Request from G. King in #3759
        if(description!=null) {
            out.write("AB  - " + flattenHtml(description) + "\r\n");
        } */
        for (String author : data.getAuthors()) {
            out.write("AU  - " + author + "\r\n");
        }

        if (!data.getProducers().isEmpty()) {
            for (String author : data.getProducers()) {
                out.write("A2  - " + author + "\r\n");
            }
        }
        if (!data.getFunders().isEmpty()) {
            for (String author : data.getFunders()) {
                out.write("A4  - " + author + "\r\n");
            }
        }
        if (!data.getKindsOfData().isEmpty()) {
            for (String kod : data.getKindsOfData()) {
                out.write("C3  - " + kod + "\r\n");
            }
        }
        if (!data.getDatesOfCollection().isEmpty()) {
            for (String dateRange : data.getDatesOfCollection()) {
                out.write("DA  - " + dateRange + "\r\n");
            }
        }

        if (data.getPersistentId() != null) {
            out.write("DO  - " + data.getPersistentId().toString() + "\r\n");
        }
        out.write("ET  - " + data.getVersion() + "\r\n");
        if (!data.getKeywords().isEmpty()) {
            for (String keyword : data.getKeywords()) {
                out.write("KW  - " + keyword + "\r\n");
            }
        }
        if (!data.getLanguages().isEmpty()) {
            for (String lang : data.getLanguages()) {
                out.write("LA  - " + lang + "\r\n");
            }
        }

        out.write("PY  - " + data.getYear() + "\r\n");

        if (!data.getSpatialCoverages().isEmpty()) {
            for (String coverage : data.getSpatialCoverages()) {
                out.write("RI  - " + coverage + "\r\n");
            }
        }

        out.write("SE  - " + data.getDate() + "\r\n");

        out.write("UR  - " + data.getPersistentId().toURL().toString() + "\r\n");
        out.write("PB  - " + data.getPublisher() + "\r\n");

        // a DataFile citation also includes filename und UNF, if applicable:
        if (data.getFileTitle() != null) {
            if (!data.isDirect()) {
                out.write("C1  - " + data.getFileTitle() + "\r\n");
            }
            if (data.getUNF() != null) {
                out.write("C2  - " + data.getUNF() + "\r\n");
            }
        }
        // closing element:
        out.write("ER  - \r\n");
        out.flush();
    }

    public String toEndNoteString() {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        writeAsEndNoteCitation(outStream);
        String xml = outStream.toString();
        return xml;
    }

    public CitationData getCitationData() {
        return data;
    }

    public void writeAsEndNoteCitation(OutputStream os) {

        XMLOutputFactory xmlOutputFactory = XMLOutputFactory.newInstance();
        XMLStreamWriter xmlw = null;
        try {
            xmlw = xmlOutputFactory.createXMLStreamWriter(os);
            xmlw.writeStartDocument();
            createEndNoteXML(xmlw);
            xmlw.writeEndDocument();
        } catch (XMLStreamException ex) {
            logger.error("", ex);
            throw new EJBException("ERROR occurred during creating endnote xml.", ex);
        } finally {
            try {
                if (xmlw != null) {
                    xmlw.close();
                }
            } catch (XMLStreamException ex) {
            }
        }
    }

    private void createEndNoteXML(XMLStreamWriter xmlw) throws XMLStreamException {

        xmlw.writeStartElement("xml");
        xmlw.writeStartElement("records");

        xmlw.writeStartElement("record");

        // "Ref-type" indicates which of the (numerous!) available EndNote
        // schemas this record will be interpreted as.
        // This is relatively important. Certain fields with generic
        // names like "custom1" and "custom2" become very specific things
        // in specific schemas; for example, custom1 shows as "legal notice"
        // in "Journal Article" (ref-type 84), or as "year published" in
        // "Government Document".
        // We don't want the UNF to show as a "legal notice"!
        // We have found a ref-type that works ok for our purposes -
        // "Dataset" (type 59). In this one, the fields Custom1
        // and Custom2 are not translated and just show as is.
        // And "Custom1" still beats "legal notice".
        // -- L.A. 12.12.2014 beta 10
        // and see https://github.com/IQSS/dataverse/issues/4816

        xmlw.writeStartElement("ref-type");
        xmlw.writeAttribute("name", "Dataset");
        xmlw.writeCharacters("59");
        xmlw.writeEndElement(); // ref-type

        xmlw.writeStartElement("contributors");
        if (!data.getAuthors().isEmpty()) {
            xmlw.writeStartElement("authors");
            for (String author : data.getAuthors()) {
                xmlw.writeStartElement("author");
                xmlw.writeCharacters(author);
                xmlw.writeEndElement(); // author
            }
            xmlw.writeEndElement(); // authors
        }
        if (!data.getProducers().isEmpty()) {
            xmlw.writeStartElement("secondary-authors");
            for (String producer : data.getProducers()) {
                xmlw.writeStartElement("author");
                xmlw.writeCharacters(producer);
                xmlw.writeEndElement(); // author
            }
            xmlw.writeEndElement(); // secondary-authors
        }
        if (!data.getFunders().isEmpty()) {
            xmlw.writeStartElement("subsidiary-authors");
            for (String funder : data.getFunders()) {
                xmlw.writeStartElement("author");
                xmlw.writeCharacters(funder);
                xmlw.writeEndElement(); // author
            }
            xmlw.writeEndElement(); // subsidiary-authors
        }
        xmlw.writeEndElement(); // contributors

        xmlw.writeStartElement("titles");
        if ((data.getFileTitle() != null) && data.isDirect()) {
            xmlw.writeStartElement("title");
            xmlw.writeCharacters(data.getFileTitle());
            xmlw.writeEndElement(); // title
            xmlw.writeStartElement("secondary-title");
            xmlw.writeCharacters(data.getTitle());
            xmlw.writeEndElement(); // secondary-title
        } else {
            xmlw.writeStartElement("title");
            xmlw.writeCharacters(data.getTitle());
            xmlw.writeEndElement(); // title
        }

        if (data.getSeriesTitle() != null) {
            xmlw.writeStartElement("tertiary-title");
            xmlw.writeCharacters(data.getSeriesTitle());
            xmlw.writeEndElement(); // tertiary-title
        }
        xmlw.writeEndElement(); // titles

        xmlw.writeStartElement("section");
        String sectionString;
        sectionString = new SimpleDateFormat("yyyy-MM-dd").format(data.getDate());

        xmlw.writeCharacters(sectionString);
        xmlw.writeEndElement(); // section
/* Removing abstract/description per Request from G. King in #3759
        xmlw.writeStartElement("abstract");
        if(description!=null) {
            xmlw.writeCharacters(flattenHtml(description));
        }
        xmlw.writeEndElement(); // abstract
         */

        xmlw.writeStartElement("dates");
        xmlw.writeStartElement("year");
        xmlw.writeCharacters(data.getYear());
        xmlw.writeEndElement(); // year
        if (!data.getDatesOfCollection().isEmpty()) {
            xmlw.writeStartElement("pub-dates");
            for (String dateRange : data.getDatesOfCollection()) {
                xmlw.writeStartElement("date");
                xmlw.writeCharacters(dateRange);
                xmlw.writeEndElement(); // date
            }
            xmlw.writeEndElement(); // pub-dates
        }
        xmlw.writeEndElement(); // dates

        xmlw.writeStartElement("edition");
        xmlw.writeCharacters(data.getVersion());
        xmlw.writeEndElement(); // edition

        if (!data.getKeywords().isEmpty()) {
            xmlw.writeStartElement("keywords");
            for (String keyword : data.getKeywords()) {
                xmlw.writeStartElement("keyword");
                xmlw.writeCharacters(keyword);
                xmlw.writeEndElement(); // keyword
            }
            xmlw.writeEndElement(); // keywords
        }
        if (!data.getKindsOfData().isEmpty()) {
            for (String kod : data.getKindsOfData()) {
                xmlw.writeStartElement("custom3");
                xmlw.writeCharacters(kod);
                xmlw.writeEndElement(); // custom3
            }
        }
        if (!data.getLanguages().isEmpty()) {
            for (String lang : data.getLanguages()) {
                xmlw.writeStartElement("language");
                xmlw.writeCharacters(lang);
                xmlw.writeEndElement(); // language
            }
        }
        xmlw.writeStartElement("publisher");
        xmlw.writeCharacters(data.getPublisher());
        xmlw.writeEndElement(); // publisher

        if (!data.getSpatialCoverages().isEmpty()) {
            for (String coverage : data.getSpatialCoverages()) {
                xmlw.writeStartElement("reviewed-item");
                xmlw.writeCharacters(coverage);
                xmlw.writeEndElement(); // reviewed-item
            }
        }

        xmlw.writeStartElement("urls");
        xmlw.writeStartElement("related-urls");
        xmlw.writeStartElement("url");
        xmlw.writeCharacters(data.getPersistentId().toURL().toString());
        xmlw.writeEndElement(); // url
        xmlw.writeEndElement(); // related-urls
        xmlw.writeEndElement(); // urls

        // a DataFile citation also includes the filename and (for Tabular
        // files) the UNF signature, that we put into the custom1 and custom2
        // fields respectively:

        if (data.getFileTitle() != null) {
            xmlw.writeStartElement("custom1");
            xmlw.writeCharacters(data.getFileTitle());
            xmlw.writeEndElement(); // custom1

            if (data.getUNF() != null) {
                xmlw.writeStartElement("custom2");
                xmlw.writeCharacters(data.getUNF());
                xmlw.writeEndElement(); // custom2
            }
        }
        if (data.getPersistentId() != null) {
            GlobalId persistentId = data.getPersistentId();
            xmlw.writeStartElement("electronic-resource-num");
            String electResourceNum = persistentId.getProtocol() + "/" + persistentId.getAuthority() + "/"
                    + persistentId.getIdentifier();
            xmlw.writeCharacters(electResourceNum);
            xmlw.writeEndElement();
        }
        //<electronic-resource-num>10.3886/ICPSR03259.v1</electronic-resource-num>
        xmlw.writeEndElement(); // record

        xmlw.writeEndElement(); // records
        xmlw.writeEndElement(); // xml

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
            return new StringBuilder(wrapperStart).append(escapeHtml ? StringEscapeUtils.escapeHtml4(value) : value)
                    .append(wrapperEnd).toString();
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

    /**
     * This method flattens html for the textual export formats.
     * It removes <b> and <i> tags, replaces <br>, <p> and headers <hX> with
     * line breaks, converts lists to form where items start with an indented '*  ',
     * and converts links to simple text showing the label and, if different,
     * the url in parenthesis after it. Since these operations may create
     * multiple line breaks, a final step limits the changes and compacts multiple
     * line breaks into one.
     *
     * @param html input string
     * @return the flattened text output
     */
    private String flattenHtml(String html) {
        html = html.replaceAll("<[pP]>", "\r\n");
        html = html.replaceAll("<\\/[pP]>", "\r\n");
        html = html.replaceAll("<[hH]\\d>", "\r\n");
        html = html.replaceAll("<\\/[hH]\\d>", "\r\n");
        html = html.replaceAll("<[\\/]?[bB]>", "");
        html = html.replaceAll("<[\\/]?[iI]>", "\r\n");

        html = html.replaceAll("<[bB][rR][\\/]?>", "\r\n");
        html = html.replaceAll("<[uU][lL]>", "\r\n");
        html = html.replaceAll("<\\/[uU][lL]>", "\r\n");
        html = html.replaceAll("<[lL][iI]>", "\t*  ");
        html = html.replaceAll("<\\/[lL][iI]>", "\r\n");
        Pattern p = Pattern.compile("<a\\W+href=\\\"(.*?)\\\".*?>(.*?)<\\/a>");
        Matcher m = p.matcher(html);
        String url = null;
        String label = null;
        while (m.find()) {
            url = m.group(1); // this variable should contain the link URL
            label = m.group(2); // this variable should contain the label
            //display either the label or label(url)
            if (!url.equals(label)) {
                label = label + "(" + url + ")";
            }
            html = html.replaceFirst("<a\\W+href=\\\"(.*?)\\\".*?>(.*?)<\\/a>", label);
        }
        //Note, this does not affect single '\n' chars originally in the text
        html = html.replaceAll("(\\r\\n?)+", "\r\n");

        return html;
    }

}
