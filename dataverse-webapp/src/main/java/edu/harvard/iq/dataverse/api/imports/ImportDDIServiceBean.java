package edu.harvard.iq.dataverse.api.imports;

import edu.harvard.iq.dataverse.DatasetFieldServiceBean;
import edu.harvard.iq.dataverse.api.imports.dto.DataFileDTO;
import edu.harvard.iq.dataverse.api.imports.dto.DatasetDTO;
import edu.harvard.iq.dataverse.api.imports.dto.DatasetVersionDTO;
import edu.harvard.iq.dataverse.api.imports.dto.FieldDTO;
import edu.harvard.iq.dataverse.api.imports.dto.FileDTO;
import edu.harvard.iq.dataverse.api.imports.dto.MetadataBlockDTO;
import edu.harvard.iq.dataverse.common.DatasetFieldConstant;
import edu.harvard.iq.dataverse.persistence.datafile.license.FileTermsOfUse.TermsOfUseType;
import edu.harvard.iq.dataverse.persistence.datafile.license.License;
import edu.harvard.iq.dataverse.persistence.dataset.CustomFieldMap;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldType;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion.VersionState;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;

import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.Stateless;
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

import static edu.harvard.iq.dataverse.export.ddi.DdiConstants.NOTE_TYPE_CONTENTTYPE;
import static edu.harvard.iq.dataverse.export.ddi.DdiConstants.NOTE_TYPE_TERMS_OF_ACCESS;
import static edu.harvard.iq.dataverse.export.ddi.DdiConstants.NOTE_TYPE_TERMS_OF_USE;
import static edu.harvard.iq.dataverse.export.ddi.DdiConstants.NOTE_TYPE_UNF;


/**
 * @author ellenk
 */
@Stateless
public class ImportDDIServiceBean {
    public static final String SOURCE_DVN_3_0 = "DVN_3_0";

    public static final String AGENCY_HANDLE = "handle";
    public static final String AGENCY_DOI = "DOI";
    public static final String AGENCY_DARA = "dara"; // da|ra - http://www.da-ra.de/en/home/
    public static final String REPLICATION_FOR_TYPE = "replicationFor";


    public static final String EVENT_START = "start";
    public static final String EVENT_END = "end";
    public static final String EVENT_SINGLE = "single";

    public static final String LEVEL_DV = "dv";

    public static final String NOTE_TYPE_EXTENDED_METADATA = "DVN:EXTENDED_METADATA";

    public static final String NOTE_TYPE_REPLICATION_FOR = "DVN:REPLICATION_FOR";
    private static final String HARVESTED_FILE_STORAGE_PREFIX = "http://";
    private static final String TERMS_OF_USE_CC0_LICENSE = "CC0 Waiver";

    @EJB
    CustomFieldServiceBean customFieldService;

    @EJB
    DatasetFieldServiceBean datasetFieldService;


    // TODO: stop passing the xml source as a string; (it could be huge!) -- L.A. 4.5
    // TODO: what L.A. Said.
    public DatasetDTO doImport(ImportType importType, String xmlToParse) throws XMLStreamException, ImportException {
        DatasetDTO datasetDTO = this.initializeDataset();

        // Read docDescr and studyDesc into DTO objects.
        mapDDI(importType, xmlToParse, datasetDTO);
        return datasetDTO;
    }

    private boolean isHarvestImport(ImportType importType) {
        return importType.equals(ImportType.HARVEST);
    }

    private void mapDDI(ImportType importType, String xmlToParse, DatasetDTO datasetDTO) throws XMLStreamException, ImportException {

        StringReader reader = new StringReader(xmlToParse);
        XMLStreamReader xmlr = null;
        XMLInputFactory xmlFactory = XMLInputFactory.newInstance();
        xmlFactory.setProperty("javax.xml.stream.isCoalescing", true); // allows the parsing of a CDATA segment into a single event
        xmlr = xmlFactory.createXMLStreamReader(reader);
        processDDI(importType, xmlr, datasetDTO);

    }

    private void processDDI(ImportType importType, XMLStreamReader xmlr, DatasetDTO datasetDTO) throws XMLStreamException, ImportException {

        // make sure we have a codeBook
        //while ( xmlr.next() == XMLStreamConstants.COMMENT ); // skip pre root comments
        xmlr.nextTag();
        xmlr.require(XMLStreamConstants.START_ELEMENT, null, "codeBook");

        // Some DDIs provide an ID in the <codeBook> section.
        // We are going to treat it as just another otherId.
        // (we've seen instances where this ID was the only ID found in
        // in a harvested DDI).

        String codeBookLevelId = xmlr.getAttributeValue(null, "ID");

        // (but first we will parse and process the entire DDI - and only
        // then add this codeBook-level id to the list of identifiers; i.e.,
        // we don't want it to be the first on the list, if one or more
        // ids are available in the studyDscr section - those should take
        // precedence!)
        // In fact, we should only use these IDs when no ID is available down
        // in the study description section!

        processCodeBook(importType, xmlr, datasetDTO);
        MetadataBlockDTO citationBlock = getCitation(datasetDTO.getDatasetVersion());

        if (StringUtils.isNotEmpty(codeBookLevelId)) {
            if (citationBlock.getField(DatasetFieldConstant.otherId) == null) {
                // this means no ids were found during the parsing of the
                // study description section. we'll use the one we found in
                // the codeBook entry:
                FieldDTO otherIdValue = FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.otherIdValue, codeBookLevelId);
                FieldDTO otherId = FieldDTO.createMultipleCompoundFieldDTO(DatasetFieldConstant.otherId, otherIdValue);
                citationBlock.getFields().add(otherId);

            }
        }

