package edu.harvard.iq.dataverse.datafile.file;

import edu.harvard.iq.dataverse.DataverseRequestServiceBean;
import edu.harvard.iq.dataverse.EjbDataverseEngine;
import edu.harvard.iq.dataverse.engine.command.impl.DeleteProvJsonCommand;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.provenance.UpdatesEntry;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Set;

import static io.vavr.collection.HashMap.of;

@ExtendWith(MockitoExtension.class)
class FileMetadataServiceTest {

    @InjectMocks
    private FileMetadataService fileMetadataService;

    @Mock
    private EjbDataverseEngine commandEngine;

    @Mock
    private DataverseRequestServiceBean dvRequestService;

    @Test
    public void updateFileMetadataWithProvFreeForm() {
        //given
        String checksum = "testChecksum";
        String provFree = "provFree";

        DataFile dataFile = new DataFile();
        dataFile.setChecksumValue(checksum);
        FileMetadata fileMetadata = new FileMetadata();
        fileMetadata.setDataFile(dataFile);

        HashMap<String, UpdatesEntry> provenanceUpdates = of(checksum, new UpdatesEntry(dataFile, "prov", false, provFree))
                .toJavaMap();

        //when
        FileMetadata updatedFile = fileMetadataService.updateFileMetadataWithProvFreeForm(fileMetadata, provenanceUpdates);

        //then
        Assert.assertEquals(provFree, updatedFile.getProvFreeForm());
    }

    @SuppressWarnings("SimplifiableJUnitAssertion")
    @Test
    public void updateFileMetadataWithProvFreeForm_WithNoProvenance() {
        //given
        String checksum = "testChecksum";

        DataFile dataFile = new DataFile();
        dataFile.setChecksumValue(checksum);
        FileMetadata fileMetadata = new FileMetadata();
        fileMetadata.setDataFile(dataFile);

        //when
        fileMetadataService.updateFileMetadataWithProvFreeForm(fileMetadata, new HashMap<>());

        //then
        Assert.assertEquals(null, fileMetadata.getProvFreeForm());
    }

    @Test
    public void manageProvJson() {
        //given
        String checksum = "testChecksum";

        DataFile dataFile = new DataFile();
        dataFile.setChecksumValue(checksum);
        dataFile.setProvEntityName("testEntity");
        FileMetadata fileMetadata = new FileMetadata();
        fileMetadata.setDataFile(dataFile);

        HashMap<String, UpdatesEntry> provenanceUpdates = of(checksum, new UpdatesEntry(dataFile, "prov", true, "provFree"))
                .toJavaMap();

        //when
        DataFile value = new DataFile();
        value.setProvEntityName("");
        Mockito.when(commandEngine.submit(Mockito.any(DeleteProvJsonCommand.class))).thenReturn(value);
        Set<DataFile> updatedFiles = fileMetadataService.manageProvJson(true, fileMetadata, provenanceUpdates);

        //then
        Assert.assertEquals("", updatedFiles.iterator().next().getProvEntityName());
        Mockito.verify(commandEngine, Mockito.times(1)).submit(Mockito.any(DeleteProvJsonCommand.class));

    }
}