/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.harvard.iq.dataverse.ingest;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author rmp553
 */
public class IngestableDataCheckerTest {

    @TempDir
    Path tempFolder;

    public IngestableDataCheckerTest() {
    }

    @BeforeAll
    public static void setUpClass() {
    }

    @AfterAll
    public static void tearDownClass() {

    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {


    }


    private File createTempFile(String filename, String fileContents) throws IOException {

        if (filename == null) {
            return null;
        }
        File fh = tempFolder.resolve(filename).toFile();
        fh.createNewFile();

        if (fileContents != null) {
            FileUtils.writeStringToFile(fh, fileContents);
        }

        return fh;
    }

    private MappedByteBuffer createTempFileAndGetBuffer(String filename, String fileContents) throws IOException {

        File fh = this.createTempFile(filename, fileContents);

        FileChannel srcChannel = new FileInputStream(fh).getChannel();

        // create a read-only MappedByteBuffer
        MappedByteBuffer buff = srcChannel.map(FileChannel.MapMode.READ_ONLY, 0, fh.length());

        return buff;
    }

    private void msg(String m) {
        System.out.println(m);
    }


    private void msgt(String m) {
        msg("---------------------------");
        msg(m);
        msg("---------------------------");
    }

    /**
     * Test of testDTAformat method, of class IngestableDataChecker.
     *
     * @throws java.io.IOException
     */
    @Test
    public void testTestDTAformat(@TempDir Path tempFolder) throws IOException {
        msgt("(1) testDTAformat");

        msgt("(1a) Mock a Legit Stata File (application/x-stata)");
        MappedByteBuffer buff = createTempFileAndGetBuffer("testDTA.txt", "l   ");

        IngestableDataChecker instance = new IngestableDataChecker();
        String result = instance.testDTAformat(buff);
        msg("result 1a: " + result);
        assertEquals(result, "application/x-stata");


        msgt("(1b) File is empty string (non-DTA)");
        buff = createTempFileAndGetBuffer("notDTA.txt", "");
        instance = new IngestableDataChecker();
        result = instance.testDTAformat(buff);
        msg("result 1b: " + result);
        assertEquals(result, null);


        msgt("(1c) File is some random text (non-DTA)");
        buff = createTempFileAndGetBuffer("notDTA2.txt", "hello-non-stata-file-how-are-you");
        instance = new IngestableDataChecker();
        result = instance.testDTAformat(buff);
        msg("result 1c: " + result);
        assertEquals(result, null);


        msgt("(1d) Mock a Legit Stata File with STATA_13_HEADER");
        buff = createTempFileAndGetBuffer("testDTA2.txt", IngestableDataChecker.STATA_13_HEADER);
        result = instance.testDTAformat(buff);
        msg("result 1d: " + result);
        assertEquals(result, "application/x-stata-13");


    }


    /**
     * Test of testSAVformat method, of class IngestableDataChecker.
     */
    @Test
    public void testTestSAVformat() throws IOException {
        msgt("(2) testSAVformat");

        msgt("(2a) Mock a Legit SPSS-SAV File (application/x-spss-sav)");
        MappedByteBuffer buff = createTempFileAndGetBuffer("testSAV.txt", "$FL2");

        IngestableDataChecker instance = new IngestableDataChecker();
        String result = instance.testSAVformat(buff);
        msg("result 2a: " + result);
        assertEquals(result, "application/x-spss-sav");

        msgt("(2b) File is empty string");
        buff = createTempFileAndGetBuffer("testNotSAV-empty.txt", "");

        instance = new IngestableDataChecker();
        result = instance.testSAVformat(buff);
        msg("result 2b: " + result);
        assertEquals(result, null);

        msgt("(2c) File is non-SAV string");
        buff = createTempFileAndGetBuffer("testNotSAV-string.txt", "i-am-not-a-x-spss-sav-file");
        instance = new IngestableDataChecker();
        result = instance.testSAVformat(buff);
        msg("result 2c: " + result);
        assertEquals(result, null);

    }

}
