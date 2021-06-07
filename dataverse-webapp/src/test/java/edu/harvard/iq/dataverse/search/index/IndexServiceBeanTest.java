package edu.harvard.iq.dataverse.search.index;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrInputDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

@ExtendWith(MockitoExtension.class)
class IndexServiceBeanTest {

    @Mock
    private SolrClient solrClient;

    @InjectMocks
    private IndexServiceBean indexServiceBean;

    @Test
    void updateDataverseParentName() throws IOException, SolrServerException {
        //given
        long childId = 1;
        String parentName = "parentName";

        //when
        indexServiceBean.updateDataverseParentName(childId, parentName);

        //then
        Mockito.verify(solrClient, Mockito.times(1)).add(Mockito.any(SolrInputDocument.class));
        Mockito.verify(solrClient, Mockito.times(1)).commit();

    }
}