package edu.harvard.iq.dataverse.util.json;

import edu.harvard.iq.dataverse.DatasetFieldServiceBean;
import edu.harvard.iq.dataverse.MetadataBlockDao;
import edu.harvard.iq.dataverse.api.dto.UningestRequestDTO;
import edu.harvard.iq.dataverse.common.Util;
import edu.harvard.iq.dataverse.datasetutility.OptionalFileParams;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.DataFileCategory;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.persistence.dataset.ControlledVocabularyValue;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetField;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldType;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.dataverse.DataverseContact;
import edu.harvard.iq.dataverse.persistence.dataverse.DataverseTheme;
import edu.harvard.iq.dataverse.persistence.group.IpAddress;
import edu.harvard.iq.dataverse.persistence.group.IpAddressRange;
import edu.harvard.iq.dataverse.persistence.group.IpGroup;
import edu.harvard.iq.dataverse.persistence.harvest.HarvestStyle;
import edu.harvard.iq.dataverse.persistence.harvest.HarvestType;
import edu.harvard.iq.dataverse.persistence.harvest.HarvestingClient;
import edu.harvard.iq.dataverse.persistence.workflow.Workflow;
import edu.harvard.iq.dataverse.persistence.workflow.WorkflowStepData;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import io.vavr.control.Option;

import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Parses JSON objects into domain objects.
 *
 * @author michael
 */
public class JsonParser {

    private static final Logger logger = Logger.getLogger(JsonParser.class.getCanonicalName());

    DatasetFieldServiceBean datasetFieldSvc;
    MetadataBlockDao blockService;
    SettingsServiceBean settingsService;

    public JsonParser(DatasetFieldServiceBean datasetFieldSvc, MetadataBlockDao blockService, SettingsServiceBean settingsService) {
        this.datasetFieldSvc = datasetFieldSvc;
        this.blockService = blockService;
        this.settingsService = settingsService;
    }

    public JsonParser() {
        this(null, null, null);
    }

    /**
     * @todo Instead of this getMandatoryString method we should run the
     * String through ConstraintValidator. See EMailValidatorTest and
     * EMailValidator for examples. That way we can check not only if it's
     * required or not but other bean validation rules such as "must match
     * this regex".
     */
    public Dataverse parseDataverse(JsonObject jobj) throws JsonParseException {
        Dataverse dv = new Dataverse();
        dv.setAlias(getMandatoryString(jobj, "alias"));
        dv.setName(getMandatoryString(jobj, "name"));
        dv.setDescription(jobj.getString("description", null));
        dv.setPermissionRoot(jobj.getBoolean("permissionRoot", false));
        dv.setFacetRoot(jobj.getBoolean("facetRoot", false));
        dv.setAffiliation(jobj.getString("affiliation", null));
        if (jobj.containsKey("dataverseContacts")) {
            JsonArray dvContacts = jobj.getJsonArray("dataverseContacts");
            int i = 0;
            List<DataverseContact> dvContactList = new LinkedList<>();
            for (JsonValue jsv : dvContacts) {
                DataverseContact dvc = new DataverseContact(dv);
                dvc.setContactEmail(getMandatoryString((JsonObject) jsv, "contactEmail"));
                dvc.setDisplayOrder(i++);
                dvContactList.add(dvc);
            }
            dv.setDataverseContacts(dvContactList);
        }

        if (jobj.containsKey("theme")) {
            DataverseTheme theme = parseDataverseTheme(jobj.getJsonObject("theme"));
            dv.setDataverseTheme(theme);
            theme.setDataverse(dv);
        }

        dv.setDataverseType(Dataverse.DataverseType.UNCATEGORIZED); // default
        if (jobj.containsKey("dataverseType")) {
            for (Dataverse.DataverseType dvtype : Dataverse.DataverseType.values()) {
                if (dvtype.name().equals(jobj.getString("dataverseType"))) {
                    dv.setDataverseType(dvtype);
                }
            }
        }

        /*  We decided that subject is not user set, but gotten from the subject of the dataverse's
            datasets - leavig this code in for now, in case we need to go back to it at some point

        if (jobj.containsKey("dataverseSubjects")) {
            List<ControlledVocabularyValue> dvSubjectList = new LinkedList<>();
            DatasetFieldType subjectType = datasetFieldSvc.findByName(DatasetFieldConstant.subject);
            List<JsonString> subjectList = jobj.getJsonArray("dataverseSubjects").getValuesAs(JsonString.class);
            if (subjectList.size() > 0) {
                // check first value for "all"
                if (subjectList.get(0).getString().trim().toLowerCase().equals("all")) {
                    dvSubjectList.addAll(subjectType.getControlledVocabularyValues());
                } else {
                    for (JsonString subject : subjectList) {
                        ControlledVocabularyValue cvv = datasetFieldSvc.findControlledVocabularyValueByDatasetFieldTypeAndStrValue(subjectType, subject.getString(),lenient);
                        if (cvv != null) {
                            dvSubjectList.add(cvv);
                        } else {
                            throw new JsonParseException("Value '" + subject.getString() + "' does not exist in type '" + subjectType.getName() + "'");
                        }
                    }
                }
            }
            dv.setDataverseSubjects(dvSubjectList);
        }
        */

        return dv;
    }

