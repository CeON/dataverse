package edu.harvard.iq.dataverse.authorization.groups;

import edu.harvard.iq.dataverse.RoleAssigneeServiceBean;
import edu.harvard.iq.dataverse.authorization.groups.impl.builtin.AllUsersGroupProvider;
import edu.harvard.iq.dataverse.authorization.groups.impl.builtin.AuthenticatedUsersProvider;
import edu.harvard.iq.dataverse.authorization.groups.impl.explicit.ExplicitGroupProvider;
import edu.harvard.iq.dataverse.authorization.groups.impl.explicit.ExplicitGroupServiceBean;
import edu.harvard.iq.dataverse.authorization.groups.impl.ipaddress.IpGroupProvider;
import edu.harvard.iq.dataverse.authorization.groups.impl.ipaddress.IpGroupsServiceBean;
import edu.harvard.iq.dataverse.authorization.groups.impl.mail.MailDomainGroupProvider;
import edu.harvard.iq.dataverse.authorization.groups.impl.mail.MailDomainGroupService;
import edu.harvard.iq.dataverse.authorization.groups.impl.saml.SamlGroupProvider;
import edu.harvard.iq.dataverse.authorization.groups.impl.saml.SamlGroupService;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.persistence.DvObject;
import edu.harvard.iq.dataverse.persistence.group.ExplicitGroup;
import edu.harvard.iq.dataverse.persistence.group.Group;
import edu.harvard.iq.dataverse.persistence.user.RoleAssignee;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.ejb.Stateless;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;

/**
 * @author michael
 */
@Stateless
public class GroupServiceBean {
    private static final Logger logger = Logger.getLogger(GroupServiceBean.class.getName());

    @EJB
    IpGroupsServiceBean ipGroupsService;
    @EJB
    SamlGroupService samlGroupService;
    @EJB
    ExplicitGroupServiceBean explicitGroupService;
    @EJB
    RoleAssigneeServiceBean roleAssigneeService;
    @EJB
    MailDomainGroupService mailDomainGroupService;

    private final Map<String, GroupProvider<?>> groupProviders = new HashMap<>();

    private IpGroupProvider ipGroupProvider;
    private SamlGroupProvider samlGroupProvider;
    private ExplicitGroupProvider explicitGroupProvider;
    private MailDomainGroupProvider mailDomainGroupProvider;

    @EJB
    RoleAssigneeServiceBean roleAssigneeSvc;

    // -------------------- GETTERS --------------------

    public IpGroupProvider getIpGroupProvider() {
        return ipGroupProvider;
    }

    public SamlGroupProvider getSamlGroupProvider() {
        return samlGroupProvider;
    }

    public ExplicitGroupProvider getExplicitGroupProvider() {
        return explicitGroupProvider;
    }

    // -------------------- LOGIC --------------------

    @PostConstruct
    public void setup() {
        addGroupProvider(AllUsersGroupProvider.get());
        addGroupProvider(AuthenticatedUsersProvider.get());
        addGroupProvider(ipGroupProvider = new IpGroupProvider(ipGroupsService));
        addGroupProvider(samlGroupProvider = new SamlGroupProvider(samlGroupService));
        addGroupProvider(mailDomainGroupProvider = new MailDomainGroupProvider(mailDomainGroupService));

        List<GroupProvider<?>> providers = new ArrayList<>(groupProviders.values());
        addGroupProvider(explicitGroupProvider = new ExplicitGroupProvider(explicitGroupService, roleAssigneeService, providers));
        Logger.getLogger(GroupServiceBean.class.getName()).log(Level.INFO, null, "PostConstruct group service call");
    }

    public Group getGroup(String groupAlias) {
        String[] comps = groupAlias.split(Group.PATH_SEPARATOR, 2);
        GroupProvider<?> gp = groupProviders.get(comps[0]);
        if (gp == null) {
            logger.log(Level.WARNING, "Cannot find group provider with alias {0}", comps[0]);
            return null;
        }
        return gp.get(comps[1]);
    }

