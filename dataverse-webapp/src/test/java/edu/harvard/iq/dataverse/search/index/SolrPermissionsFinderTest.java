package edu.harvard.iq.dataverse.search.index;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import edu.harvard.iq.dataverse.DataverseRoleServiceBean;
import edu.harvard.iq.dataverse.RoleAssigneeServiceBean;
import edu.harvard.iq.dataverse.persistence.MocksFactory;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion.VersionState;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.group.ExplicitGroup;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.persistence.user.DataverseRole;
import edu.harvard.iq.dataverse.persistence.user.Permission;
import edu.harvard.iq.dataverse.persistence.user.RoleAssignment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * @author madryk
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SolrPermissionsFinderTest {

    @InjectMocks
    private SolrPermissionsFinder searchPermissionsFinder;

    @Mock
    private RoleAssigneeServiceBean roleAssigneeService;
    @Mock
    private DataverseRoleServiceBean rolesSvc;

    private DataverseRole roleWithViewUnpublishedDataverse;
    private DataverseRole roleWithViewUnpublishedDataset;
    private DataverseRole roleWithoutViewUnpublishedWithAddDataset;

    private AuthenticatedUser user1;
    private AuthenticatedUser user2;
    private ExplicitGroup group;

    @BeforeEach
    void beforeEach() {
        roleWithViewUnpublishedDataverse = new DataverseRole();
        roleWithViewUnpublishedDataverse.addPermission(Permission.ViewUnpublishedDataverse);

        roleWithViewUnpublishedDataset = new DataverseRole();
        roleWithViewUnpublishedDataset.addPermission(Permission.ViewUnpublishedDataset);

        roleWithoutViewUnpublishedWithAddDataset = new DataverseRole();
        roleWithoutViewUnpublishedWithAddDataset.addPermission(Permission.AddDataset);

        user1 = MocksFactory.makeAuthenticatedUser("John", "Doe");
        user2 = MocksFactory.makeAuthenticatedUser("Jane", "Doe");

        group = MocksFactory.makeExplicitGroup("group1");
        group.updateAlias();
    }

    // -------------------- TESTS --------------------

    @Test
    void findDataversePerms__released_dataverse() {
        // given
        Dataverse dataverse = new Dataverse();
        dataverse.setPublicationDate(Timestamp.from(Instant.now()));
        Set<RoleAssignment> dataverseRoleAssignments = Sets.newHashSet(
                new RoleAssignment(roleWithoutViewUnpublishedWithAddDataset, user2, dataverse, null));

        when(rolesSvc.rolesAssignments(dataverse)).thenReturn(dataverseRoleAssignments);
        when(roleAssigneeService.getRoleAssignee(user2.getIdentifier())).thenReturn(user2);

        // when
        SolrPermissions permissions = searchPermissionsFinder.findDataversePerms(dataverse);
        SearchPermissions searchPermissions = permissions.getSearchPermissions();
        SolrPermission addDatasetPermissions = permissions.getAddDatasetPermissions();

        // then
        assertThat(searchPermissions.getPermissions()).isEmpty();
        assertThat(searchPermissions.getPublicFrom()).isEqualTo(SearchPermissions.ALWAYS_PUBLIC);
        assertThat(addDatasetPermissions.getPermittedEntities())
                .containsExactlyInAnyOrder("group_user" + user2.getId());
    }

    @Test
    void findDataversePerms__not_released_dataverse() {
        // given
        Dataverse dataverse = new Dataverse();
        dataverse.setId(1L);
        Set<RoleAssignment> dataverseRoleAssignments = Sets.newHashSet(
                new RoleAssignment(roleWithViewUnpublishedDataverse, user1, dataverse, null),
                new RoleAssignment(roleWithViewUnpublishedDataverse, group, dataverse, null),
                new RoleAssignment(roleWithoutViewUnpublishedWithAddDataset, user2, dataverse, null));

        when(rolesSvc.rolesAssignments(dataverse)).thenReturn(dataverseRoleAssignments);
        when(roleAssigneeService.getRoleAssignee(user1.getIdentifier())).thenReturn(user1);
        when(roleAssigneeService.getRoleAssignee(group.getIdentifier())).thenReturn(group);
        when(roleAssigneeService.getRoleAssignee(user2.getIdentifier())).thenReturn(user2);

        // when
        SolrPermissions permissions = searchPermissionsFinder.findDataversePerms(dataverse);
        SearchPermissions searchPermissions = permissions.getSearchPermissions();
        SolrPermission addDatasetPermissions = permissions.getAddDatasetPermissions();

        // then
        assertThat(searchPermissions.getPermissions())
                .containsExactlyInAnyOrder("group_user" + user1.getId(), "group_" + group.getAlias());
        assertThat(searchPermissions.getPublicFrom()).isEqualTo(SearchPermissions.NEVER_PUBLIC);
        assertThat(addDatasetPermissions.getPermittedEntities())
                .containsExactlyInAnyOrder("group_user" + user2.getId());
    }

    @Test
    void findDatasetVersionPerms__released_version() {
        // given
        DatasetVersion version = new DatasetVersion();
        version.setVersionState(VersionState.RELEASED);

        // when
        SolrPermissions permissions = searchPermissionsFinder.findDatasetVersionPerms(version);
        SearchPermissions searchPermissions = permissions.getSearchPermissions();

        // then
        assertThat(searchPermissions.getPermissions()).isEmpty();
        assertThat(searchPermissions.getPublicFrom()).isEqualTo(SearchPermissions.ALWAYS_PUBLIC);
    }

    @Test
    void findDatasetVersionPerms__not_released_version() {
        // given
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        DatasetVersion version = new DatasetVersion();
        version.setDataset(dataset);
        version.setVersionState(VersionState.DRAFT);
        Set<RoleAssignment> datasetRoleAssignments = Sets.newHashSet(
                new RoleAssignment(roleWithViewUnpublishedDataset, user1, dataset, null),
                new RoleAssignment(roleWithViewUnpublishedDataset, group, dataset, null),
                new RoleAssignment(roleWithoutViewUnpublishedWithAddDataset, user2, dataset, null));

        when(rolesSvc.rolesAssignments(dataset)).thenReturn(datasetRoleAssignments);
        when(roleAssigneeService.getRoleAssignee(user1.getIdentifier())).thenReturn(user1);
        when(roleAssigneeService.getRoleAssignee(group.getIdentifier())).thenReturn(group);

        // when
        SolrPermissions permissions = searchPermissionsFinder.findDatasetVersionPerms(version);
        SearchPermissions searchPermissions = permissions.getSearchPermissions();

        // then
        assertThat(searchPermissions.getPermissions())
                .containsExactlyInAnyOrder("group_user" + user1.getId(), "group_" + group.getAlias());
        assertThat(searchPermissions.getPublicFrom()).isEqualTo(SearchPermissions.NEVER_PUBLIC);
    }

    @Test
    void findFileMetadataPermsFromDatasetVersion__released_dataset_version() {
        // given
        DatasetVersion version = new DatasetVersion();
        version.setVersionState(VersionState.RELEASED);

        // when
        SolrPermissions permissions = searchPermissionsFinder.findDatasetVersionPerms(version);
        SearchPermissions searchPermissions = permissions.getSearchPermissions();

        // then
        assertThat(searchPermissions.getPermissions()).isEmpty();
        assertThat(searchPermissions.getPublicFrom()).isEqualTo(SearchPermissions.ALWAYS_PUBLIC);
    }

    @Test
    void findFileMetadataPermsFromDatasetVersion__released_dataset_version_with_embargo() {
        // given
        Instant embargoInstant = Instant.now().plus(10, ChronoUnit.DAYS);
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setEmbargoDate(Date.from(embargoInstant));
        DatasetVersion version = new DatasetVersion();
        version.setDataset(dataset);
        version.setVersionState(VersionState.RELEASED);
        Set<RoleAssignment> datasetRoleAssignments = Sets.newHashSet(
                new RoleAssignment(roleWithViewUnpublishedDataset, user1, dataset, null),
                new RoleAssignment(roleWithViewUnpublishedDataset, group, dataset, null),
                new RoleAssignment(roleWithoutViewUnpublishedWithAddDataset, user2, dataset, null));

        when(rolesSvc.rolesAssignments(dataset)).thenReturn(datasetRoleAssignments);
        when(roleAssigneeService.getRoleAssignee(user1.getIdentifier())).thenReturn(user1);
        when(roleAssigneeService.getRoleAssignee(group.getIdentifier())).thenReturn(group);

        // when
        SolrPermissions permissions = searchPermissionsFinder.findFileMetadataPermsFromDatasetVersion(version);
        SearchPermissions searchPermissions = permissions.getSearchPermissions();

        // then
        assertThat(searchPermissions.getPermissions())
                .containsExactlyInAnyOrder("group_user" + user1.getId(), "group_" + group.getAlias());
        assertThat(searchPermissions.getPublicFrom()).isEqualTo(embargoInstant);
    }

    @Test
    void findFileMetadataPermsFromDatasetVersion__not_released_dataset_version() {
        // given
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        DatasetVersion version = new DatasetVersion();
        version.setDataset(dataset);
        version.setVersionState(VersionState.DRAFT);
        Set<RoleAssignment> datasetRoleAssignments = Sets.newHashSet(
                new RoleAssignment(roleWithViewUnpublishedDataset, user1, dataset, null),
                new RoleAssignment(roleWithViewUnpublishedDataset, group, dataset, null),
                new RoleAssignment(roleWithoutViewUnpublishedWithAddDataset, user2, dataset, null));

        when(rolesSvc.rolesAssignments(dataset)).thenReturn(datasetRoleAssignments);
        when(roleAssigneeService.getRoleAssignee(user1.getIdentifier())).thenReturn(user1);
        when(roleAssigneeService.getRoleAssignee(group.getIdentifier())).thenReturn(group);

        // when
        SolrPermissions permissions = searchPermissionsFinder.findFileMetadataPermsFromDatasetVersion(version);
        SearchPermissions searchPermissions = permissions.getSearchPermissions();

        // then
        assertThat(searchPermissions.getPermissions())
                .containsExactlyInAnyOrder("group_user" + user1.getId(), "group_" + group.getAlias());
        assertThat(searchPermissions.getPublicFrom()).isEqualTo(SearchPermissions.NEVER_PUBLIC);
    }

    @Test
    void extractVersionsForPermissionIndexing__contains_draft_and_released() {
        // given
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setVersions(Lists.newArrayList());
        DatasetVersion draftVersion = constructDatasetVersion(13L, VersionState.DRAFT, dataset);
        DatasetVersion lastReleasedVersion = constructDatasetVersion(12L, VersionState.RELEASED, dataset);
        constructDatasetVersion(11L, VersionState.RELEASED, dataset);

        // when
        Set<DatasetVersion> versionsForIndexing = searchPermissionsFinder.extractVersionsForPermissionIndexing(dataset);

        // then
        assertThat(versionsForIndexing)
                .containsExactlyInAnyOrder(draftVersion, lastReleasedVersion);
    }

    @Test
    void extractVersionsForPermissionIndexing__contains_deaccessioned_and_draft() {
        // given
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setVersions(Lists.newArrayList());
        DatasetVersion draftVersion = constructDatasetVersion(12L, VersionState.DRAFT, dataset);
        constructDatasetVersion(11L, VersionState.DEACCESSIONED, dataset);

        // when
        Set<DatasetVersion> versionsForIndexing = searchPermissionsFinder.extractVersionsForPermissionIndexing(dataset);

        // then
        assertThat(versionsForIndexing)
                .containsExactlyInAnyOrder(draftVersion);
    }

    @Test
    void extractVersionsForPermissionIndexing__contains_deaccessioned() {
        // given
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setVersions(Lists.newArrayList());
        DatasetVersion deacessionedVersion = constructDatasetVersion(11L, VersionState.DEACCESSIONED, dataset);

        // when
        Set<DatasetVersion> versionsForIndexing = searchPermissionsFinder.extractVersionsForPermissionIndexing(dataset);

        // then
        assertThat(versionsForIndexing)
                .containsExactlyInAnyOrder(deacessionedVersion);
    }

    @Test
    void extractVersionsForPermissionIndexing__contains_deaccessioned_and_released() {
        // given
        Dataset dataset = new Dataset();
        dataset.setId(1L);
        dataset.setVersions(Lists.newArrayList());
        constructDatasetVersion(13L, VersionState.DEACCESSIONED, dataset);
        DatasetVersion releasedVersion = constructDatasetVersion(12L, VersionState.RELEASED, dataset);
        constructDatasetVersion(11L, VersionState.DEACCESSIONED, dataset);

        // when
        Set<DatasetVersion> versionsForIndexing = searchPermissionsFinder.extractVersionsForPermissionIndexing(dataset);

        // then
        assertThat(versionsForIndexing)
                .containsExactlyInAnyOrder(releasedVersion);
    }

    // -------------------- PRIVATE --------------------

    private DatasetVersion constructDatasetVersion(Long datasetVersionId, VersionState state, Dataset dataset) {
        DatasetVersion version = new DatasetVersion();
        version.setId(datasetVersionId);
        version.setVersionState(state);
        version.setDataset(dataset);

        dataset.getVersions().add(version);
        return version;
    }
}