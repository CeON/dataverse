package edu.harvard.iq.dataverse.persistence.config;

import edu.harvard.iq.dataverse.persistence.dataset.DatasetField;
import org.eclipse.persistence.config.DescriptorCustomizer;
import org.eclipse.persistence.descriptors.ClassDescriptor;
import org.eclipse.persistence.expressions.Expression;
import org.eclipse.persistence.expressions.ExpressionBuilder;
import org.eclipse.persistence.mappings.DatabaseMapping;
import org.eclipse.persistence.mappings.OneToManyMapping;

public class DatasetFieldsMappingCustomizer implements DescriptorCustomizer {

    @Override
    public void customize(ClassDescriptor classDescriptor) throws Exception {
        DatabaseMapping mapping = classDescriptor.getMappingForAttributeName("datasetFields");
        if (!mapping.isOneToManyMapping()) {
            return;
        }
        OneToManyMapping datasetFieldsMapping = (OneToManyMapping) mapping;
        Expression currentCriteria = datasetFieldsMapping.buildSelectionCriteria();
        ExpressionBuilder expressionBuilder = currentCriteria.getBuilder();
        Expression additionalCriteria = expressionBuilder.get("source").equal(DatasetField.DEFAULT_SOURCE);
        datasetFieldsMapping.setSelectionCriteria(currentCriteria.and(additionalCriteria));
    }
}