    public DataverseTheme parseDataverseTheme(JsonObject obj) {

        DataverseTheme theme = new DataverseTheme();

        if (obj.containsKey("backgroundColor")) {
            theme.setBackgroundColor(obj.getString("backgroundColor", null));
        }
        if (obj.containsKey("linkColor")) {
            theme.setLinkColor(obj.getString("linkColor", null));
        }
        if (obj.containsKey("linkUrl")) {
            theme.setLinkUrl(obj.getString("linkUrl", null));
        }
        if (obj.containsKey("logo")) {
            theme.setLogo(obj.getString("logo", null));
        }
        if (obj.containsKey("logoAlignment")) {
            String align = obj.getString("logoAlignment");
            if (align.equalsIgnoreCase("left")) {
                theme.setLogoAlignment(DataverseTheme.Alignment.LEFT);
            }
            if (align.equalsIgnoreCase("right")) {
                theme.setLogoAlignment(DataverseTheme.Alignment.RIGHT);
            }
            if (align.equalsIgnoreCase("center")) {
                theme.setLogoAlignment(DataverseTheme.Alignment.CENTER);
            }
        }
        if (obj.containsKey("logoBackgroundColor")) {
            theme.setLogoBackgroundColor(obj.getString("logoBackgroundColor", null));
        }
        if (obj.containsKey("logoFormat")) {
            String format = obj.getString("logoFormat");
            if (format.equalsIgnoreCase("square")) {
                theme.setLogoFormat(DataverseTheme.ImageFormat.SQUARE);
            }
            if (format.equalsIgnoreCase("rectangle")) {
                theme.setLogoFormat(DataverseTheme.ImageFormat.RECTANGLE);
            }
        }
        if (obj.containsKey("tagline")) {
            theme.setTagline(obj.getString("tagline", null));
        }
        if (obj.containsKey("textColor")) {
            theme.setTextColor(obj.getString("textColor", null));
        }

        return theme;
    }

    private static String getMandatoryString(JsonObject jobj, String name) throws JsonParseException {
        if (jobj.containsKey(name)) {
            return jobj.getString(name);
        }
        throw new JsonParseException("Field " + name + " is mandatory");
    }

    public IpGroup parseIpGroup(JsonObject obj) {
        IpGroup retVal = new IpGroup();

        if (obj.containsKey("id")) {
            retVal.setId((long) obj.getInt("id"));
        }
        retVal.setDisplayName(obj.getString("name", null));
        retVal.setDescription(obj.getString("description", null));
        retVal.setPersistedGroupAlias(obj.getString("alias", null));

        if (obj.containsKey("ranges")) {
            obj.getJsonArray("ranges").stream()
                    .filter(jv -> jv.getValueType() == JsonValue.ValueType.ARRAY)
                    .map(jv -> (JsonArray) jv)
                    .forEach(rr -> retVal.add(
                            IpAddressRange.make(IpAddress.valueOf(rr.getString(0)),
                                                IpAddress.valueOf(rr.getString(1)))));
        }
        if (obj.containsKey("addresses")) {
            obj.getJsonArray("addresses").stream()
                    .map(jsVal -> IpAddress.valueOf(((JsonString) jsVal).getString()))
                    .map(addr -> IpAddressRange.make(addr, addr))
                    .forEach(retVal::add);
        }

        return retVal;
    }

    public DatasetVersion parseDatasetVersion(JsonObject obj) throws JsonParseException {
        return parseDatasetVersion(obj, new DatasetVersion());
    }

