package edu.harvard.iq.dataverse.util.bagit;

import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.common.DatasetFieldConstant;
import edu.harvard.iq.dataverse.common.DateUtil;
import edu.harvard.iq.dataverse.export.ExporterType;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.DataFileTag;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.persistence.datafile.license.FileTermsOfUse.TermsOfUseType;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetField;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldType;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.dataset.FieldType;
import edu.harvard.iq.dataverse.util.json.JsonLDNamespace;
import edu.harvard.iq.dataverse.util.json.JsonLDTerm;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

public class OREMap {

    public static final String NAME = "OREMap";
    private Map<String, String> localContext = new TreeMap<String, String>();
    private DatasetVersion version;
    private boolean excludeEmail = false;
    private String dataverseSiteUrl;
    private Clock clock = Clock.systemUTC();

    public OREMap(DatasetVersion version, boolean excludeEmail, String dataverseSiteUrl) {
        this.version = version;
        this.excludeEmail = excludeEmail;
        this.dataverseSiteUrl = dataverseSiteUrl;
    }
    public OREMap(DatasetVersion version, boolean excludeEmail, String dataverseSiteUrl, Clock clock) {
        this(version, excludeEmail, dataverseSiteUrl);
        this.clock = clock;
    }

    public void writeOREMap(OutputStream outputStream) throws Exception {
        outputStream.write(getOREMap().toString().getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    public JsonObject getOREMap() {

        // Add namespaces we'll definitely use to Context
        // Additional namespaces are added as needed below
        localContext.putIfAbsent(JsonLDNamespace.ore.getPrefix(), JsonLDNamespace.ore.getUrl());
        localContext.putIfAbsent(JsonLDNamespace.dcterms.getPrefix(), JsonLDNamespace.dcterms.getUrl());
        localContext.putIfAbsent(JsonLDNamespace.dvcore.getPrefix(), JsonLDNamespace.dvcore.getUrl());
        localContext.putIfAbsent(JsonLDNamespace.schema.getPrefix(), JsonLDNamespace.schema.getUrl());

        Dataset dataset = version.getDataset();
        String id = dataset.getGlobalId().asString();
        JsonArrayBuilder fileArray = Json.createArrayBuilder();
        JsonArrayBuilder authorsArrayBuilder = Json.createArrayBuilder();
        // The map describes an aggregation
        JsonObjectBuilder aggBuilder = Json.createObjectBuilder();
        List<DatasetField> fields = version.getDatasetFields();

        Map<String, JsonArrayBuilder> fieldsMap = new HashMap<String, JsonArrayBuilder>();
        // That has it's own metadata
        for (DatasetField field : fields) {
            if (!field.isEmpty()) {
                DatasetFieldType dfType = field.getDatasetFieldType();
                if (excludeEmail && FieldType.EMAIL.equals(dfType.getFieldType())) {
                    continue;
                }
                JsonLDTerm fieldName = getTermFor(dfType);
                if (fieldName.inNamespace()) {
                    localContext.putIfAbsent(fieldName.getNamespace().getPrefix(), fieldName.getNamespace().getUrl());
                } else {
                    localContext.putIfAbsent(fieldName.getLabel(), fieldName.getUrl());
                }
                JsonArrayBuilder vals = Json.createArrayBuilder();
                if (!dfType.isCompound()) {
                    for (String val : field.getValues_nondisplay()) {
                        vals.add(val);
                    }
                } else {
                    // ToDo: Needs to be recursive
                        // compound values are of different types
                        JsonObjectBuilder child = Json.createObjectBuilder();

                        for (DatasetField dsf : field.getDatasetFieldsChildren()) {
                            DatasetFieldType dsft = dsf.getDatasetFieldType();
                            if (excludeEmail && FieldType.EMAIL.equals(dsft.getFieldType())) {
                                continue;
                            }
                            // which may have multiple values
                            if (!dsf.isEmpty()) {
                                // Add context entry
                                //ToDo - also needs to recurse here?
                                JsonLDTerm subFieldName = getTermFor(dfType, dsft);
                                if (subFieldName.inNamespace()) {
                                    localContext.putIfAbsent(subFieldName.getNamespace().getPrefix(),
                                                             subFieldName.getNamespace().getUrl());
                                } else {
                                    localContext.putIfAbsent(subFieldName.getLabel(), subFieldName.getUrl());
                                }

                                List<String> values = dsf.getValues_nondisplay();
                                if (values.size() > 1) {
                                    JsonArrayBuilder childVals = Json.createArrayBuilder();

                                    for (String val : dsf.getValues_nondisplay()) {
                                        childVals.add(val);
                                    }
                                    child.add(subFieldName.getLabel(), childVals);
                                } else {
                                    child.add(subFieldName.getLabel(), values.get(0));
                                }
                            }
                        }
                        vals.add(child);
                }
                // Add metadata value to aggregation, suppress array when only one value
                JsonArray valArray = vals.build();

                if (!fieldsMap.containsKey(fieldName.getLabel())) {
                    fieldsMap.put(fieldName.getLabel(), Json.createArrayBuilder());
                }
                fieldsMap.get(fieldName.getLabel()).add((valArray.size() != 1) ? valArray : valArray.get(0));

            }
        }

        for (String label:fieldsMap.keySet()) {
            JsonArray valArray = fieldsMap.get(label).build();
            aggBuilder.add(label, (valArray.size() != 1) ? valArray : valArray.get(0));
        }


        // Add metadata related to the Dataset/DatasetVersion
        aggBuilder.add("@id", id)
                .add("@type",
                     Json.createArrayBuilder().add(JsonLDTerm.ore("Aggregation").getLabel())
                             .add(JsonLDTerm.schemaOrg("Dataset").getLabel()))
                .add(JsonLDTerm.schemaOrg("version").getLabel(), version.getFriendlyVersionNumber())
                .add(JsonLDTerm.schemaOrg("datePublished").getLabel(), dataset.getPublicationDateFormattedYYYYMMDD())
                .add(JsonLDTerm.schemaOrg("name").getLabel(), version.getParsedTitle())
                .add(JsonLDTerm.schemaOrg("dateModified").getLabel(),
                     DateUtil.retrieveISOFormatter(ZoneId.of("UTC")).format(version.getLastUpdateTime().toInstant()));

        aggBuilder.add(JsonLDTerm.schemaOrg("includedInDataCatalog").getLabel(),
                       dataset.getDataverseContext().getDisplayName());

        // The aggregation aggregates aggregatedresources (Datafiles) which each have
        // their own entry and metadata
        JsonArrayBuilder aggResArrayBuilder = Json.createArrayBuilder();

        if(!version.getDataset().hasActiveEmbargo()) {
            for (FileMetadata fmd : version.getFileMetadatas()) {
                DataFile df = fmd.getDataFile();
                JsonObjectBuilder aggRes = Json.createObjectBuilder();

                if (fmd.getDescription() != null) {
                    aggRes.add(JsonLDTerm.schemaOrg("description").getLabel(), fmd.getDescription());
                } else {
                    addIfNotNull(aggRes, JsonLDTerm.schemaOrg("description"), df.getDescription());
                }
                addIfNotNull(aggRes, JsonLDTerm.schemaOrg("name"), fmd.getLabel()); // "label" is the filename
                addIfNotNull(aggRes, JsonLDTerm.restricted, fmd.getTermsOfUse().getTermsOfUseType() == TermsOfUseType.RESTRICTED);
                addIfNotNull(aggRes, JsonLDTerm.directoryLabel, fmd.getDirectoryLabel());
                addIfNotNull(aggRes, JsonLDTerm.schemaOrg("version"), fmd.getVersion());
                addIfNotNull(aggRes, JsonLDTerm.datasetVersionId, fmd.getDatasetVersion().getId());
                JsonArray catArray = null;
                if (fmd != null) {
                    List<String> categories = fmd.getCategoriesByName();
                    if (categories.size() > 0) {
                        JsonArrayBuilder jab = Json.createArrayBuilder();
                        for (String s : categories) {
                            jab.add(s);
                        }
                        catArray = jab.build();
                    }
                }
                addIfNotNull(aggRes, JsonLDTerm.categories, catArray);
                // File DOI if it exists
                String fileId = null;
                String fileSameAs = null;
                if (df.getGlobalId().asString().length() != 0) {
                    fileId = df.getGlobalId().asString();
                    fileSameAs = dataverseSiteUrl
                            + "/api/access/datafile/:persistentId?persistentId=" + fileId;
                } else {
                    fileId = dataverseSiteUrl + "/file.xhtml?fileId=" + df.getId();
                    fileSameAs = dataverseSiteUrl + "/api/access/datafile/" + df.getId();
                }
                aggRes.add("@id", fileId);
                aggRes.add(JsonLDTerm.schemaOrg("sameAs").getLabel(), fileSameAs);
                fileArray.add(fileId);

                aggRes.add("@type", JsonLDTerm.ore("AggregatedResource").getLabel());
                addIfNotNull(aggRes, JsonLDTerm.schemaOrg("fileFormat"), df.getContentType());
                addIfNotNull(aggRes, JsonLDTerm.filesize, df.getFilesize());
                addIfNotNull(aggRes, JsonLDTerm.storageIdentifier, df.getStorageIdentifier());
                addIfNotNull(aggRes, JsonLDTerm.originalFileFormat, df.getOriginalFileFormat());
                addIfNotNull(aggRes, JsonLDTerm.originalFormatLabel, df.getOriginalFormatLabel());
                addIfNotNull(aggRes, JsonLDTerm.UNF, df.getUnf());
                addIfNotNull(aggRes, JsonLDTerm.rootDataFileId, df.getRootDataFileId());
                addIfNotNull(aggRes, JsonLDTerm.previousDataFileId, df.getPreviousDataFileId());
                JsonObject checksum = null;
                // Add checksum. RDA recommends SHA-512
                if (df.getChecksumType() != null && df.getChecksumValue() != null) {
                    checksum = Json.createObjectBuilder().add("@type", df.getChecksumType().toString())
                            .add("@value", df.getChecksumValue()).build();
                    aggRes.add(JsonLDTerm.checksum.getLabel(), checksum);
                }
                JsonArray tabTags = null;
                JsonArrayBuilder jab = getTabularFileTags(df);
                if (jab != null) {
                    tabTags = jab.build();
                }
                addIfNotNull(aggRes, JsonLDTerm.tabularTags, tabTags);
                //Add latest resource to the array
                aggResArrayBuilder.add(aggRes.build());
            }
        }
        // Build the '@context' object for json-ld based on the localContext entries
        JsonObjectBuilder contextBuilder = Json.createObjectBuilder();
        for (Entry<String, String> e : localContext.entrySet()) {
            contextBuilder.add(e.getKey(), e.getValue());
        }

        // Now create the overall map object with it's metadata
        JsonObject oremap = Json.createObjectBuilder()
                .add(JsonLDTerm.dcTerms("modified").getLabel(), LocalDate.now(clock).toString())
                .add(JsonLDTerm.dcTerms("creator").getLabel(),
                     BundleUtil.getStringFromBundleWithLocale("institution.name", Locale.ENGLISH))
                .add("@type", JsonLDTerm.ore("ResourceMap").getLabel())
                // Define an id for the map itself (separate from the @id of the dataset being
                // described
                .add("@id",
                     dataverseSiteUrl + "/api/datasets/export?exporter="
                             + ExporterType.OAIORE.getPrefix() + "&persistentId=" + id)
                // Add the aggregation (Dataset) itself to the map.
                .add(JsonLDTerm.ore("describes").getLabel(),
                        aggBuilder.add(JsonLDTerm.ore("aggregates").getLabel(), aggResArrayBuilder.build())
                                .add(JsonLDTerm.schemaOrg("hasPart").getLabel(), fileArray.build()).build())
                // and finally add the context
                .add("@context", contextBuilder.build()).build();

        return oremap;
    }

    /*
     * Simple methods to only add an entry to JSON if the value of the term is
     * non-null. Methods created for string, JsonValue, boolean, and long
     */

    private void addIfNotNull(JsonObjectBuilder builder, JsonLDTerm key, String value) {
        if (value != null) {
            builder.add(key.getLabel(), value);
            addToContextMap(key);
        }
    }

    private void addIfNotNull(JsonObjectBuilder builder, JsonLDTerm key, JsonValue value) {
        if (value != null) {
            builder.add(key.getLabel(), value);
            addToContextMap(key);
        }
    }

    private void addIfNotNull(JsonObjectBuilder builder, JsonLDTerm key, Boolean value) {
        if (value != null) {
            builder.add(key.getLabel(), value);
            addToContextMap(key);
        }
    }

    private void addIfNotNull(JsonObjectBuilder builder, JsonLDTerm key, Long value) {
        if (value != null) {
            builder.add(key.getLabel(), value);
            addToContextMap(key);
        }
    }

    private void addToContextMap(JsonLDTerm key) {
        if (!key.inNamespace()) {
            localContext.putIfAbsent(key.getLabel(), key.getUrl());
        }
    }

    public JsonLDTerm getContactTerm() {
        return getTermFor(DatasetFieldConstant.datasetContact);
    }

    public JsonLDTerm getContactNameTerm() {
        return getTermFor(DatasetFieldConstant.datasetContact, DatasetFieldConstant.datasetContactName);
    }

    public JsonLDTerm getContactEmailTerm() {
        return getTermFor(DatasetFieldConstant.datasetContact, DatasetFieldConstant.datasetContactEmail);
    }

    public JsonLDTerm getDescriptionTerm() {
        return getTermFor(DatasetFieldConstant.description);
    }

    public JsonLDTerm getDescriptionTextTerm() {
        return getTermFor(DatasetFieldConstant.description, DatasetFieldConstant.descriptionText);
    }

    private JsonLDTerm getTermFor(String fieldTypeName) {
        for (DatasetField dsf : version.getDatasetFields()) {
            DatasetFieldType dsft = dsf.getDatasetFieldType();
            if (dsft.getName().equals(fieldTypeName)) {
                return getTermFor(dsft);
            }
        }
        return null;
    }

    private JsonLDTerm getTermFor(DatasetFieldType dsft) {
        if (dsft.getUri() != null) {
            return new JsonLDTerm(dsft.getTitle(), dsft.getUri());
        } else {
            String namespaceUri = dsft.getMetadataBlock().getNamespaceUri();
            if (namespaceUri == null) {
                namespaceUri = dataverseSiteUrl + "/schema/" + dsft.getMetadataBlock().getName()
                        + "#";
            }
            JsonLDNamespace blockNamespace = new JsonLDNamespace(dsft.getMetadataBlock().getName(), namespaceUri);
            return new JsonLDTerm(blockNamespace, dsft.getTitle());
        }
    }

    private JsonLDTerm getTermFor(DatasetFieldType dfType, DatasetFieldType dsft) {
        if (dsft.getUri() != null) {
            return new JsonLDTerm(dsft.getTitle(), dsft.getUri());
        } else {
            // Use metadatablock URI or custom URI for this field based on the path
            String subFieldNamespaceUri = dfType.getMetadataBlock().getNamespaceUri();
            if (subFieldNamespaceUri == null) {
                subFieldNamespaceUri = dataverseSiteUrl + "/schema/"
                        + dfType.getMetadataBlock().getName() + "/";
            }
            subFieldNamespaceUri = subFieldNamespaceUri + dfType.getName() + "#";
            JsonLDNamespace fieldNamespace = new JsonLDNamespace(dfType.getName(), subFieldNamespaceUri);
            return new JsonLDTerm(fieldNamespace, dsft.getTitle());
        }
    }

    private JsonLDTerm getTermFor(String type, String subType) {
        for (DatasetField dsf : version.getDatasetFields()) {
            DatasetFieldType dsft = dsf.getDatasetFieldType();
            if (dsft.getName().equals(type)) {
                    for (DatasetField subField : dsf.getDatasetFieldsChildren()) {
                        DatasetFieldType subFieldType = subField.getDatasetFieldType();
                        if (subFieldType.getName().equals(subType)) {
                            return getTermFor(dsft, subFieldType);
                        }
                    }
            }
        }
        return null;
    }

    private JsonArrayBuilder getTabularFileTags(DataFile df) {
        if (df == null) {
            return null;
        }
        List<DataFileTag> tags = df.getTags();
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        JsonArrayBuilder tabularTags = Json.createArrayBuilder();
        for (DataFileTag tag : tags) {
            String label = tag.getTypeLabel();
            if (label != null) {
                tabularTags.add(label);
            }
        }
        return tabularTags;
    }
}
