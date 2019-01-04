package edu.harvard.iq.dataverse.dataverse.messages.dto;

import com.google.common.collect.Lists;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public class DataverseTextMessageDto {

    private Long id;

    private boolean active;

    private LocalDateTime fromTime;

    private LocalDateTime toTime;

    private Long dataverseId;

    private List<DataverseLocalizedMessageDto> dataverseLocalizedMessage = Lists.newArrayList();

    public boolean isActive() {
        return active;
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getFromTime() {
        return fromTime;
    }

    public LocalDateTime getToTime() {
        return toTime;
    }

    public List<DataverseLocalizedMessageDto> getDataverseLocalizedMessage() {
        return dataverseLocalizedMessage;
    }

    public Long getDataverseId() {
        return dataverseId;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setFromTime(LocalDateTime fromTime) {
        this.fromTime = fromTime;
    }

    public void setToTime(LocalDateTime toTime) {
        this.toTime = toTime;
    }

    public void setDataverseLocalizedMessage(List<DataverseLocalizedMessageDto> dataverseLocalizedMessage) {
        this.dataverseLocalizedMessage = dataverseLocalizedMessage;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setDataverseId(Long dataverseId) {
        this.dataverseId = dataverseId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataverseTextMessageDto dto = (DataverseTextMessageDto) o;
        return active == dto.active &&
                Objects.equals(id, dto.id) &&
                Objects.equals(fromTime, dto.fromTime) &&
                Objects.equals(toTime, dto.toTime) &&
                Objects.equals(dataverseId, dto.dataverseId) &&
                Objects.equals(dataverseLocalizedMessage, dto.dataverseLocalizedMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, active, fromTime, toTime, dataverseId, dataverseLocalizedMessage);
    }
}
