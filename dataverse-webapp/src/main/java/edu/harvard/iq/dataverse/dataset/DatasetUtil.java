package edu.harvard.iq.dataverse.dataset;

import edu.harvard.iq.dataverse.DatasetField;
import edu.harvard.iq.dataverse.DatasetVersion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatasetUtil {

    public static List<DatasetField> getDatasetSummaryFields(DatasetVersion datasetVersion, List<String> customFieldList) {

        List<DatasetField> datasetFields = new ArrayList<>();

        Map<String, DatasetField> DatasetFieldsSet = new HashMap<>();

        for (DatasetField dsf : datasetVersion.getFlatDatasetFields()) {
            DatasetFieldsSet.put(dsf.getDatasetFieldType().getName(), dsf);
        }

        for (String cfl : customFieldList) {
            DatasetField df = DatasetFieldsSet.get(cfl);
            if (df != null) {
                datasetFields.add(df);
            }
        }

        return datasetFields;
    }

}
