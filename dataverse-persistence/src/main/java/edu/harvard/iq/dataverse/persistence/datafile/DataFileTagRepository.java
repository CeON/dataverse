package edu.harvard.iq.dataverse.persistence.datafile;

import edu.harvard.iq.dataverse.persistence.JpaRepository;

import javax.ejb.Stateless;
import java.util.List;

@Stateless
public class DataFileTagRepository extends JpaRepository<Long, DataFileTag> {

    // -------------------- CONSTRUCTOR --------------------

    public DataFileTagRepository() {
        super(DataFileTag.class);
    }

    // -------------------- LOGIC --------------------

    public void removeByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        em.createQuery("DELETE FROM DataFileTag t WHERE t.id IN :ids")
                .setParameter("ids", ids)
                .executeUpdate();
    }
}
