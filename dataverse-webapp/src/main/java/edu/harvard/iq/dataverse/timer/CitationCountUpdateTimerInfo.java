package edu.harvard.iq.dataverse.timer;

import java.io.Serializable;

public class CitationCountUpdateTimerInfo implements Serializable {

    String serverId;

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public CitationCountUpdateTimerInfo() {

    }

    public CitationCountUpdateTimerInfo(String serverId) {
        this.serverId = serverId;
    }
}
