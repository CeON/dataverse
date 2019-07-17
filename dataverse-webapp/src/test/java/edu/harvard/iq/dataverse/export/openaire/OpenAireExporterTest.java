package edu.harvard.iq.dataverse.export.openaire;

import com.jayway.restassured.path.xml.XmlPath;
import edu.harvard.iq.dataverse.DatasetVersion;
import edu.harvard.iq.dataverse.export.OpenAireExporter;
import edu.harvard.iq.dataverse.util.xml.XmlPrinter;
import junit.framework.Assert;
import org.junit.Test;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;

import junit.framework.Assert;
import org.junit.Test;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

import static junit.framework.Assert.assertEquals;

public class OpenAireExporterTest {

    private final OpenAireExporter openAireExporter;

    public OpenAireExporterTest() {

        openAireExporter = new OpenAireExporter(true);
    }

    /**
     * Test of getProviderName method, of class OpenAireExporter.
     */
    @Test
    public void testGetProviderName() {
        System.out.println("getProviderName");
        OpenAireExporter instance = new OpenAireExporter(true);
        String expResult = "oai_datacite";
        String result = instance.getProviderName();
        assertEquals(expResult, result);
    }

    /**
     * Test of getDisplayName method, of class OpenAireExporter.
     */
    @Test
    public void testGetDisplayName() {
        System.out.println("getDisplayName");
        OpenAireExporter instance = new OpenAireExporter(true);
        String expResult = "OpenAIRE";
        String result = instance.getDisplayName();
        assertEquals(expResult, result);
    }

    /**
     * Test of exportDataset method, of class OpenAireExporter.
     */
    @Test
    public void testExportDataset() throws Exception {
        System.out.println("exportDataset");

        byte[] file = Files.readAllBytes(Paths.get(getClass().getClassLoader()
                .getResource("json/export/openaire/dataset-spruce1.json").toURI()));
        String datasetVersionAsJson = new String(file);

        JsonReader jsonReader = Json.createReader(new StringReader(datasetVersionAsJson));
        JsonObject jsonObject = jsonReader.readObject();
        DatasetVersion nullVersion = null;

        String xmlOnOneLine = openAireExporter.exportDataset(nullVersion);
        String xmlAsString = XmlPrinter.prettyPrintXml(xmlOnOneLine);
        System.out.println("XML: " + xmlAsString);
        XmlPath xmlpath = XmlPath.from(xmlAsString);
        Assert.assertEquals("Spruce Goose", xmlpath.getString("resource.titles.title"));
        Assert.assertEquals("Spruce, Sabrina", xmlpath.getString("resource.creators.creator.creatorName"));
        Assert.assertEquals("1.0", xmlpath.getString("resource.version"));
    }

    /**
     * Test of exportDataset method, of class OpenAireExporter.
     */
    @Test
    public void testValidateExportDataset() throws Exception {
        System.out.println("validateExportDataset");
        byte[] file = Files.readAllBytes(Paths.get(getClass().getClassLoader()
                .getResource("txt/export/openaire/dataset-all-defaults.txt").toURI()));
        String datasetVersionAsJson = new String(file);
        JsonReader jsonReader = Json.createReader(new StringReader(datasetVersionAsJson));

        JsonObject jsonObject = jsonReader.readObject();
        DatasetVersion nullVersion = null;
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        openAireExporter.exportDataset(nullVersion);

        {
            String xmlOnOneLine = new String(byteArrayOutputStream.toByteArray());
            String xmlAsString = XmlPrinter.prettyPrintXml(xmlOnOneLine);
            System.out.println("XML: " + xmlAsString);
        }
        InputStream xmlStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(true);
        factory.setNamespaceAware(true);
        factory.setAttribute("http://java.sun.com/xml/jaxp/properties/schemaLanguage",
                "http://www.w3.org/2001/XMLSchema");
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new ErrorHandler() {
            public void warning(SAXParseException e) throws SAXException {
                throw new RuntimeException(e);
            }

            public void error(SAXParseException e) throws SAXException {
                throw new RuntimeException(e);
            }

            public void fatalError(SAXParseException e) throws SAXException {
                throw new RuntimeException(e);
            }
        });
        builder.parse(new InputSource(xmlStream));
        xmlStream.close();
    }

    /**
     * Test of isXMLFormat method, of class OpenAireExporter.
     */
    @Test
    public void testIsXMLFormat() {
        System.out.println("isXMLFormat");
        OpenAireExporter instance = new OpenAireExporter(true);
        Boolean expResult = true;
        Boolean result = instance.isXMLFormat();
        assertEquals(expResult, result);
    }

    /**
     * Test of isHarvestable method, of class OpenAireExporter.
     */
    @Test
    public void testIsHarvestable() {
        System.out.println("isHarvestable");
        OpenAireExporter instance = new OpenAireExporter(true);
        Boolean expResult = true;
        Boolean result = instance.isHarvestable();
        assertEquals(expResult, result);
    }

    /**
     * Test of isAvailableToUsers method, of class OpenAireExporter.
     */
    @Test
    public void testIsAvailableToUsers() {
        System.out.println("isAvailableToUsers");
        OpenAireExporter instance = new OpenAireExporter(true);
        Boolean expResult = true;
        Boolean result = instance.isAvailableToUsers();
        assertEquals(expResult, result);
    }

    /**
     * Test of getXMLNameSpace method, of class OpenAireExporter.
     */
    @Test
    public void testGetXMLNameSpace() throws Exception {
        System.out.println("getXMLNameSpace");
        OpenAireExporter instance = new OpenAireExporter(true);
        String expResult = "http://datacite.org/schema/kernel-4";
        String result = instance.getXMLNameSpace();
        assertEquals(expResult, result);
    }

    /**
     * Test of getXMLSchemaLocation method, of class OpenAireExporter.
     */
    @Test
    public void testGetXMLSchemaLocation() throws Exception {
        System.out.println("getXMLSchemaLocation");
        OpenAireExporter instance = new OpenAireExporter(true);
        String expResult = "http://schema.datacite.org/meta/kernel-4.1/metadata.xsd";
        String result = instance.getXMLSchemaLocation();
        assertEquals(expResult, result);
    }

    /**
     * Test of getXMLSchemaVersion method, of class OpenAireExporter.
     */
    @Test
    public void testGetXMLSchemaVersion() throws Exception {
        System.out.println("getXMLSchemaVersion");
        OpenAireExporter instance = new OpenAireExporter(true);
        String expResult = "4.1";
        String result = instance.getXMLSchemaVersion();
        assertEquals(expResult, result);
    }
}