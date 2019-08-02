package edu.harvard.iq.dataverse.externaltools;

import edu.harvard.iq.dataverse.DataFileServiceBean;
import edu.harvard.iq.dataverse.common.files.mime.TextMimeType;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.ExternalTool;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.user.ApiToken;
import org.junit.Test;

import javax.json.Json;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ExternalToolHandlerTest {

    // TODO: It would probably be better to split these into individual tests.
    @Test
    public void testGetToolUrlWithOptionalQueryParameters() {
        ExternalTool.Type type = ExternalTool.Type.EXPLORE;
        String toolUrl = "http://example.com";
        ExternalTool externalTool = new ExternalTool("displayName", "description", type, toolUrl, "{}", TextMimeType.TSV_ALT.getMimeValue());

        // One query parameter, not a reserved word, no {fileId} (required) used.
        externalTool.setToolParameters(Json.createObjectBuilder()
                                               .add("queryParameters", Json.createArrayBuilder()
                                                       .add(Json.createObjectBuilder()
                                                                    .add("mode", "mode1")
                                                       )
                                               )
                                               .build().toString());
        DataFile nullDataFile = null;
        ApiToken nullApiToken = null;
        Exception expectedException1 = null;
        try {
            ExternalToolHandler externalToolHandler1 = new ExternalToolHandler(externalTool, nullDataFile, nullApiToken);
        } catch (Exception ex) {
            expectedException1 = ex;
        }
        assertNotNull(expectedException1);
        assertEquals("A DataFile is required.", expectedException1.getMessage());

        // Two query parameters.
        externalTool.setToolParameters(Json.createObjectBuilder()
                                               .add("queryParameters", Json.createArrayBuilder()
                                                       .add(Json.createObjectBuilder()
                                                                    .add("mode", "mode1")
                                                       )
                                                       .add(Json.createObjectBuilder()
                                                                    .add("key2", "value2")
                                                       )
                                               )
                                               .build().toString());
        Exception expectedException2 = null;
        try {
            ExternalToolHandler externalToolHandler2 = new ExternalToolHandler(externalTool, nullDataFile, nullApiToken);
        } catch (Exception ex) {
            expectedException2 = ex;
        }
        assertNotNull(expectedException2);
        assertEquals("A DataFile is required.", expectedException2.getMessage());

        // Two query parameters, both reserved words, one is {fileId} which is required.
        externalTool.setToolParameters(Json.createObjectBuilder()
                                               .add("queryParameters", Json.createArrayBuilder()
                                                       .add(Json.createObjectBuilder()
                                                                    .add("key1", "{fileId}")
                                                       )
                                                       .add(Json.createObjectBuilder()
                                                                    .add("key2", "{apiToken}")
                                                       )
                                               )
                                               .build().toString());
        DataFile dataFile = new DataFile();
        dataFile.setId(42l);
        FileMetadata fmd = new FileMetadata();
        DatasetVersion dv = new DatasetVersion();
        Dataset ds = new Dataset();
        dv.setDataset(ds);
        fmd.setDatasetVersion(dv);
        List<FileMetadata> fmdl = new ArrayList<FileMetadata>();
        fmdl.add(fmd);
        dataFile.setFileMetadatas(fmdl);
        ApiToken apiToken = new ApiToken();
        apiToken.setTokenString("7196b5ce-f200-4286-8809-03ffdbc255d7");
        ExternalToolHandler externalToolHandler3 = new ExternalToolHandler(externalTool, dataFile, apiToken);
        String result3 = externalToolHandler3.getQueryParametersForUrl("https://localhost:8080");
        System.out.println("result3: " + result3);
        assertEquals("?key1=42&key2=7196b5ce-f200-4286-8809-03ffdbc255d7", result3);

        // Two query parameters, both reserved words, no apiToken
        externalTool.setToolParameters(Json.createObjectBuilder()
                                               .add("queryParameters", Json.createArrayBuilder()
                                                       .add(Json.createObjectBuilder()
                                                                    .add("key1", "{fileId}")
                                                       )
                                                       .add(Json.createObjectBuilder()
                                                                    .add("key2", "{apiToken}")
                                                       )
                                               )
                                               .build().toString());
        ExternalToolHandler externalToolHandler4 = new ExternalToolHandler(externalTool, dataFile, nullApiToken);
        String result4 = externalToolHandler4.getQueryParametersForUrl( "https://localhost:8080");
        System.out.println("result4: " + result4);
        assertEquals("?key1=42", result4);

        // Two query parameters, attempt to use a reserved word that doesn't exist.
        externalTool.setToolParameters(Json.createObjectBuilder()
                                               .add("queryParameters", Json.createArrayBuilder()
                                                       .add(Json.createObjectBuilder()
                                                                    .add("key1", "{junk}")
                                                       )
                                                       .add(Json.createObjectBuilder()
                                                                    .add("key2", "{apiToken}")
                                                       )
                                               )
                                               .build().toString());
        Exception expectedException = null;
        try {
            ExternalToolHandler externalToolHandler5 = new ExternalToolHandler(externalTool, dataFile, nullApiToken);
            String result5 = externalToolHandler5.getQueryParametersForUrl( "https://localhost:8080");
            System.out.println("result5: " + result5);
        } catch (Exception ex) {
            System.out.println("Exception caught: " + ex);
            expectedException = ex;
        }
        assertNotNull(expectedException);
        assertEquals("Unknown reserved word: {junk}", expectedException.getMessage());

    }

}