    public Dataset parseDataset(JsonObject obj) throws JsonParseException {
        Dataset dataset = new Dataset();

        dataset.setAuthority(obj.getString("authority", null) == null ? settingsService.getValueForKey(SettingsServiceBean.Key.Authority) : obj.getString("authority"));
        dataset.setProtocol(obj.getString("protocol", null) == null ? settingsService.getValueForKey(SettingsServiceBean.Key.Protocol) : obj.getString("protocol"));
        dataset.setIdentifier(obj.getString("identifier", null));

        DatasetVersion dsv = new DatasetVersion();
        dsv.setDataset(dataset);
        dsv = parseDatasetVersion(obj.getJsonObject("datasetVersion"), dsv);
        List<DatasetVersion> versions = new ArrayList<>(1);
        versions.add(dsv);

        dataset.setVersions(versions);
        return dataset;
    }

    public DatasetVersion parseDatasetVersion(JsonObject obj, DatasetVersion dsv) throws JsonParseException {
        try {

            String archiveNote = obj.getString("archiveNote", null);
            if (archiveNote != null) {
                dsv.setArchiveNote(archiveNote);
            }

            dsv.setDeaccessionLink(obj.getString("deaccessionLink", null));
            int versionNumberInt = obj.getInt("versionNumber", -1);
            Long versionNumber = null;
            if (versionNumberInt != -1) {
                versionNumber = (long) versionNumberInt;
            }
            dsv.setVersionNumber(versionNumber);
            dsv.setMinorVersionNumber(parseLong(obj.getString("minorVersionNumber", null)));
            // if the existing datasetversion doesn not have an id
            // use the id from the json object.
            if (dsv.getId() == null) {
                dsv.setId(parseLong(obj.getString("id", null)));
            }

            String versionStateStr = obj.getString("versionState", null);
            if (versionStateStr != null) {
                dsv.setVersionState(DatasetVersion.VersionState.valueOf(versionStateStr));
            }
            dsv.setReleaseTime(parseDate(obj.getString("releaseDate", null)));
            dsv.setLastUpdateTime(parseTime(obj.getString("lastUpdateTime", null)));
            dsv.setCreateTime(parseTime(obj.getString("createTime", null)));
            dsv.setArchiveTime(parseTime(obj.getString("archiveTime", null)));
            dsv.setUNF(obj.getString("UNF", null));
            dsv.setDatasetFields(parseMetadataBlocks(obj.getJsonObject("metadataBlocks")));

            JsonArray filesJson = obj.getJsonArray("files");
            if (filesJson != null) {
                List<FileMetadata> parsedMetadataFiles = parseFiles(filesJson, dsv);
                dsv.setFileMetadatas(new LinkedList<>());
                for (FileMetadata parsedMetadataFile : parsedMetadataFiles) {
                    dsv.addFileMetadata(parsedMetadataFile);
                }
            }
            return dsv;

        } catch (ParseException ex) {
            throw new JsonParseException("Error parsing date:" + ex.getMessage(), ex);
        } catch (NumberFormatException ex) {
            throw new JsonParseException("Error parsing number:" + ex.getMessage(), ex);
        }
    }

    public List<DatasetField> parseMetadataBlocks(JsonObject json) throws JsonParseException {
        Set<String> keys = json.keySet();
        List<DatasetField> fields = new LinkedList<>();

        for (String blockName : keys) {
            JsonObject blockJson = json.getJsonObject(blockName);
            JsonArray fieldsJson = blockJson.getJsonArray("fields");
            fields.addAll(parseFieldsFromArray(fieldsJson, true));
        }
        return fields;
    }

    public List<DatasetField> parseMultipleFields(JsonObject json) throws JsonParseException {
        JsonArray fieldsJson = json.getJsonArray("fields");
        List<DatasetField> fields = parseFieldsFromArray(fieldsJson, false);
        return fields;
    }

    public List<DatasetField> parseMultipleFieldsForDelete(JsonObject json) throws JsonParseException {
        List<DatasetField> fields = new LinkedList<>();
        for (JsonObject fieldJson : json.getJsonArray("fields").getValuesAs(JsonObject.class)) {
            fields.add(parseFieldForDelete(fieldJson));
        }
        return fields;
    }

