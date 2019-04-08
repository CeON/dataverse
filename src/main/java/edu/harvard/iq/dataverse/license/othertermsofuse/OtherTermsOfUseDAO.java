package edu.harvard.iq.dataverse.license.othertermsofuse;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;

@Stateless
public class OtherTermsOfUseDAO {

    @PersistenceContext(unitName = "VDCNet-ejbPU")
    private EntityManager em;

    public List<OtherTermsOfUse> findAll() {
        return em.createQuery("SELECT tos FROM OtherTermsOfUse tos ORDER BY tos.id ASC"
                , OtherTermsOfUse.class).getResultList();
    }

    public OtherTermsOfUse find(Long id) {
        return em.find(OtherTermsOfUse.class, id);
    }

    public OtherTermsOfUse saveChanges(OtherTermsOfUse otherTermsOfUse) {
        return em.merge(otherTermsOfUse);
    }
}
