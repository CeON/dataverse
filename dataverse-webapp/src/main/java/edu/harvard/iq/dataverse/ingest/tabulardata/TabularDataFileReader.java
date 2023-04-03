/*
   Copyright (C) 2005-2012, by the President and Fellows of Harvard College.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

   Dataverse Network - A web application to share, preserve and analyze research data.
   Developed at the Institute for Quantitative Social Science, Harvard University.
   Version 3.0.
*/

package edu.harvard.iq.dataverse.ingest.tabulardata;

import edu.harvard.iq.dataverse.ingest.tabulardata.spi.TabularDataFileReaderSpi;
import io.vavr.Tuple2;
import org.apache.commons.lang.StringUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;

import static java.lang.System.out;

//import edu.harvard.iq.dataverse.ingest.plugin.metadata.*;

/**
 * An abstract superclass for reading and writing of a statistical data file.
 * A class that implements a reader in the context of StatData I/O
 * framework must subclasse this superclass.
 *
 * @author akio sone
 */
public abstract class TabularDataFileReader {
    private static final Logger logger = Logger.getLogger(TabularDataFileReader.class.getSimpleName());

    protected String dataLanguageEncoding;

    protected TabularDataFileReaderSpi originatingProvider;

    // -------------------- CONSTRUCTORS --------------------

    protected TabularDataFileReader(TabularDataFileReaderSpi originatingProvider) {
        this.originatingProvider = originatingProvider;
    }

    public TabularDataFileReader() {
    }

    // -------------------- GETTERS --------------------

    public TabularDataFileReaderSpi getOriginatingProvider() {
        return originatingProvider;
    }

    public String getFormatName() throws IOException {
        return originatingProvider.getFormatNames()[0];
    }

    public String getDataLanguageEncoding() {
        return dataLanguageEncoding;
    }

    // -------------------- LOGIC --------------------

    /**
     * Reads the statistical data file from a supplied
     * <code>BufferedInputStream</code> and
     * returns its contents as a <code>SDIOData</code>.
     * <p>
     * The second parameter, dataFile has been added to the method
     * declaration in for implementation by plugins that provide
     * 2 file ingest, with the data set metadata in one file
     * (for ex., SPSS control card) and the raw data in a separate
     * file (character-delimited, fixed-field, etc.)
     *
     * @param streamAndFile  a <code>BufferedInputStream</code>
     *                 where a statistical data file is connected
     *                 and the statistical data file itself.
     * @param dataFile <code>File</code> optional parameter
     *                 representing the raw data file. For the plugins that only support
     *                 single file ingest, this should be set to null.
     * @return reading results as a <code>SDIOData</code>
     * @throws java.io.IOException if a reading error occurs.
     */
    public abstract TabularDataIngest read(Tuple2<BufferedInputStream, File> streamAndFile, File dataFile)
            throws IOException;


    public boolean isValid(File ddiFile) throws IOException {
        return false;
    }

    public void printHexDump(byte[] buff, String hdr) {
        int counter = 0;
        if (hdr != null) {
            out.println(hdr);
        }
        for (int i = 0; i < buff.length; i++) {
            counter = i + 1;
            out.print(String.format("%02X ", buff[i]));
            if (counter % 16 == 0) {
                out.println();
            } else {
                if (counter % 8 == 0) {
                    out.print(" ");
                }
            }
        }
        out.println();
    }

    /**
     * Returns a new null-character-free <code>String</code> object
     * from an original <code>String</code> one that may contains
     * null characters.
     *
     * @param rawString a<code>String</code> object
     * @return a new, null-character-free <code>String</code> object
     */
    protected String getNullStrippedString(String rawString) {
        String nullRemovedString = null;
        int null_position = rawString.indexOf(0);
        if (null_position >= 0) {
            // string is terminated by the null
            nullRemovedString = rawString.substring(0, null_position);
        } else {
            // not null-termiated (sometimes space-paddded, instead)
            nullRemovedString = rawString;
        }
        return nullRemovedString;
    }

    protected Charset selectCharset() {
        if (StringUtils.isNotBlank(getDataLanguageEncoding())) {
            try {
                return Charset.forName(getDataLanguageEncoding());
            } catch (IllegalArgumentException iae) {
                logger.log(Level.WARNING,
                        String.format("Exception while trying to initialize selected charset [%s]. Using UTF-8.", getDataLanguageEncoding()),
                        iae);
                return StandardCharsets.UTF_8;
            }
        } else {
            return StandardCharsets.UTF_8;
        }
    }

    protected String escapeCharacterString(String rawString) {
        /*
         * Some special characters, like new lines and tabs need to
         * be escaped - otherwise they will break our TAB file
         * structure!
         * But before we escape anything, all the back slashes
         * already in the string need to be escaped themselves.
         */
        String escapedString = rawString.replace("\\", "\\\\");
        // escape quotes:
        escapedString = escapedString.replaceAll("\"", Matcher.quoteReplacement("\\\""));
        // escape tabs and new lines:
        escapedString = escapedString.replaceAll("\t", Matcher.quoteReplacement("\\t"));
        escapedString = escapedString.replaceAll("\n", Matcher.quoteReplacement("\\n"));
        escapedString = escapedString.replaceAll("\r", Matcher.quoteReplacement("\\r"));

        // the escaped version of the string is stored in the tab file
        // enclosed in double-quotes; this is in order to be able
        // to differentiate between an empty string (tab-delimited empty string in
        // double quotes) and a missing value (tab-delimited empty string).

        escapedString = "\"" + escapedString + "\"";

        return escapedString;
    }

    // -------------------- SETTERS --------------------

    public void setDataLanguageEncoding(String dataLanguageEncoding) {
        this.dataLanguageEncoding = dataLanguageEncoding;
    }
}
