package edu.harvard.iq.dataverse.datafile;

import edu.harvard.iq.dataverse.datasetutility.FileReplacePageHelper;
import org.apache.commons.lang.StringUtils;

import javax.ejb.Stateless;
import java.io.InputStream;
import java.io.Serializable;

@Stateless
public class UploadHandler implements Serializable {

    public String handleReplaceFileUpload(InputStream inputStream, String fileName,
                                          String contentType, FileReplacePageHelper fileReplacePageHelper)  {

        if (fileReplacePageHelper.handleNativeFileUpload(inputStream,
                                                         fileName,
                                                         contentType)) {

            if (fileReplacePageHelper.hasContentTypeWarning()) {
                return fileReplacePageHelper.getContentTypeWarning();
            }

        }

        return StringUtils.EMPTY;
    }

}
