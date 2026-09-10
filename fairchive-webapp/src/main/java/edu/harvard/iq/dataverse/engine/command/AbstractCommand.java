package edu.harvard.iq.dataverse.engine.command;

import edu.harvard.iq.dataverse.persistence.DvObject;
import edu.harvard.iq.dataverse.persistence.user.Permission;
import edu.harvard.iq.dataverse.persistence.user.User;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static edu.harvard.iq.dataverse.engine.command.CommandHelper.CH;

/**
 * Convenience class for implementing the {@link Command} interface.
 *
 * @param <R> The result type of the command.
 * @author michael
 */
@SuppressWarnings("serial")
public abstract class AbstractCommand<R> implements Command<R>, Serializable {

    static protected class DvNamePair {

        final String name;
        final DvObject dvObject;

        public DvNamePair(final String name, final DvObject dvObject) {
            this.name = name;
            this.dvObject = dvObject;
        }
    }
    
    private final Map<String, DvObject> affectedDvObjects;
    private final DataverseRequest request;

    /**
     * Convenience method to name affected dataverses.
     *
     * @param s the name
     * @param d the dataverse
     * @return the named pair
     */
    protected static DvNamePair dv(final String s, final DvObject d) {
        return new DvNamePair(s, d);
    }

    public AbstractCommand(final DataverseRequest request, final DvObject afectedDvObject) {
        this(request, dv("", afectedDvObject));
    }

    public AbstractCommand(final DataverseRequest request, final DvNamePair pair, final DvNamePair... more) {
    	this(request, new HashMap<>());
        this.affectedDvObjects.put(pair.name, pair.dvObject);
        for (final DvNamePair p : more) {
            this.affectedDvObjects.put(p.name, p.dvObject);
        }
    }

    public AbstractCommand(final DataverseRequest request, 
    		final Map<String, DvObject> affectedDvObjects) {
        this.request = request;
        this.affectedDvObjects = affectedDvObjects;
    }

    @Override
    public Map<String, DvObject> getAffectedDvObjects() {
        return this.affectedDvObjects;
    }

    @Override
    public DataverseRequest getRequest() {
        return this.request;
    }

    @Override
    public Map<String, Set<Permission>> getRequiredPermissions() {
        return CH.permissionsRequired(getClass());
    }

    @Override
    public boolean isAllPermissionsRequired() {
        return CH.isAllPermissionsRequired(getClass());
    }

    /**
     * Convenience method for getting the user requesting this command.
     *
     * @return the user issuing the command (via the {@link DataverseRequest}).
     */
    protected User getUser() {
        return getRequest().getUser();
    }

    @Override
    public String describe() {
        final StringBuilder sb = new StringBuilder();
        for (final Map.Entry<String, DvObject> entry : affectedDvObjects.entrySet()) {
            final DvObject value = entry.getValue();
            sb.append(entry.getKey()).append(':');
            sb.append((value != null) ? value.accept(DvObject.NameIdPrinter) : "<null>");
            sb.append(' ');
        }
        return sb.toString();
    }

}
