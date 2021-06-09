package edu.harvard.iq.dataverse.ror;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import edu.harvard.iq.dataverse.api.dto.RorEntryDTO;
import edu.harvard.iq.dataverse.util.FileUtil;
import org.apache.commons.lang3.StringUtils;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;

import javax.ejb.Stateless;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Stateless
public class RorDataService {

    // -------------------- CONSTRUCTORS --------------------

    public RorDataService() { }

    // -------------------- LOGIC --------------------

    public void refreshRorData(File file, FormDataContentDisposition header) {
        File processed = selectFileToProcess(file, header);
        try (FileReader fileReader = new FileReader(processed);
            JsonReader jsonReader = new JsonReader(fileReader)) {
            jsonReader.beginArray();
            Gson gson = new Gson();
            RorEntryDTO rorEntryDTO = null;
            Set<RorEntryDTO> special = new HashSet<>();
            while (jsonReader.hasNext()) {
                 rorEntryDTO = gson.fromJson(jsonReader, RorEntryDTO.class);
                 if (rorEntryDTO.getLinks().length > 1 || rorEntryDTO.getCities().size() > 1) {
                     special.add(rorEntryDTO);
                 }
            }
            special.size();
            System.out.println(rorEntryDTO);
            jsonReader.endArray();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            processed.delete();
        }
    }

    // -------------------- PRIVATE --------------------

    private File selectFileToProcess(File file, FormDataContentDisposition header) {
        if (hasExtension(header.getFileName(), ".zip")) {
            return decompressJson(file);
        } else if (hasExtension(header.getFileName(), ".json")) {
            return file;
        } else {
            throw new IllegalArgumentException("No valid file uploaded (only .json or zipped .json");
        }
    }

    private File decompressJson(File zipped) {
        File decompressed = null;
        try (FileInputStream fileInputStream = new FileInputStream(zipped);
            ZipInputStream zipInputStream = new ZipInputStream(fileInputStream)) {
            ZipEntry currentEntry;
            while ((currentEntry = zipInputStream.getNextEntry()) != null) {
                if (!currentEntry.isDirectory() && hasExtension(currentEntry.getName(), ".json")) {
                    break;
                }
                zipInputStream.closeEntry();
            }
            if (currentEntry != null) {
                decompressed = FileUtil.inputStreamToFile(zipInputStream, 8192);
                zipInputStream.closeEntry();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            zipped.delete();
        }

        return decompressed;
    }

    private boolean hasExtension(String fileName, String extension) {
        return StringUtils.isNotBlank(fileName)
                ? fileName.toLowerCase().endsWith(extension)
                : false;
    }
}
