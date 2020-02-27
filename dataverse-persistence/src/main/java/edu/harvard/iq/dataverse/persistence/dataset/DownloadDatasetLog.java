package edu.harvard.iq.dataverse.persistence.dataset;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public class DownloadDatasetLog {
    @Id
    @Column(name = "dataset_id")
    private Long datasetId;

    private Integer count;

    public Long getDatasetId() {
        return datasetId;
    }

    public Integer getCount() {
        return count;
    }

    public void setDatasetId(Long datasetId) {
        this.datasetId = datasetId;
    }

    public void setCount(Integer count) {
        this.count = count;
    }
}
