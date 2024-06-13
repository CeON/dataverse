package edu.harvard.iq.dataverse.persistence.dataverse;

import edu.harvard.iq.dataverse.persistence.JpaRepository;
import io.vavr.collection.Stream;

import javax.ejb.Singleton;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class DataverseRepository extends JpaRepository<Long, Dataverse> {

    // -------------------- CONSTRUCTORS --------------------

    public DataverseRepository() {
        super(Dataverse.class);
    }

    // -------------------- LOGIC --------------------

    public List<Dataverse> findPublishedByOwnerId(Long ownerId) {
        String query = "select d from Dataverse d where d.owner.id =:ownerId and d.publicationDate is not null order by d.name";
        return em.createQuery(query, Dataverse.class)
                .setParameter("ownerId", ownerId)
                .getResultList();
    }

    public List<DataverseDatasetCount> getDatasetCountFor(List<Dataverse> dataverses) {
        if (dataverses.isEmpty()) {
            return Collections.emptyList();
        }

        List<DataverseDatasetCount> withDatasets = em.createQuery(
                "SELECT new edu.harvard.iq.dataverse.persistence.dataverse.DataverseDatasetCount(o.owner, count(o.id)) " +
                        "FROM DvObject o " +
                        "WHERE o.owner IN :ownerDataverses AND o.dtype = 'Dataset' " +
                        "GROUP BY o.owner", DataverseDatasetCount.class)
                .setParameter("ownerDataverses", dataverses)
                .getResultList();

        if (withDatasets.size() == dataverses.size()) {
            return withDatasets;
        } else {
            Set<Long> withDatasetIds = withDatasets.stream().map(dc -> dc.getDataverse().getId()).collect(Collectors.toSet());
            List<DataverseDatasetCount> withoutDatasets = dataverses.stream()
                    .filter(dv -> !withDatasetIds.contains(dv.getId()))
                    .map(dv -> new DataverseDatasetCount(dv, 0L))
                    .collect(Collectors.toList());

            return Stream.concat(withDatasets, withoutDatasets).toJavaList();
        }
    }
}
