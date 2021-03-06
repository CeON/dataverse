package edu.harvard.iq.dataverse.engine.command.impl;

import edu.harvard.iq.dataverse.engine.command.AbstractCommand;
import edu.harvard.iq.dataverse.engine.command.CommandContext;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.user.Permission;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * @author Naomi
 */
// no annotations here, since permissions are dynamically decided
public class GetDataverseCommand extends AbstractCommand<Dataverse> {
    private final Dataverse dv;

    public GetDataverseCommand(DataverseRequest aRequest, Dataverse anAffectedDataverse) {
        super(aRequest, anAffectedDataverse);
        dv = anAffectedDataverse;
    }

    @Override
    public Dataverse execute(CommandContext ctxt) {
        return dv;
    }

    @Override
    public Map<String, Set<Permission>> getRequiredPermissions() {
        return Collections.singletonMap("",
                                        dv.isReleased() ? Collections.emptySet()
                                                : Collections.singleton(Permission.ViewUnpublishedDataverse));
    }
}
