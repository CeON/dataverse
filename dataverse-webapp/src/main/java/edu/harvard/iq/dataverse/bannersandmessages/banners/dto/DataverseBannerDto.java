package edu.harvard.iq.dataverse.bannersandmessages.banners.dto;

import edu.harvard.iq.dataverse.common.DateUtil;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public class DataverseBannerDto {

    private Long id;

    @NotNull(message = "{field.required}")
    private LocalDateTime fromTime;

    @NotNull(message = "{field.required}")
    private LocalDateTime toTime;

    private boolean active;

    private Long dataverseId;

    @Valid
    private List<DataverseLocalizedBannerDto> dataverseLocalizedBanner;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getFromTime() {
        return fromTime;
    }

    public void setFromTime(LocalDateTime fromTime) {
        this.fromTime = fromTime;
    }

    public LocalDateTime getToTime() {
        return toTime;
    }

    public String getPrettyFromDate() {

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        return DateUtil.formatDate(DateUtil.convertToDate(fromTime), format);
    }

    public String getPrettyToTime() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        return DateUtil.formatDate(DateUtil.convertToDate(toTime), format);
    }

    public void setToTime(LocalDateTime toTime) {
        this.toTime = toTime;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Long getDataverseId() {
        return dataverseId;
    }

    public void setDataverseId(Long dataverseId) {
        this.dataverseId = dataverseId;
    }

    public List<DataverseLocalizedBannerDto> getDataverseLocalizedBanner() {
        return dataverseLocalizedBanner;
    }

    public void setDataverseLocalizedBanner(List<DataverseLocalizedBannerDto> dataverseLocalizedBanner) {
        this.dataverseLocalizedBanner = dataverseLocalizedBanner;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DataverseBannerDto that = (DataverseBannerDto) o;
        return active == that.active &&
                Objects.equals(id, that.id) &&
                Objects.equals(fromTime, that.fromTime) &&
                Objects.equals(toTime, that.toTime) &&
                Objects.equals(dataverseId, that.dataverseId) &&
                Objects.equals(dataverseLocalizedBanner, that.dataverseLocalizedBanner);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, fromTime, toTime, active, dataverseId, dataverseLocalizedBanner);
    }
}
