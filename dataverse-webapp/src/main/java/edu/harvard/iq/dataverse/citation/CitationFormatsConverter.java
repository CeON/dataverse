package edu.harvard.iq.dataverse.citation;

import java.io.IOException;
import java.io.OutputStream;

public interface CitationFormatsConverter {

    String toString(CitationData data, boolean escapeHtml);

    String toBibtexString(CitationData data);

    void writeAsBibtexCitation(CitationData data, OutputStream os) throws IOException;

    String toRISString(CitationData data);

    void writeAsRISCitation(CitationData data, OutputStream os) throws IOException;

    String toEndNoteString(CitationData data);

    void writeAsEndNoteCitation(CitationData data, OutputStream os);
}
