package edu.harvard.iq.dataverse.arquillian.arquillianexamples;

import edu.harvard.iq.dataverse.authorization.providers.builtin.BuiltinUserServiceBean;
import edu.harvard.iq.dataverse.persistence.user.BuiltinUser;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean.Key;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.transaction.api.annotation.TransactionMode;
import org.jboss.arquillian.transaction.api.annotation.Transactional;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.MountableFile;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

@RunWith(Arquillian.class)
@Transactional(TransactionMode.ROLLBACK)
public class ArquillianExampleIT extends WebappArquillianDeployment {

    private static final Logger logger = Logger.getLogger(ArquillianExampleIT.class.getName());

    @PersistenceContext(unitName = "VDCNet-ejbPU")
    private EntityManager em;

    @EJB
    private BuiltinUserServiceBean builtinUserServiceBean;
    
    @Inject
    private SolrClient solrClient;
     
//    @Rule
//    public GenericContainer solrContainer = new GenericContainer<>("solr:8.2")
//        .withLogConsumer(frame -> System.out.println(frame.getUtf8String()))
//        .withCopyFileToContainer(MountableFile.forClasspathResource("/testsolrconfig/mysetup.sh", 777), "/docker-entrypoint-initdb.d/mysetup.sh")
//        .withCopyFileToContainer(MountableFile.forClasspathResource("/testsolrconfig/collection1/schema.xml", 777), "/tmp/schema.xml")
//        .withCopyFileToContainer(MountableFile.forClasspathResource("/testsolrconfig/collection1/solrconfig.xml", 777), "/tmp/solrconfig.xml")
//        .withCommand("solr-precreate collection1 dataverse_config")
////        .withCommand("bash -c \"precreate-core collection1 && source /tmp/mysetup.sh && solr-foreground\"")
////        .withCommand("bash -c /tmp/mysetup.sh")
////        .withCommand("bash -c whoami")
////        .withCommand("ls -la /")
////        .withCommand("ls -l /docker-entrypoint-initdb.d")
//        .withExposedPorts(8983)
//        ;
    
//    bash -c "precreate-core gettingstarted && source /mysetup.sh && solr-foreground"
    
//    @Rule
//    public SolrContainer solrContainer = new SolrContainer("solr:6.6").withCore("collection1");
    
//    @ClassRule
//    public static GenericContainer solrContainer = new GenericContainer<>(new ImageFromDockerfile()
//            .withFileFromClasspath("Dockerfile", "/testsolrconfig/Dockerfile")
//            .withFileFromClasspath("schema.xml", "/testsolrconfig/schema.xml")
//            .withFileFromClasspath("solrconfig.xml", "/testsolrconfig/solrconfig.xml")
//            )
//    
//            .withLogConsumer(frame -> System.out.println(frame.getUtf8String()))
//            .withCommand("solr-precreate collection1 /opt/solr/server/solr/configsets/dataverse_config")
//            .withExposedPorts(8983);

    
    @Test
    public void should_create_greeting() throws SolrServerException, IOException, InterruptedException {
        logger.warning("TEST METHOD");
//        System.out.println(" --- SOLR CONTAINER RUNNING: " + solrContainer.isRunning());
//        BuiltinUser builtinUser = new BuiltinUser();
//        builtinUser.setUserName("MACIEK");
//        builtinUser.setPasswordEncryptionVersion(1);
//        builtinUserServiceBean.save(builtinUser);
//
//        List list = em.createNativeQuery("SELECT * FROM builtinuser").getResultList();
//        list.forEach(System.out::println);

        System.out.println("waiting for solr");
//        System.out.println("solr ip" + solrContainer.getContainerIpAddress());
//        System.out.println("solrContainer.getMappedPort(8983);" + solrContainer.getMappedPort(8983));
//        Thread.sleep(10000);
//        Thread.sleep(1000 * 60 * 10);
//        System.out.println(
//        solrContainer.execInContainer("ls /")
//            .getStdout()
//        );
        System.out.println("checking solr");
//        SolrClient solrClient = new HttpSolrClient.Builder("http://localhost:" + solrContainer.getMappedPort(8983) + "/solr/collection1").build();
        
//        HttpClient client = HttpClientBuilder.create().build();
//        HttpGet request = new HttpGet("http://localhost:" + solrContainer.getMappedPort(8983) + "/solr/collection1/schema");
//        HttpResponse response = client.execute(request);
//        System.out.println(IOUtils.toString(response.getEntity().getContent()).substring(0, 200));
        
        solrClient.getById("1");
    }
}
