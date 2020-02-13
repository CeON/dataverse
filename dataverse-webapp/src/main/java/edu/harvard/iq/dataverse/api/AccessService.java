package edu.harvard.iq.dataverse.api;

import edu.harvard.iq.dataverse.PermissionServiceBean;
import edu.harvard.iq.dataverse.persistence.datafile.datavariable.DataVariable;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.user.Permission;
import edu.harvard.iq.dataverse.persistence.user.User;
import io.vavr.control.Try;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@Stateless
public class AccessService extends AbstractApiBean {
    private static final Logger logger = Logger.getLogger(AccessService.class.getCanonicalName());

    private PermissionServiceBean permissionService;

    @Deprecated
    public AccessService() {
    }

    @Inject
    public AccessService(PermissionServiceBean permissionService) {
        this.permissionService = permissionService;
    }

    public boolean isRestrictedByEmbargo(Dataset ds) {
        return ds.hasActiveEmbargo() && !permissionService.on(ds).has(Permission.ViewUnpublishedDataset);
    }

    public boolean isRestrictedByEmbargo(Dataset ds, String apiToken) {
        Optional<User> apiTokenUser = getApiTokenUser(apiToken);
        if(!apiTokenUser.isPresent()) {
            return ds.hasActiveEmbargo() && !permissionService.on(ds).has(Permission.ViewUnpublishedDataset);
        }
        return apiTokenUser.map(user -> ds.hasActiveEmbargo() &&
                !permissionService.requestOn(createDataverseRequest(user), ds).has(Permission.ViewUnpublishedDataset))
                .orElse(false);
    }

    public boolean isRestrictedByEmbargo(Long dataVariableId) {
        DataVariable dataVariable = findDataVariable(dataVariableId);
        if(dataVariable == null) {
            throw new BadRequestException("There is no data variable with id: " + dataVariableId);
        }
        Dataset dataFileOwner = dataVariable.getDataTable().getDataFile().getOwner();
        return isRestrictedByEmbargo(dataFileOwner);
    }

    public Optional<User> getApiTokenUser(String apiToken) {
        return Try.of(this::findUserOrDie)
                .onFailure(throwable -> logger.log(Level.FINE, "Failed finding user for apiToken: " + apiToken, throwable))
                .toJavaOptional();
    }


}