        if (isHarvestImport(importType)) {
            datasetDTO.getDatasetVersion().setVersionState(VersionState.RELEASED);

        } else {
            datasetDTO.getDatasetVersion().setVersionState(VersionState.DRAFT);
        }


    }

    private DatasetDTO initializeDataset() {
        DatasetDTO datasetDTO = new DatasetDTO();
        DatasetVersionDTO datasetVersionDTO = new DatasetVersionDTO();
        datasetDTO.setDatasetVersion(datasetVersionDTO);
        HashMap<String, MetadataBlockDTO> metadataBlocks = new HashMap<>();
        datasetVersionDTO.setMetadataBlocks(metadataBlocks);

        datasetVersionDTO.getMetadataBlocks().put("citation", new MetadataBlockDTO());
        datasetVersionDTO.getMetadataBlocks().get("citation").setFields(new ArrayList<>());
        datasetVersionDTO.getMetadataBlocks().put("socialscience", new MetadataBlockDTO());
        datasetVersionDTO.getMetadataBlocks().get("socialscience").setFields(new ArrayList<>());
        datasetVersionDTO.getMetadataBlocks().put("geospatial", new MetadataBlockDTO());
        datasetVersionDTO.getMetadataBlocks().get("geospatial").setFields(new ArrayList<>());

        datasetDTO.getDatasetVersion().setFiles(new ArrayList<>());

        return datasetDTO;

    }

    private void processCodeBook(ImportType importType, XMLStreamReader xmlr, DatasetDTO datasetDTO) throws XMLStreamException, ImportException {
        TermsOfUseDataHolder termsOfUseDataHolder = new TermsOfUseDataHolder();

        for (int event = xmlr.next(); event != XMLStreamConstants.END_DOCUMENT; event = xmlr.next()) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (xmlr.getLocalName().equals("docDscr")) {
                    processDocDscr(xmlr, datasetDTO);
                } else if (xmlr.getLocalName().equals("stdyDscr")) {
                    processStdyDscr(importType, xmlr, datasetDTO, termsOfUseDataHolder);
                } else if (xmlr.getLocalName().equals("otherMat") && isHarvestImport(importType)) {
                    processOtherMat(xmlr, datasetDTO);
                } else if (xmlr.getLocalName().equals("fileDscr") && isHarvestImport(importType)) {
                    // If this is a harvesting import, we'll attempt to extract some minimal
                    // file-level metadata information from the fileDscr sections as well.
                    // TODO: add more info here... -- 4.6
                    processFileDscrMinimal(xmlr, datasetDTO);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (xmlr.getLocalName().equals("codeBook")) {
                    applyTermsOfUseToFiles(datasetDTO, termsOfUseDataHolder);
                    return;
                }
            }
        }
    }

    /**
     * Applies terms of use into all of the files in dataset.
     * Do note that if there is any data in {@link TermsOfUseDataHolder#getTermsOfUse()},
     * it will be interpreted by this method as a license. It is not necessarily
     * the truth. And we let further processing (parsing {@link DatasetDTO} to entity model)
     * to decide whether some license should be assigned or not.
     */
    private void applyTermsOfUseToFiles(DatasetDTO datasetDTO, TermsOfUseDataHolder termsOfUseDataHolder) {
        String termsOfUse = termsOfUseDataHolder.getTermsOfUse();

        for (FileDTO file: datasetDTO.getDatasetVersion().getFiles()) {

            if (StringUtils.isNotBlank(termsOfUse)) {

                if (termsOfUse.equals(TERMS_OF_USE_CC0_LICENSE)) {
                    file.setTermsOfUseType(TermsOfUseType.LICENSE_BASED.toString());
                    file.setLicenseName(License.CCO_LICENSE_NAME);
                } else {
                    file.setTermsOfUseType(TermsOfUseType.LICENSE_BASED.toString());
                    file.setLicenseName(termsOfUseDataHolder.getTermsOfUse());
                }
            } else {
                file.setTermsOfUseType(TermsOfUseType.TERMS_UNKNOWN.toString());
            }
        }
    }

    private void processDocDscr(XMLStreamReader xmlr, DatasetDTO datasetDTO) throws XMLStreamException {
        for (int event = xmlr.next(); event != XMLStreamConstants.END_DOCUMENT; event = xmlr.next()) {
            if (event == XMLStreamConstants.START_ELEMENT) {

                if (xmlr.getLocalName().equals("IDNo") && StringUtils.isBlank(datasetDTO.getIdentifier())) {
                    // this will set a StudyId if it has not yet been set; it will get overridden by a metadata
                    // id in the StudyDscr section, if one exists
                    if (AGENCY_HANDLE.equals(xmlr.getAttributeValue(null, "agency"))) {
                        parseStudyIdHandle(parseText(xmlr), datasetDTO);
                    }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (xmlr.getLocalName().equals("docDscr")) {
                    return;
                }
            }
        }
    }

    private String parseText(XMLStreamReader xmlr) throws XMLStreamException {
        return parseText(xmlr, true);
    }

    private String parseText(XMLStreamReader xmlr, boolean scrubText) throws XMLStreamException {
        String tempString = getElementText(xmlr);
        if (scrubText) {
            tempString = tempString.trim().replace('\n', ' ');
        }
        return tempString;
    }

    private String parseDate(XMLStreamReader xmlr) throws XMLStreamException {
        String date = xmlr.getAttributeValue(null, "date");
        if (date == null) {
            date = parseText(xmlr);
        }
        return date;
    }

    /* We had to add this method because the ref getElementText has a bug where it
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
                    || eventType == XMLStreamConstants.SPACE
                /* || eventType == XMLStreamConstants.ENTITY_REFERENCE*/) {
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

    private void processStdyDscr(ImportType importType, XMLStreamReader xmlr, DatasetDTO datasetDTO, TermsOfUseDataHolder termsOfUseDataHolder) throws XMLStreamException, ImportException {

        for (int event = xmlr.next(); event != XMLStreamConstants.END_DOCUMENT; event = xmlr.next()) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (xmlr.getLocalName().equals("citation")) {
                    processCitation(importType, xmlr, datasetDTO);
                } else if (xmlr.getLocalName().equals("stdyInfo")) {
                    processStdyInfo(xmlr, datasetDTO.getDatasetVersion());
                } else if (xmlr.getLocalName().equals("method")) {
                    processMethod(xmlr, datasetDTO.getDatasetVersion());
                } else if (xmlr.getLocalName().equals("dataAccs")) {
                    processDataAccs(xmlr, datasetDTO.getDatasetVersion(), termsOfUseDataHolder);
                } else if (xmlr.getLocalName().equals("othrStdyMat")) {
                    processOthrStdyMat(xmlr, datasetDTO.getDatasetVersion());
                } else if (xmlr.getLocalName().equals("notes")) {
                    processStdyNotes(xmlr, datasetDTO.getDatasetVersion());
                }

            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (xmlr.getLocalName().equals("stdyDscr")) {
                    return;
                }
            }
        }
    }

    private void processOthrStdyMat(XMLStreamReader xmlr, DatasetVersionDTO dvDTO) throws XMLStreamException {
        List<Set<FieldDTO>> publications = new ArrayList<>();
        List<Set<FieldDTO>> relatedMaterials = new ArrayList<>();
        List<Set<FieldDTO>> relatedStudy = new ArrayList<>();

        Set<FieldDTO> legacyMaterialFields = new HashSet<>();
        Set<FieldDTO> legacyStudyFields = new HashSet<>();

        boolean replicationForFound = false;
        final String localName = xmlr.getLocalName();
        for (int event = xmlr.next(); event != XMLStreamConstants.END_DOCUMENT; event = xmlr.next()) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (xmlr.getLocalName().equals("relMat")) {
                    // this code is still here to handle imports from old DVN created ddis
                    if (!replicationForFound && REPLICATION_FOR_TYPE.equals(xmlr.getAttributeValue(null, "type"))) {
                        if (!SOURCE_DVN_3_0.equals(xmlr.getAttributeValue(null, "source"))) {
                            // this is a ddi from pre 3.0, so we should add a publication
                            Set<FieldDTO> set = new HashSet<>();
                            addToSet(set, DatasetFieldConstant.publicationCitation, parseHtmlAwareText(xmlr));
                            if (!set.isEmpty()) {
                                publications.add(set);
                            }
                            if (!publications.isEmpty()) {
                                getCitation(dvDTO).addField(FieldDTO.createMultipleCompoundFieldDTO(DatasetFieldConstant.publication, publications));
                            }
                        }
                    } else {
                        processRelMat(xmlr, dvDTO, relatedMaterials, legacyMaterialFields);
                    }
                } else if (xmlr.getLocalName().equals("relStdy")) {
                    processRelStdy(xmlr, dvDTO, relatedStudy, legacyStudyFields);
                } else if (xmlr.getLocalName().equals("relPubl")) {
                    processRelPubl(xmlr, publications);
                } else if (xmlr.getLocalName().equals("othRefs")) {

                    List<String> otherRefs = new ArrayList<>();
                    otherRefs.add(parseHtmlAwareText(xmlr));
                    getCitation(dvDTO).addField(FieldDTO.createMultiplePrimitiveFieldDTO(DatasetFieldConstant.otherReferences, otherRefs));

                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (!publications.isEmpty()) {
                    getCitation(dvDTO).addField(FieldDTO.createMultipleCompoundFieldDTO(DatasetFieldConstant.publication, publications));
                }

                if (!legacyMaterialFields.isEmpty()){
                    legacyMaterialFields.forEach(fieldDTO -> getCitation(dvDTO).addField(fieldDTO));
                } else if (!relatedMaterials.isEmpty()){
                    getCitation(dvDTO).addField(FieldDTO.createMultipleCompoundFieldDTO(DatasetFieldConstant.relatedMaterial, relatedMaterials));
                }

                if (!legacyStudyFields.isEmpty()){
                    legacyStudyFields.forEach(fieldDTO -> getCitation(dvDTO).addField(fieldDTO));
                } else if (!relatedStudy.isEmpty()){
                    getCitation(dvDTO).addField(FieldDTO.createMultipleCompoundFieldDTO(DatasetFieldConstant.relatedDataset, relatedStudy));
                }
                if (xmlr.getLocalName().equals("othrStdyMat")) {
                    return;
                }
            }
        }
    }

    private void processRelMat(XMLStreamReader xmlr, DatasetVersionDTO dvDTO,
                               List<Set<FieldDTO>> materials, Set<FieldDTO> legacyDdi) throws XMLStreamException {
        Set<FieldDTO> set = new HashSet<>();
        for (int event = xmlr.next(), counter = 0; event != XMLStreamConstants.END_DOCUMENT; event = xmlr.next(), counter++) {
            if (isLegacyDdi(event, counter) && !StringUtils.isBlank(xmlr.getText())) {
                final String citationText = xmlr.getText();
                final FieldDTO citationFieldDto = FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.relatedMaterialCitation, citationText);
                final FieldDTO relatedMat = FieldDTO.createMultipleCompoundFieldDTO(DatasetFieldConstant.relatedMaterial, citationFieldDto);

                if (xmlr.next() == XMLStreamConstants.END_ELEMENT){
                    legacyDdi.add(relatedMat);
                    break;
                }
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (xmlr.getLocalName().equals("citation")) {
                    for (int event2 = xmlr.next(); event2 != XMLStreamConstants.END_DOCUMENT; event2 = xmlr.next()) {
                        if (event2 == XMLStreamConstants.START_ELEMENT) {
                            if (xmlr.getLocalName().equals("titlStmt")) {
                                for (int event3 = xmlr.next(); event3 != XMLStreamConstants.END_DOCUMENT; event3 = xmlr.next()) {
                                    if (event3 == XMLStreamConstants.START_ELEMENT) {
                                        if (xmlr.getLocalName().equals("IDNo")) {
                                            set.add(FieldDTO.createVocabFieldDTO(DatasetFieldConstant.relatedMaterialIDType, xmlr
                                                    .getAttributeValue(null, "agency")));
                                            addToSet(set, DatasetFieldConstant.relatedMaterialIDNumber, parseText(xmlr));
                                        }
                                    } else if (event3 == XMLStreamConstants.END_ELEMENT) {
                                        if (xmlr.getLocalName().equals("titlStmt")) {
                                            break;
                                        }
                                    }
                                }
                            } else if (xmlr.getLocalName().equals("biblCit")) {
                                addToSet(set, DatasetFieldConstant.relatedMaterialCitation, parseText(xmlr));
                            }
                        } else if (event2 == XMLStreamConstants.END_ELEMENT) {
                            if (xmlr.getLocalName().equals("citation")) {
                                break;
                            }
                        }

                    }
                } else if (xmlr.getLocalName().equals("ExtLink")) {
                    addToSet(set, DatasetFieldConstant.relatedMaterialURL, xmlr.getAttributeValue(null, "URI"));
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (xmlr.getLocalName().equals("relMat")) {
                    if (set.size() > 0) {
                        materials.add(set);
                    }
                    return;
                }
            }
        }
    }

    private boolean isLegacyDdi(int event, int counter) {
        return event == XMLStreamConstants.CHARACTERS && counter == 0;
    }

    private void processRelStdy(XMLStreamReader xmlr, DatasetVersionDTO dvDTO,  List<Set<FieldDTO>> studies, Set<FieldDTO> legacyDdi) throws XMLStreamException {
        Set<FieldDTO> set = new HashSet<>();
        for (int event = xmlr.next(), counter = 0; event != XMLStreamConstants.END_DOCUMENT; event = xmlr.next(), counter++) {
            if (isLegacyDdi(event, counter) && !StringUtils.isBlank(xmlr.getText())) {
                final String citationText = xmlr.getText();
                final FieldDTO citationFieldDto = FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.relatedDatasetCitation, citationText);
                final FieldDTO relatedDataset = FieldDTO.createMultipleCompoundFieldDTO(DatasetFieldConstant.relatedDataset, citationFieldDto);

                if (xmlr.next() == XMLStreamConstants.END_ELEMENT){
                    legacyDdi.add(relatedDataset);
                    break;
                }
            }
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (xmlr.getLocalName().equals("citation")) {
                    for (int event2 = xmlr.next(); event2 != XMLStreamConstants.END_DOCUMENT; event2 = xmlr.next()) {
                        if (event2 == XMLStreamConstants.START_ELEMENT) {
                            if (xmlr.getLocalName().equals("titlStmt")) {
                                for (int event3 = xmlr.next(); event3 != XMLStreamConstants.END_DOCUMENT; event3 = xmlr.next()) {
                                    if (event3 == XMLStreamConstants.START_ELEMENT) {
                                        if (xmlr.getLocalName().equals("IDNo")) {
                                            set.add(FieldDTO.createVocabFieldDTO(DatasetFieldConstant.relatedDatasetIDType, xmlr
                                                    .getAttributeValue(null, "agency")));
                                            addToSet(set, DatasetFieldConstant.relatedDatasetIDNumber, parseText(xmlr));
                                        }
                                    } else if (event3 == XMLStreamConstants.END_ELEMENT) {
                                        if (xmlr.getLocalName().equals("titlStmt")) {
                                            break;
                                        }
                                    }
                                }
                            } else if (xmlr.getLocalName().equals("biblCit")) {
                                addToSet(set, DatasetFieldConstant.relatedDatasetCitation, parseText(xmlr));
                            }
                        } else if (event2 == XMLStreamConstants.END_ELEMENT) {
                            if (xmlr.getLocalName().equals("citation")) {
                                break;
                            }
                        }

                    }
                } else if (xmlr.getLocalName().equals("ExtLink")) {
                    addToSet(set, DatasetFieldConstant.relatedDatasetURL, xmlr.getAttributeValue(null, "URI"));
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (xmlr.getLocalName().equals("relStdy")) {
                    if (set.size() > 0) {
                        studies.add(set);
                    }
                    return;
                }
            }
        }
    }

    private void processRelPubl(XMLStreamReader xmlr, List<Set<FieldDTO>> publications) throws XMLStreamException {
        HashSet<FieldDTO> set = new HashSet<>();
        for (int event = xmlr.next(); event != XMLStreamConstants.END_DOCUMENT; event = xmlr.next()) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (xmlr.getLocalName().equals("citation")) {
                    for (int event2 = xmlr.next(); event2 != XMLStreamConstants.END_DOCUMENT; event2 = xmlr.next()) {
                        if (event2 == XMLStreamConstants.START_ELEMENT) {
                            if (xmlr.getLocalName().equals("titlStmt")) {
                                for (int event3 = xmlr.next(); event3 != XMLStreamConstants.END_DOCUMENT; event3 = xmlr.next()) {
                                    if (event3 == XMLStreamConstants.START_ELEMENT) {
                                        if (xmlr.getLocalName().equals("IDNo")) {
                                            set.add(FieldDTO.createVocabFieldDTO(DatasetFieldConstant.publicationIDType, xmlr.getAttributeValue(null, "agency")));
                                            addToSet(set, DatasetFieldConstant.publicationIDNumber, parseText(xmlr));
                                        }
                                    } else if (event3 == XMLStreamConstants.END_ELEMENT) {
                                        if (xmlr.getLocalName().equals("titlStmt")) {
                                            break;
                                        }
                                    }
                                }
                            } else if (xmlr.getLocalName().equals("biblCit")) {
                                addToSet(set, DatasetFieldConstant.publicationCitation, parseText(xmlr));
                            }
                        } else if (event2 == XMLStreamConstants.END_ELEMENT) {
                            if (xmlr.getLocalName().equals("citation")) {
                                break;
                            }
                        }

                    }
                } else if (xmlr.getLocalName().equals("ExtLink")) {
                    addToSet(set, DatasetFieldConstant.publicationURL, xmlr.getAttributeValue(null, "URI"));
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (xmlr.getLocalName().equals("relPubl")) {
                    if (!set.isEmpty()) {
                        publications.add(set);
                    }
                    return;
                }
            }
        }
    }

    private void processCitation(ImportType importType, XMLStreamReader xmlr, DatasetDTO datasetDTO) throws XMLStreamException, ImportException {
        DatasetVersionDTO dvDTO = datasetDTO.getDatasetVersion();
        MetadataBlockDTO citation = getCitation(dvDTO);
        boolean distStatementProcessed = false;
        for (int event = xmlr.next(); event != XMLStreamConstants.END_DOCUMENT; event = xmlr.next()) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (xmlr.getLocalName().equals("titlStmt")) {
                    processTitlStmt(xmlr, datasetDTO);
                } else if (xmlr.getLocalName().equals("rspStmt")) {
                    processRspStmt(xmlr, citation);
                } else if (xmlr.getLocalName().equals("prodStmt")) {
                    processProdStmt(xmlr, citation);
                } else if (xmlr.getLocalName().equals("distStmt")) {
                    if (distStatementProcessed) {
                        // We've already encountered one Distribution Statement in
                        // this citation, we'll just skip any consecutive ones.
                        // This is a defensive check against duplicate distStmt
                        // in some DDIs (notably, from ICPSR)
                    } else {
                        processDistStmt(xmlr, citation);
                        distStatementProcessed = true;
                    }
                } else if (xmlr.getLocalName().equals("serStmt")) {
                    processSerStmt(xmlr, citation);
                } else if (xmlr.getLocalName().equals("verStmt")) {
                    processVerStmt(importType, xmlr, dvDTO);
                } else if (xmlr.getLocalName().equals("notes")) {
                    String _note = parseNoteByType(xmlr, NOTE_TYPE_UNF);
                    if (_note != null) {
                        datasetDTO.getDatasetVersion().setUNF(parseUNF(_note));
                    } else {

                        processNotes(xmlr, dvDTO);
                    }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (xmlr.getLocalName().equals("citation")) {
                    return;
                }
            }
        }
    }


    /**
     * @param xmlr
     * @param citation
     * @throws XMLStreamException
     */
    private void processStdyInfo(XMLStreamReader xmlr, DatasetVersionDTO dvDTO) throws XMLStreamException {
        List<Set<FieldDTO>> descriptions = new ArrayList<>();

        for (int event = xmlr.next(); event != XMLStreamConstants.END_DOCUMENT; event = xmlr.next()) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (xmlr.getLocalName().equals("subject")) {
                    processSubject(xmlr, getCitation(dvDTO));
                } else if (xmlr.getLocalName().equals("abstract")) {
                    Set<FieldDTO> set = new HashSet<>();
                    addToSet(set, DatasetFieldConstant.descriptionDate, xmlr.getAttributeValue(null, "date"));
                    addToSet(set, DatasetFieldConstant.descriptionText, parseCompoundText(xmlr));
                    if (!set.isEmpty()) {
                        descriptions.add(set);
                    }

                } else if (xmlr.getLocalName().equals("sumDscr")) {
                    processSumDscr(xmlr, dvDTO);
                } else if (xmlr.getLocalName().equals("notes")) {
                    processNotes(xmlr, dvDTO);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (xmlr.getLocalName().equals("stdyInfo")) {
                    if (!descriptions.isEmpty()) {
                        getCitation(dvDTO).getFields().add(FieldDTO.createMultipleCompoundFieldDTO(DatasetFieldConstant.description, descriptions));
                    }
                    return;
                }
            }
        }
    }

    private void processSubject(XMLStreamReader xmlr, MetadataBlockDTO citation) throws XMLStreamException {
        List<Set<FieldDTO>> keywords = new ArrayList<>();
        List<Set<FieldDTO>> topicClasses = new ArrayList<>();
        for (int event = xmlr.next(); event != XMLStreamConstants.END_DOCUMENT; event = xmlr.next()) {
            if (event == XMLStreamConstants.START_ELEMENT) {

                if (xmlr.getLocalName().equals("keyword")) {
                    Set<FieldDTO> set = new HashSet<>();
                    addToSet(set, DatasetFieldConstant.keywordVocab, xmlr.getAttributeValue(null, "vocab"));
                    addToSet(set, DatasetFieldConstant.keywordVocabURI, xmlr.getAttributeValue(null, "vocabURI"));
                    addToSet(set, DatasetFieldConstant.keywordValue, parseText(xmlr));
                    if (!set.isEmpty()) {
                        keywords.add(set);
                    }
                } else if (xmlr.getLocalName().equals("topcClas")) {
                    Set<FieldDTO> set = new HashSet<>();
                    addToSet(set, DatasetFieldConstant.topicClassVocab, xmlr.getAttributeValue(null, "vocab"));
                    addToSet(set, DatasetFieldConstant.topicClassVocabURI, xmlr.getAttributeValue(null, "vocabURI"));
                    addToSet(set, DatasetFieldConstant.topicClassValue, parseText(xmlr));
                    if (!set.isEmpty()) {
                        topicClasses.add(set);
                    }

                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (xmlr.getLocalName().equals("subject")) {
                    if (!keywords.isEmpty()) {
                        citation.getFields().add(FieldDTO.createMultipleCompoundFieldDTO(DatasetFieldConstant.keyword, keywords));
                    }
                    if (!topicClasses.isEmpty()) {
                        citation.getFields().add(FieldDTO.createMultipleCompoundFieldDTO(DatasetFieldConstant.topicClassification, topicClasses));
                    }
                    return;
                }
            }
        }
    }

    /**
     * Process the notes portion of the DDI doc -- if there is one
     * Return a formatted string
     *
     * @param xmlr
     * @return
     */
    private String formatNotesfromXML(XMLStreamReader xmlr) throws XMLStreamException {

        //System.out.println("formatNotesfromXML");
        // Initialize array of strings
        List<String> noteValues = new ArrayList<>();
        String attrVal;

        // Check for "subject"
        attrVal = xmlr.getAttributeValue(null, "subject");
        if (attrVal != null) {
            noteValues.add("Subject: " + attrVal);
        }

        // Check for "type"
        attrVal = xmlr.getAttributeValue(null, "type");
        if (attrVal != null) {
            noteValues.add("Type: " + attrVal);
        }

        // Add notes, if they exist
        attrVal = parseHtmlAwareText(xmlr);
        if ((attrVal != null) && (!attrVal.isEmpty())) {
            noteValues.add("Notes: " + attrVal);
        }

        // Nothing to add
        if (noteValues.isEmpty()) {
            return null;
        }

        //System.out.println(StringUtils.join(noteValues, " ") + ";");
        return StringUtils.join(noteValues, " ") + ";";

        /*
        Examples of xml:
        <notes type="Statistics" subject="Babylon"> </notes>
        <notes type="Note Type" subject="Note Subject">Note Text</notes>
        <notes type="Note Type 2" subject="Note Subject 2">Note Text 2</notes>
        <notes>Note Text 3</notes>
        */

        /*
        // Original, changed b/c of string 'null' appearing in final output
        String note = " Subject: "+xmlr.getAttributeValue(null, "subject")+" "
        + " Type: "+xmlr.getAttributeValue(null, "type")+" "
        + " Notes: "+parseText(xmlr, "notes")+";";
        addNote(note, dvDTO);
       */
    }


    private void processNotes(XMLStreamReader xmlr, DatasetVersionDTO dvDTO) throws XMLStreamException {

        String formattedNotes = this.formatNotesfromXML(xmlr);

        if (formattedNotes != null) {
            this.addNote(formattedNotes, dvDTO);
        }
    }

    private void processStdyNotes(XMLStreamReader xmlr, DatasetVersionDTO dvDTO) throws XMLStreamException {
        // Add notes, if they exist
        String stdyNotes = parseHtmlAwareText(xmlr);
        if (StringUtils.isNotBlank(stdyNotes)){
            FieldDTO notesText = FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.datasetLevelErrorNotes, stdyNotes);
            getSocialScience(dvDTO).addField(notesText);
        }
    }

    private void processNotesSocialScience(XMLStreamReader xmlr, DatasetVersionDTO dvDTO) throws XMLStreamException {

        FieldDTO notesSubject = null;
        String attrVal;

        // Check for "subject"
        attrVal = xmlr.getAttributeValue(null, "subject");
        if (attrVal != null) {
            notesSubject = FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.socialScienceNotesSubject, attrVal);
        }

        FieldDTO notesType = null;
        // Check for "type"
        attrVal = xmlr.getAttributeValue(null, "type");
        if (attrVal != null) {
            notesType = FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.socialScienceNotesType, attrVal);
        }

        FieldDTO notesText = null;
        // Add notes, if they exist
        attrVal = parseHtmlAwareText(xmlr);
        if ((attrVal != null) && (!attrVal.isEmpty())) {
            notesText  = FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.socialScienceNotesText, attrVal);
        }

        if (notesSubject != null || notesType != null || notesText != null ) {

            getSocialScience(dvDTO).addField(FieldDTO.createCompoundFieldDTO(DatasetFieldConstant.socialScienceNotes, notesSubject, notesType, notesText ));
        }
    }

    private void addNote(String noteText, DatasetVersionDTO dvDTO) {
        MetadataBlockDTO citation = getCitation(dvDTO);
        FieldDTO field = citation.getField(DatasetFieldConstant.notesText);
        if (field == null) {
            field = FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.notesText, "");
            citation.getFields().add(field);
        }
        String noteValue = field.getSinglePrimitive();
        noteValue += noteText;
        field.setSinglePrimitive(noteValue);
    }

    private void processSumDscr(XMLStreamReader xmlr, DatasetVersionDTO dvDTO) throws XMLStreamException {
        List<String> geoUnit = new ArrayList<>();
        List<String> unitOfAnalysis = new ArrayList<>();
        List<String> universe = new ArrayList<>();
        List<String> kindOfData = new ArrayList<>();
        List<Set<FieldDTO>> geoBoundBox = new ArrayList<>();
        List<Set<FieldDTO>> geoCoverages = new ArrayList<>();
        List<FieldDTO> timePeriod = new ArrayList<>();
        List<FieldDTO> dateOfCollection = new ArrayList<>();
        FieldDTO timePeriodStart = null;
        FieldDTO timePeriodEnd = null;
        FieldDTO dateOfCollectionStart = null;
        FieldDTO dateOfCollectionEnd = null;
        HashSet<FieldDTO> geoCoverageSet = null;
        String otherGeographicCoverage = null;

        for (int event = xmlr.next(); event != XMLStreamConstants.END_DOCUMENT; event = xmlr.next()) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (xmlr.getLocalName().equals("timePrd")) {

                    String eventAttr = xmlr.getAttributeValue(null, "event");
                    if (eventAttr == null || EVENT_SINGLE.equalsIgnoreCase(eventAttr) || EVENT_START.equalsIgnoreCase(eventAttr)) {
                        timePeriodStart = FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.timePeriodCoveredStart, parseDate(xmlr));
                    } else if (EVENT_END.equals(eventAttr)) {
                        timePeriodEnd = FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.timePeriodCoveredEnd, parseDate(xmlr));
                        timePeriod.add(FieldDTO.createMultipleCompoundFieldDTO(DatasetFieldConstant.timePeriodCovered, timePeriodStart, timePeriodEnd));

                    }
                } else if (xmlr.getLocalName().equals("collDate")) {
                    String eventAttr = xmlr.getAttributeValue(null, "event");
                    if (eventAttr == null || EVENT_SINGLE.equalsIgnoreCase(eventAttr) || EVENT_START.equalsIgnoreCase(eventAttr)) {
                        dateOfCollectionStart = FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.dateOfCollectionStart, parseDate(xmlr));
                    } else if (EVENT_END.equals(eventAttr)) {
                        dateOfCollectionEnd = FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.dateOfCollectionEnd, parseDate(xmlr));
                        dateOfCollection.add(FieldDTO.createMultipleCompoundFieldDTO(DatasetFieldConstant.dateOfCollection, dateOfCollectionStart, dateOfCollectionEnd ));
                    }

                } else if (xmlr.getLocalName().equals("nation")) {
                    if (StringUtils.isNotBlank(otherGeographicCoverage)) {
                        geoCoverageSet.add(FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.otherGeographicCoverage, otherGeographicCoverage));
                        otherGeographicCoverage = null;
                    }
                    if (geoCoverageSet != null && geoCoverageSet.size() > 0) {
                        geoCoverages.add(geoCoverageSet);
                    }
                    geoCoverageSet = new HashSet<>();
                    geoCoverageSet.add(FieldDTO.createVocabFieldDTO(DatasetFieldConstant.country, parseText(xmlr)));

                } else if (xmlr.getLocalName().equals("geogCover")) {
                    if (geoCoverageSet == null) {
                        geoCoverageSet = new HashSet<>();
                    }
                    if (otherGeographicCoverage != null) {
                        otherGeographicCoverage = otherGeographicCoverage + "; " + parseText(xmlr);
                    } else {
                        otherGeographicCoverage = parseText(xmlr);
                    }

                } else if (xmlr.getLocalName().equals("geogUnit")) {
                    geoUnit.add(parseText(xmlr));
                } else if (xmlr.getLocalName().equals("geoBndBox")) {
                    geoBoundBox.add(processGeoBndBox(xmlr));
                } else if (xmlr.getLocalName().equals("anlyUnit")) {
                    unitOfAnalysis.add(parseHtmlAwareText(xmlr));
                } else if (xmlr.getLocalName().equals("universe")) {
                    universe.add(parseHtmlAwareText(xmlr));
                } else if (xmlr.getLocalName().equals("dataKind")) {
                    kindOfData.add(parseText(xmlr));
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (xmlr.getLocalName().equals("sumDscr")) {
                    for  (FieldDTO time : timePeriod) {
                        getCitation(dvDTO).addField( time);
                    }
                    for (FieldDTO date : dateOfCollection) {
                        getCitation(dvDTO).addField(date);
                    }

                    if (!geoUnit.isEmpty()) {
                        getGeospatial(dvDTO).addField(FieldDTO.createMultiplePrimitiveFieldDTO(DatasetFieldConstant.geographicUnit, geoUnit));
                    }
                    if (!unitOfAnalysis.isEmpty()) {
                        getSocialScience(dvDTO).addField(FieldDTO.createMultiplePrimitiveFieldDTO(DatasetFieldConstant.unitOfAnalysis, unitOfAnalysis));
                    }
                    if (!universe.isEmpty()) {
                        getSocialScience(dvDTO).addField(FieldDTO.createMultiplePrimitiveFieldDTO(DatasetFieldConstant.universe, universe));
                    }
                    if (!kindOfData.isEmpty()) {
                        getCitation(dvDTO).addField(FieldDTO.createMultiplePrimitiveFieldDTO(DatasetFieldConstant.kindOfData, kindOfData));
                    }
                    if (StringUtils.isNotEmpty(otherGeographicCoverage)) {
                        geoCoverageSet.add(FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.otherGeographicCoverage, otherGeographicCoverage));
                    }
                    if (CollectionUtils.isNotEmpty(geoCoverageSet)) {
                        geoCoverages.add(geoCoverageSet);
                    }
                    if (!geoCoverages.isEmpty()) {
                        getGeospatial(dvDTO).addField(FieldDTO.createMultipleCompoundFieldDTO(DatasetFieldConstant.geographicCoverage, geoCoverages));
                    }
                    if (!geoBoundBox.isEmpty()) {
                        getGeospatial(dvDTO).addField(FieldDTO.createMultipleCompoundFieldDTO(DatasetFieldConstant.geographicBoundingBox, geoBoundBox));
                    }
                    return;
                }
            }
        }
    }


    private Set<FieldDTO> processGeoBndBox(XMLStreamReader xmlr) throws XMLStreamException {
        Set<FieldDTO> set = new HashSet<>();

        for (int event = xmlr.next(); event != XMLStreamConstants.END_DOCUMENT; event = xmlr.next()) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (xmlr.getLocalName().equals("westBL")) {
                    addToSet(set, DatasetFieldConstant.westLongitude, parseText(xmlr));
                } else if (xmlr.getLocalName().equals("eastBL")) {
                    addToSet(set, DatasetFieldConstant.eastLongitude, parseText(xmlr));
                } else if (xmlr.getLocalName().equals("southBL")) {
                    addToSet(set, DatasetFieldConstant.southLatitude, parseText(xmlr));
                } else if (xmlr.getLocalName().equals("northBL")) {
                    addToSet(set, DatasetFieldConstant.northLatitude, parseText(xmlr));
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (xmlr.getLocalName().equals("geoBndBox")) {
                    break;
                }
            }
        }
        return set;
    }

    private void processMethod(XMLStreamReader xmlr, DatasetVersionDTO dvDTO) throws XMLStreamException, ImportException {
        for (int event = xmlr.next(); event != XMLStreamConstants.END_DOCUMENT; event = xmlr.next()) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (xmlr.getLocalName().equals("dataColl")) {
                    processDataColl(xmlr, dvDTO);
                } else if (xmlr.getLocalName().equals("notes")) {

                    String noteType = xmlr.getAttributeValue(null, "type");
                    if (NOTE_TYPE_EXTENDED_METADATA.equalsIgnoreCase(noteType)) {
                        processCustomField(xmlr, dvDTO);
                    } else {
                        processNotesSocialScience(xmlr, dvDTO);
                    }
                } else if (xmlr.getLocalName().equals("anlyInfo")) {
                    processAnlyInfo(xmlr, getSocialScience(dvDTO));
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (xmlr.getLocalName().equals("method")) {
                    return;
                }
            }
        }
    }

    private void processCustomField(XMLStreamReader xmlr, DatasetVersionDTO dvDTO) throws XMLStreamException, ImportException {
        String subject = xmlr.getAttributeValue(null, "subject");
        if (!subject.isEmpty()) {
            // Syntax of subject attribute:
            // TEMPLATE:Contains Custom Fields;FIELD:Customfield1
            // first parse by semicolon
            String template = subject.substring(subject.indexOf(":") + 1, subject.indexOf(";"));
            String sourceField = subject.substring(subject.lastIndexOf(":") + 1);
            String fieldValue = parseText(xmlr);

            CustomFieldMap map = customFieldService.findByTemplateField(template.trim(), sourceField.trim());

            if (map == null) {
                throw new ImportException("Did not find mapping for template: " + template + ", sourceField: " + sourceField);
            }
            if (map.getTargetDatasetField().endsWith("#IGNORE")) {
                // if the target field is #IGNORE, that means we don't want to
                // copy this field from 3.6 to 4.0
                return;
            }

            // 1. Get datasetFieldType for the targetField
            // 2. find the metadatablock for this field type
            // 3. If this metadatablock doesn't exist in DTO, create it
            // 4. add field to mdatadatablock
            DatasetFieldType dsfType = datasetFieldService.findByName(map.getTargetDatasetField());
            if (dsfType == null) {
                throw new ImportException("Did not find datasetField for target: " + map.getTargetDatasetField());
            }
            String metadataBlockName = dsfType.getMetadataBlock().getName();
            MetadataBlockDTO customBlock = dvDTO.getMetadataBlocks().get(metadataBlockName);
            if (customBlock == null) {
                customBlock = new MetadataBlockDTO();
                customBlock.setDisplayName(metadataBlockName);
                dvDTO.getMetadataBlocks().put(metadataBlockName, customBlock);
            }
            if (dsfType.isChild()) {
                handleChildField(customBlock, dsfType, fieldValue);
            } else {
                if (dsfType.isAllowMultiples()) {
                    List<String> valList = new ArrayList<>();
                    valList.add(fieldValue);
                    if (dsfType.isAllowControlledVocabulary()) {
                        customBlock.addField(FieldDTO.createMultipleVocabFieldDTO(dsfType.getName(), valList));
                    } else if (dsfType.isPrimitive()) {
                        customBlock.addField(FieldDTO.createMultiplePrimitiveFieldDTO(dsfType.getName(), valList));
                    } else {
                        throw new ImportException("Unsupported custom field type: " + dsfType);
                    }
                } else {
                    if (dsfType.isAllowControlledVocabulary()) {
                        customBlock.addField(FieldDTO.createVocabFieldDTO(dsfType.getName(), fieldValue));
                    } else if (dsfType.isPrimitive()) {
                        customBlock.addField(FieldDTO.createPrimitiveFieldDTO(dsfType.getName(), fieldValue));
                    } else {
                        throw new ImportException("Unsupported custom field type: " + dsfType);
                    }
                }
            }
        }
    }

    private void handleChildField(MetadataBlockDTO customBlock, DatasetFieldType dsfType, String fieldValue) throws ImportException {
        DatasetFieldType parent = dsfType.getParentDatasetFieldType();

        // Create child Field
        FieldDTO child = null;
        if (dsfType.isAllowControlledVocabulary()) {
            child = FieldDTO.createVocabFieldDTO(dsfType.getName(), fieldValue);
        } else if (dsfType.isPrimitive()) {
            child = FieldDTO.createPrimitiveFieldDTO(dsfType.getName(), fieldValue);
        } else {
            throw new ImportException("Unsupported custom child field type: " + dsfType);
        }
        // Create compound field with this child as its only element
        FieldDTO compound = null;
        if (parent.isAllowMultiples()) {
            compound = FieldDTO.createMultipleCompoundFieldDTO(parent.getName(), child);
        } else {
            compound = FieldDTO.createCompoundFieldDTO(parent.getName(), child);
        }
        customBlock.addField(compound);

    }

    private void processSources(XMLStreamReader xmlr, MetadataBlockDTO citation) throws XMLStreamException {
        for (int event = xmlr.next(); event != XMLStreamConstants.END_DOCUMENT; event = xmlr.next()) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                // citation dataSources
                String parsedText;
                if (xmlr.getLocalName().equals("dataSrc")) {
                    parsedText = parseHtmlAwareText(xmlr);
                    if (!parsedText.isEmpty()) {
                        citation.addField(FieldDTO.createMultiplePrimitiveFieldDTO(DatasetFieldConstant.dataSources, Arrays.asList(parsedText)));
                    }
                    // citation originOfSources
                } else if (xmlr.getLocalName().equals("srcOrig")) {
                    parsedText = parseHtmlAwareText(xmlr);
                    if (!parsedText.isEmpty()) {
                        citation.getFields().add(FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.originOfSources, parsedText));
                    }
                    // citation characteristicOfSources
                } else if (xmlr.getLocalName().equals("srcChar")) {
                    parsedText = parseHtmlAwareText(xmlr);
                    if (!parsedText.isEmpty()) {
                        citation.getFields().add(FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.characteristicOfSources, parsedText));
                    }
                    // citation accessToSources
                } else if (xmlr.getLocalName().equals("srcDocu")) {
                    parsedText = parseHtmlAwareText(xmlr);
                    if (!parsedText.isEmpty()) {
                        citation.getFields().add(FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.accessToSources, parsedText));
                    }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (xmlr.getLocalName().equals("sources")) {
                    return;
                }
            }
        }
    }

    private void processAnlyInfo(XMLStreamReader xmlr, MetadataBlockDTO socialScience) throws XMLStreamException {
        for (int event = xmlr.next(); event != XMLStreamConstants.END_DOCUMENT; event = xmlr.next()) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (xmlr.getLocalName().equals("respRate")) {
                    socialScience.getFields().add(FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.responseRate, parseHtmlAwareText(xmlr)));
                } else if (xmlr.getLocalName().equals("EstSmpErr")) {
                    socialScience.getFields().add(FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.samplingErrorEstimates, parseHtmlAwareText(xmlr)));
                } else if (xmlr.getLocalName().equals("dataAppr")) {
                    socialScience.getFields().add(FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.otherDataAppraisal, parseHtmlAwareText(xmlr)));
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (xmlr.getLocalName().equals("anlyInfo")) {
                    return;
                }
            }
        }
    }

    private void processDataColl(XMLStreamReader xmlr, DatasetVersionDTO dvDTO) throws XMLStreamException {
        MetadataBlockDTO socialScience = getSocialScience(dvDTO);

        String collMode = "";
        String timeMeth = "";
        String weight = "";
        String dataCollector = "";

        for (int event = xmlr.next(); event != XMLStreamConstants.END_DOCUMENT; event = xmlr.next()) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (xmlr.getLocalName().equals("timeMeth")) {
                    String thisValue = parseHtmlAwareText(xmlr);
                    if (StringUtils.isNotBlank(thisValue)) {
                        if (!timeMeth.isEmpty()) {
                            timeMeth = timeMeth.concat(", ");
                        }
                        timeMeth = timeMeth.concat(thisValue);
                    }
                } else if (xmlr.getLocalName().equals("dataCollector")) {
                    String thisValue = parseHtmlAwareText(xmlr);
                    if (StringUtils.isNotBlank(thisValue)) {
                        if (!dataCollector.isEmpty()) {
                            dataCollector = dataCollector.concat(", ");
                        }
                        dataCollector = dataCollector.concat(thisValue);
                    }
                } else if (xmlr.getLocalName().equals("frequenc")) {
                    socialScience.getFields().add(FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.frequencyOfDataCollection, parseHtmlAwareText(xmlr)));
                } else if (xmlr.getLocalName().equals("sampProc")) {
                    socialScience.getFields().add(FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.samplingProcedure, parseHtmlAwareText(xmlr)));
                } else if (xmlr.getLocalName().equals("targetSampleSize")) {
                    processTargetSampleSize(xmlr, socialScience);
                } else if (xmlr.getLocalName().equals("deviat")) {
                    socialScience.getFields().add(FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.deviationsFromSampleDesign, parseHtmlAwareText(xmlr)));
                } else if (xmlr.getLocalName().equals("collMode")) {
                    String thisValue = parseHtmlAwareText(xmlr);
                    if (StringUtils.isNotBlank(thisValue)) {
                        if (!collMode.isEmpty()) {
                            collMode = collMode.concat(", ");
                        }
                        collMode = collMode.concat(thisValue);
                    }
                } else if (xmlr.getLocalName().equals("resInstru")) {
                    socialScience.getFields().add(FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.researchInstrument, parseHtmlAwareText(xmlr)));
                } else if (xmlr.getLocalName().equals("sources")) {
                    processSources(xmlr, getCitation(dvDTO));
                } else if (xmlr.getLocalName().equals("collSitu")) {
                    socialScience.getFields().add(FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.dataCollectionSituation, parseHtmlAwareText(xmlr)));
                } else if (xmlr.getLocalName().equals("actMin")) {
                    socialScience.getFields().add(FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.actionsToMinimizeLoss, parseHtmlAwareText(xmlr)));
                } else if (xmlr.getLocalName().equalsIgnoreCase("conOps")) { // From DDI schema it should be "ConOps", but some dataverse installations will have "conOps" instead
                    socialScience.getFields().add(FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.controlOperations, parseHtmlAwareText(xmlr)));
                } else if (xmlr.getLocalName().equals("weight")) {
                    String thisValue = parseHtmlAwareText(xmlr);
                    if (StringUtils.isNotBlank(thisValue)) {
                        if (!weight.isEmpty()) {
                            weight = weight.concat(", ");
                        }
                        weight = weight.concat(thisValue);
                    }
                } else if (xmlr.getLocalName().equals("cleanOps")) {
                    socialScience.getFields().add(FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.cleaningOperations, parseHtmlAwareText(xmlr)));
                } else if (xmlr.getLocalName().equals("collectorTraining")) {
                    socialScience.getFields().add(FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.collectorTraining, parseHtmlAwareText(xmlr)));
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (xmlr.getLocalName().equals("dataColl")) {
                    if (StringUtils.isNotBlank(timeMeth)) {
                        socialScience.getFields().add(FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.timeMethod, timeMeth));
                    }
                    if (StringUtils.isNotBlank(collMode)) {
                        socialScience.getFields().add(FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.collectionMode, collMode));
                    }
                    if (StringUtils.isNotBlank(weight)) {
                        socialScience.getFields().add(FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.weighting, weight));
                    }
                    if (StringUtils.isNotBlank(dataCollector)) {
                        socialScience.getFields().add(FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.dataCollector, dataCollector));
                    }
                    return;
                }
            }
        }
    }

    private void processTargetSampleSize(XMLStreamReader xmlr, MetadataBlockDTO socialScience) throws XMLStreamException {
        FieldDTO sampleSize = null;
        FieldDTO sampleSizeFormula = null;
        for (int event = xmlr.next(); event != XMLStreamConstants.END_DOCUMENT; event = xmlr.next()) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (xmlr.getLocalName().equals("sampleSize")) {
                    sampleSize = FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.targetSampleActualSize, parseHtmlAwareText(xmlr));
                } else if (xmlr.getLocalName().equals("sampleSizeFormula")) {
                    sampleSizeFormula = FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.targetSampleSizeFormula, parseHtmlAwareText(xmlr));
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (xmlr.getLocalName().equals("targetSampleSize")) {
                    if (sampleSize != null || sampleSizeFormula != null) {
                        socialScience.getFields().add(FieldDTO.createCompoundFieldDTO(DatasetFieldConstant.targetSampleSize, sampleSize, sampleSizeFormula));
                    }
                    return;
                }
            }
        }

    }

    /*
    EMK TODO:  In DVN 3.6, users were allowed to enter their own version date, and in addition the app assigned a version date when
     the version is released.  So DDI's that we have to migrate, we can see this:
    <verStmt>
		<version date="2004-04-04">1</version>
	</verStmt>
	<verStmt source="DVN">
		<version date="2014-05-21" type="RELEASED">1</version>
	</verStmt>
    Question:  what to do with these two different dates?  Need to review with Eleni
    Note: we should use the verStmt with source="DVN" as the 'official' version statement
    DDI's that we are migrating should have one and only one DVN version statement
    */
    private void processVerStmt(ImportType importType, XMLStreamReader xmlr, DatasetVersionDTO dvDTO) throws XMLStreamException {
        if (isHarvestImport(importType)) {
            if (!"DVN".equals(xmlr.getAttributeValue(null, "source"))) {
                for (int event = xmlr.next(); event != XMLStreamConstants.END_DOCUMENT; event = xmlr.next()) {
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        if (xmlr.getLocalName().equals("version")) {
                            addNote("Version Date: " + xmlr.getAttributeValue(null, "date"), dvDTO);
                            addNote("Version Text: " + parseText(xmlr), dvDTO);
                        } else if (xmlr.getLocalName().equals("notes")) {
                            processNotes(xmlr, dvDTO);
                        }
                    } else if (event == XMLStreamConstants.END_ELEMENT) {
                        if (xmlr.getLocalName().equals("verStmt")) {
                            return;
                        }
                    }
                }
            } else {
                // this is the DVN version info; get version number for StudyVersion object
                for (int event = xmlr.next(); event != XMLStreamConstants.END_DOCUMENT; event = xmlr.next()) {
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        if (xmlr.getLocalName().equals("version")) {
                            dvDTO.setReleaseDate(xmlr.getAttributeValue(null, "date"));
                            String versionState = xmlr.getAttributeValue(null, "type");
                            if (versionState != null) {
                                if (versionState.equals("ARCHIVED")) {
                                    versionState = DatasetVersion.VersionState.RELEASED.toString();
                                } else if (versionState.equals("IN_REVIEW")) {
                                    versionState = DatasetVersion.VersionState.DRAFT.toString();
                                    dvDTO.setInReview(true);
                                }
                                dvDTO.setVersionState(Enum.valueOf(VersionState.class, versionState));
                            }
                            parseVersionNumber(dvDTO, parseText(xmlr));
                        }
                    } else if (event == XMLStreamConstants.END_ELEMENT) {
                        if (xmlr.getLocalName().equals("verStmt")) {
                            return;
                        }
                    }
                }
            }
        }
    }

    private void processDataAccs(XMLStreamReader xmlr, DatasetVersionDTO dvDTO, TermsOfUseDataHolder termsOfUseDataHolder) throws XMLStreamException {

        for (int event = xmlr.next(); event != XMLStreamConstants.END_DOCUMENT; event = xmlr.next()) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (xmlr.getLocalName().equals("setAvail")) {
                    processSetAvail(xmlr, dvDTO);
                } else if (xmlr.getLocalName().equals("notes")) {
                    String noteType = xmlr.getAttributeValue(null, "type");
                    if (NOTE_TYPE_TERMS_OF_USE.equalsIgnoreCase(noteType)) {
                        if (LEVEL_DV.equalsIgnoreCase(xmlr.getAttributeValue(null, "level"))) {
                            termsOfUseDataHolder.setTermsOfUse(parseHtmlAwareText(xmlr));
                        }
                    } else if (NOTE_TYPE_TERMS_OF_ACCESS.equalsIgnoreCase(noteType) ) {
                        // skip
                    } else {
                        processNotes(xmlr, dvDTO);
                    }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (xmlr.getLocalName().equals("dataAccs")) {
                    return;
                }
            }
        }
    }

    private void processSetAvail(XMLStreamReader xmlr, DatasetVersionDTO dvDTO) throws XMLStreamException {
        for (int event = xmlr.next(); event != XMLStreamConstants.END_DOCUMENT; event = xmlr.next()) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (xmlr.getLocalName().equals("notes")) {
                    processNotes(xmlr, dvDTO);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (xmlr.getLocalName().equals("setAvail")) {
                    return;
                }
            }
        }
    }

    /**
     * Separate the versionNumber into two parts - before the first '.'
     * is the versionNumber, and after is the minorVersionNumber.
     * If no minorVersionNumber exists, set to "0".
     *
     * @param dvDTO
     * @param versionNumber
     */
    private void parseVersionNumber(DatasetVersionDTO dvDTO, String versionNumber) {
        int firstIndex = versionNumber.indexOf('.');
        if (firstIndex == -1) {
            dvDTO.setVersionNumber(Long.parseLong(versionNumber));
            dvDTO.setVersionMinorNumber(0L);
        } else {
            dvDTO.setVersionNumber(Long.parseLong(versionNumber.substring(0, firstIndex - 1)));
            dvDTO.setVersionMinorNumber(Long.valueOf(versionNumber.substring(firstIndex + 1)));
        }


    }

    private void processSerStmt(XMLStreamReader xmlr, MetadataBlockDTO citation) throws XMLStreamException {
        FieldDTO seriesName = null;
        FieldDTO seriesInformation = null;
        for (int event = xmlr.next(); event != XMLStreamConstants.END_DOCUMENT; event = xmlr.next()) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (xmlr.getLocalName().equals("serName")) {
                    seriesName = FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.seriesName, parseText(xmlr));

                } else if (xmlr.getLocalName().equals("serInfo")) {
                    seriesInformation = FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.seriesInformation, parseText(xmlr));
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (xmlr.getLocalName().equals("serStmt")) {
                    citation.getFields().add(FieldDTO.createCompoundFieldDTO(DatasetFieldConstant.series, seriesName, seriesInformation));
                    return;
                }
            }
        }
    }

    private void processDistStmt(XMLStreamReader xmlr, MetadataBlockDTO citation) throws XMLStreamException {
        List<Set<FieldDTO>> distributors = new ArrayList<>();
        List<Set<FieldDTO>> datasetContacts = new ArrayList<>();
        for (int event = xmlr.next(); event != XMLStreamConstants.END_DOCUMENT; event = xmlr.next()) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (xmlr.getLocalName().equals("distrbtr")) {
                    String source = xmlr.getAttributeValue(null, "source");
                    if (source == null || !source.equals("archive")) {
                        HashSet<FieldDTO> set = new HashSet<>();
                        addToSet(set, DatasetFieldConstant.distributorAbbreviation, xmlr.getAttributeValue(null, "abbr"));
                        addToSet(set, DatasetFieldConstant.distributorAffiliation, xmlr.getAttributeValue(null, "affiliation"));
                        addToSet(set, DatasetFieldConstant.distributorURL, xmlr.getAttributeValue(null, "URI"));
                        addToSet(set, DatasetFieldConstant.distributorLogo, xmlr.getAttributeValue(null, "role"));
                        addToSet(set, DatasetFieldConstant.distributorName, xmlr.getElementText());
                        distributors.add(set);
                    }

                } else if (xmlr.getLocalName().equals("contact")) {
                    Set<FieldDTO> set = new HashSet<>();
                    addToSet(set, DatasetFieldConstant.datasetContactEmail, xmlr.getAttributeValue(null, "email"));
                    addToSet(set, DatasetFieldConstant.datasetContactAffiliation, xmlr.getAttributeValue(null, "affiliation"));
                    addToSet(set, DatasetFieldConstant.datasetContactName, parseText(xmlr));
                    datasetContacts.add(set);

                } else if (xmlr.getLocalName().equals("depositr")) {
                    citation.getFields().add(FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.depositor, parseCompoundText(xmlr)));
                } else if (xmlr.getLocalName().equals("depDate")) {
                    citation.getFields().add(FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.dateOfDeposit, parseDate(xmlr)));

                } else if (xmlr.getLocalName().equals("distDate")) {
                    citation.getFields().add(FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.distributionDate, parseDate(xmlr)));

                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (xmlr.getLocalName().equals("distStmt")) {
                    if (!distributors.isEmpty()) {
                        citation.addField(FieldDTO.createMultipleCompoundFieldDTO(DatasetFieldConstant.distributor, distributors));
                    }
                    if (!datasetContacts.isEmpty()) {
                        citation.addField(FieldDTO.createMultipleCompoundFieldDTO(DatasetFieldConstant.datasetContact, datasetContacts));
                    }
                    return;
                }
            }
        }
    }

    private void processProdStmt(XMLStreamReader xmlr, MetadataBlockDTO citation) throws XMLStreamException {
        List<Set<FieldDTO>> producers = new ArrayList<>();
        List<Set<FieldDTO>> grants = new ArrayList<>();
        List<Set<FieldDTO>> software = new ArrayList<>();

        for (int event = xmlr.next(); event != XMLStreamConstants.END_DOCUMENT; event = xmlr.next()) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (xmlr.getLocalName().equals("producer")) {
                    Set<FieldDTO> set = new HashSet<>();
                    addToSet(set, DatasetFieldConstant.producerAbbreviation, xmlr.getAttributeValue(null, "abbr"));
                    addToSet(set, DatasetFieldConstant.producerAffiliation, xmlr.getAttributeValue(null, "affiliation"));
                    addToSet(set, DatasetFieldConstant.producerLogo, xmlr.getAttributeValue(null, "role"));
                    addToSet(set, DatasetFieldConstant.producerURL, xmlr.getAttributeValue(null, "URI"));
                    addToSet(set, DatasetFieldConstant.producerName, xmlr.getElementText());
                    if (!set.isEmpty()) {
                        producers.add(set);
                    }
                } else if (xmlr.getLocalName().equals("prodDate")) {
                    citation.getFields().add(FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.productionDate, parseDate(xmlr)));
                } else if (xmlr.getLocalName().equals("prodPlac")) {
                    citation.getFields().add(FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.productionPlace, parseDate(xmlr)));
                } else if (xmlr.getLocalName().equals("software")) {
                    Set<FieldDTO> set = new HashSet<>();
                    addToSet(set, DatasetFieldConstant.softwareVersion, xmlr.getAttributeValue(null, "version"));
                    addToSet(set, DatasetFieldConstant.softwareName,  parseText(xmlr));
                    if (!set.isEmpty()) {
                        software.add(set);
                    }

                } else if (xmlr.getLocalName().equals("grantNo")) {
                    Set<FieldDTO> set = new HashSet<>();
                    addToSet(set, DatasetFieldConstant.grantNumberAgency, xmlr.getAttributeValue(null, "agency"));
                    addToSet(set, DatasetFieldConstant.grantNumberValue, parseText(xmlr));
                    if (!set.isEmpty()) {
                        grants.add(set);
                    }

                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (xmlr.getLocalName().equals("prodStmt")) {
                    if (!software.isEmpty()) {
                        citation.addField(FieldDTO.createMultipleCompoundFieldDTO(DatasetFieldConstant.software, software));
                    }
                    if (!grants.isEmpty()) {
                        citation.addField(FieldDTO.createMultipleCompoundFieldDTO(DatasetFieldConstant.grantNumber, grants));
                    }
                    if (!producers.isEmpty()) {
                        citation.getFields().add(FieldDTO.createMultipleCompoundFieldDTO(DatasetFieldConstant.producer, producers));
                    }
                    return;
                }
            }
        }
    }

    private void processTitlStmt(XMLStreamReader xmlr, DatasetDTO datasetDTO) throws XMLStreamException, ImportException {
        MetadataBlockDTO citation = getCitation(datasetDTO.getDatasetVersion());
        List<Set<FieldDTO>> otherIds = new ArrayList<>();

        for (int event = xmlr.next(); event != XMLStreamConstants.END_DOCUMENT; event = xmlr.next()) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (xmlr.getLocalName().equals("titl")) {
                    FieldDTO field = FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.title, parseText(xmlr));
                    citation.getFields().add(field);
                } else if (xmlr.getLocalName().equals("subTitl")) {
                    FieldDTO field = FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.subTitle, parseText(xmlr));
                    citation.getFields().add(field);
                } else if (xmlr.getLocalName().equals("altTitl")) {
                    FieldDTO field = FieldDTO.createPrimitiveFieldDTO(DatasetFieldConstant.alternativeTitle, parseText(xmlr));
                    citation.getFields().add(field);
                } else if (xmlr.getLocalName().equals("IDNo")) {
                    String idAgency = xmlr.getAttributeValue(null, "agency");

                    if (AGENCY_HANDLE.equals(idAgency)) {
                        parseStudyIdHandle(parseText(xmlr), datasetDTO);
                    } else if (AGENCY_DOI.equals(idAgency)) {
                        parseStudyIdDOI(parseText(xmlr), datasetDTO);
                    } else if (AGENCY_DARA.equals(idAgency)) {
                        /*
                            da|ra - "Registration agency for social and economic data"
                            (http://www.da-ra.de/en/home/)
                            ICPSR uses da|ra to register their DOIs; so they have agency="dara"
                            in their IDNo entries.
                            Also, their DOIs are formatted differently, without the
                            hdl: prefix.
                        */
                        parseStudyIdDoiICPSRdara(parseText(xmlr), datasetDTO);
                    } else {
                        Set<FieldDTO> set = new HashSet<>();
                        addToSet(set, DatasetFieldConstant.otherIdAgency, idAgency);
                        addToSet(set, DatasetFieldConstant.otherIdValue, parseText(xmlr));
                        if (!set.isEmpty()) {
                            otherIds.add(set);
                        }
                    }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (xmlr.getLocalName().equals("titlStmt")) {
                    if (!otherIds.isEmpty()) {
                        citation.addField(FieldDTO.createMultipleCompoundFieldDTO(DatasetFieldConstant.otherId, otherIds));
                    }
                    return;
                }
            }
        }
    }

    private void processRspStmt(XMLStreamReader xmlr, MetadataBlockDTO citation) throws XMLStreamException {

        List<Set<FieldDTO>> authors = new ArrayList<>();
        List<Set<FieldDTO>> contributors = new ArrayList<>();
        for (int event = xmlr.next(); event != XMLStreamConstants.END_DOCUMENT; event = xmlr.next()) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (xmlr.getLocalName().equals("AuthEnty")) {
                    Set<FieldDTO> set = new HashSet<>();
                    addToSet(set, DatasetFieldConstant.authorAffiliation, xmlr.getAttributeValue(null, "affiliation"));
                    addToSet(set, DatasetFieldConstant.authorName, parseText(xmlr));
                    if (!set.isEmpty()) {
                        authors.add(set);
                    }
                }
                if (xmlr.getLocalName().equals("othId")) {
                    HashSet<FieldDTO> set = new HashSet<>();
                    set.add(FieldDTO.createVocabFieldDTO(DatasetFieldConstant.contributorType, xmlr.getAttributeValue(null, "role") ));
                    addToSet(set, DatasetFieldConstant.contributorName, parseText(xmlr));
                    if (!set.isEmpty()) {
                        contributors.add(set);
                    }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (xmlr.getLocalName().equals("rspStmt")) {
                    if (!authors.isEmpty()) {
                        FieldDTO author = FieldDTO.createMultipleCompoundFieldDTO(DatasetFieldConstant.author, authors);
                        citation.getFields().add(author);
                    }
                    if (!contributors.isEmpty()) {
                        FieldDTO contributor = FieldDTO.createMultipleCompoundFieldDTO(DatasetFieldConstant.contributor, contributors);
                        citation.getFields().add(contributor);
                    }

                    return;
                }
            }
        }
    }

    private String parseCompoundText(XMLStreamReader xmlr) throws XMLStreamException {
        String endTag = xmlr.getLocalName();

        String text = "";

        while (true) {
            int event = xmlr.next();
            if (event == XMLStreamConstants.CHARACTERS) {
                if (!text.isEmpty()) {
                    text += "\n";
                }
                text += xmlr.getText().trim().replace('\n', ' ');
            } else if (event == XMLStreamConstants.START_ELEMENT) {
                if (xmlr.getLocalName().equals("ExtLink")) {
                    parseHtmlAwareText(xmlr); // this line effectively just skips though until the end of the tag
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (xmlr.getLocalName().equals(endTag)) {
                    break;
                }
            }
        }

        return text;
    }

    private String parseHtmlAwareText(XMLStreamReader xmlr) throws XMLStreamException {
        return (String) parseHtmlAwareTextNew(xmlr);
    }

    // #FIXME We should really type stabalize this.
    private Object parseHtmlAwareTextNew(XMLStreamReader xmlr) throws XMLStreamException {
        String endTag = xmlr.getLocalName();

        String returnString = "";
        Map<String, Object> returnMap = null;

        while (true) {
            if (!returnString.equals("")) {
                returnString += "\n";
            }
            int event = xmlr.next();
            if (event == XMLStreamConstants.CHARACTERS) {
                returnString += xmlr.getText().trim().replace('\n', ' ');
            } else if (event == XMLStreamConstants.START_ELEMENT) {
                if (xmlr.getLocalName().equals("p") || xmlr.getLocalName().equals("br") || xmlr.getLocalName().equals("head")) {
                    returnString += "<p>" + parseHtmlAwareText(xmlr) + "</p>";
                } else if (xmlr.getLocalName().equals("emph") || xmlr.getLocalName().equals("em") || xmlr.getLocalName().equals("i")) {
                    returnString += "<em>" + parseHtmlAwareText(xmlr) + "</em>";
                } else if (xmlr.getLocalName().equals("hi") || xmlr.getLocalName().equals("b")) {
                    returnString += "<strong>" + parseHtmlAwareText(xmlr) + "</strong>";
                } else if (xmlr.getLocalName().equals("ExtLink")) {
                    String uri = xmlr.getAttributeValue(null, "URI");
                    String text = parseHtmlAwareText(xmlr).trim();
                    returnString += "<a href=\"" + uri + "\">" + (StringUtils.isBlank(text) ? uri : text) + "</a>";
                } else if (xmlr.getLocalName().equals("a") || xmlr.getLocalName().equals("A")) {
                    String uri = xmlr.getAttributeValue(null, "URI");
                    if (StringUtils.isBlank(uri)) {
                        uri = xmlr.getAttributeValue(null, "HREF");
                    }
                    String text = parseHtmlAwareText(xmlr).trim();
                    returnString += "<a href=\"" + uri + "\">" + (StringUtils.isBlank(text) ? uri : text) + "</a>";
                } else if (xmlr.getLocalName().equals("list")) {
                    returnString += parseText_list(xmlr);
                } else if (xmlr.getLocalName().equals("citation")) {
                    if (SOURCE_DVN_3_0.equals(xmlr.getAttributeValue(null, "source"))) {
                        returnMap = parseDVNCitation(xmlr);
                    } else {
                        returnString += parseText_citation(xmlr);
                    }
                } else if (xmlr.getLocalName().equals("txt")) {
                    returnString += parseText(xmlr);
                } else {
                    throw new EJBException("ERROR occurred in mapDDI (parseText): tag not yet supported: <" + xmlr.getLocalName() + ">");
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (xmlr.getLocalName().equals(endTag)) {
                    break;
                }
            }
        }

        if (returnMap != null) {
            // this is one of our new citation areas for DVN3.0
            return returnMap;
        }

        // otherwise it's a standard section and just return the String like we always did
        return returnString.trim();
    }

    private String parseNoteByType(XMLStreamReader xmlr, String type) throws XMLStreamException {
        if (type.equalsIgnoreCase(xmlr.getAttributeValue(null, "type"))) {
            return parseText(xmlr);
        } else {
            return null;
        }
    }

    private String parseText_list(XMLStreamReader xmlr) throws XMLStreamException {
        String listString = null;
        String listCloseTag = null;

        // check type
        String listType = xmlr.getAttributeValue(null, "type");
        if ("bulleted".equals(listType) || listType == null) {
            listString = "<ul>\n";
            listCloseTag = "</ul>";
        } else if ("ordered".equals(listType)) {
            listString = "<ol>\n";
            listCloseTag = "</ol>";
        } else {
            // this includes the default list type of "simple"
            throw new EJBException("ERROR occurred in mapDDI (parseText): ListType of types other than {bulleted, ordered} not currently supported.");
        }

        while (true) {
            int event = xmlr.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (xmlr.getLocalName().equals("itm")) {
                    listString += "<li>" + parseHtmlAwareText(xmlr) + "</li>\n";
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
        String citation = "<!--  parsed from DDI citation title and holdings -->";
        boolean addHoldings = false;
        String holdings = "";

        while (true) {
            int event = xmlr.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (xmlr.getLocalName().equals("titlStmt")) {
                    while (true) {
                        event = xmlr.next();
                        if (event == XMLStreamConstants.START_ELEMENT) {
                            if (xmlr.getLocalName().equals("titl")) {
                                citation += parseText(xmlr);
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

                    if (StringUtils.isNotBlank(uri) || StringUtils.isNotBlank(holdingsText)) {
                        holdings += addHoldings ? ", " : "";
                        addHoldings = true;

                        if (StringUtils.isBlank(uri)) {
                            holdings += holdingsText;
                        } else if (StringUtils.isBlank(holdingsText)) {
                            holdings += "<a href=\"" + uri + "\">" + uri + "</a>";
                        } else {
                            // both uri and text have values
                            holdings += "<a href=\"" + uri + "\">" + holdingsText + "</a>";
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
            citation += " (" + holdings + ")";
        }

        return citation;
    }

    private String parseUNF(String unfString) {
        if (unfString.contains("UNF:")) {
            return unfString.substring(unfString.indexOf("UNF:"));
        } else {
            return null;
        }
    }

    private Map<String, Object> parseDVNCitation(XMLStreamReader xmlr) throws XMLStreamException {
        Map<String, Object> returnValues = new HashMap<>();

        while (true) {
            int event = xmlr.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (xmlr.getLocalName().equals("IDNo")) {
                    returnValues.put("idType", xmlr.getAttributeValue(null, "agency"));
                    returnValues.put("idNumber", parseText(xmlr));
                } else if (xmlr.getLocalName().equals("biblCit")) {
                    returnValues.put("text", parseText(xmlr));
                } else if (xmlr.getLocalName().equals("holdings")) {
                    returnValues.put("url", xmlr.getAttributeValue(null, "URI"));
                } else if (xmlr.getLocalName().equals("notes")) {
                    if (NOTE_TYPE_REPLICATION_FOR.equals(xmlr.getAttributeValue(null, "type"))) {
                        returnValues.put("replicationData", true);
                    }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (xmlr.getLocalName().equals("citation")) {
                    break;
                }
            }
        }

        return returnValues;
    }

    private void parseStudyIdHandle(String _id, DatasetDTO datasetDTO) {

        int index1 = _id.indexOf(':');
        int index2 = _id.indexOf('/');
        if (index1 == -1) {
            throw new EJBException("Error parsing (Handle) IdNo: " + _id + ". ':' not found in string");
        } else {
            datasetDTO.setProtocol(_id.substring(0, index1));
        }
        if (index2 == -1) {
            throw new EJBException("Error parsing (Handle) IdNo: " + _id + ". '/' not found in string");

        } else {
            datasetDTO.setAuthority(_id.substring(index1 + 1, index2));
        }
        datasetDTO.setProtocol("hdl");
        datasetDTO.setIdentifier(_id.substring(index2 + 1));
    }

    private void parseStudyIdDOI(String _id, DatasetDTO datasetDTO) throws ImportException {
        int index1 = _id.indexOf(':');
        int index2 = _id.indexOf('/');
        if (index1 == -1) {
            throw new EJBException("Error parsing (DOI) IdNo: " + _id + ". ':' not found in string");
        }

        if (index2 == -1) {
            throw new ImportException("Error parsing (DOI) IdNo: " + _id + ". '/' not found in string");

        } else {
            datasetDTO.setAuthority(_id.substring(index1 + 1, index2));
        }
        datasetDTO.setProtocol("doi");

        datasetDTO.setIdentifier(_id.substring(index2 + 1));
    }

    private void parseStudyIdDoiICPSRdara(String _id, DatasetDTO datasetDTO) throws ImportException {
        /*
            dara/ICPSR DOIs are formatted without the hdl: prefix; for example -
            10.3886/ICPSR06635.v1
            so we assume that everything before the "/" is the authority,
            and everything past it - the identifier:
        */

        int index = _id.indexOf('/');

        if (index == -1) {
            throw new ImportException("Error parsing ICPSR/dara DOI IdNo: " + _id + ". '/' not found in string");
        }

        if (index == _id.length() - 1) {
            throw new ImportException("Error parsing ICPSR/dara DOI IdNo: " + _id + " ends with '/'");
        }

        datasetDTO.setAuthority(_id.substring(0, index));
        datasetDTO.setProtocol("doi");

        datasetDTO.setIdentifier(_id.substring(index + 1));
    }

    // Helper methods
    private MetadataBlockDTO getCitation(DatasetVersionDTO dvDTO) {
        return dvDTO.getMetadataBlocks().get("citation");
    }

    private MetadataBlockDTO getGeospatial(DatasetVersionDTO dvDTO) {
        return dvDTO.getMetadataBlocks().get("geospatial");
    }

    private MetadataBlockDTO getSocialScience(DatasetVersionDTO dvDTO) {
        return dvDTO.getMetadataBlocks().get("socialscience");
    }


    private void addToSet(Set<FieldDTO> set, String typeName, String value) {
        if (StringUtils.isNotBlank(value)) {
            set.add(FieldDTO.createPrimitiveFieldDTO(typeName, value));
        }
    }

    // TODO : determine what is going on here ?
    private void processOtherMat(XMLStreamReader xmlr, DatasetDTO datasetDTO) throws XMLStreamException {
        FileDTO fileDTO = new FileDTO();

        DataFileDTO dfDTO = new DataFileDTO();

        dfDTO.setStorageIdentifier(xmlr.getAttributeValue(null, "URI"));
        dfDTO.setPidURL(xmlr.getAttributeValue(null, "pidURL")); // This attribute is not a part of DDI schema

        fileDTO.setDataFile(dfDTO);


        for (int event = xmlr.next(); event != XMLStreamConstants.END_DOCUMENT; event = xmlr.next()) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (xmlr.getLocalName().equals("labl")) {
                    // this is the file name:
                    fileDTO.setLabel(parseText(xmlr));
                    // TODO: in DVN3 we used to make an attempt to determine the file type
                    // based on the file name.
                } else if (xmlr.getLocalName().equals("txt")) {
                    fileDTO.setDescription(parseText(xmlr));
                } else if (xmlr.getLocalName().equals("notes")) {
                    String noteType = xmlr.getAttributeValue(null, "type");

                    if (NOTE_TYPE_CONTENTTYPE.equalsIgnoreCase(noteType)) {
                        String contentType = parseText(xmlr);
                        if (StringUtils.isNotBlank(contentType)) {
                            dfDTO.setContentType(contentType);
                        }
                    }
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (xmlr.getLocalName().equals("otherMat")) {
                    // post process
                    if (StringUtils.isBlank(fileDTO.getLabel())) {
                        fileDTO.setLabel("harvested file");
                    }

                    datasetDTO.getDatasetVersion().getFiles().add(fileDTO);
                    // TODO: handle categories; note that multiple categories are allowed in Dataverse 4;
                    return;
                }
            }
        }
    }

    // this method is for attempting to extract the minimal amount of file-level
    // metadata from an ICPSR-supplied DDI. (they use the "fileDscr" instead of
    // "otherMat" for general file metadata; the only field they populate is
    // "fileName". -- 4.6

    private void processFileDscrMinimal(XMLStreamReader xmlr, DatasetDTO datasetDTO) throws XMLStreamException {
        FileDTO fileDTO = new FileDTO();

        DataFileDTO dfDTO = new DataFileDTO();
        dfDTO.setContentType("data/various-formats"); // reserved ICPSR content type identifier
        fileDTO.setDataFile(dfDTO);

        for (int event = xmlr.next(); event != XMLStreamConstants.END_DOCUMENT; event = xmlr.next()) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (xmlr.getLocalName().equals("fileName")) {
                    // this is the file name:
                    String label = parseText(xmlr);
                    // do some cleanup:
                    int col = label.lastIndexOf(':');
                    if (col > -1) {
                        if (col < label.length() - 1) {
                            label = label.substring(col + 1);
                        } else {
                            label = label.replaceAll(":", "");
                        }
                    }
                    label = label.replaceAll("[#;<>\\?\\|\\*\"]", "");
                    label = label.replaceAll("/", "-");
                    // strip leading blanks:
                    label = label.replaceFirst("^[ \t]*", "");
                    fileDTO.setLabel(label);
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (xmlr.getLocalName().equals("fileDscr")) {
                    if (StringUtils.isBlank(fileDTO.getLabel())) {
                        fileDTO.setLabel("harvested file");
                    }
                    if (StringUtils.isBlank(fileDTO.getDataFile().getStorageIdentifier())) {
                        fileDTO.getDataFile().setStorageIdentifier(HARVESTED_FILE_STORAGE_PREFIX);
                    }

                    datasetDTO.getDatasetVersion().getFiles().add(fileDTO);

                    return;
                }
            }
        }
    }

    /**
     * Helper class for storing ddi terms of use.
     * It enables postponing processing of terms
     * of use to the end. Where all the parsed
     * files are available in {@link DatasetDTO} model.
     */
    private static class TermsOfUseDataHolder {
        private String termsOfUse;

        public String getTermsOfUse() {
            return termsOfUse;
        }

        public void setTermsOfUse(String termsOfUse) {
            this.termsOfUse = termsOfUse;
        }
    }
}

