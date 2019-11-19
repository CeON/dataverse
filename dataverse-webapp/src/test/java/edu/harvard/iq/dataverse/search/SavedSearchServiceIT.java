package edu.harvard.iq.dataverse.search;


import com.google.common.collect.Lists;
import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.GenericDao;
import edu.harvard.iq.dataverse.arquillian.arquillianexamples.WebappArquillianDeployment;
import edu.harvard.iq.dataverse.authorization.AuthenticationServiceBean;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.dataverse.link.SavedSearch;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.transaction.api.annotation.TransactionMode;
import org.jboss.arquillian.transaction.api.annotation.Transactional;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;

@Transactional(TransactionMode.ROLLBACK)
@RunWith(Arquillian.class)
public class SavedSearchServiceIT extends WebappArquillianDeployment {

    @Inject
    private SavedSearchService savedSearchService;
    
    @Inject
    private GenericDao genericDao;

    @Inject
    private AuthenticationServiceBean authenticationServiceBean;

    @Inject
    private DataverseSession dataverseSession;

    @Before
    public void setUp() {
        dataverseSession.setUser(authenticationServiceBean.getAdminUser());
    }

    @Test
    public void saveSavedDataverseSearch() {
        //given
        Dataverse dataverseToBeLinked = genericDao.find(19, Dataverse.class);
        String searchQuery = "testQuery";
        String facetQuery = "facetQuery";

        //when
        SavedSearch savedSearch = savedSearchService.saveSavedDataverseSearch(searchQuery, Lists.newArrayList(facetQuery), dataverseToBeLinked);

        //then
        Assert.assertEquals(savedSearch.getQuery(), searchQuery);
        Assert.assertEquals(1, savedSearch.getFilterQueriesAsStrings().size());
        Assert.assertTrue( savedSearch.getFilterQueriesAsStrings().contains(facetQuery));
        Assert.assertEquals(savedSearch.getDefinitionPoint(), dataverseToBeLinked);


    }
}
