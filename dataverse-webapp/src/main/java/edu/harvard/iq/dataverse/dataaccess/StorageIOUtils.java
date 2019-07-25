package edu.harvard.iq.dataverse.dataaccess;

import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class StorageIOUtils {


    public static byte[] fetchAuxFileAsBytes(StorageIO<?> storageIO, String auxItemTag) throws IOException {
        try (InputStream is = storageIO.getAuxFileAsInputStream(auxItemTag)) {
            return IOUtils.toByteArray(is);
        }
    }
    
    public static void saveBytesAsAuxFile(StorageIO<?> storageIO, byte[] bytes, String auxItemTag) throws IOException {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes)) {
            storageIO.saveInputStreamAsAux(inputStream, auxItemTag, Long.valueOf(bytes.length));
        }
    }
}
