/*
 *  (C) Michael Bar-Sinai
 */
package edu.harvard.iq.dataverse.persistence.group;

import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.persistence.user.GuestUser;
import org.junit.jupiter.api.Test;

import static edu.harvard.iq.dataverse.persistence.MocksFactory.makeAuthenticatedUser;
import static edu.harvard.iq.dataverse.persistence.MocksFactory.makeDataverse;
import static edu.harvard.iq.dataverse.persistence.MocksFactory.makeExplicitGroup;
import static edu.harvard.iq.dataverse.persistence.MocksFactory.nextId;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author michael
 */
public class ExplicitGroupTest {

    @Test
    public void addGroupToSelf() throws Exception {
        assertThrows(GroupException.class, () -> {
            ExplicitGroup sut = new ExplicitGroup();
            sut.setDisplayName("a group");
            sut.add(sut);
        }, "A group cannot be added to itself.");
    }

    @Test
    public void addGroupToDescendant()  {
        assertThrows(GroupException.class, () -> {
            Dataverse dv = makeDataverse();
            ExplicitGroup root = new ExplicitGroup();
            root.setId(nextId());
            root.setGroupAliasInOwner("top");
            ExplicitGroup sub = new ExplicitGroup();
            sub.setGroupAliasInOwner("sub");
            sub.setId(nextId());
            ExplicitGroup subSub = new ExplicitGroup();
            subSub.setGroupAliasInOwner("subSub");
            subSub.setId(nextId());
            root.setOwner(dv);
            sub.setOwner(dv);
            subSub.setOwner(dv);

            sub.add(subSub);
            root.add(sub);
            subSub.add(root);
        }, "A group cannot contain its parent");
    }

    @Test
    public void addGroupToUnrealtedGroup()  {
        assertThrows(GroupException.class, () -> {
            Dataverse dv1 = makeDataverse();
            Dataverse dv2 = makeDataverse();
            ExplicitGroup g1 = new ExplicitGroup();
            ExplicitGroup g2 = new ExplicitGroup();
            g1.setOwner(dv1);
            g2.setOwner(dv2);

            g1.add(g2);
        }, "An explicit group cannot contain an explicit group defined in "
                + "a dataverse that's not an ancestor of that group's owner dataverse.");
    }

    @Test
    public void addGroup()  {
        Dataverse dvParent = makeDataverse();
        Dataverse dvSub = makeDataverse();
        dvSub.setOwner(dvParent);

        ExplicitGroup g1 = new ExplicitGroup();
        ExplicitGroup g2 = new ExplicitGroup();
        g1.setOwner(dvSub);
        g2.setOwner(dvParent);

        g1.add(g2);
        assertTrue(g1.structuralContains(g2));
    }

    @Test
    public void adds()  {
        Dataverse dvParent = makeDataverse();
        ExplicitGroup g1 = new ExplicitGroup();
        g1.setOwner(dvParent);

        AuthenticatedUser au1 = makeAuthenticatedUser("Lauren", "Ipsum");
        g1.add(au1);
        g1.add(GuestUser.get());

        assertTrue(g1.structuralContains(GuestUser.get()));
        assertTrue(g1.structuralContains(au1));
        assertFalse(g1.structuralContains(makeAuthenticatedUser("Sima", "Kneidle")));
        assertFalse(g1.structuralContains(AllUsers.get()));
    }


    @Test
    public void recursiveStructuralContainment()  {
        Dataverse dvParent = makeDataverse();
        ExplicitGroup parentGroup = makeExplicitGroup();
        ExplicitGroup childGroup = makeExplicitGroup();
        ExplicitGroup grandChildGroup = makeExplicitGroup();
        parentGroup.setOwner(dvParent);
        childGroup.setOwner(dvParent);
        grandChildGroup.setOwner(dvParent);

        childGroup.add(grandChildGroup);
        parentGroup.add(childGroup);

        AuthenticatedUser au = makeAuthenticatedUser("Jane", "Doe");
        grandChildGroup.add(au);
        childGroup.add(GuestUser.get());

        assertTrue(grandChildGroup.structuralContains(au));
        assertTrue(childGroup.structuralContains(au));
        assertTrue(parentGroup.structuralContains(au));

        assertTrue(childGroup.structuralContains(GuestUser.get()));
        assertTrue(parentGroup.structuralContains(GuestUser.get()));

        grandChildGroup.remove(au);

        assertFalse(grandChildGroup.structuralContains(au));
        assertFalse(childGroup.structuralContains(au));
        assertFalse(parentGroup.structuralContains(au));

        childGroup.add(AuthenticatedUsers.get());

        assertFalse(grandChildGroup.structuralContains(au));
        assertFalse(childGroup.structuralContains(au));
        assertFalse(parentGroup.structuralContains(au));
        assertTrue(childGroup.structuralContains(AuthenticatedUsers.get()));

        final IpGroup ipGroup = new IpGroup();
        grandChildGroup.add(ipGroup);
        ipGroup.add(IpAddressRange.make(IpAddress.valueOf("0.0.1.1"), IpAddress.valueOf("0.0.255.255")));

        assertTrue(grandChildGroup.structuralContains(ipGroup));
        assertTrue(childGroup.structuralContains(ipGroup));
        assertTrue(parentGroup.structuralContains(ipGroup));
    }
}


