package edu.harvard.iq.dataverse.test;

import edu.harvard.iq.dataverse.authorization.providers.builtin.BuiltinUser;
import edu.harvard.iq.dataverse.authorization.providers.builtin.BuiltinUserServiceBean;
import edu.harvard.iq.dataverse.flyway.StartupFlywayMigrator;
import edu.harvard.iq.dataverse.passwordreset.PasswordResetServiceBean;
import edu.harvard.iq.dataverse.search.IndexServiceBean;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.transaction.api.annotation.TransactionMode;
import org.jboss.arquillian.transaction.api.annotation.Transactional;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;

@RunWith(Arquillian.class)
@Transactional(TransactionMode.ROLLBACK)
public class ArqTest {

    @PersistenceContext(unitName = "VDCNet-ejbPU")
    private EntityManager em;

    @Inject
    BuiltinUserServiceBean builtinUserServiceBean;

    @Deployment
    public static JavaArchive createDeployment() {
        JavaArchive javaArchive = ShrinkWrap.create(JavaArchive.class)
                .addClass(StartupFlywayMigrator.class)
                .addClass(BuiltinUserServiceBean.class)
                .addClass(BuiltinUser.class)
                .addClass(IndexServiceBean.class)
                .addClass(PasswordResetServiceBean.class)
                .addAsResource("test-persistence.xml", "META-INF/persistence.xml")
                .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
        System.out.println(javaArchive.toString(true));
        return javaArchive;
    }

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
