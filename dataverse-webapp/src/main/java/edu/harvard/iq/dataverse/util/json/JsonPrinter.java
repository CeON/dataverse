package edu.harvard.iq.dataverse.util.json;

import edu.harvard.iq.dataverse.citation.CitationFactory;
import edu.harvard.iq.dataverse.common.NullSafeJsonBuilder;
import edu.harvard.iq.dataverse.common.Util;
import edu.harvard.iq.dataverse.persistence.GlobalId;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.datafile.DataFileTag;
import edu.harvard.iq.dataverse.persistence.datafile.FileMetadata;
import edu.harvard.iq.dataverse.persistence.datafile.license.FileTermsOfUse.RestrictType;
import edu.harvard.iq.dataverse.persistence.datafile.license.FileTermsOfUse.TermsOfUseType;
import edu.harvard.iq.dataverse.persistence.datafile.license.License;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetField;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldType;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.dataset.MetadataBlock;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.dataverse.DataverseContact;
import edu.harvard.iq.dataverse.persistence.dataverse.DataverseTheme;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.persistence.user.BuiltinUser;
import edu.harvard.iq.dataverse.persistence.user.DataverseRole;
import edu.harvard.iq.dataverse.persistence.user.Permission;
import edu.harvard.iq.dataverse.util.StringUtil;
import org.apache.commons.lang.StringUtils;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static edu.harvard.iq.dataverse.common.NullSafeJsonBuilder.jsonObjectBuilder;

/**
 * Convert objects to Json.
 *
 * @author michael
 */
@Stateless
public class JsonPrinter {

    public static final BriefJsonPrinter brief = new BriefJsonPrinter();

    private CitationFactory citationFactory;

    // -------------------- CONSTRUCTORS --------------------

    public JsonPrinter() { }

    @Inject
    public JsonPrinter(CitationFactory citationFactory) {
        this.citationFactory = citationFactory;
    }

    // -------------------- LOGIC --------------------

    public JsonArrayBuilder asJsonArray(Collection<String> strings) {
        JsonArrayBuilder arr = Json.createArrayBuilder();
        for (String s : strings) {
            arr.add(s);
        }
        return arr;
    }

    public JsonObjectBuilder json(AuthenticatedUser authenticatedUser) {
        return jsonObjectBuilder()
                .add("id", authenticatedUser.getId())
                .add("identifier", authenticatedUser.getIdentifier())
                .add("displayName", authenticatedUser.getDisplayInfo().getTitle())
                .add("firstName", authenticatedUser.getFirstName())
                .add("lastName", authenticatedUser.getLastName())
                .add("email", authenticatedUser.getEmail())
                .add("superuser", authenticatedUser.isSuperuser())
                .add("affiliation", authenticatedUser.getAffiliation())
                .add("position", authenticatedUser.getPosition())
                .add("persistentUserId", authenticatedUser.getAuthenticatedUserLookup().getPersistentUserId())
                .add("emailLastConfirmed", authenticatedUser.getEmailConfirmed())
                .add("createdTime", authenticatedUser.getCreatedTime())
                .add("lastLoginTime", authenticatedUser.getLastLoginTime())
                .add("lastApiUseTime", authenticatedUser.getLastApiUseTime())
                .add("authenticationProviderId",
                     authenticatedUser.getAuthenticatedUserLookup().getAuthenticationProviderId());
    }

    public JsonArrayBuilder json(Set<Permission> permissions) {
        JsonArrayBuilder bld = Json.createArrayBuilder();
        permissions.forEach(p -> bld.add(p.name()));
        return bld;
    }

    public JsonArrayBuilder rolesToJson(List<DataverseRole> role) {
        JsonArrayBuilder bld = Json.createArrayBuilder();
        for (DataverseRole r : role) {
            bld.add(json(r));
        }
        return bld;
    }

    public JsonObjectBuilder json(DataverseRole role) {
        JsonObjectBuilder bld = jsonObjectBuilder()
                .add("alias", role.getAlias())
                .add("name", role.getName())
                .add("permissions", json(role.permissions()))
                .add("description", role.getDescription());
        if (role.getId() != null) {
            bld.add("id", role.getId());
        }
        if (role.getOwner() != null && role.getOwner().getId() != null) {
            bld.add("ownerId", role.getOwner().getId());
        }

        return bld;
    }

