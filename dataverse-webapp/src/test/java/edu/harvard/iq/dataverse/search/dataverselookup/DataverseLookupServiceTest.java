package edu.harvard.iq.dataverse.search.dataverselookup;

import edu.harvard.iq.dataverse.DataverseDao;
import edu.harvard.iq.dataverse.PermissionServiceBean;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.search.SearchFields;
import edu.harvard.iq.dataverse.search.query.PermissionFilterQueryBuilder;
import edu.harvard.iq.dataverse.search.query.SolrQuerySanitizer;
import io.vavr.Tuple2;
import org.apache.solr.client.solrj.SolrClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static edu.harvard.iq.dataverse.search.MockSolrResponseUtil.createSolrResponse;
import static edu.harvard.iq.dataverse.search.MockSolrResponseUtil.document;
import static edu.harvard.iq.dataverse.search.MockSolrResponseUtil.field;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataverseLookupServiceTest {

    @Mock
    private SolrClient solrClient;
    @Mock
    private PermissionFilterQueryBuilder permissionFilterQueryBuilder;
    @Mock
    private SolrQuerySanitizer querySanitizer;
    @Mock
    private DataverseDao dataverseDao;
    @Mock
    private PermissionServiceBean permissionService;

    @InjectMocks
    private DataverseLookupService service;

    @Mock
    private DataverseRequest dataverseRequest;
    @Mock
    private PermissionServiceBean.RequestPermissionQuery requestPermissionQuery;

    // -------------------- TESTS --------------------

    @Test
    void fetchLookupData() throws Exception {
        // given
        when(solrClient.query(any())).thenReturn(
                createSolrResponse(4L,
                        document(fields(11L, "dv_11", "dv1", "Dataverse 1", "1", "Root")),
                        document(fields(12L, "dv_12", "dv2", "Dataverse 2", "1", "Root"))),
                createSolrResponse(
                        document(fields(13L, "dv_13", "sub_dv", "Sub-dataverse", "11", "Dataverse 1")),
                        document(fields(14L, "dv_14", "sub_dv2", "Sub-dataverse 2", "15", "Some dataverse"))),
                createSolrResponse(
                        document(fields(15L, "dv_15", "some", "Some dataverse", "1", "Root"))));
        String permissionFilterQuery = "";

        // when
        List<LookupData> lookupData = service.fetchLookupData("", permissionFilterQuery);

        // then
        assertThat(lookupData).extracting(LookupData::getIdentifier, LookupData::getName,
                        LookupData::getParentName, LookupData::getUpperParentName)
                .containsExactlyInAnyOrder(
                        tuple("dv1", "Dataverse 1", "Root", ""),
                        tuple("dv2", "Dataverse 2", "Root", ""),
                        tuple("sub_dv", "Sub-dataverse", "Dataverse 1", "Root"),
                        tuple("sub_dv2", "Sub-dataverse 2", "Some dataverse", "Root"));
    }

    @Test
    void buildFilterQuery() {
        // given
        when(permissionFilterQueryBuilder.buildPermissionFilterQueryForAddDataset(dataverseRequest))
                .thenReturn("PERMISSION_QUERY");

        // when
        String permissionFilterQuery = service.buildFilterQuery(dataverseRequest);

        // then
        assertThat(permissionFilterQuery).isEqualTo("PERMISSION_QUERY");
    }

    @Test
    void findDataverseById() throws Exception {
        // given
        when(solrClient.query(any())).thenReturn(
                createSolrResponse(document(
                        field(SearchFields.ENTITY_ID, 11L),
                        field(SearchFields.IDENTIFIER, "testDv"),
                        field(SearchFields.NAME, "Test dataverse"),
                        field(SearchFields.PARENT_NAME, "Root"))));

        // when
        LookupData found = service.findDataverseById(11L);

        // then
        assertThat(found)
                .extracting(LookupData::getId, LookupData::getIdentifier, LookupData::getName, LookupData::getParentName)
                .containsExactly(11L, "testDv", "Test dataverse", "Root");
    }

    // -------------------- PRIVATE --------------------

    private static Tuple2<String, Object>[] fields(Long id, String solrId, String identifier,
                                                   String name, String parentId, String parentName) {
        return new Tuple2[] {
                field(SearchFields.ENTITY_ID, id), field(SearchFields.ID, solrId),
                field(SearchFields.IDENTIFIER, identifier), field(SearchFields.NAME, name),
                field(SearchFields.PARENT_ID, parentId), field(SearchFields.PARENT_NAME, parentName) };
    }
}