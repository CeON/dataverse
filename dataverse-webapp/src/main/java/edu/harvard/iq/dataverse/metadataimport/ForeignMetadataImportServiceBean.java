package edu.harvard.iq.dataverse.metadataimport;


import edu.harvard.iq.dataverse.DatasetFieldServiceBean;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetField;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldType;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.dataset.ForeignMetadataFieldMapping;
import edu.harvard.iq.dataverse.persistence.dataset.ForeignMetadataFormatMapping;

import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.util.logging.Logger;

/**
 * @author Leonid Andreev
 * <p>
 * Draft/prototype XML import service for DVN 4.0
 */
@Stateless
public class ForeignMetadataImportServiceBean {

    private static final Logger logger = Logger.getLogger(ForeignMetadataImportServiceBean.class.getCanonicalName());

    @EJB
    DatasetFieldServiceBean datasetfieldService;

    @PersistenceContext(unitName = "VDCNet-ejbPU")
    private EntityManager em;

    ForeignMetadataFormatMapping findFormatMappingByName(String name) {
        try {
            return em.createNamedQuery("ForeignMetadataFormatMapping.findByName", ForeignMetadataFormatMapping.class)
                    .setParameter("name", name)
                    .getSingleResult();
        } catch (NoResultException nre) {
            return null;
        }
    }

    public void importXML(String xmlToParse, String foreignFormat, DatasetVersion datasetVersion) {
        StringReader reader = null;
        XMLStreamReader xmlr = null;

        ForeignMetadataFormatMapping mappingSupported = findFormatMappingByName(foreignFormat);
        if (mappingSupported == null) {
            throw new EJBException("Unknown/unsupported foreign metadata format " + foreignFormat);
        }

        try {
            reader = new StringReader(xmlToParse);
            XMLInputFactory xmlFactory = javax.xml.stream.XMLInputFactory.newInstance();
            xmlr = xmlFactory.createXMLStreamReader(reader);
            processXML(xmlr, mappingSupported, datasetVersion);

        } catch (XMLStreamException ex) {
            //Logger.getLogger("global").log(Level.SEVERE, null, ex);
            throw new EJBException("ERROR occurred while parsing XML fragment  (" + xmlToParse.substring(0,
                                                                                                         64) + "...); ",
                                   ex);
        } finally {
            try {
                if (xmlr != null) {
                    xmlr.close();
                }
            } catch (XMLStreamException ex) {
            }
        }
    }