    public JsonObjectBuilder json(Dataverse dv) {
        JsonObjectBuilder bld = jsonObjectBuilder()
                .add("id", dv.getId())
                .add("alias", dv.getAlias())
                .add("name", dv.getName())
                .add("affiliation", dv.getAffiliation())
                .add("dataverseContacts", json(dv.getDataverseContacts()))
                .add("permissionRoot", dv.isPermissionRoot())
                .add("description", dv.getDescription())
                .add("dataverseType", dv.getDataverseType().name());
        if (dv.getOwner() != null) {
            bld.add("ownerId", dv.getOwner().getId());
        }
        if (dv.getCreateDate() != null) {
            bld.add("creationDate", Util.getDateTimeFormat().format(dv.getCreateDate()));
        }
        if (dv.getCreator() != null) {
            bld.add("creator", json(dv.getCreator()));
        }
        if (dv.getDataverseTheme() != null) {
            bld.add("theme", json(dv.getDataverseTheme()));
        }

        return bld;
    }

    public JsonArrayBuilder json(List<DataverseContact> dataverseContacts) {
        return dataverseContacts.stream()
                .map(dc -> jsonObjectBuilder()
                        .add("displayOrder", dc.getDisplayOrder())
                        .add("contactEmail", dc.getContactEmail())
                ).collect(toJsonArray());
    }

    public JsonObjectBuilder json(DataverseTheme theme) {
        final NullSafeJsonBuilder baseObject = jsonObjectBuilder()
                .add("id", theme.getId())
                .add("logo", theme.getLogo())
                .add("tagline", theme.getTagline())
                .add("linkUrl", theme.getLinkUrl())
                .add("linkColor", theme.getLinkColor())
                .add("textColor", theme.getTextColor())
                .add("backgroundColor", theme.getBackgroundColor());
        if (theme.getLogoAlignment() != null) {
            baseObject.add("logoBackgroundColor", theme.getLogoBackgroundColor());
        }
        return baseObject;
    }

    public JsonObjectBuilder json(BuiltinUser user) {
        return (user == null)
                ? null
                : jsonObjectBuilder()
                .add("id", user.getId())
                .add("userName", user.getUserName());
    }

    public JsonObjectBuilder json(Dataset ds) {
        return jsonObjectBuilder()
                .add("id", ds.getId())
                .add("identifier", ds.getIdentifier())
                .add("persistentUrl", ds.getPersistentURL())
                .add("protocol", ds.getProtocol())
                .add("authority", ds.getAuthority())
                .add("publisher", getRootDataverseNameforCitation(ds))
                .add("publicationDate", ds.getPublicationDateFormattedYYYYMMDD())
                .add("storageIdentifier", ds.getStorageIdentifier())
                .add("hasActiveGuestbook", ds.getGuestbook() != null)
                .add("embargoDate", format(ds.getEmbargoDate().getOrNull(), Util::getDateFormat))
                .add("embargoActive", ds.hasActiveEmbargo());
    }

    public JsonObjectBuilder json(DatasetVersion dsv, boolean excludeEmailFields) {
        JsonObjectBuilder bld = jsonObjectBuilder()
                .add("id", dsv.getId())
                .add("storageIdentifier", dsv.getDataset().getStorageIdentifier())
                .add("versionNumber", dsv.getVersionNumber())
                .add("versionMinorNumber", dsv.getMinorVersionNumber())
                .add("versionState", dsv.getVersionState().name())
                .add("versionNote", dsv.getVersionNote())
                .add("archiveNote", dsv.getArchiveNote())
                .add("deaccessionLink", dsv.getDeaccessionLink())
                .add("distributionDate", dsv.getDistributionDate())
                .add("productionDate", dsv.getProductionDate())
                .add("UNF", dsv.getUNF())
                .add("archiveTime", format(dsv.getArchiveTime()))
                .add("lastUpdateTime", format(dsv.getLastUpdateTime()))
                .add("releaseTime", format(dsv.getReleaseTime()))
                .add("createTime", format(dsv.getCreateTime()));

        bld.add("metadataBlocks", jsonByBlocks(dsv.getDatasetFields(), excludeEmailFields));

        bld.add("files", dsv.getDataset().hasActiveEmbargo() ?
                Json.createArrayBuilder() : jsonFileMetadatas(dsv.getFileMetadatas()));

        return bld;
    }


