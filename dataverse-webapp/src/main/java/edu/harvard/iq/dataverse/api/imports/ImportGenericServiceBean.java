package edu.harvard.iq.dataverse.api.imports;

import com.google.gson.Gson;
import edu.harvard.iq.dataverse.DatasetFieldServiceBean;
import edu.harvard.iq.dataverse.MetadataBlockDao;
import edu.harvard.iq.dataverse.api.dto.DatasetDTO;
import edu.harvard.iq.dataverse.api.dto.DatasetVersionDTO;
import edu.harvard.iq.dataverse.api.dto.FieldDTO;
import edu.harvard.iq.dataverse.api.dto.MetadataBlockDTO;
import edu.harvard.iq.dataverse.common.DatasetFieldConstant;
import edu.harvard.iq.dataverse.persistence.GlobalId;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldType;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.dataset.ForeignMetadataFieldMapping;
import edu.harvard.iq.dataverse.persistence.dataset.ForeignMetadataFormatMapping;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.util.StringUtil;
import edu.harvard.iq.dataverse.util.json.JsonParseException;
import edu.harvard.iq.dataverse.util.json.JsonParser;

import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * @author ellenk
 * @author Leonid Andreev
 * @author Bob Treacy
 */
@Stateless
public class ImportGenericServiceBean {
    private static final Logger logger = Logger.getLogger(ImportGenericServiceBean.class.getCanonicalName());

    @EJB
    DatasetFieldServiceBean datasetfieldService;

    @EJB
    DatasetFieldServiceBean datasetFieldSvc;

    @EJB
    MetadataBlockDao blockService;

    @Inject
    SettingsServiceBean settingsService;

    @PersistenceContext(unitName = "VDCNet-ejbPU")
    private EntityManager em;

    public static String DCTERMS = "http://purl.org/dc/terms/";
    public static final String OAI_DC_OPENING_TAG = "dc";
    public static final String SOURCE_DVN_3_0 = "DVN_3_0";

    public static final String NOTE_TYPE_REPLICATION_FOR = "DVN:REPLICATION_FOR";

    public enum ImportType {NEW, MIGRATION, HARVEST}

    public ForeignMetadataFormatMapping findFormatMappingByName(String name) {
        try {
            return em.createNamedQuery("ForeignMetadataFormatMapping.findByName", ForeignMetadataFormatMapping.class)
                    .setParameter("name", name)
                    .getSingleResult();
        } catch (NoResultException nre) {
            return null;
        }
    }

    public void importXML(String xmlToParse, String foreignFormat, DatasetVersion datasetVersion) {

        StringReader reader;
        XMLStreamReader xmlr = null;

        ForeignMetadataFormatMapping mappingSupported = findFormatMappingByName(foreignFormat);
        if (mappingSupported == null) {
            throw new EJBException("Unknown/unsupported foreign metadata format " + foreignFormat);
        }

        try {
            reader = new StringReader(xmlToParse);
            XMLInputFactory xmlFactory = javax.xml.stream.XMLInputFactory.newInstance();
            xmlr = xmlFactory.createXMLStreamReader(reader);
            DatasetDTO datasetDTO = processXML(xmlr, mappingSupported);

            Gson gson = new Gson();
            String json = gson.toJson(datasetDTO.getDatasetVersion());
            logger.fine(json);
            JsonReader jsonReader = Json.createReader(new StringReader(json));
            JsonObject obj = jsonReader.readObject();
            new JsonParser(datasetFieldSvc, blockService, settingsService).parseDatasetVersion(obj, datasetVersion);
        } catch (XMLStreamException ex) {
            throw new EJBException("ERROR occurred while parsing XML fragment  (" + xmlToParse.substring(0, 64) + "...); ", ex);
        } catch (JsonParseException ex) {
            Logger.getLogger(ImportGenericServiceBean.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                if (xmlr != null) {
                    xmlr.close();
                }
            } catch (XMLStreamException ex) {
                logger.log(Level.WARNING, "", ex);
            }
        }
    }