    private List<DatasetField> parseFieldsFromArray(JsonArray fieldsArray, Boolean testType) throws JsonParseException {
        List<DatasetField> fields = new LinkedList<>();
        for (JsonObject fieldJson : fieldsArray.getValuesAs(JsonObject.class)) {
            try {
                List<DatasetField> c = parseField(fieldJson, testType);
                fields.addAll(c);
            } catch (CompoundVocabularyException ex) {
                throw ex;
            }

        }
        return fields;

    }

    public List<FileMetadata> parseFiles(JsonArray metadatasJson, DatasetVersion dsv) throws JsonParseException {
        List<FileMetadata> fileMetadatas = new LinkedList<>();

        if (metadatasJson != null) {
            for (JsonObject filemetadataJson : metadatasJson.getValuesAs(JsonObject.class)) {
                String label = filemetadataJson.getString("label");
                String directoryLabel = filemetadataJson.getString("directoryLabel", null);
                String description = filemetadataJson.getString("description", null);

                FileMetadata fileMetadata = new FileMetadata();
                fileMetadata.setLabel(label);
                fileMetadata.setDirectoryLabel(directoryLabel);
                fileMetadata.setDescription(description);
                fileMetadata.setDatasetVersion(dsv);

                if (filemetadataJson.containsKey("dataFile")) {
                    DataFile dataFile = parseDataFile(filemetadataJson.getJsonObject("dataFile"));
                    dataFile.getFileMetadatas().add(fileMetadata);
                    dataFile.setOwner(dsv.getDataset());
                    fileMetadata.setDataFile(dataFile);
                    if (dsv.getDataset().getFiles() == null) {
                        dsv.getDataset().setFiles(new ArrayList<>());
                    }
                    dsv.getDataset().getFiles().add(dataFile);
                }


                fileMetadatas.add(fileMetadata);
                fileMetadata.setCategories(getCategories(filemetadataJson, dsv.getDataset()));
            }
        }

        return fileMetadatas;
    }

    public DataFile parseDataFile(JsonObject datafileJson) {
        DataFile dataFile = new DataFile();

        Timestamp timestamp = new Timestamp(new Date().getTime());
        dataFile.setCreateDate(timestamp);
        dataFile.setModificationTime(timestamp);
        dataFile.setPermissionModificationTime(timestamp);

        if (datafileJson.containsKey("filesize")) {
            dataFile.setFilesize(datafileJson.getJsonNumber("filesize").longValueExact());
        }

        String contentType = datafileJson.getString("contentType", null);
        if (contentType == null) {
            contentType = "application/octet-stream";
        }
        String storageIdentifier = datafileJson.getString("storageIdentifier", " ");
        JsonObject checksum = datafileJson.getJsonObject("checksum");
        if (checksum != null) {
            // newer style that allows for SHA-1 rather than MD5
            /**
             * @todo Add more error checking. Do we really expect people to set
             * file metadata without uploading files? Some day we'd like to work
             * on a "native" API that allows for multipart upload of the JSON
             * describing the files (this "parseDataFile" method) and the bits
             * of the files themselves. See
             * https://github.com/IQSS/dataverse/issues/1612
             */
            String type = checksum.getString("type");
            if (type != null) {
                String value = checksum.getString("value");
                if (value != null) {
                    try {
                        dataFile.setChecksumType(DataFile.ChecksumType.fromString(type));
                        dataFile.setChecksumValue(value);
                    } catch (IllegalArgumentException ex) {
                        logger.info("Invalid");
                    }
                }
            }
        } else {
            // older, MD5 logic, still her for backward compatibility
            String md5 = datafileJson.getString("md5", null);
            if (md5 == null) {
                md5 = "unknown";
            }
            dataFile.setChecksumType(DataFile.ChecksumType.MD5);
            dataFile.setChecksumValue(md5);
        }

        // TODO:
        // unf (if available)... etc.?

        dataFile.setContentType(contentType);
        dataFile.setStorageIdentifier(storageIdentifier);

        return dataFile;
    }

    public UningestRequestDTO parseUningestRequest(JsonObject json) {
        UningestRequestDTO rq = new UningestRequestDTO();

        Option.of(json.getJsonArray("dataFileIds"))
                .map(ar -> ar.getValuesAs(JsonNumber.class))
                .forEach(ids -> ids.stream().map(JsonNumber::longValue)
                        .forEach(rq::addDataFileId));

        return rq;
    }

