package edu.harvard.iq.dataverse.arquillianglassfishconfig;

import io.vavr.control.Try;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

public class ArquillianGlassfishConfigurator {

    public void createTempGlassfishResources() {
        try {

            String userHomeDirectory = System.getProperty("user.home");
            Path dataversePropertiesDirectory = Files.createDirectories(Paths.get(userHomeDirectory + "/.dataverse"));
            Path propertiesPath = Paths.get(dataversePropertiesDirectory.toString() + "/glassfish.properties");

            if (isPropertiesFileExists(propertiesPath)) {
                Files.copy(Paths.get("src/test/resources-glassfish-embedded/glassfish.properties"), propertiesPath);
            }

            Properties properties = new Properties();
            properties.load(new FileInputStream(propertiesPath.toString()));

            Document document = replaceGlassfishXmlValues(properties);

            createGlassfishResources(document, "/tmp/glassfish-resources.xml");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createGlassfishResources(Document document, String savePath) throws IOException {
        XMLWriter xmlWriter = new XMLWriter(new FileWriter(savePath));
        xmlWriter.write(document);
        xmlWriter.close();
    }

    private Document replaceGlassfishXmlValues(Properties properties) throws IOException {
        SAXReader reader = new SAXReader();
        Document document = Try.of(() -> reader
                .read(new FileInputStream("src/test/resources-glassfish-embedded/glassfish-resources.xml")))
                .getOrElseThrow(throwable -> {
                    throw new RuntimeException(throwable);
                });

        List<Node> list = document.selectNodes("/resources/jdbc-connection-pool/child::*");

        list.forEach(node -> {
                    Element element = (Element) node;
                    String propertyName = element.attribute(0).getValue();

                    switch (propertyName) {
                        case "password":
                            element.attribute(1).setValue(properties.getProperty("password"));
                            break;
                        case "PortNumber":
                            element.attribute(1).setValue(properties.getProperty("portnumber"));
                            break;
                        case "User":
                            element.attribute(1).setValue(properties.getProperty("user"));
                            break;
                        case "databaseName":
                            element.attribute(1).setValue(properties.getProperty("databasename"));
                            break;
                    }
                }
        );
        return document;
    }

    private boolean isPropertiesFileExists(Path propertiesPath) {
        return !propertiesPath.toFile().exists();
    }
}
