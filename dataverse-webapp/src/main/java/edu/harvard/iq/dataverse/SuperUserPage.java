package edu.harvard.iq.dataverse;

import edu.harvard.iq.dataverse.persistence.user.User;
import edu.harvard.iq.dataverse.search.index.IndexBatchServiceBean;
import edu.harvard.iq.dataverse.search.index.IndexServiceBean;

import javax.ejb.EJB;
import javax.enterprise.context.SessionScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.json.JsonObjectBuilder;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

@SessionScoped
@Named("SuperUserPage")
public class SuperUserPage implements java.io.Serializable {

    @Inject
    DataverseSession session;
    @Inject
    PermissionsWrapper permissionsWrapper;

    @EJB
    IndexServiceBean indexService;
    @EJB
    IndexBatchServiceBean indexAllService;

    private String indexAllStatus = "No status available";

    private Future<JsonObjectBuilder> indexAllFuture;

    public String init() {
        if (!session.getUser().isSuperuser()) {
            return permissionsWrapper.notAuthorized();
        }
        return null;
    }

    // modeled off http://docs.oracle.com/javaee/7/tutorial/ejb-async002.htm
    public String getIndexAllStatus() {
        if (indexAllFuture != null) {
            if (indexAllFuture.isDone()) {
                try {
                    JsonObjectBuilder status = indexAllFuture.get();
                    indexAllStatus = status.build().toString();
                } catch (ExecutionException | CancellationException | InterruptedException ex) {
                    indexAllStatus = ex.getCause().toString();
                }
            } else {
                indexAllStatus = "Index all is running...";
            }
        }
        return indexAllStatus;
    }

    public void startIndexAll() {
        User user = session.getUser();
        if (user.isSuperuser()) {
            long numPartitions = 1;
            long partitionId = 0;
            indexAllFuture = indexAllService.indexAllOrSubsetAsync(numPartitions, partitionId, false);
            indexAllStatus = "Index all started...";
        } else {
            indexAllStatus = "Only a superuser can run index all";
        }
    }

    public void updateIndexAllStatus() {
        getIndexAllStatus();
    }

}
