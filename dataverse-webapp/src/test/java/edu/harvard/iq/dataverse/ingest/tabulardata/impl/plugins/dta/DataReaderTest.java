package edu.harvard.iq.dataverse.ingest.tabulardata.impl.plugins.dta;

import edu.harvard.iq.dataverse.metrics.MetricsUtil;
import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author oscardssmith
 */
public class DataReaderTest {
    @Test
    public void testReadInt() throws IOException {
        byte[] bytes = ByteBuffer.allocate(4).putInt(-1).array();
        BufferedInputStream stream = new BufferedInputStream(new ByteArrayInputStream(bytes));
        DataReader reader = new DataReader(stream);
        reader.setLSF(true);
        assertEquals(-1, reader.readInt());
    }

    @Test
    public void testReadUInt() throws IOException {
        byte[] bytes = ByteBuffer.allocate(4).putInt(-1).array();
        BufferedInputStream stream = new BufferedInputStream(new ByteArrayInputStream(bytes));
        DataReader reader = new DataReader(stream);
        reader.setLSF(true);
        assertEquals(4294967295L, reader.readUInt());
    }

    @Test
    public void testReadUShort() throws IOException {
        byte[] bytes = ByteBuffer.allocate(2).putShort((short) -1).array();
        BufferedInputStream stream = new BufferedInputStream(new ByteArrayInputStream(bytes));
        DataReader reader = new DataReader(stream);
        reader.setLSF(true);
        assertEquals(65535, reader.readUShort());
    }

    @Test
    public void testReadULong() throws IOException {
        byte[] bytes = {-1, -1, -1, -1, -1, -1, -1, -1,};
        BufferedInputStream stream = new BufferedInputStream(new ByteArrayInputStream(bytes));
        DataReader reader = new DataReader(stream);
        reader.setLSF(true);
        // This should throw until we figure out what to do with uLongs that are large
        assertThrows(IOException.class, () -> assertEquals(-1, reader.readULong()));
    }
}
