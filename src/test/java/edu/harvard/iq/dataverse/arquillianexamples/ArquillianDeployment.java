package edu.harvard.iq.dataverse.arquillianexamples;

import edu.harvard.iq.dataverse.ArquillianIntegrationTests;
import edu.harvard.iq.dataverse.arquillianglassfishconfig.ArquillianGlassfishConfigurationParser;
import io.vavr.control.Try;
import org.eu.ingwar.tools.arquillian.extension.suite.annotations.ArquillianSuiteDeployment;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.AfterClass;
import org.junit.experimental.categories.Category;

import java.nio.file.Files;
import java.nio.file.Paths;

@ArquillianSuiteDeployment
@Category(ArquillianIntegrationTests.class)
public class ArquillianDeployment {

    @Deployment
    public static Archive<?> createDeployment() {

        JavaArchive javaArchive = ShrinkWrap.create(JavaArchive.class)
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml")
                .addPackages(true, "edu.harvard.iq.dataverse")
                .addAsResource("test-persistence.xml", "META-INF/persistence.xml");
        System.out.println(javaArchive.toString(true));

        return javaArchive;
    }

    @AfterClass
    public static void removeTempGlassfishResource() {
        Try.of(() -> Files.deleteIfExists(Paths.get(ArquillianGlassfishConfigurationParser.NEW_RESOURCE_PATH)))
                .getOrElseThrow(throwable -> new RuntimeException("Unable to delete temporary glassfish resource", throwable));
    }

}
