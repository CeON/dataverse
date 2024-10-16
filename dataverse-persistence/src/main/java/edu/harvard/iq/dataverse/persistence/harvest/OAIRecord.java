/*
    Copyright (C) 2005-2012, by the President and Fellows of Harvard College.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

   Dataverse Network - A web application to share, preserve and analyze research data.
   Developed at the Institute for Quantitative Social Science, Harvard University.
   Version 3.0.
 */
package edu.harvard.iq.dataverse.persistence.harvest;

import edu.harvard.iq.dataverse.persistence.JpaEntity;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import java.io.Serializable;
import java.util.Date;

/**
 * @author Leonid Andreev
 * based on the DVN implementation of "HarvestStudy" by
 * @author Gustavo Durand
 */
@Entity
public class OAIRecord implements Serializable, JpaEntity<Long> {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String setName;

    private String globalId;

    @Temporal(value = TemporalType.TIMESTAMP)
    private Date lastUpdateTime;

    private boolean removed;

    // -------------------- CONSTRUCTORS --------------------

    protected OAIRecord() {
    }

    public OAIRecord(String setName, String globalId, Date lastUpdateTime) {
        this.setName = setName;
        this.globalId = globalId;
        this.lastUpdateTime = lastUpdateTime;
    }

    // -------------------- GETTERS --------------------

    public Long getId() {
        return id;
    }

    public String getSetName() {
        return setName;
    }

    public String getGlobalId() {
        return globalId;
    }

    public Date getLastUpdateTime() {
        return lastUpdateTime;
    }

    public boolean isRemoved() {
        return removed;
    }

    // -------------------- SETTERS --------------------

    public void setSetName(String setName) {
        this.setName = setName;
    }

    public void setGlobalId(String globalId) {
        this.globalId = globalId;
    }

    public void setLastUpdateTime(Date lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    public void setRemoved(boolean removed) {
        this.removed = removed;
    }

    // -------------------- hashCode & equals --------------------

    @Override
    public int hashCode() {
        int hash = 0;
        hash += (id != null ? id.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        // TODO: Warning - this method won't work in the case the id fields are not set
        if (!(object instanceof OAIRecord)) {
            return false;
        }
        OAIRecord other = (OAIRecord) object;
        return (this.id != null || other.id == null) && (this.id == null || this.id.equals(other.id));
    }

    // -------------------- toString --------------------

    @Override
    public String toString() {
        return "OAIRecord[ id=" + id + " ]";
    }

}
