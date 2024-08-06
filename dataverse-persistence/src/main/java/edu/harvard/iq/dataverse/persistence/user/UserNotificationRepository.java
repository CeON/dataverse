package edu.harvard.iq.dataverse.persistence.user;


import com.google.common.collect.Lists;
import edu.harvard.iq.dataverse.persistence.JpaRepository;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;
import java.util.Set;

/**
 * @author xyang
 */
@Stateless
public class UserNotificationRepository extends JpaRepository<Long, UserNotification> {
    private final static int DELETE_BATCH_SIZE = 100;

    @PersistenceContext(unitName = "VDCNet-ejbPU")
    private EntityManager em;

    // -------------------- CONSTRUCTORS --------------------

    public UserNotificationRepository() {
        super(UserNotification.class);
    }

    // -------------------- LOGIC --------------------

    public List<UserNotification> findByUser(Long userId) {
        return em.createQuery("select un from UserNotification un where un.user.id =:userId order by un.sendDate desc", UserNotification.class)
                .setParameter("userId", userId)
                .getResultList();
    }

    public List<UserNotification> findByUser(Long userId, String searchTerm, int offset, int resultLimit, boolean isAscending) {
        return em.createQuery("select un from UserNotification un where un.user.id =:userId order by un.sendDate "
                        + getOrderByDirection(isAscending), UserNotification.class)
                .setParameter("userId", userId)
                .setFirstResult(offset)
                .setMaxResults(resultLimit)
                .getResultList();
    }

    public Long countByUser(Long userId) {
        return em.createQuery("select count(un) from UserNotification as un where un.user.id = :userId", Long.class)
                .setParameter("userId", userId)
                .getSingleResult();
    }

    public int updateRequestor(Long oldId, Long newId) {
        if (oldId == null || newId == null) {
            throw new IllegalArgumentException("Null encountered: [oldId]:" + oldId + ", [newId]:" + newId);
        }
        return em.createNativeQuery(String.format("update usernotification " +
                "set parameters = jsonb_set(parameters::jsonb, '{requestorId}', '\"%s\"')::json " +
                "where parameters ->> 'requestorId' = '%s'", newId.toString(), oldId.toString()))
                .executeUpdate();
    }

    public int deleteByIds(Set<Long> ids) {
        return Lists.partition(Lists.newArrayList(ids), DELETE_BATCH_SIZE).stream()
                .mapToInt(idBatch ->
                        em.createQuery("delete from UserNotification where id in :ids", UserNotification.class)
                                .setParameter("ids", idBatch)
                                .executeUpdate())
                .sum();
    }

    public int deleteByUser(Long userId) {
        return em.createQuery("delete from UserNotification where user.id = :userId", UserNotification.class)
                .setParameter("userId", userId)
                .executeUpdate();
    }

    public Long getUnreadNotificationCountByUser(Long userId) {
        return em.createQuery("select count(un) from UserNotification as un where un.user.id = :userId and un.readNotification = :readNotification", Long.class)
                .setParameter("userId", userId)
                .setParameter("readNotification", false)
                .getSingleResult();
    }

    public int updateEmailSent(long userNotificationId) {
        return em.createQuery("UPDATE UserNotification notification SET notification.emailed = :emailSent" +
                                      " WHERE notification.id = :userNotificationId")
                .setParameter("emailSent", true)
                .setParameter("userNotificationId", userNotificationId)
                .executeUpdate();
    }

    public UserNotification findLastSubmitNotificationByObjectId(long datasetId) {
        List<UserNotification> notifications = em.createQuery("SELECT un FROM UserNotification un " +
                "WHERE un.objectId = :objectId AND un.type = :type " +
                "ORDER BY un.sendDate DESC", UserNotification.class)
                .setParameter("objectId", datasetId)
                .setParameter("type", NotificationType.SUBMITTEDDS)
                .getResultList();
        return notifications.isEmpty() ? null : notifications.get(0);
    }

    private String getOrderByDirection(boolean isAscending) {
        if (isAscending) {
            return "asc";
        }
        return "desc";
    }
}