    public void importXML(File xmlFile, String foreignFormat, DatasetVersion datasetVersion) {
        FileInputStream in = null;
        XMLStreamReader xmlr = null;

        // look up the foreign metadata mapping for this format:

        ForeignMetadataFormatMapping mappingSupported = findFormatMappingByName(foreignFormat);
        if (mappingSupported == null) {
            throw new EJBException("Unknown/unsupported foreign metadata format " + foreignFormat);
        }

        try {
            in = new FileInputStream(xmlFile);
            XMLInputFactory xmlFactory = javax.xml.stream.XMLInputFactory.newInstance();
            xmlr = xmlFactory.createXMLStreamReader(in);
            processXML(xmlr, mappingSupported, datasetVersion);
        } catch (FileNotFoundException ex) {
            //Logger.getLogger("global").log(Level.SEVERE, null, ex);
            throw new EJBException("ERROR occurred in mapDDI: File Not Found!");
        } catch (XMLStreamException ex) {
            //Logger.getLogger("global").log(Level.SEVERE, null, ex);
            throw new EJBException("ERROR occurred while parsing XML (file " + xmlFile.getAbsolutePath() + "); ", ex);
        } finally {
            try {
                if (xmlr != null) {
                    xmlr.close();
                }
            } catch (XMLStreamException ex) {
            }

            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException ex) {
            }
        }

    }

    private void processXML(XMLStreamReader xmlr, ForeignMetadataFormatMapping foreignFormatMapping, DatasetVersion datasetVersion) throws XMLStreamException {
        // init - similarly to what I'm doing in the metadata extraction code? 

        //while ( xmlr.next() == XMLStreamConstants.COMMENT ); // skip pre root comments
        xmlr.nextTag();
        String openingTag = foreignFormatMapping.getStartElement();
        if (openingTag != null) {
            xmlr.require(XMLStreamConstants.START_ELEMENT, null, openingTag);
        } else {
            // TODO: 
            // add support for parsing the body regardless of the start element.
            // June 20 2014 -- L.A. 
            throw new EJBException("No support for format mappings without start element defined (yet)");
        }

        processXMLElement(xmlr, ":", openingTag, foreignFormatMapping, datasetVersion);

    }

    private void processXMLElement(XMLStreamReader xmlr, String currentPath, String openingTag, ForeignMetadataFormatMapping foreignFormatMapping, DatasetVersion datasetVersion) throws XMLStreamException {
        logger.fine("entering processXMLElement; (" + currentPath + ")");
        for (int event = xmlr.next(); event != XMLStreamConstants.END_DOCUMENT; event = xmlr.next()) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                String currentElement = xmlr.getLocalName();

                ForeignMetadataFieldMapping mappingDefined = datasetfieldService.findFieldMapping(foreignFormatMapping.getName(),
                                                                                                  currentPath + currentElement);

                if (mappingDefined != null) {
                    DatasetField cachedCompoundValue = null;

                    // Process attributes, if any are defined in the mapping:

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
                                        cachedCompoundValue = createDatasetFieldValue(mappedFieldType,
                                                                                      cachedCompoundValue,
                                                                                      attributeValue,
                                                                                      datasetVersion);
                                    } catch (Exception ex) {
                                        logger.warning("Caught unknown exception when processing attribute " + currentPath + currentElement + "{" + attributeName + "} (skipping);");
                                    }
                                } else {
                                    throw new EJBException("Bad foreign metadata field mapping: no such DatasetField " + mappedFieldName + "!");
                                }
                            }
                        }
                    }

                    // Process the payload of this XML element:
                    String dataverseFieldName = mappingDefined.getDatasetfieldName();
                    if (dataverseFieldName != null && !dataverseFieldName.equals("")) {
                        DatasetFieldType dataverseFieldType = datasetfieldService.findByNameOpt(dataverseFieldName);
                        if (dataverseFieldType != null) {
                            String elementTextPayload = parseText(xmlr);
                            createDatasetFieldValue(dataverseFieldType,
                                                    cachedCompoundValue,
                                                    elementTextPayload,
                                                    datasetVersion);
                        } else {
                            throw new EJBException("Bad foreign metadata field mapping: no such DatasetField " + dataverseFieldName + "!");
                        }
                    }
                } else {
                    // recursively, process the xml stream further down: 
                    processXMLElement(xmlr,
                                      currentPath + currentElement + ":",
                                      currentElement,
                                      foreignFormatMapping,
                                      datasetVersion);
                }

            } else if (event == XMLStreamConstants.END_ELEMENT) {
                if (xmlr.getLocalName().equals(openingTag)) {
                    return;
                }
            }
        }
    }


    private DatasetField createDatasetFieldValue(DatasetFieldType dsft, DatasetField savedCompoundValue, String elementText, DatasetVersion datasetVersion) {
        if (dsft.isPrimitive()) {
            if (!dsft.isHasParent()) {
                // simple primitive: 

                DatasetField dsf = null;

                for (DatasetField existingDsf : datasetVersion.getFlatDatasetFields()) {
                    if (existingDsf.getDatasetFieldType().equals(dsft)) {
                        dsf = existingDsf;
                    }
                }

                // if doesn't exist, create a new one: 
                if (dsf == null) {
                    dsf = new DatasetField();
                    dsf.setDatasetFieldType(dsft);
                    datasetVersion.getDatasetFields().add(dsf);
                    dsf.setDatasetVersion(datasetVersion);
                }

                String dsfName = dsft.getName();

                if (!dsft.isControlledVocabulary()) {
                    logger.fine("Creating a new value for field " + dsfName + ": " + elementText);

                    if (dsft.isAllowMultiples()) {
                        DatasetField fieldValue = new DatasetField();
                        fieldValue.setDatasetFieldParent(dsf);
                        fieldValue.setFieldValue(elementText);
                        dsf.getDatasetFieldsChildren().add(fieldValue);

                    } else {
                        dsf.setFieldValue(elementText);
                    }

                }
                // No compound values had to be created; returning null:
                return null;
            } else {
                // a primitive that is part of a compound value:

                // first, let's create the field and the value, for the 
                // primitive node itself: 

                DatasetField childField = new DatasetField();
                childField.setDatasetFieldType(dsft);



                if (dsft.isAllowMultiples()){
                    DatasetField childValue = new DatasetField();
                    childValue.setDatasetFieldParent(childField);
                    childValue.setFieldValue(elementText);
                    childField.getDatasetFieldsChildren().add(childValue);

                } else {
                    childField.setFieldValue(elementText);
                }

                return childField;
            }
        }


        return null;
    }

    private String parseText(XMLStreamReader xmlr) throws XMLStreamException {
        return parseText(xmlr, true);
    }

    private String parseText(XMLStreamReader xmlr, boolean scrubText) throws XMLStreamException {
        String tempString = xmlr.getElementText();
        // TODO: 
        // In 3.* we had to provide our own getElementText method, because 
        // at that point xmlr.getElementText() was found to be buggy. 
        // Investitage if that's still needed! -- See comments in the 
        // DDIServiceBean in 3.6 for details. 
        // -- L.A. June 23 2014
        if (scrubText) {
            tempString = tempString.trim().replace('\n', ' ');
        }
        return tempString;
    }


}
