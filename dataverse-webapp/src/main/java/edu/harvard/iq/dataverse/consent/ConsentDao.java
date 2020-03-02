package edu.harvard.iq.dataverse.consent;

import edu.harvard.iq.dataverse.persistence.consent.AcceptedConsent;
import edu.harvard.iq.dataverse.persistence.consent.Consent;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;
import java.util.Locale;

@Stateless
public class ConsentDao {

    @PersistenceContext(unitName = "VDCNet-ejbPU")
    private EntityManager em;

    public List<Consent> findConsentsForDisplay(Locale preferredLanguage) {

        return em.createQuery("SELECT cons FROM Consent cons JOIN cons.consentDetails details" +
                                      " WHERE cons.hidden = false AND details.language = :lang OR details.language = :defaultLanugage",
                              Consent.class)
                .setParameter("lang", preferredLanguage)
                .setParameter("defaultLanugage", Locale.ENGLISH)
                .getResultList();
    }

    public void saveConsents(AcceptedConsent acceptedConsent){
        em.persist(acceptedConsent);
    }
}