     /**Helper method for importing harvested Dublin Core xml.
     Dublin Core is considered a mandatory, built in metadata format mapping.
     It is distributed as required content, in reference_data.sql.
     Note that arbitrary formatting tags are supported for the outer xml
     wrapper. -- L.A. 4.5 **/
    public DatasetDTO processOAIDCxml(String DcXmlToParse) throws XMLStreamException {

        ForeignMetadataFormatMapping dublinCoreMapping = findFormatMappingByName(DCTERMS);
        if (dublinCoreMapping == null) {
            throw new EJBException("Failed to find metadata mapping for " + DCTERMS);
        }

        DatasetDTO datasetDTO = this.initializeDataset();
        StringReader reader;
        XMLStreamReader xmlr;

        try {
            reader = new StringReader(DcXmlToParse);
            XMLInputFactory xmlFactory = javax.xml.stream.XMLInputFactory.newInstance();
            xmlr = xmlFactory.createXMLStreamReader(reader);

            xmlr.nextTag();

            xmlr.require(XMLStreamConstants.START_ELEMENT, null, OAI_DC_OPENING_TAG);

            processXMLElement(xmlr, ":", OAI_DC_OPENING_TAG, dublinCoreMapping, datasetDTO);
        } catch (XMLStreamException ex) {
            throw new EJBException("ERROR occurred while parsing XML fragment  (" + DcXmlToParse.substring(0, 64) + "...); ", ex);
        }


        datasetDTO.getDatasetVersion().setVersionState(DatasetVersion.VersionState.RELEASED);

        // Our DC import handles the contents of the dc:identifier field
        // as an "other id". In the context of OAI harvesting, we expect
        // the identifier to be a global id, so we need to rearrange that:

        String identifier = getOtherIdFromDTO(datasetDTO.getDatasetVersion());
        logger.fine("Imported identifier: " + identifier);

        String globalIdentifier = reassignIdentifierAsGlobalId(identifier, datasetDTO);
        logger.fine("Detected global identifier: " + globalIdentifier);

        if (globalIdentifier == null) {
            throw new EJBException("Failed to find a global identifier in the OAI_DC XML record.");
        }

        return datasetDTO;

    }

    private DatasetDTO processXML(XMLStreamReader xmlr, ForeignMetadataFormatMapping foreignFormatMapping) throws XMLStreamException {
        DatasetDTO datasetDTO = this.initializeDataset();

        while (xmlr.next() == XMLStreamConstants.COMMENT) ; // skip pre root comments
        String openingTag = foreignFormatMapping.getStartElement();
        if (openingTag != null) {
            xmlr.require(XMLStreamConstants.START_ELEMENT, null, openingTag);
        } else {
            throw new EJBException("No support for format mappings without start element defined (yet)");
        }

        processXMLElement(xmlr, ":", openingTag, foreignFormatMapping, datasetDTO);

        return datasetDTO;

    }

