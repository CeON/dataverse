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

    public License saveChanges(License license) {
        return em.merge(license);
    }

    public Long countActiveLicenses() {
        return em.createQuery("SELECT count(l) FROM License l where l.active = true", Long.class).getSingleResult();
    }

    public Long countInactiveLicenses() {
        return em.createQuery("SELECT count(l) FROM License l where l.active = false ", Long.class).getSingleResult();
    }
}