    public DatasetField parseFieldForDelete(JsonObject json) throws JsonParseException {
        DatasetField ret = new DatasetField();
        DatasetFieldType type = datasetFieldSvc.findByNameOpt(json.getString("typeName", ""));
        if (type == null) {
            throw new JsonParseException("Can't find type '" + json.getString("typeName", "") + "'");
        }
        return ret;
    }


    public List<DatasetField> parseField(JsonObject json) throws JsonParseException {
        return parseField(json, true);
    }


    public List<DatasetField> parseField(JsonObject json, Boolean testType) throws JsonParseException {
        if (json == null) {
            return null;
        }

        DatasetFieldType type = datasetFieldSvc.findByNameOpt(json.getString("typeName", ""));


        if (type == null) {
            throw new JsonParseException("Can't find type '" + json.getString("typeName", "") + "'");
        }
        if (testType && type.isAllowMultiples() != json.getBoolean("multiple")) {
            throw new JsonParseException("incorrect multiple   for field " + json.getString("typeName", ""));
        }
        if (testType && type.isCompound() && !json.getString("typeClass").equals("compound")) {
            throw new JsonParseException("incorrect  typeClass for field " + json.getString("typeName", "") + ", should be compound.");
        }
        if (testType && !type.isControlledVocabulary() && type.isPrimitive() && !json.getString("typeClass").equals("primitive")) {
            throw new JsonParseException("incorrect  typeClass for field: " + json.getString("typeName", "") + ", should be primitive");
        }
        if (testType && type.isControlledVocabulary() && !json.getString("typeClass").equals("controlledVocabulary")) {
            throw new JsonParseException("incorrect  typeClass for field " + json.getString("typeName", "") + ", should be controlledVocabulary");
        }

        DatasetField datasetField = new DatasetField();
        datasetField.setDatasetFieldType(type);

        ArrayList<DatasetField> parsedFields = new ArrayList<>();

        if (type.isCompound()) {
            parsedFields.addAll(parseCompoundValue(type, json, testType));
        } else if (type.isControlledVocabulary()) {
            List<ControlledVocabularyValue> vals = parseControlledVocabularyValue(type, json);
            for (ControlledVocabularyValue cvv : vals) {
                cvv.setDatasetFieldType(type);
            }
            datasetField.setControlledVocabularyValues(vals);
            parsedFields.add(datasetField);

        } else {
            // primitive
            List<DatasetField> values = parsePrimitiveValue(type, json);

            if (values.size() == 1){
                datasetField.setFieldValue(values.get(0).getValue());
                parsedFields.add(datasetField);
            } else {
                parsedFields.addAll(values);
            }
        }

        return parsedFields;
    }

    public List<DatasetField> parseCompoundValue(DatasetFieldType compoundType, JsonObject json, Boolean testType) throws JsonParseException {
        List<ControlledVocabularyException> vocabExceptions = new ArrayList<>();
        List<DatasetField> vals = new LinkedList<>();
        if (compoundType.isAllowMultiples()) {
            try {
                json.getJsonArray("value").getValuesAs(JsonObject.class);
            } catch (ClassCastException cce) {
                throw new JsonParseException("Invalid values submitted for " + compoundType.getName() + ". It should be an array of values.");
            }

            JsonArray value = json.getJsonArray("value");
            System.out.println(value);

            for (JsonObject obj : json.getJsonArray("value").getValuesAs(JsonObject.class)) {
                DatasetField parentField = new DatasetField();
                parentField.setDatasetFieldType(compoundType);

                for (String fieldName : obj.keySet()) {
                    JsonObject childFieldJson = obj.getJsonObject(fieldName);
                    DatasetField childField = null;
                    try {
                        childField = parseField(childFieldJson, testType).get(0);
                    } catch (ControlledVocabularyException ex) {
                        vocabExceptions.add(ex);
                    }

                    if (childField != null) {
                        if (!compoundType.getChildDatasetFieldTypes().contains(childField.getDatasetFieldType())) {
                            throw new JsonParseException("field " + childField.getTypeName() + " is not a child of " + compoundType.getName());
                        }

                        parentField.getDatasetFieldsChildren().add(childField);
                        childField.setDatasetFieldParent(parentField);
                    }
                }
                vals.add(parentField);
            }


        } else {
            JsonObject value = json.getJsonObject("value");
            DatasetField parentField = new DatasetField();
            parentField.setDatasetFieldType(compoundType);

            for (String key : value.keySet()) {
                JsonObject childFieldJson = value.getJsonObject(key);
                DatasetField childField = null;
                try {
                    childField  = parseField(childFieldJson, testType).get(0);
                } catch (ControlledVocabularyException ex) {
                    vocabExceptions.add(ex);
                }
                if (childField != null) {
                    parentField.getDatasetFieldsChildren().add(childField);
                    childField.setDatasetFieldParent(parentField);
                }
            }

            vals.add(parentField);
        }
        if (!vocabExceptions.isEmpty()) {
            throw new CompoundVocabularyException("Invalid controlled vocabulary in compound field ", vocabExceptions, vals);
        }
        return vals;
    }

