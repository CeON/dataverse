package edu.harvard.iq.dataverse.search.ror;

import com.google.common.collect.ImmutableSet;
import edu.harvard.iq.dataverse.persistence.ror.RorData;
import edu.harvard.iq.dataverse.persistence.ror.RorLabel;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrInputDocument;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

@ExtendWith(MockitoExtension.class)
class RorIndexingServiceTest {

    @Mock
    private SolrClient solrClient;

    @InjectMocks
    private RorIndexingService rorIndexingService;

    @Test
    void indexRorRecord() throws IOException, SolrServerException {
        //given
        String rorId = "testRor";
        String name = "testName";
        String countryName = "Poland";
        String countryCode = "PL";
        final ImmutableSet<String> aliases = ImmutableSet.of("alias");
        final ImmutableSet<String> acronyms = ImmutableSet.of("acronym");
        final ImmutableSet<RorLabel> labels = ImmutableSet.of(new RorLabel("label", "123"));

        final RorData rorData = new RorData(rorId, name, countryName, countryCode, aliases, acronyms, labels);
        rorData.setId(1L);

        //when
        rorIndexingService.indexRorRecord(rorData);

        //then
        Mockito.verify(solrClient, Mockito.times(1)).add(Mockito.any(SolrInputDocument.class));
        Mockito.verify(solrClient, Mockito.times(1)).commit();

    }

    @Test
    void indexRorRecord_withoutId() {
        //given
        String rorId = "testRor";
        String name = "testName";
        String countryName = "Poland";
        String countryCode = "PL";
        final ImmutableSet<String> aliases = ImmutableSet.of("alias");
        final ImmutableSet<String> acronyms = ImmutableSet.of("acronym");
        final ImmutableSet<RorLabel> labels = ImmutableSet.of(new RorLabel("label", "123"));

        final RorData rorData = new RorData(rorId, name, countryName, countryCode, aliases, acronyms, labels);

        //when & then
        Assertions.assertThatThrownBy(() -> rorIndexingService.indexRorRecord(rorData)).isInstanceOf(AssertionError.class);
    }
}