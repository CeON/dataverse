package edu.harvard.iq.dataverse.license;

import javax.ejb.Stateless;
import javax.inject.Named;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;

@Stateless
@Named
public class LicenseDAO {

    @PersistenceContext(unitName = "VDCNet-ejbPU")
    private EntityManager em;


    //-------------------- LOGIC --------------------

    public License find(long id) {
        return em.find(License.class, id);
    }

    public List<License> findAll() {
        return em.createQuery("SELECT l FROM License l ORDER BY l.position ASC", License.class).getResultList();
    }

    public List<License> findLicensesWithLocales(List<String> locales) {
        return em.createQuery("SELECT licenses FROM License licenses JOIN licenses.localizedNames as localizedNames" +
                " WHERE localizedNames.locale IN (:locales) ORDER BY licenses.position ASC", License.class)
                .setParameter("locales", locales)
                .getResultList();
    }
}
