package edu.harvard.iq.dataverse.dataverse.banners;

import edu.harvard.iq.dataverse.Dataverse;
import edu.harvard.iq.dataverse.dataverse.banners.dto.DataverseBannerDto;
import edu.harvard.iq.dataverse.dataverse.validation.DataverseTextMessageValidator;

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

    public void save(DataverseBannerDto bannerDto) {

        DataverseTextMessageValidator.validateEndDate(bannerDto.getFromTime(), bannerDto.getToTime());

        DataverseBanner banner = new DataverseBanner();

        banner.setActive(bannerDto.isActive());
        Dataverse dataverse = em.find(Dataverse.class, bannerDto.getDataverseId());
        if (dataverse == null) {
            throw new IllegalArgumentException("Dataverse doesn't exist:" + bannerDto.getDataverseId());
        }
        banner.setDataverse(dataverse);
        banner.setFromTime(bannerDto.getFromTime());
        banner.setToTime(bannerDto.getToTime());

        banner.getDataverseLocalizedBanner().clear();

        bannerDto.getDataverseLocalizedBanner().forEach(lm -> {
            DataverseLocalizedBanner dataverseLocalizedBanner = new DataverseLocalizedBanner();
            dataverseLocalizedBanner.setImage(lm.getImage());
            dataverseLocalizedBanner.setDataverseBanner(banner);
            dataverseLocalizedBanner.setImageLink(lm.getImageLink());
            dataverseLocalizedBanner.setLocale(lm.getLocale());
        });

        em.merge(banner);
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
