/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.harvard.iq.dataverse.dataverse.messages;

import edu.harvard.iq.dataverse.DataverseLocaleBean;
import edu.harvard.iq.dataverse.DataverseSession;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

/**
 *
 * @author tjanek
 */
@Stateless
@Named
public class DataverseTextMessageServiceBean implements java.io.Serializable {

    private static final Logger logger = Logger.getLogger(DataverseTextMessageServiceBean.class.getCanonicalName());

    @Inject
    private DataverseSession session;

    @PersistenceContext(unitName = "VDCNet-ejbPU")
    private EntityManager em;

    public List<String> getTextMessagesForDataverse(Long dataverseId) {
        logger.info("Getting text messages for dataverse: " + dataverseId);
        DataverseLocaleBean locale = new DataverseLocaleBean();
        List<String> messages = em.createNativeQuery("select r.message from (select distinct dvtml.message, dvtm.totime  from\n" +
                "  dataversetextmessage dvtm\n" +
                "  join dataversetextmessagelocale dvtml on dvtml.dataversetextmessage_id = dvtm.id\n" +
                "  where\n" +
                "    dvtm.active = true and\n" +
                "    dvtml.locale = ? and\n" +
                "    ? between dvtm.fromtime and dvtm.totime and\n" +
                "    dvtm.dataverse_id in (with recursive dv_roots as (\n" +
                "      select dv.id, dv.name, dvo.owner_id, dv.allowmessagesbanners\n" +
                "      from dataverse dv\n" +
                "             join dvobject dvo on dv.id = dvo.id\n" +
                "      where dv.id in (select dv2.id\n" +
                "                      from dataverse dv2\n" +
                "                             inner join dvobject dvo2 on dv2.id = dvo2.id\n" +
                "                      where (dvo2.owner_id is null or dvo2.id = ?))\n" +
                "      union\n" +
                "      select dv.id, dv.name, dvr.owner_id, dv.allowmessagesbanners\n" +
                "      from dataverse dv\n" +
                "             join dv_roots dvr on dv.id = dvr.owner_id\n" +
                "    ) select dr.id from dv_roots dr where dr.allowmessagesbanners = true) order by dvtm.totime asc) r")
                .setParameter(1, locale.getLocaleCode())
                .setParameter(2, LocalDateTime.now())
                .setParameter(3, dataverseId)
                .getResultList();
        return messages;
    }

    public void deactivateAllowMessagesAndBanners(Long dataverseId) {
        if (session.getUser().isSuperuser()) {
            logger.info("As superuser, deactivating text messages for dataverse: " + dataverseId);
            em.createNativeQuery("update dataversetextmessage set active = false where dataverse_id = ?")
                    .setParameter(1, dataverseId)
                    .executeUpdate();
        }
    }

}
