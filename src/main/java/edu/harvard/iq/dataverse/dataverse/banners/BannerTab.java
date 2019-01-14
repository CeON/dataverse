package edu.harvard.iq.dataverse.dataverse.banners;

import org.apache.commons.lang.StringUtils;

import javax.ejb.EJB;
import javax.faces.view.ViewScoped;
import javax.inject.Named;
import java.io.Serializable;

@ViewScoped
@Named("BannerTab")
public class BannerTab implements Serializable {

    private long dataverseId;

    @EJB
    private LazyBannerHistory lazyBannerHistory;

    @EJB
    private BannerDAO bannerDAO;

    public String init() {
        lazyBannerHistory.setDataverseId(dataverseId);

        return StringUtils.EMPTY;
    }

    public String newBannerPage() {
        return "/dataverse-editBanner.xhtml?dataverseId=" + dataverseId + "&faces-redirect=true";
    }

    public String reuseBanner(String bannerId) {
        return "/dataverse-editBanner.xhtml?dataverseId=" + dataverseId +
                "&id=" + bannerId + "&faces-redirect=true";
    }

    public long getDataverseId() {
        return dataverseId;
    }

    public void setDataverseId(long dataverseId) {
        this.dataverseId = dataverseId;
    }

    public LazyBannerHistory getLazyBannerHistory() {
        return lazyBannerHistory;
    }

    public void setLazyBannerHistory(LazyBannerHistory lazyBannerHistory) {
        this.lazyBannerHistory = lazyBannerHistory;
    }

    public BannerDAO getBannerDAO() {
        return bannerDAO;
    }
}
