package edu.harvard.iq.dataverse.dataverse.banners;

import javax.ejb.Stateless;
import javax.inject.Named;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;

@Stateless
@Named
public class BannerDAO {

    @PersistenceContext(unitName = "VDCNet-ejbPU")
    private EntityManager em;

    public void deactivate(Long bannerId) {
        DataverseBanner banner = em.find(DataverseBanner.class, bannerId);
        banner.setActive(false);
        em.merge(banner);
    }

    public void delete(Long bannerId) {
        DataverseBanner banner = em.find(DataverseBanner.class, bannerId);
        em.remove(banner);
    }

    public DataverseBanner getTextMessage(Long bannerId) {
        return em.find(DataverseBanner.class, bannerId);
    }

    /**
     * Fetches history of banners for dataverse with paging
     * (paging is offset based so it will not offer the best performance if there will be a lot of records)
     *
     * @param dataverseId
     * @param firstResult
     * @param maxResult
     * @return List<DataverseBanner>
     */
    public List<DataverseBanner> fetchBannersForDataverseWithPaging(long dataverseId, int firstResult, int maxResult) {
        return em.createQuery("SELECT ban FROM DataverseBanner ban" +
                " join fetch DataverseLocalizedBanner " +
                "where ban.dataverse.id = :dataverseId order by ban.id DESC", DataverseBanner.class)
                .setParameter("dataverseId", dataverseId)
                .setFirstResult(firstResult)
                .setMaxResults(maxResult)
                .getResultList();
    }

    public Long countBannersForDataverse(long dataverseId) {
        return em.createQuery("select count(ban.id) FROM DataverseBanner as ban " +
                "where ban.dataverse.id = :dataverseid", Long.class)
                .setParameter("dataverseid", dataverseId)
                .getSingleResult();
    }
}
