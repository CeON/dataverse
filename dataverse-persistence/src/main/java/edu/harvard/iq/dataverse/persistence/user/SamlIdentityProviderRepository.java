package edu.harvard.iq.dataverse.persistence.user;

import edu.harvard.iq.dataverse.persistence.JpaRepository;

import javax.ejb.Singleton;
import java.util.List;

@Singleton
public class SamlIdentityProviderRepository extends JpaRepository<Long, SamlIdentityProvider> {

    // -------------------- CONSTRUCTORS --------------------

    public SamlIdentityProviderRepository() {
        super(SamlIdentityProvider.class);
    }

    // -------------------- LOGIC --------------------

    public SamlIdentityProvider findByEntityId(String entityId) {
        List<SamlIdentityProvider> entityIdList =
                em.createNamedQuery("SamlIdentityProvider.findByEntityId", SamlIdentityProvider.class)
                        .setParameter("entityId", entityId)
                        .getResultList();
        return entityIdList.isEmpty()
                ? null
                : entityIdList.get(0);
    }
}