    public JsonObjectBuilder jsonDataFileList(List<DataFile> dataFiles) {

        if (dataFiles == null) {
            throw new NullPointerException("dataFiles cannot be null");
        }

        JsonObjectBuilder bld = jsonObjectBuilder();


        List<FileMetadata> dataFileList = dataFiles.stream()
                .map(DataFile::getFileMetadata)
                .collect(Collectors.toList());


        bld.add("files", jsonFileMetadatas(dataFileList));

        return bld;
    }



    /**
     * Export formats such as DDI require the citation to be included. See
     * https://github.com/IQSS/dataverse/issues/2579 for more on DDI export.
     *
     * @todo Instead of having this separate method, should "citation" be added
     * to the regular `json` method for DatasetVersion? Will anything break?
     * Unit tests for that method could not be found.
     */
    public JsonObjectBuilder jsonWithCitation(DatasetVersion dsv, boolean excludeEmailFromExport) {
        JsonObjectBuilder dsvWithCitation = json(dsv, excludeEmailFromExport);
        dsvWithCitation.add("citation", citationFactory.create(dsv).toString(false));
        return dsvWithCitation;
    }

    /**
     * Export formats such as DDI require the persistent identifier components
     * such as "protocol", "authority" and "identifier" to be included so we
     * create a JSON object we can convert to a DatasetDTO which can include a
     * DatasetVersionDTO, which has all the metadata fields we need to export.
     * See https://github.com/IQSS/dataverse/issues/2579 for more on DDI export.
     *
     * @todo Instead of having this separate method, should "datasetVersion" be
     * added to the regular `json` method for Dataset? Will anything break? Unit
     * tests for that method could not be found. If we keep this method as-is
     * should the method be renamed?
     */
    public JsonObjectBuilder jsonAsDatasetDto(DatasetVersion dsv, boolean excludeEmailFromExport) {
        JsonObjectBuilder datasetDtoAsJson = json(dsv.getDataset());
        datasetDtoAsJson.add("datasetVersion", jsonWithCitation(dsv, excludeEmailFromExport));
        return datasetDtoAsJson;
    }

    public JsonArrayBuilder jsonFileMetadatas(Collection<FileMetadata> fmds) {
        JsonArrayBuilder filesArr = Json.createArrayBuilder();
        for (FileMetadata fmd : fmds) {
            filesArr.add(json(fmd));
        }

        return filesArr;
    }

    public JsonObjectBuilder jsonByBlocks(List<DatasetField> fields, boolean excludeEmailFromExport) {
        JsonObjectBuilder blocksBld = jsonObjectBuilder();

        for (Map.Entry<MetadataBlock, List<DatasetField>> blockAndFields : DatasetField.groupByBlock(fields).entrySet()) {
            MetadataBlock block = blockAndFields.getKey();
            blocksBld.add(block.getName(), json(block, blockAndFields.getValue(), excludeEmailFromExport));
        }
        return blocksBld;
    }

    /**
     * Create a JSON object for the block and its fields. The fields are assumed
     * to belong to the block - there's no checking of that in the method.
     *
     * @param block
     * @param fields
     * @return JSON Object builder with the block and fields information.
     */
    public JsonObjectBuilder json(MetadataBlock block, List<DatasetField> fields, boolean excludeEmailFromExport) {
        JsonObjectBuilder blockBld = jsonObjectBuilder();

        blockBld.add("displayName", block.getLocaleDisplayName());

        JsonArrayBuilder parsedFields = new JsonDatasetFieldsPrinter().json(fields, excludeEmailFromExport);

        blockBld.add("fields", parsedFields);
        return blockBld;
    }

    public JsonObjectBuilder json(MetadataBlock blk) {
        JsonObjectBuilder bld = jsonObjectBuilder();
        bld.add("id", blk.getId());
        bld.add("name", blk.getName());
        bld.add("displayName", blk.getLocaleDisplayName());

        JsonObjectBuilder fieldsBld = jsonObjectBuilder();
        for (DatasetFieldType df : new TreeSet<>(blk.getDatasetFieldTypes())) {
            fieldsBld.add(df.getName(), json(df));
        }

        bld.add("fields", fieldsBld);

        return bld;
    }

