package edu.harvard.iq.dataverse.persistence.dataset;

import edu.harvard.iq.dataverse.common.DatasetFieldConstant;
import edu.harvard.iq.dataverse.persistence.MocksFactory;
import jersey.repackaged.com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static edu.harvard.iq.dataverse.persistence.MockMetadataFactory.extractFieldTypeByName;
import static edu.harvard.iq.dataverse.persistence.MockMetadataFactory.makeAuthorFieldType;
import static edu.harvard.iq.dataverse.persistence.MockMetadataFactory.makeDatasetField;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DatasetFieldUtilTest {

    // -------------------- TESTS --------------------

    @Test
    public void getFlatDatasetFields() {
        // given
        DatasetFieldType fieldType1Child1 = MocksFactory.makeChildDatasetFieldType("field1Child1",
                                                                                   FieldType.TEXT,
                                                                                   false);
        DatasetFieldType fieldType1Child2 = MocksFactory.makeChildDatasetFieldType("field1Child1",
                                                                                   FieldType.TEXT,
                                                                                   false);
        DatasetFieldType fieldType1 = MocksFactory.makeComplexDatasetFieldType("field1", false, new MetadataBlock(),
                                                                               fieldType1Child1, fieldType1Child2);
        DatasetFieldType fieldType2 = MocksFactory.makeDatasetFieldType("field2",
                                                                        FieldType.TEXT,
                                                                        false,
                                                                        new MetadataBlock());

        List<DatasetField> field1 = MocksFactory.makeEmptyDatasetFields(fieldType1, 2);
        List<DatasetField> field2 = MocksFactory.makeEmptyDatasetFields(fieldType2, 5);

        List<DatasetField> datasetFields = Stream.concat(field1.stream(), field2.stream())
                .collect(Collectors.toList());
        ;

        // when
        List<DatasetField> flatDatasetFields = DatasetFieldUtil.getFlatDatasetFields(datasetFields);

        // then
        assertThat(flatDatasetFields, contains(
                field1.get(0),
                field1.get(1),
                field2.get(0),
                field2.get(1),
                field2.get(2),
                field2.get(3),
                field2.get(4)));
    }

    @Test
    public void copyDatasetFields() {
        // given
        MetadataBlock block1 = MocksFactory.makeMetadataBlock("block1", "Block 1");
        MetadataBlock block2 = MocksFactory.makeMetadataBlock("block2", "Block 2");

        DatasetFieldType fieldType1 = MocksFactory.makeDatasetFieldType("field1", FieldType.TEXT, false, block1);
        DatasetFieldType fieldType2 = MocksFactory.makeDatasetFieldType("field2", FieldType.TEXT, false, block1);
        DatasetFieldType fieldType3 = MocksFactory.makeDatasetFieldType("field3", FieldType.TEXT, false, block2);

        DatasetField field1 = MocksFactory.makeEmptyDatasetFields(fieldType1, 1).get(0);
        DatasetField field2 = MocksFactory.makeEmptyDatasetFields(fieldType2, 1).get(0);
        DatasetField field3 = MocksFactory.makeEmptyDatasetFields(fieldType3, 1).get(0);

        List<DatasetField> datasetFields = Lists.newArrayList(field1, field2, field3);

        // when
        List<DatasetField> datasetFieldsCopy = DatasetFieldUtil.copyDatasetFields(datasetFields);

        // then
        assertEquals(3, datasetFieldsCopy.size());

        assertEquals("field1", datasetFieldsCopy.get(0).getDatasetFieldType().getName());
        assertNotEquals(field1, datasetFieldsCopy.get(0));

        assertEquals("field2", datasetFieldsCopy.get(1).getDatasetFieldType().getName());
        assertNotEquals(field2, datasetFieldsCopy.get(1));

        assertEquals("field3", datasetFieldsCopy.get(2).getDatasetFieldType().getName());
        assertNotEquals(field3, datasetFieldsCopy.get(2));
    }

    @Test
    public void groupByBlock() {
        // given
        MetadataBlock block1 = MocksFactory.makeMetadataBlock("block1", "Block 1");
        MetadataBlock block2 = MocksFactory.makeMetadataBlock("block2", "Block 2");

        DatasetFieldType fieldType1 = MocksFactory.makeDatasetFieldType("field1", FieldType.TEXT, false, block1);
        DatasetFieldType fieldType2 = MocksFactory.makeDatasetFieldType("field2", FieldType.TEXT, false, block1);
        DatasetFieldType fieldType3 = MocksFactory.makeDatasetFieldType("field3", FieldType.TEXT, false, block2);

        DatasetField field1 = MocksFactory.makeEmptyDatasetFields(fieldType1, 1).get(0);
        DatasetField field2 = MocksFactory.makeEmptyDatasetFields(fieldType2, 1).get(0);
        DatasetField field3 = MocksFactory.makeEmptyDatasetFields(fieldType3, 1).get(0);

        List<DatasetField> datasetFields = Lists.newArrayList(field1, field2, field3);

        // when
        Map<MetadataBlock, List<DatasetField>> retBlocks = DatasetFieldUtil.groupByBlock(datasetFields);

        // then
        assertEquals(2, retBlocks.size());
        assertThat(retBlocks.keySet(), contains(block1, block2));

        assertThat(retBlocks.get(block1), contains(field1, field2));
        assertThat(retBlocks.get(block2), contains(field3));
    }

    @Test
    public void mergeDatasetFields() {
        // given
        DatasetFieldType fieldType1 = MocksFactory.makeDatasetFieldType("field1",
                                                                        FieldType.TEXT,
                                                                        false,
                                                                        new MetadataBlock());
        DatasetFieldType fieldType2 = MocksFactory.makeDatasetFieldType("field2",
                                                                        FieldType.TEXT,
                                                                        false,
                                                                        new MetadataBlock());
        DatasetFieldType fieldType3 = MocksFactory.makeDatasetFieldType("field3",
                                                                        FieldType.TEXT,
                                                                        false,
                                                                        new MetadataBlock());

        DatasetField field1 = MocksFactory.makeEmptyDatasetFields(fieldType1, 1).get(0);
        DatasetField field2 = MocksFactory.makeEmptyDatasetFields(fieldType2, 1).get(0);
        List<DatasetField> datasetFields1 = Lists.newArrayList(field1, field2);

        DatasetField field3 = MocksFactory.makeEmptyDatasetFields(fieldType2, 1).get(0);
        DatasetField field4 = MocksFactory.makeEmptyDatasetFields(fieldType3, 1).get(0);
        List<DatasetField> datasetFields2 = Lists.newArrayList(field3, field4);

        // when
        List<DatasetField> mergedDatasetFields = DatasetFieldUtil.mergeDatasetFields(datasetFields1, datasetFields2);

        // then
        assertEquals(3, mergedDatasetFields.size());
        assertEquals(field1, mergedDatasetFields.get(0));
        assertEquals(field3, mergedDatasetFields.get(1));
        assertEquals(field4, mergedDatasetFields.get(2));
    }

    @Test
    public void joinFieldValues() {
        //given
        DatasetField authorField = makeDatasetField(makeAuthorFieldType(new MetadataBlock()));
        DatasetFieldType authorNameType = extractFieldTypeByName(DatasetFieldConstant.authorName,
                                                                 authorField.getDatasetFieldType().getChildDatasetFieldTypes());
        DatasetFieldType authorAffiliationType = extractFieldTypeByName(DatasetFieldConstant.authorAffiliation,
                                                                        authorField.getDatasetFieldType().getChildDatasetFieldTypes());

        String AUTHORNAME1 = "John Doe";
        String AUTHORAFFILIATION1 = "John Aff";


        authorField.getDatasetFieldsChildren().add(makeDatasetField(authorField, authorNameType, AUTHORNAME1, 0));
        authorField.getDatasetFieldsChildren().add(makeDatasetField(authorField,
                                                                    authorAffiliationType,
                                                                    AUTHORAFFILIATION1,
                                                                    1));

        //when
        String joinedValues = DatasetFieldUtil.joinCompoundFieldValues(authorField);

        //then
        Assert.assertEquals("John Doe; John Aff", joinedValues);

    }
}