    public List<DatasetField> parsePrimitiveValue(DatasetFieldType dft, JsonObject json) throws JsonParseException {

        List<DatasetField> vals = new LinkedList<>();
        if (dft.isAllowMultiples() && json.get("value").getValueType().equals(ValueType.ARRAY)) {

            for (JsonString val : json.getJsonArray("value").getValuesAs(JsonString.class)) {
                DatasetField datasetFieldValue = new DatasetField();
                datasetFieldValue.setDisplayOrder(vals.size() - 1);
                datasetFieldValue.setFieldValue(val.getString().trim());
                datasetFieldValue.setDatasetFieldType(dft);
                vals.add(datasetFieldValue);
            }

        } else {
            try {
                json.getString("value");
            } catch (ClassCastException cce) {
                throw new JsonParseException("Invalid value submitted for " + dft.getName() + ". It should be a single value.");
            }
            DatasetField datasetFieldValue = new DatasetField();
            datasetFieldValue.setFieldValue(json.getString("value", "").trim());
            datasetFieldValue.setDatasetFieldType(dft);
            vals.add(datasetFieldValue);
        }

        return vals;
    }

    public Workflow parseWorkflow(JsonObject json) throws JsonParseException {
        Workflow retVal = new Workflow();
        validate("", json, "name", ValueType.STRING);
        validate("", json, "steps", ValueType.ARRAY);
        retVal.setName(json.getString("name"));
        JsonArray stepArray = json.getJsonArray("steps");
        List<WorkflowStepData> steps = new ArrayList<>(stepArray.size());
        for (JsonValue jv : stepArray) {
            steps.add(parseStepData((JsonObject) jv));
        }
        retVal.setSteps(steps);
        return retVal;
    }

    public WorkflowStepData parseStepData(JsonObject json) throws JsonParseException {
        WorkflowStepData wsd = new WorkflowStepData();
        validate("step", json, "provider", ValueType.STRING);
        validate("step", json, "stepType", ValueType.STRING);

        wsd.setProviderId(json.getString("provider"));
        wsd.setStepType(json.getString("stepType"));
        if (json.containsKey("parameters")) {
            JsonObject params = json.getJsonObject("parameters");
            Map<String, String> paramMap = new HashMap<>();
            params.keySet().forEach(k -> paramMap.put(k, jsonValueToString(params.get(k))));
            wsd.setStepParameters(paramMap);
        }
        if (json.containsKey("requiredSettings")) {
            JsonObject settings = json.getJsonObject("requiredSettings");
            Map<String, String> settingsMap = new HashMap<>();
            settings.keySet().forEach(k -> settingsMap.put(k, jsonValueToString(settings.get(k))));
            wsd.setStepSettings(settingsMap);
        }
        return wsd;
    }

    private String jsonValueToString(JsonValue jv) {
        switch (jv.getValueType()) {
            case STRING:
                return ((JsonString) jv).getString();
            default:
                return jv.toString();
        }
    }