    /**
     * Finds all the groups {@code req} is part of in {@code dvo}'s context.
     * Recurses upwards in {@link ExplicitGroup}s, as needed.
     *
     * @param req The request whose group memberships we seek.
     * @param dvo The {@link DvObject} we determining the context fo the membership.
     * @return The groups {@code req} is part of under {@code dvo}.
     */
    public Set<Group> groupsFor(DataverseRequest req, DvObject dvo) {
        return groupProviders.values().stream()
                .flatMap(gp -> gp.groupsFor(req, dvo).stream())
                .collect(toSet());
    }

    /**
     * All the groups a Role assignee belongs to. Does not take request-level groups
     * (such as IPGroups) into account.
     */
    public Set<Group> groupsFor(RoleAssignee ra, DvObject dvo) {
        return groupProviders.values().stream()
                .flatMap(gp -> gp.groupsFor(ra, dvo).stream())
                .collect(toSet());
    }

    /**
     * groupsFor( RoleAssignee ra, DvObject dvo ) doesn't really get *all* the
     * groups a Role assignee belongs to as advertised but this method comes
     * closer.
     *
     * @param ra An AuthenticatedUser.
     * @return As many groups as we can find for the AuthenticatedUser.
     * @deprecated Does not look into IP Groups. Use {@link #groupsFor(edu.harvard.iq.dataverse.engine.command.DataverseRequest)}
     */
    @Deprecated
    public Set<Group> groupsFor(RoleAssignee ra) {
        return groupProviders.values().stream()
                .flatMap(gp -> gp.groupsFor(ra).stream())
                .collect(toSet());
    }


    public Set<Group> groupsFor(DataverseRequest req) {
        return groupProviders.values().stream()
                .flatMap(gp -> gp.groupsFor(req).stream())
                .collect(toSet());
    }

    /**
     * Collections of groups may include {@link ExplicitGroup}s, which have a
     * recursive structure (more precisely, a Composite Pattern}. This has many
     * advantages, but it makes answering the question "which groups are
     * contained in this group" non-trivial. This method deals with this issue by
     * providing a "flat list" of the groups contained in the groups at the
     * passed collection.
     * <p>
     * The resultant stream is distinct - groups appear in it only once, even if
     * some of them are members of multiple groups.
     *
     * @param groups A collection of groups
     * @return A distinct stream of groups who are members of, or are
     * descendants of members of the groups in {@code groups}.
     */
    public Stream<Group> flattenGroupsCollection(Collection<Group> groups) {
        Stream.Builder<Group> out = Stream.builder();
        groups.forEach(g -> {
            out.accept(g);
            if (g instanceof ExplicitGroup) {
                collectGroupContent((ExplicitGroup) g, out);
            }
        });
        return out.build().distinct();
    }

    /**
     * Returns all the groups that are in, of are ancestors of a group in
     * the passed group collection.
     *
     * @return {@code groups} and their ancestors.
     */
    public Set<Group> collectAncestors(Collection<Group> groups) {
        // Ancestors will be collected here.
        Set<Group> retVal = new HashSet<>();

        // Groups whose ancestors were not collected yet.
        Set<Group> perimeter = new HashSet<>(groups);

        while (!perimeter.isEmpty()) {
            Group next = perimeter.iterator().next();
            retVal.add(next);
            perimeter.remove(next);
            explicitGroupService.findDirectlyContainingGroups(next).forEach(g -> {
                if (!retVal.contains(g)) {
                    perimeter.add(g);
                }
            });
        }

        return retVal;
    }

    public Set<Group> findGlobalGroups() {
        Set<Group> groups = new HashSet<>();
        groupProviders.values().forEach(
                gp -> groups.addAll(gp.findGlobalGroups()));
        return groups;
    }

    // -------------------- PRIVATE --------------------

    private void collectGroupContent(ExplicitGroup eg, Stream.Builder<Group> out) {
        eg.getContainedRoleAssgineeIdentifiers().stream()
                .map(idtf -> roleAssigneeSvc.getRoleAssignee(idtf))
                .filter(asn -> asn instanceof Group)
                .forEach(group -> out.accept((Group) group));

        eg.getContainedExplicitGroups().forEach(meg -> {
            out.accept(meg);
            collectGroupContent(meg, out);
        });
    }

    private void addGroupProvider(GroupProvider<?> gp) {
        groupProviders.put(gp.getGroupProviderAlias(), gp);
    }
}
