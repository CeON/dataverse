package edu.harvard.iq.dataverse.persistence;

import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.DataFileCategory;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.persistence.datafile.license.FileTermsOfUse;
import edu.harvard.iq.dataverse.persistence.datafile.license.License;
import edu.harvard.iq.dataverse.persistence.dataset.ControlledVocabularyValue;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetField;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldType;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.dataset.FieldType;
import edu.harvard.iq.dataverse.persistence.dataset.MetadataBlock;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.dataverse.DataverseFieldTypeInputLevel;
import edu.harvard.iq.dataverse.persistence.group.ExplicitGroup;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.persistence.user.DataverseRole;
import edu.harvard.iq.dataverse.persistence.user.Permission;
import org.assertj.core.util.Lists;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A utility class for creating mock objects for unit tests. Mostly, the non-parameter
 * methods created objects with reasonable defaults that should fit most tests.
 * Of course, feel free to change of make these mocks more elaborate as the code
 * evolves.
 *
 * @author michael
 */
public class MocksFactory {

    private static final AtomicInteger NEXT_ID = new AtomicInteger();

    public static Long nextId() {
        return Long.valueOf(NEXT_ID.incrementAndGet());
    }

    public static Date date(int year, int month, int day) {
        return new Date(LocalDate.of(year, Month.of(month), day).toEpochDay());
    }

    public static Timestamp timestamp(int year, int month, int day) {
        return new Timestamp(date(year, month, day).getTime());
    }

    public static DataFile makeDataFile() {
        DataFile retVal = new DataFile();
        retVal.setId(nextId());
        retVal.setContentType("application/unitTests");
        retVal.setCreateDate(new Timestamp(System.currentTimeMillis()));
        addFileMetadata(retVal);
        retVal.setModificationTime(retVal.getCreateDate());
        return retVal;
    }