    public List<ControlledVocabularyValue> parseControlledVocabularyValue(DatasetFieldType cvvType, JsonObject json) throws JsonParseException {
        try {
            if (cvvType.isAllowMultiples()) {
                try {
                    json.getJsonArray("value").getValuesAs(JsonObject.class);
                } catch (ClassCastException cce) {
                    throw new JsonParseException("Invalid values submitted for " + cvvType.getName() + ". It should be an array of values.");
                }
                List<ControlledVocabularyValue> vals = new LinkedList<>();
                for (JsonString strVal : json.getJsonArray("value").getValuesAs(JsonString.class)) {
                    String strValue = strVal.getString();
                    ControlledVocabularyValue cvv = datasetFieldSvc.findControlledVocabularyValueByDatasetFieldTypeAndStrValue(cvvType, strValue, false);
                    if (cvv == null) {
                        throw new ControlledVocabularyException("Value '" + strValue + "' does not exist in type '" + cvvType.getName() + "'", cvvType, strValue);
                    }
                    // Only add value to the list if it is not a duplicate
                    if (strValue.equals("Other")) {
                        System.out.println("vals = " + vals + ", contains: " + vals.contains(cvv));
                    }
                    if (!vals.contains(cvv)) {
                        vals.add(cvv);
                    }
                }
                return vals;

            } else {
                try {
                    json.getString("value");
                } catch (ClassCastException cce) {
                    throw new JsonParseException("Invalid value submitted for " + cvvType.getName() + ". It should be a single value.");
                }
                String strValue = json.getString("value", "");
                ControlledVocabularyValue cvv = datasetFieldSvc.findControlledVocabularyValueByDatasetFieldTypeAndStrValue(cvvType, strValue, false);
                if (cvv == null) {
                    throw new ControlledVocabularyException("Value '" + strValue + "' does not exist in type '" + cvvType.getName() + "'", cvvType, strValue);
                }
                return Collections.singletonList(cvv);
            }
        } catch (ClassCastException cce) {
            throw new JsonParseException("Invalid values submitted for " + cvvType.getName());
        }
    }

    Date parseDate(String str) throws ParseException {
        return str == null ? null : Util.getDateFormat().parse(str);
    }

    Date parseTime(String str) throws ParseException {
        return str == null ? null : Util.getDateTimeFormat().parse(str);
    }

    Long parseLong(String str) throws NumberFormatException {
        return (str == null) ? null : Long.valueOf(str);
    }

    int parsePrimitiveInt(String str, int defaultValue) {
        return str == null ? defaultValue : Integer.parseInt(str);
    }

    public String parseHarvestingClient(JsonObject obj, HarvestingClient harvestingClient) throws JsonParseException {

        String dataverseAlias = obj.getString("dataverseAlias", null);

        harvestingClient.setName(obj.getString("nickName", null));
        harvestingClient.setHarvestType(Option.of(obj.getString("type", null)).map(HarvestType::valueOf).getOrNull());
        harvestingClient.setHarvestStyle(Option.of(obj.getString("style", null)).map(HarvestStyle::valueOf).getOrNull());
        harvestingClient.setHarvestingUrl(obj.getString("harvestUrl", null));
        harvestingClient.setArchiveUrl(obj.getString("archiveUrl", null));
        harvestingClient.setMetadataPrefix(obj.getString("metadataFormat", null));
        harvestingClient.setHarvestingSet(obj.getString("set", null));

        return dataverseAlias;
    }

    private List<DataFileCategory> getCategories(JsonObject filemetadataJson, Dataset dataset) {
        JsonArray categories = filemetadataJson.getJsonArray(OptionalFileParams.CATEGORIES_ATTR_NAME);
        if (categories == null || categories.isEmpty() || dataset == null) {
            return null;
        }
        List<DataFileCategory> dataFileCategories = new ArrayList<>();
        for (Object category : categories.getValuesAs(JsonString.class)) {
            JsonString categoryAsJsonString;
            try {
                categoryAsJsonString = (JsonString) category;
            } catch (ClassCastException ex) {
                logger.info("ClassCastException caught in getCategories: " + ex);
                return null;
            }
            DataFileCategory dfc = new DataFileCategory();
            dfc.setDataset(dataset);
            dfc.setName(categoryAsJsonString.getString());
            dataFileCategories.add(dfc);
        }
        return dataFileCategories;
    }

    /**
     * Validate than a JSON object has a field of an expected type, or throw an
     * inforamtive exception.
     *
     * @param objectName
     * @param jobject
     * @param fieldName
     * @param expectedValueType
     * @throws JsonParseException
     */
    private void validate(String objectName, JsonObject jobject, String fieldName, ValueType expectedValueType) throws JsonParseException {
        if ((!jobject.containsKey(fieldName))
                || (jobject.get(fieldName).getValueType() != expectedValueType)) {
            throw new JsonParseException(objectName + " missing a field named '" + fieldName + "' of type " + expectedValueType);
        }
    }
}
