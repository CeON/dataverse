package edu.harvard.iq.dataverse.persistence.datafile;

import edu.harvard.iq.dataverse.common.files.mime.TextMimeType;
import edu.harvard.iq.dataverse.persistence.datafile.ExternalTool;
import org.junit.jupiter.api.Test;

import javax.json.JsonObject;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExternalToolTest {

    @Test
    public void testToJson() {
        System.out.println("toJson");
        String displayName = "myDisplayName";
        String description = "myDescription";
        ExternalTool.Type type = ExternalTool.Type.EXPLORE;
        String toolUrl = "http://example.com";
        String toolParameters = "{}";
        ExternalTool externalTool = new ExternalTool(displayName, description, type, toolUrl, toolParameters, TextMimeType.TSV_ALT.getMimeValue());
        externalTool.setId(42l);
        JsonObject jsonObject = externalTool.toJson().build();
        System.out.println("result: " + jsonObject);
        assertEquals("myDisplayName", jsonObject.getString(ExternalTool.DISPLAY_NAME));
        assertEquals("myDescription", jsonObject.getString(ExternalTool.DESCRIPTION));
        assertEquals("explore", jsonObject.getString(ExternalTool.TYPE));
        assertEquals("http://example.com", jsonObject.getString(ExternalTool.TOOL_URL));
        assertEquals("{}", jsonObject.getString(ExternalTool.TOOL_PARAMETERS));
        assertEquals(TextMimeType.TSV_ALT.getMimeValue(), jsonObject.getString(ExternalTool.CONTENT_TYPE));
    }

}
