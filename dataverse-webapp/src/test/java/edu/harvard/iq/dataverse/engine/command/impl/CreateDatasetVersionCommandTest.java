package edu.harvard.iq.dataverse.engine.command.impl;

import edu.harvard.iq.dataverse.DatasetDao;
import edu.harvard.iq.dataverse.engine.TestCommandContext;
import edu.harvard.iq.dataverse.engine.TestDataverseEngine;
import edu.harvard.iq.dataverse.engine.command.exception.IllegalCommandException;
import edu.harvard.iq.dataverse.persistence.DvObject;
import edu.harvard.iq.dataverse.persistence.MocksFactory;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.user.Permission;
import edu.harvard.iq.dataverse.validation.DatasetFieldValidationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static edu.harvard.iq.dataverse.mocks.MockRequestFactory.makeRequest;
import static edu.harvard.iq.dataverse.persistence.MocksFactory.makeDataset;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author michael
 */
@ExtendWith(MockitoExtension.class)
public class CreateDatasetVersionCommandTest {

    @Mock
    private DatasetDao datasetDao;

    @Mock
    private DatasetFieldValidationService fieldValidationService;

    @Test
    public void testSimpleVersionAddition() throws Exception {
        Mockito.when(datasetDao.storeVersion(Mockito.any(DatasetVersion.class))).thenReturn(new DatasetVersion());
        SimpleDateFormat dateFmt = new SimpleDateFormat("yyyyMMdd");
        // Create Dataset
        Dataset ds = makeDataset();

        // Populate the Initial version
        DatasetVersion dsvInitial = ds.getEditVersion();
        dsvInitial.setCreateTime(dateFmt.parse("20001012"));
        dsvInitial.setLastUpdateTime(dsvInitial.getLastUpdateTime());
        dsvInitial.setId(MocksFactory.nextId());
        dsvInitial.setReleaseTime(dateFmt.parse("20010101"));
        dsvInitial.setVersionState(DatasetVersion.VersionState.RELEASED);
        dsvInitial.setMinorVersionNumber(0L);
        dsvInitial.setVersionNumber(1L);

        // Create version to be added
        DatasetVersion dsvNew = new DatasetVersion();
        dsvNew.setVersionState(DatasetVersion.VersionState.DRAFT);

        // Execute
        CreateDatasetVersionCommand sut = new CreateDatasetVersionCommand(makeRequest(), ds, dsvNew);

        TestDataverseEngine testEngine = new TestDataverseEngine(new TestCommandContext() {
            @Override
            public DatasetDao datasets() {
                return datasetDao;
            }

            @Override
            public DatasetFieldValidationService fieldValidationService() {
                return fieldValidationService;
            }
        });

        testEngine.submit(sut);

        // asserts
        Mockito.verify(datasetDao, Mockito.times(1)).storeVersion(Mockito.any());
        Date dsvCreationDate = dsvNew.getCreateTime();
        assertEquals(dsvCreationDate, dsvNew.getLastUpdateTime());
        assertEquals(dsvCreationDate.getTime(), ds.getModificationTime().getTime());
        assertEquals(ds, dsvNew.getDataset());
        assertEquals(dsvNew, ds.getEditVersion());
        Map<DvObject, Set<Permission>> expected = new HashMap<>();
        expected.put(ds, Collections.singleton(Permission.AddDataset));
        assertEquals(expected, testEngine.getReqiredPermissionsForObjects());
    }

    @Test
    public void testCantCreateTwoDraftVersions() throws Exception {
        DatasetVersion dsvNew = new DatasetVersion();
        dsvNew.setVersionState(DatasetVersion.VersionState.DRAFT);
        Dataset sampleDataset = makeDataset();
        sampleDataset.getLatestVersion().setVersionState(DatasetVersion.VersionState.DRAFT);

        // Execute
        CreateDatasetVersionCommand sut = new CreateDatasetVersionCommand(makeRequest(), sampleDataset, dsvNew);

        TestDataverseEngine testEngine = new TestDataverseEngine(new TestCommandContext() {
            @Override
            public DatasetDao datasets() {
                return datasetDao;
            }
        });

        assertThrows(IllegalCommandException.class, () -> testEngine.submit(sut));
    }
}
