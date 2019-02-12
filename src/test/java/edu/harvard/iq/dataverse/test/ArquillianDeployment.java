package edu.harvard.iq.dataverse.test;

import edu.harvard.iq.dataverse.ArquillianIntegrationTests;
import org.eu.ingwar.tools.arquillian.extension.suite.annotations.ArquillianSuiteDeployment;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.experimental.categories.Category;

@ArquillianSuiteDeployment
@Category(ArquillianIntegrationTests.class)
public class ArquillianDeployment {

    @Deployment
    public static JavaArchive createDeployment() {

        JavaArchive javaArchive = ShrinkWrap.create(JavaArchive.class)
                .addPackages(true, "edu.harvard.iq.dataverse")
                .addAsResource("test-persistence.xml", "META-INF/persistence.xml")
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
        System.out.println(javaArchive.toString(true));
        return javaArchive;
    }
}
