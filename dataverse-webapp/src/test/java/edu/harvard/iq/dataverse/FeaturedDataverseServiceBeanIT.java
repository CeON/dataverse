package edu.harvard.iq.dataverse;

import edu.harvard.iq.dataverse.arquillian.arquillianexamples.WebappArquillianDeployment;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.dataverse.DataverseRepository;
import org.assertj.core.util.Lists;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.transaction.api.annotation.TransactionMode;
import org.jboss.arquillian.transaction.api.annotation.Transactional;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Arquillian.class)
@Transactional(TransactionMode.ROLLBACK)
public class FeaturedDataverseServiceBeanIT extends WebappArquillianDeployment {

    @Inject
    private FeaturedDataverseServiceBean service;

    @Inject
    private DataverseRepository dataverseRepository;

    // -------------------- TESTS --------------------

    @Test
    public void refreshFeaturedDataversesAutomaticSorting() {
        // given
        Dataverse root = dataverseRepository.getById(1L);
        List<Dataverse> featuredDataverses = service.findByDataverseId(1L);
        assertThat(featuredDataverses.stream().map(Dataverse::getId)).containsExactly(19L, 23L, 20L, 21L);

        // when
        root.setFeaturedDataversesSorting(Dataverse.FeaturedDataversesSorting.BY_DATASET_COUNT);
        dataverseRepository.save(root);
        service.refreshFeaturedDataversesAutomaticSorting();

        // then
        assertThat(service.findByDataverseId(1L).stream().map(Dataverse::getId)).containsExactly(21L, 19L, 23L, 20L);
    }

    @Test
    public void sortFeaturedDataverses() {
        // given
        Dataverse root = dataverseRepository.getById(1L);
        List<Dataverse> featuredDataverses = service.findByDataverseId(1L);
        assertThat(featuredDataverses.stream().map(Dataverse::getId)).containsExactly(19L, 23L, 20L, 21L);
        List<Dataverse> manualOrder = Lists.newArrayList(
                featuredDataverses.get(2), featuredDataverses.get(0), featuredDataverses.get(3), featuredDataverses.get(1));

        // when
        List<Dataverse> sortedManual = service.sortFeaturedDataverses(manualOrder, Dataverse.FeaturedDataversesSorting.BY_HAND);

        // then
        assertThat(sortedManual.stream().map(Dataverse::getId)).containsExactly(20L, 19L, 21L, 23L);

        // when
        List<Dataverse> sortedNameAsc = service.sortFeaturedDataverses(manualOrder, Dataverse.FeaturedDataversesSorting.BY_NAME_ASC);

        // then
        assertThat(sortedNameAsc.stream().map(Dataverse::getId)).containsExactly(21L, 19L, 23L, 20L);

        // when
        List<Dataverse> sortedNameDesc = service.sortFeaturedDataverses(manualOrder, Dataverse.FeaturedDataversesSorting.BY_NAME_DESC);

        // then
        assertThat(sortedNameDesc.stream().map(Dataverse::getId)).containsExactly(20L, 23L, 19L, 21L);

        // when
        List<Dataverse> sortedDatasetCount = service.sortFeaturedDataverses(manualOrder, Dataverse.FeaturedDataversesSorting.BY_DATASET_COUNT);

        // then
        assertThat(sortedDatasetCount.stream().map(Dataverse::getId)).containsExactly(21L, 19L, 20L, 23L);
    }
}
