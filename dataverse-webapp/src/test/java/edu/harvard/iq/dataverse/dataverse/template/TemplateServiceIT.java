package edu.harvard.iq.dataverse.dataverse.template;

import edu.harvard.iq.dataverse.arquillian.arquillianexamples.WebappArquillianDeployment;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.transaction.api.annotation.TransactionMode;
import org.jboss.arquillian.transaction.api.annotation.Transactional;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.util.Date;

@RunWith(Arquillian.class)
@Transactional(TransactionMode.ROLLBACK)
public class TemplateServiceIT extends WebappArquillianDeployment {

    @PersistenceContext(unitName = "VDCNet-ejbPU")
    private EntityManager em;

    @Inject
    private TemplateService templateService;

    @Test
    public void shouldSuccessfullyUpdateDataverse() {
        //given
        Dataverse dataverse = new Dataverse();
        dataverse.setPublicationDate(new Timestamp(new Date().getTime()));

        em.persist(dataverse);

        //when
        //templateService.updateDataverse();

        //then
    }
}