    private void processXMLElement(XMLStreamReader xmlr, String currentPath, String openingTag, ForeignMetadataFormatMapping foreignFormatMapping, DatasetDTO datasetDTO) throws XMLStreamException {
        logger.fine("entering processXMLElement; (" + currentPath + ")");

        for (int event = xmlr.next(); event != XMLStreamConstants.END_DOCUMENT; event = xmlr.next()) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                String currentElement = xmlr.getLocalName();

                ForeignMetadataFieldMapping mappingDefined = datasetfieldService.findFieldMapping(foreignFormatMapping.getName(), currentPath + currentElement);

                if (mappingDefined != null) {
                    DatasetFieldType mappingDefinedFieldType = datasetfieldService.findByNameOpt(mappingDefined.getDatasetfieldName());
                    String dataverseFieldName = mappingDefined.getDatasetfieldName();
                    // Process attributes, if any are defined in the mapping:
                    if (mappingDefinedFieldType.isCompound()) {
                        List<Set<FieldDTO>> compoundField = new ArrayList<>();
                        Set<FieldDTO> set = new HashSet<>();
                        for (ForeignMetadataFieldMapping childMapping : mappingDefined.getChildFieldMappings()) {
                            if (childMapping.isAttribute()) {
                                String attributeName = childMapping.getForeignFieldXPath();

                                String attributeValue = xmlr.getAttributeValue(null, attributeName);
                                if (attributeValue != null) {
                                    String mappedFieldName = childMapping.getDatasetfieldName();

                                    logger.fine("looking up dataset field " + mappedFieldName);

                                    DatasetFieldType mappedFieldType = datasetfieldService.findByNameOpt(mappedFieldName);
                                    if (mappedFieldType != null) {
                                        try {
                                            addToSet(set, attributeName, attributeValue);
                                        } catch (Exception ex) {
                                            logger.warning("Caught unknown exception when processing attribute " + currentPath + currentElement + "{" + attributeName + "} (skipping);");
                                        }
                                    } else {
                                        throw new EJBException("Bad foreign metadata field mapping: no such DatasetField " + mappedFieldName + "!");
                                    }
                                }
                            }
                        }
                        if (!set.isEmpty()) {
                            compoundField.add(set);
                            MetadataBlockDTO citationBlock = datasetDTO.getDatasetVersion().getMetadataBlocks().get(mappingDefinedFieldType.getMetadataBlock().getName());
                            citationBlock.addField(FieldDTO.createMultipleCompoundFieldDTO(mappingDefined.getDatasetfieldName(), compoundField));
                        } else {
                            FieldDTO value;
                            if (mappingDefinedFieldType.isAllowMultiples()) {
                                List<String> values = new ArrayList<>();
                                values.add(parseText(xmlr));
                                value = FieldDTO.createMultiplePrimitiveFieldDTO(dataverseFieldName, values);
                            } else {
                                value = FieldDTO.createPrimitiveFieldDTO(dataverseFieldName, parseText(xmlr));
                            }

                            value = makeDTO(mappingDefinedFieldType, value, dataverseFieldName);
                            MetadataBlockDTO citationBlock = datasetDTO.getDatasetVersion().getMetadataBlocks().get(mappingDefinedFieldType.getMetadataBlock().getName());
                            citationBlock.addField(value);
                        }
                    } else if (dataverseFieldName != null && !dataverseFieldName.isEmpty()) {
                            DatasetFieldType dataverseFieldType = datasetfieldService.findByNameOpt(dataverseFieldName);
                            FieldDTO value;
                            if (dataverseFieldType != null) {

                                if (dataverseFieldType.isControlledVocabulary()) {
                                    value = FieldDTO.createVocabFieldDTO(dataverseFieldName, parseText(xmlr));
                                } else {
                                    value = FieldDTO.createPrimitiveFieldDTO(dataverseFieldName, parseText(xmlr));
                                }
                                value = makeDTO(dataverseFieldType, value, dataverseFieldName);
                                MetadataBlockDTO citationBlock = datasetDTO.getDatasetVersion().getMetadataBlocks().get(mappingDefinedFieldType.getMetadataBlock().getName());
                                citationBlock.addField(value);
                            } else {
                                throw new EJBException("Bad foreign metadata field mapping: no such DatasetField " + dataverseFieldName + "!");
                            }
                        }
                } else {
                    processXMLElement(xmlr, currentPath + currentElement + ":", currentElement, foreignFormatMapping, datasetDTO);
                }

            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (xmlr.getLocalName().equals(openingTag)) {
                    return;
                }
            }
        }
    }

    private FieldDTO makeDTO(DatasetFieldType dataverseFieldType, FieldDTO value, String dataverseFieldName) {
        if (dataverseFieldType.isAllowMultiples()) {
            if (dataverseFieldType.isCompound()) {
                value = FieldDTO.createMultipleCompoundFieldDTO(dataverseFieldName, value);
            } else if (dataverseFieldType.isControlledVocabulary()) {
                value = FieldDTO.createMultipleVocabFieldDTO(dataverseFieldName, Arrays.asList(value.getSinglePrimitive()));
            } else {
                value = FieldDTO.createMultiplePrimitiveFieldDTO(dataverseFieldName, Arrays.asList(value.getSinglePrimitive()));
            }
            if (dataverseFieldType.isChild()) {
                DatasetFieldType parentDatasetFieldType = dataverseFieldType.getParentDatasetFieldType();
                if (parentDatasetFieldType.isAllowMultiples()) {
                    value = FieldDTO.createMultipleCompoundFieldDTO(parentDatasetFieldType.getName(), value);

                }
            }
        } else {
            if (dataverseFieldType.isCompound()) {
                value = FieldDTO.createCompoundFieldDTO(dataverseFieldName, value);
            }
        }

        // TODO:
        // it looks like the code below has already been executed, in one of the
        // if () blocks above... is this ok to be doing it again?? -- L.A. 4.5
        if (dataverseFieldType.isChild()) {
            DatasetFieldType parentDatasetFieldType = dataverseFieldType.getParentDatasetFieldType();
            if (parentDatasetFieldType.isAllowMultiples()) {
                value = FieldDTO.createMultipleCompoundFieldDTO(parentDatasetFieldType.getName(), value);

            }
        }
        return value;
    }

    private String getOtherIdFromDTO(DatasetVersionDTO datasetVersionDTO) {
        for (Map.Entry<String, MetadataBlockDTO> entry : datasetVersionDTO.getMetadataBlocks().entrySet()) {
            String key = entry.getKey();
            MetadataBlockDTO value = entry.getValue();
            if ("citation".equals(key)) {
                for (FieldDTO fieldDTO : value.getFields()) {
                    if (DatasetFieldConstant.otherId.equals(fieldDTO.getTypeName())) {
                        String otherId = "";
                        for (Set<FieldDTO> foo : fieldDTO.getMultipleCompound()) {
                            for (FieldDTO next : foo) {
                                if (DatasetFieldConstant.otherIdValue.equals(next.getTypeName())) {
                                    otherId = next.getSinglePrimitive();
                                }
                            }
                            if (!otherId.isEmpty()) {
                                return otherId;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private String reassignIdentifierAsGlobalId(String identifierString, DatasetDTO datasetDTO) {

        int index1 = identifierString.indexOf(':');
        int index2 = identifierString.indexOf('/');
        if (index1 == -1) {
            logger.warning("Error parsing identifier: " + identifierString + ". ':' not found in string");
            return null;
        }

        String protocol = identifierString.substring(0, index1);

        if (GlobalId.DOI_PROTOCOL.equals(protocol) || GlobalId.HDL_PROTOCOL.equals(protocol)) {
            logger.fine("Processing hdl:- or doi:-style identifier : " + identifierString);

        } else if ("http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol)) {

            // We also recognize global identifiers formatted as global resolver URLs:
            if (identifierString.startsWith(GlobalId.HDL_RESOLVER_URL)) {
                logger.fine("Processing Handle identifier formatted as a resolver URL: " + identifierString);
                protocol = GlobalId.HDL_PROTOCOL;
                index1 = GlobalId.HDL_RESOLVER_URL.length() - 1;
                index2 = identifierString.indexOf("/", index1 + 1);
            } else if (identifierString.startsWith(GlobalId.DOI_RESOLVER_URL)) {
                logger.fine("Processing DOI identifier formatted as a resolver URL: " + identifierString);
                protocol = GlobalId.DOI_PROTOCOL;
                index1 = GlobalId.DOI_RESOLVER_URL.length() - 1;
                index2 = identifierString.indexOf("/", index1 + 1);
            } else {
                logger.warning("HTTP Url in supplied as the identifier is neither a Handle nor DOI resolver: " + identifierString);
                return null;
            }
        } else {
            logger.warning("Unknown identifier format: " + identifierString);
            return null;
        }

        if (index2 == -1) {
            logger.warning("Error parsing identifier: " + identifierString + ". Second '/' not found in string");
            return null;
        }

        String authority = identifierString.substring(index1 + 1, index2);
        String identifier = identifierString.substring(index2 + 1);

        datasetDTO.setProtocol(protocol);
        datasetDTO.setAuthority(authority);
        datasetDTO.setIdentifier(identifier);

        // reassemble and return:
        logger.fine("parsed identifier, finalized " + protocol + ":" + authority + "/" + identifier);
        return protocol + ":" + authority + "/" + identifier;
    }

    // EMK TODO: update unit test so this doesn't have to be public
    private DatasetDTO initializeDataset() {
        DatasetDTO datasetDTO = new DatasetDTO();
        DatasetVersionDTO datasetVersionDTO = new DatasetVersionDTO();
        datasetDTO.setDatasetVersion(datasetVersionDTO);
        HashMap<String, MetadataBlockDTO> metadataBlocks = new HashMap<>();
        datasetVersionDTO.setMetadataBlocks(metadataBlocks);

        datasetVersionDTO.getMetadataBlocks().put("citation", new MetadataBlockDTO());
        datasetVersionDTO.getMetadataBlocks().get("citation").setFields(new ArrayList<>());
        datasetVersionDTO.getMetadataBlocks().put("geospatial", new MetadataBlockDTO());
        datasetVersionDTO.getMetadataBlocks().get("geospatial").setFields(new ArrayList<>());
        datasetVersionDTO.getMetadataBlocks().put("social_science", new MetadataBlockDTO());
        datasetVersionDTO.getMetadataBlocks().get("social_science").setFields(new ArrayList<>());
        datasetVersionDTO.getMetadataBlocks().put("astrophysics", new MetadataBlockDTO());
        datasetVersionDTO.getMetadataBlocks().get("astrophysics").setFields(new ArrayList<>());

        return datasetDTO;
    }

    private String parseText(XMLStreamReader xmlr) throws XMLStreamException {
        String tempString = getElementText(xmlr);
        tempString = tempString.trim().replace('\n', ' ');
        return tempString;
    }

    /** We had to add this method because the ref getElementText has a bug where it
     * would append a null before the text, if there was an escaped apostrophe; it appears
     * that the code finds an null ENTITY_REFERENCE in this case which seems like a bug;
     * the workaround for the moment is to comment or handling ENTITY_REFERENCE in this case
     */
    private String getElementText(XMLStreamReader xmlr) throws XMLStreamException {
        if (xmlr.getEventType() != XMLStreamConstants.START_ELEMENT) {
            throw new XMLStreamException("parser must be on START_ELEMENT to read next text", xmlr.getLocation());
        }
        int eventType = xmlr.next();
        StringBuilder content = new StringBuilder();
        while (eventType != XMLStreamConstants.END_ELEMENT) {
            if (eventType == XMLStreamConstants.CHARACTERS
                    || eventType == XMLStreamConstants.CDATA
                    || eventType == XMLStreamConstants.SPACE) {
                content.append(xmlr.getText());
            } else if (eventType == XMLStreamConstants.PROCESSING_INSTRUCTION
                    || eventType == XMLStreamConstants.COMMENT
                    || eventType == XMLStreamConstants.ENTITY_REFERENCE) {
                // skipping
            } else if (eventType == XMLStreamConstants.END_DOCUMENT) {
                throw new XMLStreamException("unexpected end of document when reading element text content");
            } else if (eventType == XMLStreamConstants.START_ELEMENT) {
                throw new XMLStreamException("element text content may not contain START_ELEMENT", xmlr.getLocation());
            } else {
                throw new XMLStreamException("Unexpected event type " + eventType, xmlr.getLocation());
            }
            eventType = xmlr.next();
        }
        return content.toString();
    }

    private String parseText(XMLStreamReader xmlr, String endTag) throws XMLStreamException {
        return (String) parseTextNew(xmlr, endTag);
    }


    private Object parseTextNew(XMLStreamReader xmlr, String endTag) throws XMLStreamException {
        StringBuilder returnString = new StringBuilder();
        Map<String, Object> returnMap = null;

        while (true) {
            if (returnString.length() > 0) {
                returnString.append("\n");
            }
            int event = xmlr.next();
            if (event == XMLStreamConstants.CHARACTERS) {
                returnString.append(xmlr.getText().trim().replace('\n', ' '));
            } else if (event == XMLStreamConstants.START_ELEMENT) {
                switch (xmlr.getLocalName()) {
                    case "p":
                        returnString.append("<p>").append(parseText(xmlr, "p")).append("</p>");
                        break;
                    case "emph":
                        returnString.append("<em>").append(parseText(xmlr, "emph")).append("</em>");
                        break;
                    case "hi":
                        returnString.append("<strong>").append(parseText(xmlr, "hi")).append("</strong>");
                        break;
                    case "ExtLink":
                        String uri = xmlr.getAttributeValue(null, "URI");
                        String text = parseText(xmlr, "ExtLink").trim();
                        returnString
                                .append("<a href=\"")
                                .append(uri)
                                .append("\">")
                                .append(StringUtil.isEmpty(text) ? uri : text)
                                .append("</a>");
                        break;
                    case "list":
                        returnString.append(parseText_list(xmlr));
                        break;
                    case "citation":
                        if (SOURCE_DVN_3_0.equals(xmlr.getAttributeValue(null, "source"))) {
                            returnMap = parseDVNCitation(xmlr);
                        } else {
                            returnString.append(parseText_citation(xmlr));
                        }
                        break;
                    default:
                        throw new EJBException("ERROR occurred in mapDDI (parseText): tag not yet supported: <" + xmlr.getLocalName() + ">");
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (xmlr.getLocalName().equals(endTag)) {
                    break;
                }
            }
        }

        if (returnMap != null) {
            return returnMap;
        }

        return returnString.toString().trim();
    }

    private String parseText_list(XMLStreamReader xmlr) throws XMLStreamException {
        String listString;
        String listCloseTag;

        String listType = xmlr.getAttributeValue(null, "type");
        if ("bulleted".equals(listType)) {
            listString = "<ul>\n";
            listCloseTag = "</ul>";
        } else if ("ordered".equals(listType)) {
            listString = "<ol>\n";
            listCloseTag = "</ol>";
        } else {
            throw new EJBException("ERROR occurred in mapDDI (parseText): ListType of types other than {bulleted, ordered} not currently supported.");
        }

        while (true) {
            int event = xmlr.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (xmlr.getLocalName().equals("itm")) {
                    listString += "<li>" + parseText(xmlr, "itm") + "</li>\n";
                } else {
                    throw new EJBException("ERROR occurred in mapDDI (parseText): ListType does not currently supported contained LabelType.");
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (xmlr.getLocalName().equals("list")) {
                    break;
                }
            }
        }

        return (listString + listCloseTag);
    }

    private String parseText_citation(XMLStreamReader xmlr) throws XMLStreamException {
        StringBuilder citation = new StringBuilder("<!--  parsed from DDI citation title and holdings -->");
        boolean addHoldings = false;
        StringBuilder holdings = new StringBuilder();

        while (true) {
            int event = xmlr.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (xmlr.getLocalName().equals("titlStmt")) {
                    while (true) {
                        event = xmlr.next();
                        if (event == XMLStreamConstants.START_ELEMENT) {
                            if (xmlr.getLocalName().equals("titl")) {
                                citation.append(parseText(xmlr));
                            }
                        } else if (event == XMLStreamConstants.END_ELEMENT) {
                            if (xmlr.getLocalName().equals("titlStmt")) {
                                break;
                            }
                        }
                    }
                } else if (xmlr.getLocalName().equals("holdings")) {
                    String uri = xmlr.getAttributeValue(null, "URI");
                    String holdingsText = parseText(xmlr);

                    if (!StringUtil.isEmpty(uri) || !StringUtil.isEmpty(holdingsText)) {
                        holdings.append(addHoldings ? ", " : "");
                        addHoldings = true;

                        if (StringUtil.isEmpty(uri)) {
                            holdings.append(holdingsText);
                        } else if (StringUtil.isEmpty(holdingsText)) {
                            holdings.append("<a href=\"").append(uri).append("\">").append(uri).append("</a>");
                        } else {
                            // both uri and text have values
                            holdings.append("<a href=\"").append(uri).append("\">").append(holdingsText).append("</a>");
                        }
                    }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (xmlr.getLocalName().equals("citation")) {
                    break;
                }
            }
        }

        if (addHoldings) {
            citation.append(" (").append(holdings).append(")");
        }

        return citation.toString();
    }

    private Map<String, Object> parseDVNCitation(XMLStreamReader xmlr) throws XMLStreamException {
        Map<String, Object> returnValues = new HashMap<>();

        while (true) {
            int event = xmlr.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                switch (xmlr.getLocalName()) {
                    case "IDNo":
                        returnValues.put("idType", xmlr.getAttributeValue(null, "agency"));
                        returnValues.put("idNumber", parseText(xmlr));
                        break;
                    case "biblCit":
                        returnValues.put("text", parseText(xmlr));
                        break;
                    case "holdings":
                        returnValues.put("url", xmlr.getAttributeValue(null, "URI"));
                        break;
                    case "notes":
                        if (NOTE_TYPE_REPLICATION_FOR.equals(xmlr.getAttributeValue(null, "type"))) {
                            returnValues.put("replicationData", true);
                        }
                        break;
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (xmlr.getLocalName().equals("citation")) {
                    break;
                }
            }
        }

        return returnValues;
    }

    private void addToSet(Set<FieldDTO> set, String typeName, String value) {
        if (value != null) {
            set.add(FieldDTO.createPrimitiveFieldDTO(typeName, value));
        }
    }

}
