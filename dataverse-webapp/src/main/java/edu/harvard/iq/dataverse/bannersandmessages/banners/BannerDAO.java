package edu.harvard.iq.dataverse.bannersandmessages.banners;

import com.google.common.collect.Lists;
import edu.harvard.iq.dataverse.bannersandmessages.banners.dto.ImageWithLinkDto;
import edu.harvard.iq.dataverse.persistence.dataverse.bannersandmessages.DataverseBanner;
import org.primefaces.model.DefaultStreamedContent;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Stateless
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

    public void save(DataverseBanner banner) {
        em.merge(banner);
    }

    public DataverseBanner getBanner(Long bannerId) {
        return em.find(DataverseBanner.class, bannerId);
    }

    public List<ImageWithLinkDto> getBannersForDataverse(Long dataverseId, String localeCode) {
        if (dataverseId == null) {
            return Lists.newArrayList();
        }
        List<Object[]> banners = em.createNativeQuery("select r.image, r.imagelink from (select distinct dvtml.image, dvtml.imagelink, dvtm.totime  from\n" +
                                                              "  dataversebanner dvtm\n" +
                                                              "  join dataverselocalizedbanner dvtml on dvtml.dataversebanner_id = dvtm.id\n" +
                                                              "  where\n" +
                                                              "    dvtm.active = true and\n" +
                                                              "    dvtml.locale = ? and\n" +
                                                              "    ? between dvtm.fromtime and dvtm.totime and\n" +
                                                              "    dvtm.dataverse_id in (with recursive dv_roots as (\n" +
                                                              "    select\n" +
                                                              "        dv_obj.id,\n" +
                                                              "        dv_obj.owner_id,\n" +
                                                              "        dv.allowmessagesbanners\n" +
                                                              "    from dvobject dv_obj\n" +
                                                              "      join dataverse dv on dv_obj.id = dv.id\n" +
                                                              "    where\n" +
                                                              "        dv.id = ?\n" +
                                                              "        union all\n" +
                                                              "        select\n" +
                                                              "               parent_dv_obj.id,\n" +
                                                              "               parent_dv_obj.owner_id,\n" +
                                                              "               parent_dv.allowmessagesbanners\n" +
                                                              "        from dvobject parent_dv_obj\n" +
                                                              "               join dataverse parent_dv on parent_dv_obj.id = parent_dv.id\n" +
                                                              "               join dv_roots on dv_roots.owner_id = parent_dv_obj.id\n" +
                                                              "    )\n" +
                                                              "    select id from dv_roots dr where dr.allowmessagesbanners = true) order by dvtm.totime asc) r")
                .setParameter(1, localeCode)
                .setParameter(2, LocalDateTime.now())
                .setParameter(3, dataverseId)
                .getResultList();

        return banners.stream()
                .map(localizedBanner -> new ImageWithLinkDto(
                        new DefaultStreamedContent(new ByteArrayInputStream((byte[]) localizedBanner[0])),
                        (String) localizedBanner[1]))
                .collect(Collectors.toList());
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
