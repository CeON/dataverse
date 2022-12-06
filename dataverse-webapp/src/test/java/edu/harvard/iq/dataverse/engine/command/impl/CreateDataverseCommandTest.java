package edu.harvard.iq.dataverse.engine.command.impl;

import edu.harvard.iq.dataverse.DataverseDao;
import edu.harvard.iq.dataverse.DataverseFacetServiceBean;
import edu.harvard.iq.dataverse.DataverseFieldTypeInputLevelServiceBean;
import edu.harvard.iq.dataverse.DataverseRoleServiceBean;
import edu.harvard.iq.dataverse.engine.TestCommandContext;
import edu.harvard.iq.dataverse.engine.TestDataverseEngine;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.engine.command.exception.IllegalCommandException;
import edu.harvard.iq.dataverse.persistence.DvObject;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetFieldType;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.dataverse.DataverseFacet;
import edu.harvard.iq.dataverse.persistence.dataverse.DataverseFieldTypeInputLevel;
import edu.harvard.iq.dataverse.persistence.group.IpAddress;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.persistence.user.DataverseRole;
import edu.harvard.iq.dataverse.persistence.user.DataverseRole.BuiltInRole;
import edu.harvard.iq.dataverse.persistence.user.GuestUser;
import edu.harvard.iq.dataverse.persistence.user.RoleAssignment;
import edu.harvard.iq.dataverse.search.index.IndexServiceBean;
import org.junit.Before;
import org.junit.Test;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import static edu.harvard.iq.dataverse.mocks.MockRequestFactory.makeRequest;
import static edu.harvard.iq.dataverse.persistence.MocksFactory.makeAuthenticatedUser;
import static edu.harvard.iq.dataverse.persistence.MocksFactory.makeDatasetFieldType;
import static edu.harvard.iq.dataverse.persistence.MocksFactory.makeDataverse;
import static edu.harvard.iq.dataverse.persistence.MocksFactory.makeDataverseFieldTypeInputLevel;
import static edu.harvard.iq.dataverse.persistence.MocksFactory.makeRole;
import static edu.harvard.iq.dataverse.persistence.MocksFactory.nextId;
import static edu.harvard.iq.dataverse.persistence.MocksFactory.timestamp;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author michael
 */
public class CreateDataverseCommandTest {

    boolean indexCalled = false;
    Map<String, Dataverse> dvByAliasStore = new HashMap<>();
    Map<Long, Dataverse> dvStore = new HashMap<>();
    boolean isRootDvExists;
    boolean facetsDeleted;
    boolean dftilsDeleted;
    List<DataverseFieldTypeInputLevel> createdDftils;
    List<DataverseFacet> createdFacets;

    DataverseDao dataverses = new DataverseDao() {
        @Override
        public boolean isRootDataverseExists() {
            return isRootDvExists;
        }

        @Override
        public Dataverse findByAlias(String anAlias) {
            return dvByAliasStore.get(anAlias);
        }

        @Override
        public Dataverse save(Dataverse dataverse) {
            if (dataverse.getId() == null) {
                dataverse.setId(nextId());
            }
            dvStore.put(dataverse.getId(), dataverse);
            if (dataverse.getAlias() != null) {
                dvByAliasStore.put(dataverse.getAlias(), dataverse);
            }
            return dataverse;
        }

    };

    DataverseRoleServiceBean roles = new DataverseRoleServiceBean() {

        List<RoleAssignment> assignments = new LinkedList<>();

        Map<BuiltInRole, DataverseRole> builtInRoles;

        {
            builtInRoles = new HashMap<>();
            builtInRoles.put(BuiltInRole.EDITOR, makeRole("default-editor"));
            builtInRoles.put(BuiltInRole.ADMIN, makeRole("default-admin"));
            builtInRoles.put(BuiltInRole.CURATOR, makeRole("default-curator"));
        }

        @Override
        public DataverseRole findBuiltinRoleByAlias(BuiltInRole builtInRole) {
            return builtInRoles.get(builtInRole);
        }

        @Override
        public RoleAssignment save(RoleAssignment assignment) {
            assignment.setId(nextId());
            assignments.add(assignment);
            return assignment;
        }

        @Override
        public List<RoleAssignment> directRoleAssignments(DvObject dvObject) {
            // works since there's only one dataverse involved in the context
            // of this unit test.
            return assignments;
        }


    };

