package edu.harvard.iq.dataverse.harvest.server;

import com.google.common.collect.Lists;
import edu.harvard.iq.dataverse.persistence.GlobalId;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataset.DatasetVersion;
import edu.harvard.iq.dataverse.persistence.harvest.OAIRecord;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.logging.Logger;

@ExtendWith(MockitoExtension.class)
class OAIRecordServiceBeanTest {

    @InjectMocks
    private OAIRecordServiceBean oaiRecordServiceBean;

    @Mock
    private EntityManager entityManager;

    @Test
    public void updateOaiRecordForDataset_ForUpdatedGuestbook() {
        //given
        Clock testClock = Clock.fixed(Instant.ofEpochMilli(12345678), ZoneId.of("UTC"));
        oaiRecordServiceBean.setSystemClock(testClock);

        Dataset dataset = setupDatasetData();

        HashMap<String, OAIRecord> oaiRecords = new HashMap<>();
        OAIRecord oaiRecord = setupOaiRecord(oaiRecords);

        //when
        oaiRecordServiceBean.updateOaiRecordForDataset(dataset,"setName", oaiRecords, Logger.getGlobal());

        //then
        Assert.assertEquals(testClock.instant(), oaiRecord.getLastUpdateTime().toInstant());
        Assert.assertEquals(0, oaiRecords.size());

    }

    @Test
    public void updateOaiRecordForDataset_ForUpdatedRealeseTime() {
        //given
        Clock testClock = Clock.fixed(Instant.ofEpochMilli(12345678), ZoneId.of("UTC"));
        oaiRecordServiceBean.setSystemClock(testClock);

        Dataset dataset = setupDatasetData();
        DatasetVersion releasedVersion = dataset.getReleasedVersion();
        dataset.setGuestbookChangeTime(null);
        releasedVersion.setReleaseTime(Date.from(Instant.ofEpochMilli(123456799)));

        HashMap<String, OAIRecord> oaiRecords = new HashMap<>();
        OAIRecord oaiRecord = setupOaiRecord(oaiRecords);

        //when
        oaiRecordServiceBean.updateOaiRecordForDataset(dataset,"setName", oaiRecords, Logger.getGlobal());

        //then
        Assert.assertEquals(testClock.instant(), oaiRecord.getLastUpdateTime().toInstant());
        Assert.assertEquals(0, oaiRecords.size());

    }

    @Test
    public void updateOaiRecordForDataset_WithoutUpdates() {
        //given
        Clock testClock = Clock.fixed(Instant.ofEpochMilli(12345678), ZoneId.of("UTC"));
        oaiRecordServiceBean.setSystemClock(testClock);

        Dataset dataset = setupDatasetData();
        DatasetVersion releasedVersion = dataset.getReleasedVersion();
        dataset.setGuestbookChangeTime(null);
        releasedVersion.setReleaseTime(Date.from(Instant.ofEpochMilli(123)));

        HashMap<String, OAIRecord> oaiRecords = new HashMap<>();
        OAIRecord oaiRecord = setupOaiRecord(oaiRecords);
        oaiRecord.setRemoved(false);

        //when
        oaiRecordServiceBean.updateOaiRecordForDataset(dataset,"setName", oaiRecords, Logger.getGlobal());

        //then
        Assert.assertEquals(Instant.ofEpochMilli(123456), oaiRecord.getLastUpdateTime().toInstant());
        Assert.assertEquals(0, oaiRecords.size());

    }

    @Test
    public void updateOaiRecordForDataset_ForRemovedRecord() {
        //given
        Clock testClock = Clock.fixed(Instant.ofEpochMilli(12345678), ZoneId.of("UTC"));
        oaiRecordServiceBean.setSystemClock(testClock);

        Dataset dataset = setupDatasetData();

        HashMap<String, OAIRecord> oaiRecords = new HashMap<>();
        OAIRecord oaiRecord = setupOaiRecord(oaiRecords);

        //when
        oaiRecordServiceBean.updateOaiRecordForDataset(dataset,"setName", oaiRecords, Logger.getGlobal());

        //then
        Assert.assertEquals(testClock.instant(), oaiRecord.getLastUpdateTime().toInstant());
        Assert.assertEquals(0, oaiRecords.size());

    }

    @Test
    public void updateOaiRecordForDataset_ForNullRecord() {
        //given
        Clock testClock = Clock.fixed(Instant.ofEpochMilli(12345678), ZoneId.of("UTC"));
        oaiRecordServiceBean.setSystemClock(testClock);

        Dataset dataset = setupDatasetData();

        OAIRecord oaiRecord = new OAIRecord();

        String setName = "setName";

        //when
        OAIRecord persistedRecord = oaiRecordServiceBean.updateOaiRecordForDataset(dataset,
                                                                              setName,
                                                                              new HashMap<>(),
                                                                              Logger.getGlobal());

        //then
        Assert.assertEquals(testClock.instant(), persistedRecord.getLastUpdateTime().toInstant());
        Assert.assertEquals(setName, persistedRecord.getSetName());
        Assert.assertEquals("doi:nice/ID1", persistedRecord.getGlobalId());

    }

    // -------------------- PRIVATE --------------------

    private OAIRecord setupOaiRecord(HashMap<String, OAIRecord> oaiRecords) {
        OAIRecord oaiRecord = new OAIRecord();
        oaiRecord.setGlobalId("doi:nice/ID1");
        oaiRecord.setRemoved(true);
        oaiRecord.setLastUpdateTime(Date.from(Instant.ofEpochMilli(123456)));
        oaiRecords.put("doi:nice/ID1", oaiRecord);
        return oaiRecord;
    }

    private Dataset setupDatasetData() {
        Dataset dataset = new Dataset();
        DatasetVersion datasetVersion = new DatasetVersion();
        datasetVersion.setReleaseTime(Date.from(Instant.ofEpochMilli(1234)));
        datasetVersion.setVersionState(DatasetVersion.VersionState.RELEASED);
        dataset.setVersions(Lists.newArrayList(datasetVersion));
        dataset.setGuestbookChangeTime(Date.from(Instant.ofEpochMilli(1234567)));
        dataset.setGlobalId(new GlobalId("doi", "nice", "ID1"));
        return dataset;
    }
}