    public static List<DataFile> makeFiles(int count) {
        List<DataFile> retVal = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            retVal.add(makeDataFile());
        }
        return retVal;
    }

    public static FileMetadata addFileMetadata(DataFile df) {
        FileMetadata fmd = new FileMetadata();

        fmd.setId(nextId());
        fmd.setLabel("Metadata for DataFile " + df.getId());

        fmd.setDataFile(df);
        if (df.getFileMetadatas() != null) {
            df.getFileMetadatas().add(fmd);
        } else {
            df.setFileMetadatas(new LinkedList(Arrays.asList(fmd)));
        }

        return fmd;
    }

    public static AuthenticatedUser makeAuthenticatedUser(String firstName, String lastName) {
        AuthenticatedUser user = new AuthenticatedUser();
        user.setId(nextId());
        user.setAffiliation("UnitTester");
        user.setEmail(firstName + "." + lastName + "@someU.edu");
        user.setLastName(lastName);
        user.setFirstName(firstName);
        user.setPosition("In-Memory user");
        user.setUserIdentifier("unittest" + user.getId());
        user.setCreatedTime(new Timestamp(new Date().getTime()));
        user.setLastLoginTime(user.getCreatedTime());
        return user;
    }

    public static Dataverse makeDataverse() {
        Dataverse retVal = new Dataverse();
        retVal.setId(nextId());

        retVal.setAffiliation("Unit Test U");
        retVal.setAlias("unitTest" + retVal.getId());
        retVal.setCreateDate(timestamp(2012, 4, 5));
        retVal.setMetadataBlockRoot(true);
        retVal.setName("UnitTest Dataverse #" + retVal.getId());


        MetadataBlock mtb = new MetadataBlock();
        mtb.setDisplayName("Test Block #1-" + retVal.getId());
        mtb.setId(nextId());
        mtb.setDatasetFieldTypes(Arrays.asList(
                new DatasetFieldType("JustAString", FieldType.TEXT, false),
                new DatasetFieldType("ManyStrings", FieldType.TEXT, true),
                new DatasetFieldType("AnEmail", FieldType.EMAIL, false)
        ));

        retVal.setMetadataBlocks(Arrays.asList(mtb));

        return retVal;
    }

    public static Dataset makeDataset() {
        Dataset ds = new Dataset();
        ds.setId(nextId());
        ds.setIdentifier("sample-ds-" + ds.getId());
        ds.setCategoriesByName(Arrays.asList("CatOne", "CatTwo", "CatThree"));
        final List<DataFile> files = makeFiles(10);
        final List<FileMetadata> metadatas = new ArrayList<>(10);
        final List<DataFileCategory> categories = ds.getCategories();
        Random rand = new Random();
        files.forEach(df -> {
            df.getFileMetadata().addCategory(categories.get(rand.nextInt(categories.size())));
            FileTermsOfUse termsOfUse = new FileTermsOfUse();
            termsOfUse.setAllRightsReserved(true);
            df.getFileMetadata().setTermsOfUse(termsOfUse);
            metadatas.add(df.getFileMetadata());
        });
        ds.setFiles(files);
        final DatasetVersion initialVersion = ds.getVersions().get(0);
        initialVersion.setFileMetadatas(metadatas);

        List<DatasetField> fields = new ArrayList<>();
        DatasetField field = new DatasetField();
        field.setId(nextId());
        field.setFieldValue("Sample Field Value");
        field.setDatasetFieldType(makeDatasetFieldType());
        fields.add(field);
        initialVersion.setDatasetFields(fields);
        ds.setOwner(makeDataverse());

        return ds;
    }

    public static DatasetVersion makeDatasetVersion(List<DataFileCategory> categories) {
        final DatasetVersion retVal = new DatasetVersion();
        final List<DataFile> files = makeFiles(10);
        final List<FileMetadata> metadatas = new ArrayList<>(10);
        Random rand = new Random();
        files.forEach(df -> {
            df.getFileMetadata().addCategory(categories.get(rand.nextInt(categories.size())));
            metadatas.add(df.getFileMetadata());
        });
        retVal.setFileMetadatas(metadatas);

        List<DatasetField> fields = new ArrayList<>();
        DatasetField field = new DatasetField();
        field.setId(nextId());
        field.setFieldValue("Sample Field Value");
        field.setDatasetFieldType(makeDatasetFieldType());
        fields.add(field);
        retVal.setDatasetFields(fields);

        return retVal;
    }

    public static MetadataBlock makeMetadataBlock(String name, String displayName) {
        final Long id = nextId();
        MetadataBlock metadataBlock = new MetadataBlock();
        metadataBlock.setId(id);
        metadataBlock.setName(name);
        metadataBlock.setDisplayName(displayName);
        return metadataBlock;
    }

    public static DatasetFieldType makeDatasetFieldType() {
        final Long id = nextId();
        DatasetFieldType retVal = new DatasetFieldType("SampleType-" + id, FieldType.TEXT, false);
        retVal.setId(id);
        return retVal;
    }

    public static DatasetFieldType makeDatasetFieldType(String name, FieldType fieldType, boolean allowMultiple, MetadataBlock metadataBlock) {
        final Long id = nextId();
        DatasetFieldType retVal = new DatasetFieldType(name, fieldType, allowMultiple);
        retVal.setId(id);

        if (metadataBlock.getDatasetFieldTypes() == null) {
            metadataBlock.setDatasetFieldTypes(new ArrayList<>());
        }
        metadataBlock.getDatasetFieldTypes().add(retVal);
        retVal.setMetadataBlock(metadataBlock);
        return retVal;
    }

    public static DatasetFieldType makeControlledVocabDatasetFieldType(String name, boolean allowMultiple, MetadataBlock metadataBlock, String... vocabularyStrValues) {
        final Long id = nextId();
        DatasetFieldType retVal = new DatasetFieldType(name, FieldType.TEXT, allowMultiple);
        retVal.setControlledVocabularyValues(new ArrayList<>());
        for (String vocabStrValue : vocabularyStrValues) {
            ControlledVocabularyValue vocabValue = new ControlledVocabularyValue(nextId(), vocabStrValue, retVal);
            retVal.getControlledVocabularyValues().add(vocabValue);
        }
        retVal.setId(id);

        if (metadataBlock.getDatasetFieldTypes() == null) {
            metadataBlock.setDatasetFieldTypes(new ArrayList<>());
        }
        metadataBlock.getDatasetFieldTypes().add(retVal);
        retVal.setMetadataBlock(metadataBlock);
        return retVal;
    }

    public static DatasetFieldType makeComplexDatasetFieldType(String name, boolean allowMultiple, MetadataBlock metadataBlock, DatasetFieldType... childDatasetTypes) {
        final Long id = nextId();
        DatasetFieldType retVal = new DatasetFieldType(name, FieldType.NONE, allowMultiple);
        retVal.getChildDatasetFieldTypes().addAll(Arrays.asList(childDatasetTypes));
        retVal.setId(id);

        if (metadataBlock.getDatasetFieldTypes() == null) {
            metadataBlock.setDatasetFieldTypes(new ArrayList<>());
        }
        metadataBlock.getDatasetFieldTypes().add(retVal);
        retVal.setMetadataBlock(metadataBlock);
        return retVal;
    }

    public static DatasetFieldType makeChildDatasetFieldType(String name, FieldType fieldType, boolean allowMultiple) {
        final Long id = nextId();
        DatasetFieldType retVal = new DatasetFieldType(name, fieldType, allowMultiple);
        retVal.setId(id);
        return retVal;
    }

    public static DatasetField makeEmptyDatasetField(DatasetFieldType datasetFieldType, int numberOfValues) {
        DatasetField datasetField = new DatasetField();

        datasetField.setDatasetFieldType(datasetFieldType);

        if (datasetFieldType.isPrimitive()) {
            if (!datasetFieldType.isControlledVocabulary()) {
               datasetField.setFieldValue("testValue");
            }
        } else {
            for (int i = 0; i < numberOfValues; ++i) {
                DatasetField dsfValueField = new DatasetField();
                dsfValueField.setId(nextId());
                dsfValueField.setDatasetFieldParent(datasetField);
                dsfValueField.setDatasetFieldType(datasetFieldType.getChildDatasetFieldTypes().get(i));
                datasetField.getDatasetFieldsChildren().add(dsfValueField);

            }

        }
        datasetField.setId(nextId());

        return datasetField;
    }

    public static DatasetField create(String name, String value, List<DatasetField> subfields) {
        DatasetField result = new DatasetField();
        DatasetFieldType type = new DatasetFieldType();
        type.setName(name);
        result.setDatasetFieldType(type);
        result.setValue(value);
        result.setDatasetFieldsChildren(subfields);
        subfields.forEach(s -> s.setDatasetFieldParent(result));
        return result;
    }

    public static DatasetField create(String name, String value, DatasetField... subfields) {
        return create(name, value, Arrays.asList(subfields));
    }

    public static DatasetField createCVV(String name, String... values) {
        DatasetField field = new DatasetField();
        DatasetFieldType type = new DatasetFieldType();
        type.setName(name);
        field.setDatasetFieldType(type);
        for (String value : values) {
            ControlledVocabularyValue vocabValue = new ControlledVocabularyValue(null, value, type);
            field.getControlledVocabularyValues().add(vocabValue);
        }
        return field;
    }

    public static DataverseRole makeRole(String name) {
        DataverseRole dvr = new DataverseRole();

        dvr.setId(nextId());
        dvr.setAlias(name);
        dvr.setName(name);
        dvr.setDescription(name + "  " + name + " " + name);

        dvr.addPermission(Permission.ManageDatasetPermissions);
        dvr.addPermission(Permission.EditDataset);
        dvr.addPermission(Permission.PublishDataset);
        dvr.addPermission(Permission.ViewUnpublishedDataset);

        return dvr;
    }

    public static DataverseFieldTypeInputLevel makeDataverseFieldTypeInputLevel(DatasetFieldType fieldType) {
        DataverseFieldTypeInputLevel retVal = new DataverseFieldTypeInputLevel();

        retVal.setId(nextId());
        retVal.setInclude(true);
        retVal.setDatasetFieldType(fieldType);

        return retVal;
    }

    public static ExplicitGroup makeExplicitGroup(String name) {
        long id = nextId();
        ExplicitGroup eg = new ExplicitGroup();

        eg.setId(id);
        eg.setDisplayName(name == null ? "explicitGroup-" + id : name);
        eg.setGroupAliasInOwner("eg" + id);

        return eg;
    }

    public static FileMetadata makeFileMetadata(Long id, String label, int displayOrder) {
        FileMetadata fileMetadata = new FileMetadata();
        fileMetadata.setId(id);

        fileMetadata.setLabel(label);
        fileMetadata.setDisplayOrder(displayOrder);
        fileMetadata.setCategories(new ArrayList<>());

        FileTermsOfUse termsOfUse = new FileTermsOfUse();
        License license = new License();
        license.setId(90l);
        license.setName("License name");
        termsOfUse.setLicense(license);
        fileMetadata.setTermsOfUse(termsOfUse);

        DataFile dataFile = makeDataFile();
        dataFile.setFileMetadatas(Lists.newArrayList(fileMetadata));
        fileMetadata.setDataFile(dataFile);

        return fileMetadata;
    }

    public static ExplicitGroup makeExplicitGroup() {
        return makeExplicitGroup(null);
    }

}