    public JsonObjectBuilder json(DatasetFieldType fld) {
        JsonObjectBuilder fieldsBld = jsonObjectBuilder();
        fieldsBld.add("name", fld.getName());
        fieldsBld.add("displayName", fld.getDisplayName());
        fieldsBld.add("title", fld.getTitle());
        fieldsBld.add("type", fld.getFieldType().toString());
        fieldsBld.add("watermark", fld.getWatermark());
        fieldsBld.add("description", fld.getDescription());
        if (!fld.getChildDatasetFieldTypes().isEmpty()) {
            JsonObjectBuilder subFieldsBld = jsonObjectBuilder();
            for (DatasetFieldType subFld : fld.getChildDatasetFieldTypes()) {
                subFieldsBld.add(subFld.getName(), json(subFld));
            }
            fieldsBld.add("childFields", subFieldsBld);
        }

        return fieldsBld;
    }

    public JsonObjectBuilder json(FileMetadata fmd) {
        return jsonObjectBuilder()
                // deprecated: .add("category", fmd.getCategory())
                // TODO: uh, figure out what to do here... it's deprecated
                // in a sense that there's no longer the category field in the
                // fileMetadata object; but there are now multiple, oneToMany file
                // categories - and we probably need to export them too!) -- L.A. 4.5
                .add("description", fmd.getDescription())
                .add("label", fmd.getLabel()) // "label" is the filename
                .add("restricted", fmd.getTermsOfUse().getTermsOfUseType() == TermsOfUseType.RESTRICTED)
                .add("termsOfUseType", fmd.getTermsOfUse().getTermsOfUseType().toString())
                .add("licenseName", getLicenseName(fmd))
                .add("licenseUrl", getLicenseUrl(fmd))
                .add("accessConditions", getAccessConditions(fmd))
                .add("accessConditionsCustomText", fmd.getTermsOfUse().getRestrictCustomText())
                .add("directoryLabel", fmd.getDirectoryLabel())
                .add("version", fmd.getVersion())
                .add("datasetVersionId", fmd.getDatasetVersion().getId())
                .add("categories", getFileCategories(fmd))
                .add("dataFile", json(fmd.getDataFile(), fmd));
    }

    public JsonObjectBuilder json(DataFile df) {
        return json(df, null);
    }

    public JsonObjectBuilder json(DataFile df, FileMetadata fileMetadata) {
        // File names are no longer stored in the DataFile entity;
        // (they are instead in the FileMetadata (as "labels") - this way
        // the filename can change between versions...
        // It does appear that for some historical purpose we still need the
        // filename in the file DTO (?)... We rely on it to be there for the
        // DDI export, for example. So we need to make sure this is is the
        // *correct* file name - i.e., that it comes from the right version.
        // (TODO...? L.A. 4.5, Aug 7 2016)
        String fileName = null;

        if (fileMetadata != null) {
            fileName = fileMetadata.getLabel();
        } else if (df.getFileMetadata() != null) {
            // Note that this may not necessarily grab the file metadata from the
            // version *you want*! (L.A.)
            fileName = df.getFileMetadata().getLabel();
        }

        String pidURL = "";

        if (new GlobalId(df).toURL() != null) {
            pidURL = new GlobalId(df).toURL().toString();
        }

        return jsonObjectBuilder()
                .add("id", df.getId())
                .add("persistentId", df.getGlobalIdString())
                .add("pidURL", pidURL)
                .add("filename", fileName)
                .add("contentType", df.getContentType())
                .add("filesize", df.getFilesize())
                .add("description", df.getDescription())
                //.add("released", df.isReleased())
                //.add("restricted", df.isRestricted())
                .add("storageIdentifier", df.getStorageIdentifier())
                .add("originalFileFormat", df.getOriginalFileFormat())
                .add("originalFormatLabel", df.getOriginalFormatLabel())
                .add("originalFileSize", df.getOriginalFileSize())
                .add("UNF", df.getUnf())
                //---------------------------------------------
                // For file replace: rootDataFileId, previousDataFileId
                //---------------------------------------------
                .add("rootDataFileId", df.getRootDataFileId())
                .add("previousDataFileId", df.getPreviousDataFileId())
                //---------------------------------------------
                // Checksum
                // * @todo Should we deprecate "md5" now that it's under
                // * "checksum" (which may also be a SHA-1 rather than an MD5)? - YES!
                //---------------------------------------------
                .add("md5", getMd5IfItExists(df.getChecksumType(), df.getChecksumValue()))
                .add("checksum", getChecksumTypeAndValue(df.getChecksumType(), df.getChecksumValue()))
                .add("tabularTags", getTabularFileTags(df))
                ;
    }