    IndexServiceBean index = new IndexServiceBean() {
        @Override
        public Future<String> indexDataverse(Dataverse dataverse) {
            indexCalled = true;
            return null;
        }
    };

    DataverseFieldTypeInputLevelServiceBean dfils = new DataverseFieldTypeInputLevelServiceBean() {
        @Override
        public void create(DataverseFieldTypeInputLevel dataverseFieldTypeInputLevel) {
            createdDftils.add(dataverseFieldTypeInputLevel);
        }

        @Override
        public void deleteFacetsFor(Dataverse d) {
            dftilsDeleted = true;
        }
    };

    DataverseFacetServiceBean facets = new DataverseFacetServiceBean() {
        @Override
        public DataverseFacet create(int displayOrder, DatasetFieldType fieldType, Dataverse ownerDv) {
            DataverseFacet df = new DataverseFacet();
            df.setDatasetFieldType(fieldType);
            df.setDataverse(ownerDv);
            df.setDisplayOrder(displayOrder);
            createdFacets.add(df);
            return df;
        }


        @Override
        public void deleteFacetsFor(Dataverse d) {
            facetsDeleted = true;
        }

    };

    TestDataverseEngine engine;


    @Before
    public void setUp() {
        indexCalled = false;
        dvStore.clear();
        dvByAliasStore.clear();
        isRootDvExists = true;
        facetsDeleted = false;
        createdDftils = new ArrayList<>();
        createdFacets = new ArrayList<>();

        engine = new TestDataverseEngine(new TestCommandContext() {
            @Override
            public IndexServiceBean index() {
                return index;
            }

            @Override
            public DataverseRoleServiceBean roles() {
                return roles;
            }

            @Override
            public DataverseDao dataverses() {
                return dataverses;
            }

            @Override
            public DataverseFacetServiceBean facets() {
                return facets;
            }

            @Override
            public DataverseFieldTypeInputLevelServiceBean fieldTypeInputLevels() {
                return dfils;
            }

        });
    }


    @Test
    public void testDefaultOptions() {
        Dataverse dv = makeDataverse();
        dv.setCreateDate(null);
        dv.setId(null);
        dv.setCreator(null);
        dv.setDefaultContributorRole(null);
        dv.setOwner(makeDataverse());
        final DataverseRequest request = makeRequest(makeAuthenticatedUser("jk", "rollin'"));

        CreateDataverseCommand sut = new CreateDataverseCommand(dv, request, null, null);
        Dataverse result = engine.submit(sut);

        assertNotNull(result.getCreateDate());
        assertNotNull(result.getId());

        assertEquals(result.getCreator(), request.getUser());
        assertEquals(Dataverse.DataverseType.UNCATEGORIZED, result.getDataverseType());
        assertEquals(roles.findBuiltinRoleByAlias(BuiltInRole.EDITOR), result.getDefaultContributorRole());

        // Assert that the creator is admin.
        final RoleAssignment roleAssignment = roles.directRoleAssignments(dv).get(0);
        assertEquals(roles.findBuiltinRoleByAlias(BuiltInRole.ADMIN), roleAssignment.getRole());
        assertEquals(dv, roleAssignment.getDefinitionPoint());
        assertEquals(roleAssignment.getAssigneeIdentifier(), request.getUser().getIdentifier());

        // The following is a pretty wierd way to test that the create date defaults to
        // now, but it works across date changes.
        assertTrue("When the supplied creation date is null, date shuld default to command execution time",
                   Math.abs(System.currentTimeMillis() - result.getCreateDate().toInstant().toEpochMilli()) < 1000);

        assertTrue(result.isPermissionRoot());
        assertTrue(result.isThemeRoot());
        assertTrue(indexCalled);
    }

