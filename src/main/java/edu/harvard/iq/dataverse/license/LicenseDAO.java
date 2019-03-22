package edu.harvard.iq.dataverse.license;

import edu.harvard.iq.dataverse.license.dto.LicenseReorderDto;

import javax.ejb.Stateless;
import javax.inject.Named;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;
import java.util.Locale;

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

    public List<LicenseReorderDto> findLocalizedLicenses(Locale locale) {
        return em.createQuery("SELECT new edu.harvard.iq.dataverse.license.dto.LicenseReorderDto(l.id,names.text)" +
                "FROM License l " +
                "JOIN l.localizedNames names WHERE names.locale = :locale ORDER BY l.position ASC", LicenseReorderDto.class)
                .setParameter("locale", locale)
                .getResultList();
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
