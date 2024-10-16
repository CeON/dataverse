package edu.harvard.iq.dataverse.harvest.server.xoai;

import com.lyncode.xml.exceptions.XmlWriteException;
import org.dspace.xoai.model.oaipmh.Metadata;
import org.dspace.xoai.xml.XmlWriter;

/**
 * @author Leonid Andreev
 */
public class Xmetadata extends Metadata {


    public Xmetadata(String value) {
        super(value);
    }


    @Override
    public void write(XmlWriter writer) throws XmlWriteException {
        // Do nothing!
        // - rather than writing Metadata as an XML writer stram, we will write
        // the pre-exported *and pre-validated* content as a byte stream, directly.
    }

}
