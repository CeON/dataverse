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

    @PersistenceContext(unitName = "VDCNet-ejbPU")
    private EntityManager em;

    @EJB
    private BuiltinUserServiceBean builtinUserServiceBean;

    @Test
    public void should_create_greeting() {
        BuiltinUser builtinUser = new BuiltinUser();
        builtinUser.setUserName("MACIEK");
        builtinUser.setPasswordEncryptionVersion(1);
        builtinUserServiceBean.save(builtinUser);

        List list = em.createNativeQuery("SELECT * FROM builtinuser").getResultList();
        list.forEach(System.out::println);
    }
}
