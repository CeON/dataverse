package edu.harvard.iq.dataverse.export.openaire;

import com.rometools.utils.Lists;
import edu.harvard.iq.dataverse.api.dto.DatasetDTO;
import edu.harvard.iq.dataverse.api.dto.DatasetVersionDTO;
import edu.harvard.iq.dataverse.api.dto.FileMetadataDTO;
import edu.harvard.iq.dataverse.api.dto.MetadataBlockWithFieldsDTO;
import edu.harvard.iq.dataverse.api.dto.DatasetFieldDTO;
import edu.harvard.iq.dataverse.common.DatasetFieldConstant;
import edu.harvard.iq.dataverse.common.MarkupChecker;
import edu.harvard.iq.dataverse.export.RelatedIdentifierTypeConstants;
import edu.harvard.iq.dataverse.persistence.GlobalId;
import edu.harvard.iq.dataverse.persistence.datafile.license.FileTermsOfUse;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetField;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OpenAireExportUtil {

    private static final Logger logger = Logger.getLogger(OpenAireExportUtil.class.getCanonicalName());

    private static final String RIGHTS_URI_CLOSED_ACCESS = "info:eu-repo/semantics/closedAccess";
    private static final String RIGHTS_URI_RESTRICTED_ACCESS = "info:eu-repo/semantics/restrictedAccess";
    private static final String RIGHTS_URI_OPEN_ACCESS = "info:eu-repo/semantics/openAccess";
    private static final String RIGHTS_URI_EMBARGOED_ACCESS = "info:eu-repo/semantics/embargoedAccess";
    private static final String GRANT_INFO_PREFIX = "info:eu-repo/grantAgreement/";

    public static String XSI_NAMESPACE = "http://www.w3.org/2001/XMLSchema-instance";
    public static String SCHEMA_VERSION = "3.1";
    public static String RESOURCE_NAMESPACE = "http://datacite.org/schema/kernel-3";
    public static String RESOURCE_SCHEMA_LOCATION = "http://schema.datacite.org/meta/kernel-3.1/metadata.xsd";

    public static void datasetJson2openaire(DatasetDTO datasetDto, OutputStream outputStream) throws XMLStreamException {
        XMLStreamWriter xmlw = XMLOutputFactory.newInstance().createXMLStreamWriter(outputStream);

        xmlw.writeStartElement("resource"); // <resource>

        xmlw.writeAttribute("xmlns:xsi", XSI_NAMESPACE);
        xmlw.writeAttribute("xmlns", RESOURCE_NAMESPACE);
        xmlw.writeAttribute("xsi:schemaLocation", RESOURCE_NAMESPACE + " " + RESOURCE_SCHEMA_LOCATION);

        createOpenAire(xmlw, datasetDto);

        xmlw.writeEndElement(); // </resource>

        xmlw.flush();
    }

    private static void createOpenAire(XMLStreamWriter xmlw, DatasetDTO datasetDto) throws XMLStreamException {
        DatasetVersionDTO version = datasetDto.getDatasetVersion();
        String persistentAgency = datasetDto.getProtocol();
        String persistentAuthority = datasetDto.getAuthority();
        String persistentId = datasetDto.getIdentifier();
        GlobalId globalId = new GlobalId(persistentAgency, persistentAuthority, persistentId);

        // The sequence is revied using sample:
        // https://schema.datacite.org/meta/kernel-4.0/example/datacite-example-full-v4.0.xml
        //
        // See also: https://schema.datacite.org/meta/kernel-4.0/doc/DataCite-MetadataKernel_v4.0.pdf
        // Table 1: DataCite Mandatory Properties
        // set language
        //String language = getLanguage(xmlw, version);
        String language = null;

        // 1, Identifier (with mandatory type sub-property) (M)
        writeIdentifierElement(xmlw, globalId.toURL().toString(), language);

        // 2, Creator (with optional name identifier and affiliation sub-properties) (M)
        writeCreatorsElement(xmlw, version, language);

        // 3, Title (with optional type sub-properties)
        writeTitlesElement(xmlw, version, language);

        // 4, Publisher (M)
        String publisher = datasetDto.getPublisher();
        if (StringUtils.isNotBlank(publisher)) {
            writeFullElement(xmlw, null, "publisher", null, publisher, language);
        }

        // 5, PublicationYear (M)
        String publicationDate = datasetDto.getPublicationDate();
        writePublicationYearElement(xmlw, version, publicationDate, language);

        // 6, Subject (with scheme sub-property)
        writeSubjectsElement(xmlw, version, language);

        // 7, Contributor (with optional name identifier and affiliation sub-properties)
        writeContributorsElement(xmlw, version, language);

        // 8, Date (with type sub-property)  (R)
        writeDatesElement(xmlw, datasetDto, language);

        // 9, Language (MA), language
        writeFullElement(xmlw, null, "language", null, language, null);

        // 10, ResourceType (with mandatory general type
        //      description sub- property) (M)
        writeResourceTypeElement(xmlw, version, language);

        // 11. AlternateIdentifier (with type sub-property) (O)
        writeAlternateIdentifierElement(xmlw, version, language);

        // 12, RelatedIdentifier (with type and relation type sub-properties) (R)
        writeRelatedIdentifierElement(xmlw, version, language);

        // 13, Size (O)
        writeSizeElement(xmlw, version, language);

        // 14 Format (O)
        writeFormatElement(xmlw, version, language);

        // 15 Version (O)
        writeVersionElement(xmlw, version, language);

        // 16 Rights (O), rights
        writeAccessRightsElement(xmlw, datasetDto);

        // 17 Description (R), description
        writeDescriptionsElement(xmlw, version, language);

        // 18 GeoLocation (with point, box and polygon sub-properties) (R)
        writeFullGeoLocationsElement(xmlw, version, language);
    }

    /**
     * Get the language value or null
     */
    public static String getLanguage(XMLStreamWriter xmlw, DatasetVersionDTO datasetVersionDTO) throws XMLStreamException {
        String language = null;

        // set the default language (using language attribute)
        for (Map.Entry<String, MetadataBlockWithFieldsDTO> entry : datasetVersionDTO.getMetadataBlocks().entrySet()) {
            String key = entry.getKey();
            MetadataBlockWithFieldsDTO value = entry.getValue();
            if ("citation".equals(key)) {
                for (DatasetFieldDTO fieldDTO : value.getFields()) {
                    if (DatasetFieldConstant.language.equals(fieldDTO.getTypeName())) {
                        for (String language_found : fieldDTO.getMultipleVocabulary()) {
                            if (StringUtils.isNotBlank(language_found)) {
                                language = language_found;
                                break;
                            }
                        }
                    }
                }
            }
        }

        return language;
    }

    /**
     * 1, Identifier (with mandatory type sub-property) (M)
     *
     * @param xmlw       The Steam writer
     * @param identifier The identifier url like https://doi.org/10.123/123
     * @throws XMLStreamException
     */
    public static void writeIdentifierElement(XMLStreamWriter xmlw, String identifier, String language) throws XMLStreamException {
        // identifier with identifierType attribute
        if (StringUtils.isNotBlank(identifier)) {
            Map<String, String> identifier_map = new HashMap<String, String>();

            if (StringUtils.containsIgnoreCase(identifier, GlobalId.DOI_RESOLVER_URL)) {
                identifier_map.put("identifierType", "DOI");
                identifier = StringUtils.substring(identifier, identifier.indexOf("10."));
            } else if (StringUtils.containsIgnoreCase(identifier, GlobalId.HDL_RESOLVER_URL)) {
                identifier_map.put("identifierType", "Handle");
                if (StringUtils.contains(identifier, "http")) {
                    identifier = identifier.replace(identifier.substring(0, identifier.indexOf("/") + 2), "");
                    identifier = identifier.substring(identifier.indexOf("/") + 1);
                }
            }
            writeFullElement(xmlw, null, "identifier", identifier_map, identifier, language);
        }
    }

    /**
     * 2, Creator (with optional name identifier and
     * affiliation sub-properties) (M)
     */
    public static void writeCreatorsElement(XMLStreamWriter xmlw, DatasetVersionDTO datasetVersionDTO, String language) throws XMLStreamException {
        // creators -> creator -> creatorName -> nameIdentifier
        // write all creators
        boolean creator_check = false;

        for (Map.Entry<String, MetadataBlockWithFieldsDTO> entry : datasetVersionDTO.getMetadataBlocks().entrySet()) {
            String key = entry.getKey();
            MetadataBlockWithFieldsDTO value = entry.getValue();
            if ("citation".equals(key)) {
                for (DatasetFieldDTO fieldDTO : value.getFields()) {
                    if (DatasetFieldConstant.author.equals(fieldDTO.getTypeName())) {
                        for (Set<DatasetFieldDTO> foo : fieldDTO.getMultipleCompound()) {
                            String creatorName = null;
                            String affiliation = null;
                            String nameIdentifier = null;
                            String nameIdentifierScheme = null;

                            for (Iterator<DatasetFieldDTO> iterator = foo.iterator(); iterator.hasNext(); ) {
                                DatasetFieldDTO next = iterator.next();
                                if (DatasetFieldConstant.authorName.equals(next.getTypeName())) {
                                    creatorName = next.getSinglePrimitive();
                                }
                                if (DatasetFieldConstant.authorIdValue.equals(next.getTypeName())) {
                                    nameIdentifier = next.getSinglePrimitive();
                                }
                                if (DatasetFieldConstant.authorIdType.equals(next.getTypeName())) {
                                    nameIdentifierScheme = next.getSinglePrimitive();
                                }
                                if (DatasetFieldConstant.authorAffiliation.equals(next.getTypeName())) {
                                    affiliation = next.getSinglePrimitive();
                                }
                            }

                            if (StringUtils.isNotBlank(creatorName)) {
                                creator_check = writeOpenTag(xmlw, "creators", creator_check);
                                xmlw.writeStartElement("creator"); // <creator>

                                creatorName = Cleanup.normalize(creatorName);

                                Map<String, String> creator_map = new HashMap<String, String>();
                                writeFullElement(xmlw, null, "creatorName", creator_map, creatorName, language);

                                if (StringUtils.isNotBlank(nameIdentifier)) {
                                    creator_map.clear();

                                    if (StringUtils.contains(nameIdentifier, "http")) {
                                        String site = nameIdentifier.substring(0, nameIdentifier.indexOf("/") + 2);
                                        nameIdentifier = nameIdentifier.replace(nameIdentifier.substring(0,
                                                                                                         nameIdentifier.indexOf(
                                                                                                                 "/") + 2),
                                                                                "");
                                        site = site + nameIdentifier.substring(0, nameIdentifier.indexOf("/") + 1);
                                        nameIdentifier = nameIdentifier.substring(nameIdentifier.indexOf("/") + 1);

                                        creator_map.put("SchemeURI", site);
                                    }

                                    if (StringUtils.isNotBlank(nameIdentifierScheme)) {
                                        creator_map.put("nameIdentifierScheme", nameIdentifierScheme);
                                        writeFullElement(xmlw,
                                                         null,
                                                         "nameIdentifier",
                                                         creator_map,
                                                         nameIdentifier,
                                                         language);
                                    } else {
                                        writeFullElement(xmlw, null, "nameIdentifier", null, nameIdentifier, language);
                                    }
                                }

                                if (StringUtils.isNotBlank(affiliation)) {
                                    writeFullElement(xmlw, null, "affiliation", null, affiliation, language);
                                }
                                xmlw.writeEndElement(); // </creator>
                            }
                        }
                    }
                }
            }
        }
        writeEndTag(xmlw, creator_check);
    }

    /**
     * 3, Title (with optional type sub-properties) (M)
     */
    public static void writeTitlesElement(XMLStreamWriter xmlw, DatasetVersionDTO datasetVersionDTO, String language) throws XMLStreamException {
        // titles -> title with titleType attribute
        boolean title_check = false;

        String title = dto2Primitive(datasetVersionDTO, DatasetFieldConstant.title);
        title_check = writeTitleElement(xmlw, null, title, title_check, language);

        String subtitle = dto2Primitive(datasetVersionDTO, DatasetFieldConstant.subTitle);
        title_check = writeTitleElement(xmlw, "Subtitle", subtitle, title_check, language);

        String alternativeTitle = dto2Primitive(datasetVersionDTO, DatasetFieldConstant.alternativeTitle);
        title_check = writeTitleElement(xmlw, "AlternativeTitle", alternativeTitle, title_check, language);

        writeEndTag(xmlw, title_check);
    }

    /**
     * 3, Title (with optional type sub-properties) (M)
     *
     * @param xmlw        The Steam writer
     * @param titleType   The item type, for instance AlternativeTitle
     * @param title       The title
     * @param title_check
     * @param language    current language
     */
    private static boolean writeTitleElement(XMLStreamWriter xmlw, String titleType, String title, boolean title_check, String language) throws XMLStreamException {
        // write a title
        if (StringUtils.isNotBlank(title)) {
            title_check = writeOpenTag(xmlw, "titles", title_check);
            xmlw.writeStartElement("title"); // <title>

            if (StringUtils.isNotBlank(language)) {
                xmlw.writeAttribute("xml:lang", language);
            }

            if (StringUtils.isNotBlank(titleType)) {
                xmlw.writeAttribute("titleType", titleType);
            }

            xmlw.writeCharacters(title);
            xmlw.writeEndElement(); // </title>
        }
        return title_check;
    }

    /**
     * 5, PublicationYear (M)
     */
    public static void writePublicationYearElement(XMLStreamWriter xmlw, DatasetVersionDTO datasetVersionDTO, String publicationDate, String language) throws XMLStreamException {

        // publicationYear
        String distributionDate = dto2Primitive(datasetVersionDTO, DatasetFieldConstant.distributionDate);
        //String publicationDate = datasetDto.getPublicationDate();
        String depositDate = dto2Primitive(datasetVersionDTO, DatasetFieldConstant.dateOfDeposit);

        int distributionYear = -1;
        int publicationYear = -1;
        int yearOfDeposit = -1;
        int pubYear = 0;

        if (distributionDate != null) {
            distributionYear = Integer.parseInt(distributionDate.substring(0, 4));
        }
        if (publicationDate != null) {
            publicationYear = Integer.parseInt(publicationDate.substring(0, 4));
        }
        if (depositDate != null) {
            yearOfDeposit = Integer.parseInt(depositDate.substring(0, 4));
        }

        pubYear = Integer.max(Integer.max(distributionYear, publicationYear), yearOfDeposit);
        if (pubYear > -1) {
            writeFullElement(xmlw, null, "publicationYear", null, String.valueOf(pubYear), language);
        }
    }

    /**
     * 6, Subject (with scheme sub-property) R
     */
    public static void writeSubjectsElement(XMLStreamWriter xmlw, DatasetVersionDTO datasetVersionDTO, String language) throws XMLStreamException {
        // subjects -> subject with subjectScheme and schemeURI attributes
        boolean subject_check = false;

        for (Map.Entry<String, MetadataBlockWithFieldsDTO> entry : datasetVersionDTO.getMetadataBlocks().entrySet()) {
            String key = entry.getKey();
            MetadataBlockWithFieldsDTO value = entry.getValue();
            if ("citation".equals(key)) {
                for (DatasetFieldDTO fieldDTO : value.getFields()) {
                    if (DatasetFieldConstant.subject.equals(fieldDTO.getTypeName())) {
                        for (String subject : fieldDTO.getMultipleVocabulary()) {
                            if (StringUtils.isNotBlank(subject)) {
                                subject_check = writeOpenTag(xmlw, "subjects", subject_check);
                                writeSubjectElement(xmlw, null, null, subject, language);
                            }
                        }
                    }

                    if (DatasetFieldConstant.keyword.equals(fieldDTO.getTypeName())) {
                        for (Set<DatasetFieldDTO> foo : fieldDTO.getMultipleCompound()) {
                            String subject = null;
                            String subjectScheme = null;
                            String schemeURI = null;

                            for (Iterator<DatasetFieldDTO> iterator = foo.iterator(); iterator.hasNext(); ) {
                                DatasetFieldDTO next = iterator.next();
                                if (DatasetFieldConstant.keywordValue.equals(next.getTypeName())) {
                                    subject = next.getSinglePrimitive();
                                }

                                if (DatasetFieldConstant.keywordVocab.equals(next.getTypeName())) {
                                    subjectScheme = next.getSinglePrimitive();
                                }

                                if (DatasetFieldConstant.keywordVocabURI.equals(next.getTypeName())) {
                                    schemeURI = next.getSinglePrimitive();
                                }
                            }

                            if (StringUtils.isNotBlank(subject)) {
                                subject_check = writeOpenTag(xmlw, "subjects", subject_check);
                                writeSubjectElement(xmlw, subjectScheme, schemeURI, subject, language);
                            }
                        }
                    }

                    if (DatasetFieldConstant.topicClassification.equals(fieldDTO.getTypeName())) {
                        for (Set<DatasetFieldDTO> foo : fieldDTO.getMultipleCompound()) {
                            String subject = null;
                            String subjectScheme = null;
                            String schemeURI = null;

                            for (Iterator<DatasetFieldDTO> iterator = foo.iterator(); iterator.hasNext(); ) {
                                DatasetFieldDTO next = iterator.next();
                                if (DatasetFieldConstant.topicClassValue.equals(next.getTypeName())) {
                                    subject = next.getSinglePrimitive();
                                }

                                if (DatasetFieldConstant.topicClassVocab.equals(next.getTypeName())) {
                                    subjectScheme = next.getSinglePrimitive();
                                }

                                if (DatasetFieldConstant.topicClassVocabURI.equals(next.getTypeName())) {
                                    schemeURI = next.getSinglePrimitive();
                                }
                            }

                            if (StringUtils.isNotBlank(subject)) {
                                subject_check = writeOpenTag(xmlw, "subjects", subject_check);
                                writeSubjectElement(xmlw, subjectScheme, schemeURI, subject, language);
                            }
                        }
                    }
                }
            }
        }
        writeEndTag(xmlw, subject_check);
    }

    /**
     * 6, Subject (with scheme sub-property) R
     */
    private static void writeSubjectElement(XMLStreamWriter xmlw, String subjectScheme, String schemeURI, String value, String language) throws XMLStreamException {
        // write a subject
        Map<String, String> subject_map = new HashMap<String, String>();

        if (StringUtils.isNotBlank(language)) {
            subject_map.put("xml:lang", language);
        }

        if (StringUtils.isNotBlank(subjectScheme)) {
            subject_map.put("subjectScheme", subjectScheme);
        }
        if (StringUtils.isNotBlank(schemeURI)) {
            subject_map.put("schemeURI", schemeURI);
        }

        if (!subject_map.isEmpty()) {
            writeFullElement(xmlw, null, "subject", subject_map, value, language);
        } else {
            writeFullElement(xmlw, null, "subject", null, value, language);
        }
    }

    /**
     * 7, Contributor (with optional name identifier
     * and affiliation sub-properties)
     * @see #writeContributorElement(XMLStreamWriter, String, String, String, String)
     */
    public static void writeContributorsElement(XMLStreamWriter xmlw, DatasetVersionDTO datasetVersionDTO, String language) throws XMLStreamException {
        // contributors -> contributor with ContributorType attribute -> contributorName, affiliation
        boolean contributor_check = false;

        for (Map.Entry<String, MetadataBlockWithFieldsDTO> entry : datasetVersionDTO.getMetadataBlocks().entrySet()) {
            String key = entry.getKey();
            MetadataBlockWithFieldsDTO value = entry.getValue();
            if ("citation".equals(key)) {
                for (DatasetFieldDTO fieldDTO : value.getFields()) {
                    // skip non-scompound value

                    if (DatasetFieldConstant.producer.equals(fieldDTO.getTypeName())) {
                        for (Set<DatasetFieldDTO> foo : fieldDTO.getMultipleCompound()) {
                            String producerName = null;
                            String producerAffiliation = null;

                            for (DatasetFieldDTO next : foo) {
                                if (DatasetFieldConstant.producerName.equals(next.getTypeName())) {
                                    producerName = next.getSinglePrimitive();
                                }
                                if (DatasetFieldConstant.producerAffiliation.equals(next.getTypeName())) {
                                    producerAffiliation = next.getSinglePrimitive();
                                }
                            }

                            if (StringUtils.isNotBlank(producerName)) {
                                contributor_check = writeOpenTag(xmlw, "contributors", contributor_check);
                                writeContributorElement(xmlw, "Producer", producerName, producerAffiliation, language);
                            }
                        }
                    } else if (DatasetFieldConstant.distributor.equals(fieldDTO.getTypeName())) {
                        for (Set<DatasetFieldDTO> foo : fieldDTO.getMultipleCompound()) {
                            String distributorName = null;
                            String distributorAffiliation = null;

                            for (DatasetFieldDTO next : foo) {
                                if (DatasetFieldConstant.distributorName.equals(next.getTypeName())) {
                                    distributorName = next.getSinglePrimitive();
                                }
                                if (DatasetFieldConstant.distributorAffiliation.equals(next.getTypeName())) {
                                    distributorAffiliation = next.getSinglePrimitive();
                                }
                            }

                            if (StringUtils.isNotBlank(distributorName)) {
                                contributor_check = writeOpenTag(xmlw, "contributors", contributor_check);
                                writeContributorElement(xmlw,
                                        "Distributor",
                                        distributorName,
                                        distributorAffiliation,
                                        language);
                            }
                        }
                    } else if (DatasetFieldConstant.datasetContact.equals(fieldDTO.getTypeName())) {
                        for (Set<DatasetFieldDTO> foo : fieldDTO.getMultipleCompound()) {
                            String contactName = null;
                            String contactAffiliation = null;

                            for (DatasetFieldDTO next : foo) {
                                if (DatasetFieldConstant.datasetContactName.equals(next.getTypeName())) {
                                    contactName = next.getSinglePrimitive();
                                }
                                if (DatasetFieldConstant.datasetContactAffiliation.equals(next.getTypeName())) {
                                    contactAffiliation = next.getSinglePrimitive();
                                }
                            }

                            if (StringUtils.isNotBlank(contactName)) {
                                contributor_check = writeOpenTag(xmlw, "contributors", contributor_check);
                                writeContributorElement(xmlw,
                                        "ContactPerson",
                                        contactName,
                                        contactAffiliation,
                                        language);
                            }
                        }
                    } else if (DatasetFieldConstant.contributor.equals(fieldDTO.getTypeName())) {
                        for (Set<DatasetFieldDTO> foo : fieldDTO.getMultipleCompound()) {
                            String contributorName = null;
                            String contributorType = null;

                            for (DatasetFieldDTO next : foo) {
                                if (DatasetFieldConstant.contributorName.equals(next.getTypeName())) {
                                    contributorName = next.getSinglePrimitive();
                                }
                                if (DatasetFieldConstant.contributorType.equals(next.getTypeName())) {
                                    contributorType = next.getSinglePrimitive();
                                }
                            }

                            if (StringUtils.isNotBlank(contributorName)) {
                                contributor_check = writeOpenTag(xmlw, "contributors", contributor_check);
                                writeContributorElement(xmlw, contributorType, contributorName, null, language);
                            }
                        }
                    } else if (DatasetFieldConstant.grantNumber.equals(fieldDTO.getTypeName())) {

                        for (Set<DatasetFieldDTO> foo : fieldDTO.getMultipleCompound()) {
                            GrantInfo grantInfo = new GrantInfo();

                            for (DatasetFieldDTO next : foo) {

                                if (DatasetFieldConstant.grantNumberAgency.equals(next.getTypeName())) {
                                    grantInfo.setGrantFunder(next.getSinglePrimitive());
                                }

                                if (DatasetFieldConstant.grantNumberAgencyShortName.equals(next.getTypeName())) {
                                    grantInfo.setGrantFunderShort(next.getSinglePrimitive());
                                }

                                if (DatasetFieldConstant.grantNumberProgram.equals(next.getTypeName())) {
                                    grantInfo.setGrantProgram(next.getSinglePrimitive());
                                }

                                if (DatasetFieldConstant.grantNumberValue.equals(next.getTypeName())) {
                                    grantInfo.setGrantId(next.getSinglePrimitive());
                                }

                            }

                            if (grantInfo.areAllFieldsPresent()) {
                                contributor_check = writeOpenTag(xmlw, "contributors", contributor_check);
                                writeGrantElement(xmlw, grantInfo, language);
                            }
                        }
                    }
                }
            }
        }
        writeEndTag(xmlw, contributor_check);
    }

    /**
     * 7, Writes info about funder in compliance with documentation.
     */
    private static void writeGrantElement(XMLStreamWriter xmlw, GrantInfo grantInfo, String language) throws XMLStreamException {

        xmlw.writeStartElement("contributor");

        xmlw.writeAttribute("contributorType", "Funder");

        writeFullElement(xmlw, null, "contributorName", new HashMap<>(), grantInfo.getGrantFunder(), language);

        HashMap<String, String> nameIdAttributes = new HashMap<>();
        nameIdAttributes.put("nameIdentifierScheme", "info");

        writeFullElement(xmlw,
                         null,
                         "nameIdentifier",
                         nameIdAttributes,
                         GRANT_INFO_PREFIX + grantInfo.createGrantInfoForOpenAire(),
                         language);

        xmlw.writeEndElement();
    }

    /**
     * 7, Contributor (with optional name identifier
     * and affiliation sub-properties)
     * <p>
     * Write single contributor tag.
     *
     * @param xmlw                   The stream writer
     * @param contributorType        The contributorType (M)
     * @param contributorName        The contributorName (M)
     * @param contributorAffiliation
     * @param language               current language
     * @throws XMLStreamException
     */
    public static void writeContributorElement(XMLStreamWriter xmlw, String contributorType, String contributorName,
                                               String contributorAffiliation, String language) throws XMLStreamException {
        // write a contributor
        xmlw.writeStartElement("contributor"); // <contributor>

        if (StringUtils.isNotBlank(contributorType)) {
            xmlw.writeAttribute("contributorType", contributorType.replaceAll(" ", ""));
        }

        Map<String, String> contributor_map = new HashMap<String, String>();
        contributorName = Cleanup.normalize(contributorName);

        writeFullElement(xmlw, null, "contributorName", contributor_map, contributorName, language);

        if (StringUtils.isNotBlank(contributorAffiliation)) {
            writeFullElement(xmlw, null, "affiliation", null, contributorAffiliation, language);
        }
        xmlw.writeEndElement(); // </contributor>
    }

    /**
     * 8, Date (with type sub-property) (R)
     */
    public static void writeDatesElement(XMLStreamWriter xmlw, DatasetDTO dataset, String language) throws XMLStreamException {
        if (dataset.getEmbargoActive()) {
            embargoDatesElement(xmlw, dataset, language);
        } else {
            standardDatesElement(xmlw, dataset.getDatasetVersion(), language);
        }
    }

    private static void embargoDatesElement(XMLStreamWriter xmlw, DatasetDTO dataset, String language) throws XMLStreamException {
        xmlw.writeStartElement("dates");
        writeFullElement(xmlw, null, "date", createMap("dateType", "Accepted"), dataset.getPublicationDate(), language);
        writeFullElement(xmlw, null, "date", createMap("dateType", "Available"), dataset.getEmbargoDate(), language);
        xmlw.writeEndElement();
    }

    private static void standardDatesElement(XMLStreamWriter xmlw, DatasetVersionDTO datasetVersionDTO, String language) throws XMLStreamException {
        boolean date_check = false;
        String dateOfDistribution = dto2Primitive(datasetVersionDTO, DatasetFieldConstant.distributionDate);
        if (StringUtils.isNotBlank(dateOfDistribution)) {
            date_check = writeOpenTag(xmlw, "dates", date_check);
            writeFullElement(xmlw, null, "date", createMap("dateType", "Issued"), dateOfDistribution, language);
        }
        // dates -> date with dateType attribute

        String dateOfProduction = dto2Primitive(datasetVersionDTO, DatasetFieldConstant.productionDate);
        if (StringUtils.isNotBlank(dateOfProduction)) {
            date_check = writeOpenTag(xmlw, "dates", date_check);
            writeFullElement(xmlw, null, "date", createMap("dateType", "Created"), dateOfProduction, language);
        }

        String dateOfDeposit = dto2Primitive(datasetVersionDTO, DatasetFieldConstant.dateOfDeposit);
        if (StringUtils.isNotBlank(dateOfDeposit)) {
            date_check = writeOpenTag(xmlw, "dates", date_check);
            writeFullElement(xmlw, null, "date", createMap("dateType", "Submitted"), dateOfDeposit, language);
        }

        String dateOfVersion = datasetVersionDTO.getReleaseTime();
        if (StringUtils.isNotBlank(dateOfVersion)) {
            date_check = writeOpenTag(xmlw, "dates", date_check);
            writeFullElement(xmlw,
                             null,
                             "date",
                             createMap("dateType", "Updated"),
                             dateOfVersion.substring(0, 10),
                             language);
        }

        for (Map.Entry<String, MetadataBlockWithFieldsDTO> entry : datasetVersionDTO.getMetadataBlocks().entrySet()) {
            String key = entry.getKey();
            MetadataBlockWithFieldsDTO value = entry.getValue();
            if ("citation".equals(key)) {
                for (DatasetFieldDTO fieldDTO : value.getFields()) {
                    if (DatasetFieldConstant.dateOfCollection.equals(fieldDTO.getTypeName())) {
                        for (Set<DatasetFieldDTO> foo : fieldDTO.getMultipleCompound()) {
                            String dateOfCollectionStart = null;
                            String dateOfCollectionEnd = null;

                            for (Iterator<DatasetFieldDTO> iterator = foo.iterator(); iterator.hasNext(); ) {
                                DatasetFieldDTO next = iterator.next();
                                if (DatasetFieldConstant.dateOfCollectionStart.equals(next.getTypeName())) {
                                    dateOfCollectionStart = next.getSinglePrimitive();
                                }
                                if (DatasetFieldConstant.dateOfCollectionEnd.equals(next.getTypeName())) {
                                    dateOfCollectionEnd = next.getSinglePrimitive();
                                }
                            }

                            if (StringUtils.isNotBlank(dateOfCollectionStart) && StringUtils.isNotBlank(
                                    dateOfCollectionEnd)) {
                                date_check = writeOpenTag(xmlw, "dates", date_check);
                                writeFullElement(xmlw,
                                                 null,
                                                 "date",
                                                 createMap("dateType", "Collected"),
                                                 dateOfCollectionStart + "/" + dateOfCollectionEnd,
                                                 language);
                            }
                        }
                    }
                }
            }
        }
        writeEndTag(xmlw, date_check);
    }

    private static <K, V> Map<K, V> createMap(K key, V value) {
        Map<K, V> result = new HashMap<>();
        result.put(key, value);
        return result;
    }

    /**
     * 10, ResourceType (with optional general type description sub- property)
     */
    public static void writeResourceTypeElement(XMLStreamWriter xmlw, DatasetVersionDTO datasetVersionDTO, String language) throws XMLStreamException {
        // resourceType with resourceTypeGeneral attribute
        boolean resourceTypeFound = false;
        for (Map.Entry<String, MetadataBlockWithFieldsDTO> entry : datasetVersionDTO.getMetadataBlocks().entrySet()) {
            String key = entry.getKey();
            MetadataBlockWithFieldsDTO value = entry.getValue();
            if ("citation".equals(key)) {
                for (DatasetFieldDTO fieldDTO : value.getFields()) {
                    if (DatasetFieldConstant.kindOfData.equals(fieldDTO.getTypeName())) {
                        for (String resourceType : fieldDTO.getMultipleVocabulary()) {
                            if (StringUtils.isNotBlank(resourceType)) {
                                Map<String, String> resourceType_map = new HashMap<String, String>();
                                resourceType_map.put("resourceTypeGeneral", "Dataset");
                                writeFullElement(xmlw, null, "resourceType", resourceType_map, resourceType, language);
                                resourceTypeFound = true;
                                break;
                            }
                        }
                    }
                }
            }
        }
        if (!resourceTypeFound) {
            xmlw.writeStartElement("resourceType"); // <resourceType>
            xmlw.writeAttribute("resourceTypeGeneral", "Dataset");
            xmlw.writeEndElement(); // </resourceType>
        }
    }

    /**
     * 11 AlternateIdentifier (with type sub-property) (O)
     */
    public static void writeAlternateIdentifierElement(XMLStreamWriter xmlw, DatasetVersionDTO datasetVersionDTO, String language) throws XMLStreamException {
        // alternateIdentifiers -> alternateIdentifier with alternateIdentifierType attribute
        boolean alternateIdentifier_check = false;

        for (Map.Entry<String, MetadataBlockWithFieldsDTO> entry : datasetVersionDTO.getMetadataBlocks().entrySet()) {
            String key = entry.getKey();
            MetadataBlockWithFieldsDTO value = entry.getValue();
            if ("citation".equals(key)) {
                for (DatasetFieldDTO fieldDTO : value.getFields()) {
                    if (DatasetFieldConstant.otherId.equals(fieldDTO.getTypeName())) {
                        for (Set<DatasetFieldDTO> foo : fieldDTO.getMultipleCompound()) {
                            String alternateIdentifier = null;
                            String alternateIdentifierType = null;

                            for (Iterator<DatasetFieldDTO> iterator = foo.iterator(); iterator.hasNext(); ) {
                                DatasetFieldDTO next = iterator.next();
                                if (DatasetFieldConstant.otherIdValue.equals(next.getTypeName())) {
                                    alternateIdentifier = next.getSinglePrimitive();
                                }
                                if (DatasetFieldConstant.otherIdAgency.equals(next.getTypeName())) {
                                    alternateIdentifierType = next.getSinglePrimitive();
                                }
                            }

                            if (StringUtils.isNotBlank(alternateIdentifier)) {
                                alternateIdentifier_check = writeOpenTag(xmlw,
                                                                         "alternateIdentifiers",
                                                                         alternateIdentifier_check);

                                if (StringUtils.isNotBlank(alternateIdentifierType)) {
                                    Map<String, String> alternateIdentifier_map = new HashMap<String, String>();
                                    alternateIdentifier_map.put("alternateIdentifierType", alternateIdentifierType);
                                    writeFullElement(xmlw,
                                                     null,
                                                     "alternateIdentifier",
                                                     alternateIdentifier_map,
                                                     alternateIdentifier,
                                                     language);
                                } else {
                                    writeFullElement(xmlw,
                                                     null,
                                                     "alternateIdentifier",
                                                     null,
                                                     alternateIdentifier,
                                                     language);
                                }
                            }
                        }
                    }
                }
            }
        }
        writeEndTag(xmlw, alternateIdentifier_check);
    }

    /**
     * 12, RelatedIdentifier (with type and relation type sub-properties) (R)
     */
    public static void writeRelatedIdentifierElement(XMLStreamWriter xmlw, DatasetVersionDTO datasetVersionDTO, String language) throws XMLStreamException {
        // relatedIdentifiers -> relatedIdentifier with relatedIdentifierType and relationType attributes
        boolean relatedIdentifier_check = false;

        MetadataBlockWithFieldsDTO value = datasetVersionDTO.getMetadataBlocks().get("citation");
        List<DatasetFieldDTO> fields = value != null ? value.getFields() : Collections.emptyList();
        for (DatasetFieldDTO fieldDTO : fields) {
            if (!DatasetFieldConstant.publication.equals(fieldDTO.getTypeName())
                    && !DatasetFieldConstant.relatedDataset.equals(fieldDTO.getTypeName())
                    && !DatasetFieldConstant.relatedMaterial.equals(fieldDTO.getTypeName())) {
                        continue;
            }
            for (Set<DatasetFieldDTO> compound : fieldDTO.getMultipleCompound()) {
                String relatedIdentifierType = null;
                String relatedIdentifier = null; // is used when relatedIdentifierType variable is not URL
                String relatedURL = null; // is used when relatedIdentifierType variable is URL
                String relationType = null;

                for (DatasetFieldDTO subField : compound) {
                    if (DatasetFieldConstant.publicationIDType.equals(subField.getTypeName())
                            || DatasetFieldConstant.relatedDatasetIDType.equals(subField.getTypeName())
                            || DatasetFieldConstant.relatedMaterialIDType.equals(subField.getTypeName())) {
                        relatedIdentifierType = subField.getSinglePrimitive();
                    }
                    if (DatasetFieldConstant.publicationIDNumber.equals(subField.getTypeName())
                            || DatasetFieldConstant.relatedDatasetIDNumber.equals(subField.getTypeName())
                            || DatasetFieldConstant.relatedMaterialIDNumber.equals(subField.getTypeName())) {
                        relatedIdentifier = subField.getSinglePrimitive();
                    }
                    if (DatasetFieldConstant.publicationURL.equals(subField.getTypeName())
                            || DatasetFieldConstant.relatedDatasetURL.equals(subField.getTypeName())
                            || DatasetFieldConstant.relatedMaterialURL.equals(subField.getTypeName())) {
                        relatedURL = subField.getSinglePrimitive();
                    }
                    if (DatasetFieldConstant.publicationRelationType.equals(subField.getTypeName())
                            || DatasetFieldConstant.relatedDatasetRelationType.equals(subField.getTypeName())
                            || DatasetFieldConstant.relatedMaterialRelationType.equals(subField.getTypeName())) {
                        relationType = subField.getSinglePrimitive();
                    }
                }

                if (StringUtils.isNotBlank(relatedIdentifierType)) {
                    relatedIdentifier_check = writeOpenTag(xmlw, "relatedIdentifiers", relatedIdentifier_check);

                    // fix case
                    if (RelatedIdentifierTypeConstants.ALTERNATIVE_TO_MAIN_ID_TYPE_INDEX.containsKey(relatedIdentifierType)) {
                        relatedIdentifierType = RelatedIdentifierTypeConstants.ALTERNATIVE_TO_MAIN_ID_TYPE_INDEX.get(relatedIdentifierType);
                    }

                    Map<String, String> relatedIdentifier_map = new HashMap<>();
                    relatedIdentifier_map.put("relatedIdentifierType", relatedIdentifierType);
                    relatedIdentifier_map.put("relationType", relationType);

                    writeFullElement(xmlw, null, "relatedIdentifier", relatedIdentifier_map,
                            StringUtils.containsIgnoreCase(relatedIdentifierType, "url")
                                    ? relatedURL : relatedIdentifier,
                            language);
                }
            }
        }
        writeEndTag(xmlw, relatedIdentifier_check);
    }

    /**
     * 13, Size (O)
     */
    public static void writeSizeElement(XMLStreamWriter xmlw, DatasetVersionDTO datasetVersionDTO, String language) throws XMLStreamException {
        // sizes -> size
        boolean size_check = false;

        if (datasetVersionDTO.getFiles() != null) {
            for (int i = 0; i < datasetVersionDTO.getFiles().size(); i++) {
                Long size = datasetVersionDTO.getFiles().get(i).getDataFile().getFilesize();
                if (size != null) {
                    size_check = writeOpenTag(xmlw, "sizes", size_check);
                    writeFullElement(xmlw, null, "size", null, size.toString(), language);
                }
            }
            writeEndTag(xmlw, size_check);
        }
    }

    /**
     * 14, Format (O)
     */
    public static void writeFormatElement(XMLStreamWriter xmlw, DatasetVersionDTO datasetVersionDTO, String language) throws XMLStreamException {
        // formats -> format
        boolean format_check = false;

        if (datasetVersionDTO.getFiles() != null) {
            for (int i = 0; i < datasetVersionDTO.getFiles().size(); i++) {
                String format = datasetVersionDTO.getFiles().get(i).getDataFile().getContentType();
                if (StringUtils.isNotBlank(format)) {
                    format_check = writeOpenTag(xmlw, "formats", format_check);
                    writeFullElement(xmlw, null, "format", null, format, language);
                }
            }
            writeEndTag(xmlw, format_check);
        }
    }

    /**
     * 15, Version (O)
     */
    public static void writeVersionElement(XMLStreamWriter xmlw, DatasetVersionDTO datasetVersionDTO, String language) throws XMLStreamException {
        Long majorVersionNumber = datasetVersionDTO.getVersionNumber();
        Long minorVersionNumber = datasetVersionDTO.getVersionMinorNumber();

        if (majorVersionNumber != null && StringUtils.isNotBlank(majorVersionNumber.toString())) {
            if (minorVersionNumber != null && StringUtils.isNotBlank(minorVersionNumber.toString())) {
                writeFullElement(xmlw,
                                 null,
                                 "version",
                                 null,
                                 majorVersionNumber.toString() + "." + minorVersionNumber.toString(),
                                 language);
            } else {
                writeFullElement(xmlw, null, "version", null, majorVersionNumber.toString(), language);
            }
        }
    }

    /**
     * 16 Rights (O)
     */
    public static void writeAccessRightsElement(XMLStreamWriter xmlw, DatasetDTO dataset) throws XMLStreamException {
        // rightsList -> rights with rightsURI attribute
        xmlw.writeStartElement("rightsList"); // <rightsList>
        if (dataset.getEmbargoActive()) {
            writeRightsHeader(xmlw);
            xmlw.writeAttribute("rightsURI", RIGHTS_URI_EMBARGOED_ACCESS);
            xmlw.writeEndElement();
        } else {
            List<FileMetadataDTO> files = Optional.ofNullable(dataset.getDatasetVersion())
                    .map(DatasetVersionDTO::getFiles)
                    .orElseGet(Collections::emptyList);
            // set terms from the info:eu-repo-Access-Terms vocabulary
            writeRightsUriInfoAttribute(xmlw, files, dataset.getHasActiveGuestbook());
            writeRightsUriLicenseInfoAttribute(xmlw, files);
        }
        xmlw.writeEndElement(); // </rightsList>
    }


    /**
     * 16 Rights (O)
     * <p>
     * Write headers
     */
    private static void writeRightsHeader(XMLStreamWriter xmlw) throws XMLStreamException {
        // write the rights header
        xmlw.writeStartElement("rights"); // <rights>
    }

    /**
     * 17 Descriptions (R)
     */
    public static void writeDescriptionsElement(XMLStreamWriter xmlw, DatasetVersionDTO datasetVersionDTO, String language) throws XMLStreamException {
        // descriptions -> description with descriptionType attribute
        boolean description_check = false;

        for (Map.Entry<String, MetadataBlockWithFieldsDTO> entry : datasetVersionDTO.getMetadataBlocks().entrySet()) {
            String key = entry.getKey();
            MetadataBlockWithFieldsDTO value = entry.getValue();
            if ("citation".equals(key)) {
                for (DatasetFieldDTO fieldDTO : value.getFields()) {
                    if (DatasetFieldConstant.description.equals(fieldDTO.getTypeName())) {
                        for (Set<DatasetFieldDTO> foo : fieldDTO.getMultipleCompound()) {
                            String descriptionOfAbstract = null;

                            for (Iterator<DatasetFieldDTO> iterator = foo.iterator(); iterator.hasNext(); ) {
                                DatasetFieldDTO next = iterator.next();
                                if (DatasetFieldConstant.descriptionText.equals(next.getTypeName())) {
                                    descriptionOfAbstract = MarkupChecker.stripAllTags(next.getSinglePrimitive());
                                    descriptionOfAbstract = StringEscapeUtils.unescapeHtml(descriptionOfAbstract);
                                }
                            }

                            if (StringUtils.isNotBlank(descriptionOfAbstract)) {
                                description_check = writeOpenTag(xmlw, "descriptions", description_check);
                                writeDescriptionElement(xmlw, "Abstract", descriptionOfAbstract, language);
                            }
                        }
                    }
                }
            }
        }

        for (Map.Entry<String, MetadataBlockWithFieldsDTO> entry : datasetVersionDTO.getMetadataBlocks().entrySet()) {
            String key = entry.getKey();
            MetadataBlockWithFieldsDTO value = entry.getValue();
            if ("citation".equals(key)) {
                for (DatasetFieldDTO fieldDTO : value.getFields()) {
                    if (DatasetFieldConstant.software.equals(fieldDTO.getTypeName())) {
                        for (Set<DatasetFieldDTO> foo : fieldDTO.getMultipleCompound()) {
                            String softwareName = null;
                            String softwareVersion = null;

                            for (Iterator<DatasetFieldDTO> iterator = foo.iterator(); iterator.hasNext(); ) {
                                DatasetFieldDTO next = iterator.next();
                                if (DatasetFieldConstant.softwareName.equals(next.getTypeName())) {
                                    softwareName = next.getSinglePrimitive();
                                }
                                if (DatasetFieldConstant.softwareVersion.equals(next.getTypeName())) {
                                    softwareVersion = next.getSinglePrimitive();
                                }
                            }

                            if (StringUtils.isNotBlank(softwareName) && StringUtils.isNotBlank(softwareVersion)) {
                                description_check = writeOpenTag(xmlw, "descriptions", description_check);
                                writeDescriptionElement(xmlw,
                                                        "Methods",
                                                        softwareName + ", " + softwareVersion,
                                                        language);
                            }
                        }
                    }
                }
            }
        }

        String descriptionOfMethodsOrigin = dto2Primitive(datasetVersionDTO, DatasetFieldConstant.originOfSources);
        if (StringUtils.isNotBlank(descriptionOfMethodsOrigin)) {
            description_check = writeOpenTag(xmlw, "descriptions", description_check);
            writeDescriptionElement(xmlw, "Methods", descriptionOfMethodsOrigin, language);
        }

        String descriptionOfMethodsCharacteristic = dto2Primitive(datasetVersionDTO,
                                                                  DatasetFieldConstant.characteristicOfSources);
        if (StringUtils.isNotBlank(descriptionOfMethodsCharacteristic)) {
            description_check = writeOpenTag(xmlw, "descriptions", description_check);
            writeDescriptionElement(xmlw, "Methods", descriptionOfMethodsCharacteristic, language);
        }

        String descriptionOfMethodsAccess = dto2Primitive(datasetVersionDTO, DatasetFieldConstant.accessToSources);
        if (StringUtils.isNotBlank(descriptionOfMethodsAccess)) {
            description_check = writeOpenTag(xmlw, "descriptions", description_check);
            writeDescriptionElement(xmlw, "Methods", descriptionOfMethodsAccess, language);
        }

        for (Map.Entry<String, MetadataBlockWithFieldsDTO> entry : datasetVersionDTO.getMetadataBlocks().entrySet()) {
            String key = entry.getKey();
            MetadataBlockWithFieldsDTO value = entry.getValue();
            if ("citation".equals(key)) {
                for (DatasetFieldDTO fieldDTO : value.getFields()) {
                    if (DatasetFieldConstant.series.equals(fieldDTO.getTypeName())) {
                        // String seriesName = null;
                        String seriesInformation = null;

                        Set<DatasetFieldDTO> foo = fieldDTO.getSingleCompound();
                        for (Iterator<DatasetFieldDTO> iterator = foo.iterator(); iterator.hasNext(); ) {
                            DatasetFieldDTO next = iterator.next();
                            /*if (DatasetFieldConstant.seriesName.equals(next.getTypeName())) {
                                seriesName =  next.getSinglePrimitive();
                            }*/
                            if (DatasetFieldConstant.seriesInformation.equals(next.getTypeName())) {
                                seriesInformation = next.getSinglePrimitive();
                            }
                        }

                        /*if (StringUtils.isNotBlank(seriesName)){
                        	contributor_check = writeOpenTag(xmlw, "descriptions", description_check);

                        	writeDescriptionElement(xmlw, "SeriesInformation", seriesName);
                        }*/
                        if (StringUtils.isNotBlank(seriesInformation)) {
                            description_check = writeOpenTag(xmlw, "descriptions", description_check);
                            writeDescriptionElement(xmlw, "SeriesInformation", seriesInformation, language);
                        }
                    }
                }
            }
        }

        String descriptionOfOther = dto2Primitive(datasetVersionDTO, DatasetFieldConstant.notesText);
        if (StringUtils.isNotBlank(descriptionOfOther)) {
            description_check = writeOpenTag(xmlw, "descriptions", description_check);
            writeDescriptionElement(xmlw, "Other", descriptionOfOther, language);
        }
        writeEndTag(xmlw, description_check);
    }

    /**
     * 17 Descriptions (R)
     */
    private static void writeDescriptionElement(XMLStreamWriter xmlw, String descriptionType, String description, String language) throws XMLStreamException {
        // write a description
        Map<String, String> description_map = new HashMap<String, String>();

        if (StringUtils.isNotBlank(language)) {
            description_map.put("xml:lang", language);
        }

        description_map.put("descriptionType", descriptionType);
        writeFullElement(xmlw, null, "description", description_map, description, language);
    }

    /**
     * 18 GeoLocation (R)
     */
    public static void writeFullGeoLocationsElement(XMLStreamWriter xmlw, DatasetVersionDTO datasetVersionDTO, String language) throws XMLStreamException {
        // geoLocation -> geoLocationPlace
        String geoLocationPlace = dto2Primitive(datasetVersionDTO, DatasetFieldConstant.productionPlace);
        boolean geoLocations_check = false;
        boolean hasValidBoxLocation = false;

        // get DatasetFieldConstant.geographicBoundingBox
        for (Map.Entry<String, MetadataBlockWithFieldsDTO> entry : datasetVersionDTO.getMetadataBlocks().entrySet()) {
            MetadataBlockWithFieldsDTO value = entry.getValue();
            for (DatasetFieldDTO fieldDTO : value.getFields()) {
                if (DatasetFieldConstant.geographicBoundingBox.equals(fieldDTO.getTypeName())) {
                    geoLocations_check = writeOpenTag(xmlw, "geoLocations", geoLocations_check);
                    if (fieldDTO.getMultiple()) {
                        for (Set<DatasetFieldDTO> foo : fieldDTO.getMultipleCompound()) {
                            boolean hasValidCurrentBoxLocation = writeGeoLocationsElement(xmlw, foo, geoLocationPlace, language);
                            hasValidBoxLocation = hasValidBoxLocation || hasValidCurrentBoxLocation;
                        }
                    } else {
                        hasValidBoxLocation = writeGeoLocationsElement(xmlw, fieldDTO.getSingleCompound(), geoLocationPlace, language);
                    }
                }
            }
        }

        if (!hasValidBoxLocation && StringUtils.isNotBlank(geoLocationPlace)) {
            if (!geoLocations_check) {
                geoLocations_check = writeOpenTag(xmlw, "geoLocations", geoLocations_check);
                writeOpenTag(xmlw, "geoLocation", false);
            }
            writeFullElement(xmlw, null, "geoLocationPlace", null, geoLocationPlace, language);
            writeEndTag(xmlw, true);
        }

        writeEndTag(xmlw, geoLocations_check);
    }

    /**
     * 18 GeoLocation (R)
     */
    public static boolean writeGeoLocationsElement(XMLStreamWriter xmlw, Set<DatasetFieldDTO> foo, String geoLocationPlace, String language) throws XMLStreamException {

        String northLatitude = StringUtils.EMPTY;
        String southLatitude = StringUtils.EMPTY;
        String eastLongitude = StringUtils.EMPTY;
        String westLongitude = StringUtils.EMPTY;

        for (Iterator<DatasetFieldDTO> iterator = foo.iterator(); iterator.hasNext(); ) {
            DatasetFieldDTO next = iterator.next();

            String value = next.getSinglePrimitive().trim();

            Pattern pattern = Pattern.compile("([a-z]+)(Longitude|Latitude)");
            Matcher matcher = pattern.matcher(next.getTypeName());
            if (matcher.find()) {
                switch (matcher.group(1)) {
                    case "south":
                        southLatitude = value;
                        break;
                    case "north":
                        northLatitude = value;
                        break;
                    case "west":
                        westLongitude = value;
                        break;
                    case "east":
                        eastLongitude = value;
                        break;
                }
            }
        }


        if (hasValidLocationBox(northLatitude, southLatitude, eastLongitude, westLongitude)) {
            String locationBoxValue = southLatitude + " " + westLongitude + " " + northLatitude + " " + eastLongitude;

            writeOpenTag(xmlw, "geoLocation", false);
            writeFullElement(xmlw, null, "geoLocationBox", null, locationBoxValue.trim().replaceAll(" +", " "), language);
            if (StringUtils.isNotBlank(geoLocationPlace)) {
                writeFullElement(xmlw, null, "geoLocationPlace", null, geoLocationPlace, language);
            }
            writeEndTag(xmlw, true);

            return true;
        }
        return false;
    }

    private static boolean hasValidLocationBox(String north, String south, String east, String west) {
        return isValidLatitude(north) &&
                isValidLatitude(south) &&
                isValidLongitude(east) &&
                isValidLongitude(west);
    }

    private static boolean isValidLatitude(String value) {
        try {
            double val = Double.parseDouble(value);
            return val >= -90.0 && val <= 90.0;
        } catch (NullPointerException | NumberFormatException ex) {
            return false;
        }
    }

    private static boolean isValidLongitude(String value) {
        try {
            double val = Double.parseDouble(value);
            return val >= -180.0 && val <= 180.0;
        } catch (NullPointerException | NumberFormatException ex) {
            return false;
        }
    }


    private static String dto2Primitive(DatasetVersionDTO datasetVersionDTO, String datasetFieldTypeName) {
        // give the single value of the given metadata
        for (Map.Entry<String, MetadataBlockWithFieldsDTO> entry : datasetVersionDTO.getMetadataBlocks().entrySet()) {
            MetadataBlockWithFieldsDTO value = entry.getValue();
            for (DatasetFieldDTO fieldDTO : value.getFields()) {
                if (datasetFieldTypeName.equals(fieldDTO.getTypeName())) {
                    return fieldDTO.getSinglePrimitive();
                }
            }
        }
        return null;
    }

    /**
     * Write a full tag.
     */
    public static void writeFullElement(XMLStreamWriter xmlw, String tag_parent, String tag_son,
                                        Map<String, String> map, String value, String language) throws XMLStreamException {
        // write a full generic metadata
        if (StringUtils.isNotBlank(value)) {
            boolean tag_parent_check = false;
            if (StringUtils.isNotBlank(tag_parent)) {
                xmlw.writeStartElement(tag_parent); // <value of tag_parent>
                tag_parent_check = true;
            }
            boolean tag_son_check = false;
            if (StringUtils.isNotBlank(tag_son)) {
                xmlw.writeStartElement(tag_son); // <value of tag_son>
                tag_son_check = true;
            }

            if (map != null) {
                if (StringUtils.isNotBlank(language)) {
                    if (StringUtils.containsIgnoreCase(tag_son, "subject")
                            || StringUtils.containsIgnoreCase(tag_parent, "subject")) {
                        map.put("xml:lang", language);
                    }
                }
                writeAttribute(xmlw, map);
            }

            xmlw.writeCharacters(value);

            writeEndTag(xmlw, tag_son_check); // </value of tag_son>
            writeEndTag(xmlw, tag_parent_check); //  </value of tag_parent>
        }
    }

    private static void writeAttribute(XMLStreamWriter xmlw, Map<String, String> map) throws XMLStreamException {
        // write attribute(s) of the current tag
        for (Map.Entry<String, String> entry : map.entrySet()) {
            String map_key = entry.getKey();
            String map_value = entry.getValue();

            if (StringUtils.isNotBlank(map_key) && StringUtils.isNotBlank(map_value)) {
                xmlw.writeAttribute(map_key, map_value);
            }
        }
    }

    private static boolean writeOpenTag(XMLStreamWriter xmlw, String tag, boolean element_check) throws XMLStreamException {
        // check if the current tag isn't opened
        if (!element_check) {
            xmlw.writeStartElement(tag); // <value of tag>
        }
        return true;
    }

    private static void writeEndTag(XMLStreamWriter xmlw, boolean element_check) throws XMLStreamException {
        // close the current tag
        if (element_check) {
            xmlw.writeEndElement(); // </value of current tag>
        }
    }

    /**
     * Check if the string is a valid email.
     */
    private static boolean isValidEmailAddress(String email) {
        boolean result = true;
        try {
            InternetAddress emailAddr = new InternetAddress(email);
            emailAddr.validate();
        } catch (AddressException ex) {
            result = false;
        }
        return result;
    }

    private static void writeRightsUriLicenseInfoAttribute(XMLStreamWriter xmlw, List<FileMetadataDTO> files) throws XMLStreamException {
        if (!Lists.isEmpty(files)) {
            writeRightsHeader(xmlw);

            if (areAllFilesRestricted(files)) {
                xmlw.writeCharacters("Access to all files in the dataset is restricted.");
            } else if (areAllFilesAllRightsReserved(files)) {
                xmlw.writeCharacters("All rights reserved.");
            } else if (areAllFilesUnderSameLincese(files)) {
                xmlw.writeAttribute("rightsURI", files.get(0).getLicenseUrl());
                xmlw.writeCharacters(files.get(0).getLicenseName());
            } else if (hasRestrictedFile(files)) {
                xmlw.writeCharacters(
                        "Different licenses and/or terms apply to individual files in the dataset. Access to some files in the dataset is restricted.");
            } else {
                xmlw.writeCharacters("Different licenses and/or terms apply to individual files in the dataset.");
            }

            xmlw.writeEndElement();
        }
    }

    private static void writeRightsUriInfoAttribute(XMLStreamWriter xmlw, List<FileMetadataDTO> files, boolean hasActiveGuestbook) throws XMLStreamException {
        writeRightsHeader(xmlw);

        if (Lists.isEmpty(files)) {
            xmlw.writeAttribute("rightsURI", RIGHTS_URI_CLOSED_ACCESS);
        } else if (hasActiveGuestbook || hasRestrictedFile(files)) {
            xmlw.writeAttribute("rightsURI", RIGHTS_URI_RESTRICTED_ACCESS);
        } else {
            xmlw.writeAttribute("rightsURI", RIGHTS_URI_OPEN_ACCESS);
        }


        xmlw.writeEndElement(); // </rights>
    }

    public static boolean areAllFilesUnderSameLincese(List<FileMetadataDTO> files) {
        final String firstFileLicense = files.get(0).getLicenseName();
        if (StringUtils.isNotEmpty(firstFileLicense)) {
            return files
                    .stream()
                    .allMatch(fileDTO -> firstFileLicense.equals(fileDTO.getLicenseName()));
        }
        return false;
    }

    public static boolean areAllFilesAllRightsReserved(List<FileMetadataDTO> files) {
        return files
                .stream()
                .allMatch(fileDTO -> isOfTermsOfUseType(fileDTO, FileTermsOfUse.TermsOfUseType.ALL_RIGHTS_RESERVED));
    }

    public static boolean areAllFilesRestricted(List<FileMetadataDTO> files) {
        return files
                .stream()
                .allMatch(fileDTO -> isOfTermsOfUseType(fileDTO, FileTermsOfUse.TermsOfUseType.RESTRICTED));
    }


    public static boolean hasRestrictedFile(List<FileMetadataDTO> files) {
        return files
                .stream()
                .anyMatch(fileDTO -> isOfTermsOfUseType(fileDTO, FileTermsOfUse.TermsOfUseType.RESTRICTED));
    }


    public static boolean isOfTermsOfUseType(FileMetadataDTO fileDTO, FileTermsOfUse.TermsOfUseType termsOfUseType) {
        return fileDTO.getTermsOfUseType().equals(termsOfUseType.toString());
    }
}