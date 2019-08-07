package edu.harvard.iq.dataverse.arquillian.arquillianexamples;

import edu.harvard.iq.dataverse.persistence.PersistenceArquillianDeployment;
import edu.harvard.iq.dataverse.test.arquillian.ArquillianIntegrationTests;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.experimental.categories.Category;

import java.util.logging.Logger;


@Category(ArquillianIntegrationTests.class)
public class WebappArquillianDeployment {

    private static final Logger logger = Logger.getLogger(WebappArquillianDeployment.class.getName());

    @Deployment
    public static Archive<?> createDeployment() {

        JavaArchive javaArchive = ShrinkWrap.create(JavaArchive.class, "dv-webapp.jar")
                .merge(PersistenceArquillianDeployment.createDeployment())
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml")
                .addPackages(true, "edu.harvard.iq.dataverse");
        
        logger.info(javaArchive.toString(true));

        return javaArchive;
    }

}