    public String format(Date date) {
        return format(date, Util::getDateTimeFormat);
    }

    public String format(Date date, Supplier<SimpleDateFormat> formatter) {
        return date != null ? formatter.get().format(date) : null;
    }

    public JsonArrayBuilder getTabularFileTags(DataFile df) {
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

    public Collector<JsonObjectBuilder, ArrayList<JsonObjectBuilder>, JsonArrayBuilder> toJsonArray() {
        return new Collector<JsonObjectBuilder, ArrayList<JsonObjectBuilder>, JsonArrayBuilder>() {

            @Override
            public Supplier<ArrayList<JsonObjectBuilder>> supplier() {
                return ArrayList::new;
            }

            @Override
            public BiConsumer<ArrayList<JsonObjectBuilder>, JsonObjectBuilder> accumulator() {
                return ArrayList::add;
            }

            @Override
            public BinaryOperator<ArrayList<JsonObjectBuilder>> combiner() {
                return (jab1, jab2) -> {
                    jab1.addAll(jab2);
                    return jab1;
                };
            }

            @Override
            public Function<ArrayList<JsonObjectBuilder>, JsonArrayBuilder> finisher() {
                return l -> {
                    JsonArrayBuilder bld = Json.createArrayBuilder();
                    l.forEach(bld::add);
                    return bld;
                };
            }

            @Override
            public Set<Collector.Characteristics> characteristics() {
                return Collections.emptySet();
            }
        };
    }

    public String getMd5IfItExists(DataFile.ChecksumType checksumType, String checksumValue) {
        if (DataFile.ChecksumType.MD5.equals(checksumType)) {
            return checksumValue;
        } else {
            return null;
        }
    }

    public JsonObjectBuilder getChecksumTypeAndValue(DataFile.ChecksumType checksumType, String checksumValue) {
        if (checksumType != null) {
            return Json.createObjectBuilder()
                    .add("type", checksumType.toString())
                    .add("value", checksumValue);
        } else {
            return null;
        }
    }

    // -------------------- PRIVATE --------------------

    private String getRootDataverseNameforCitation(Dataset dataset) {
        Dataverse root = dataset.getOwner();
        while (root.getOwner() != null) {
            root = root.getOwner();
        }
        String rootDataverseName = root.getName();
        if (!StringUtil.isEmpty(rootDataverseName)) {
            return rootDataverseName;
        } else {
            return "";
        }
    }

    private JsonArrayBuilder getFileCategories(FileMetadata fmd) {
        if (fmd == null) {
            return null;
        }
        List<String> categories = fmd.getCategoriesByName();
        if (categories == null || categories.isEmpty()) {
            return null;
        }
        JsonArrayBuilder fileCategories = Json.createArrayBuilder();
        for (String category : categories) {
            fileCategories.add(category);
        }
        return fileCategories;
    }

    private String getLicenseName(FileMetadata fmd) {
        return Optional.ofNullable(fmd.getTermsOfUse().getLicense()).map(License::getName).orElse(StringUtils.EMPTY);
    }

    private String getLicenseUrl(FileMetadata fmd) {
        return Optional.ofNullable(fmd.getTermsOfUse().getLicense()).map(License::getUrl).orElse(StringUtils.EMPTY);
    }

    private String getAccessConditions(FileMetadata fmd) {
        return Optional.ofNullable(fmd.getTermsOfUse().getRestrictType()).map(RestrictType::name).orElse(StringUtils.EMPTY);
    }
}