    @Test
    public void testCustomOptions() {
        Dataverse dv = makeDataverse();

        Timestamp creation = timestamp(1990, 12, 12);
        AuthenticatedUser creator = makeAuthenticatedUser("Joe", "Walsh");

        dv.setCreateDate(creation);

        dv.setId(null);
        dv.setCreator(creator);
        dv.setDefaultContributorRole(null);
        dv.setOwner(makeDataverse());
        dv.setDataverseType(Dataverse.DataverseType.JOURNALS);
        dv.setDefaultContributorRole(roles.findBuiltinRoleByAlias(BuiltInRole.CURATOR));

        final DataverseRequest request = makeRequest();
        List<DatasetFieldType> expectedFacets = Arrays.asList(makeDatasetFieldType(), makeDatasetFieldType(), makeDatasetFieldType());
        List<DataverseFieldTypeInputLevel> dftils = Arrays.asList(makeDataverseFieldTypeInputLevel(makeDatasetFieldType()),
                                                                  makeDataverseFieldTypeInputLevel(makeDatasetFieldType()),
                                                                  makeDataverseFieldTypeInputLevel(makeDatasetFieldType()));

        CreateDataverseCommand sut = new CreateDataverseCommand(dv, request, new LinkedList<>(expectedFacets), new LinkedList<>(dftils));
        Dataverse result = engine.submit(sut);

        assertEquals(creation, result.getCreateDate());
        assertNotNull(result.getId());

        assertEquals(creator, result.getCreator());
        assertEquals(Dataverse.DataverseType.JOURNALS, result.getDataverseType());
        assertEquals(roles.findBuiltinRoleByAlias(BuiltInRole.CURATOR), result.getDefaultContributorRole());

        // Assert that the creator is admin.
        final RoleAssignment roleAssignment = roles.directRoleAssignments(dv).get(0);
        assertEquals(roles.findBuiltinRoleByAlias(BuiltInRole.ADMIN), roleAssignment.getRole());
        assertEquals(dv, roleAssignment.getDefinitionPoint());
        assertEquals(roleAssignment.getAssigneeIdentifier(), request.getUser().getIdentifier());

        assertTrue(result.isPermissionRoot());
        assertTrue(result.isThemeRoot());
        assertTrue(indexCalled);

        assertTrue(facetsDeleted);
        int i = 0;
        for (DataverseFacet df : createdFacets) {
            assertEquals(i, df.getDisplayOrder());
            assertEquals(result, df.getDataverse());
            assertEquals(expectedFacets.get(i), df.getDatasetFieldType());

            i++;
        }

        assertTrue(dftilsDeleted);
        for (DataverseFieldTypeInputLevel dftil : createdDftils) {
            assertEquals(result, dftil.getDataverse());
        }
    }

    @Test(expected = IllegalCommandException.class)
    public void testCantCreateAdditionalRoot() throws Exception {
        engine.submit(new CreateDataverseCommand(makeDataverse(), makeRequest(), null, null));
    }

    @Test(expected = IllegalCommandException.class)
    public void testGuestCantCreateDataverse() throws Exception {
        final DataverseRequest request = new DataverseRequest(GuestUser.get(), IpAddress.valueOf("::"));
        isRootDvExists = false;
        engine.submit(new CreateDataverseCommand(makeDataverse(), request, null, null));
    }

    @Test(expected = IllegalCommandException.class)
    public void testCantCreateAnotherWithSameAlias() throws Exception {

        String alias = "alias";
        final Dataverse dvFirst = makeDataverse();
        dvFirst.setAlias(alias);
        dvFirst.setOwner(makeDataverse());
        engine.submit(new CreateDataverseCommand(dvFirst, makeRequest(), null, null));

        final Dataverse dv = makeDataverse();
        dv.setOwner(makeDataverse());
        dv.setAlias(alias);
        engine.submit(new CreateDataverseCommand(dv, makeRequest(), null, null));
    }

}
