package edu.harvard.iq.dataverse.dataverse.validation;

import edu.harvard.iq.dataverse.dataverse.banners.BannerLimits;
import edu.harvard.iq.dataverse.dataverse.banners.DataverseBanner;
import edu.harvard.iq.dataverse.dataverse.banners.DataverseLocalizedBanner;
import edu.harvard.iq.dataverse.locale.DataverseLocaleBean;
import edu.harvard.iq.dataverse.util.DataverseClock;
import edu.harvard.iq.dataverse.util.DateUtil;

import javax.ejb.Stateless;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.inject.Inject;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;

@Stateless
public class BannerErrorHandler {

    @Inject
    private DataverseLocaleBean localeBean;

    public void handleBannerAddingErrors(DataverseBanner banner,
                                         DataverseLocalizedBanner dlb,
                                         FacesContext faceContext) {

        if (dlb.getImage().length < 1) {
            throwImageWasMissing(dlb, faceContext);
        } else if (ImageValidator.isImageResolutionTooBig(dlb.getImage(), 9999, 9999)) {
            throwResolutionTooBigError(dlb, faceContext);
        }

        if (dlb.getImage().length > BannerLimits.MAX_SIZE_IN_BYTES.getValue()) {
            throwSizeTooBigError(dlb, faceContext);
        }

        validateEndDate(banner.getFromTime(), banner.getToTime(), faceContext);
    }

    private void throwResolutionTooBigError(DataverseLocalizedBanner dlb, FacesContext faceContext) {

        Optional<FacesMessage> resolutionMsg = findMessageWithContent(faceContext, "The Resolution was too big for");

        if (resolutionMsg.isPresent()) {
            resolutionMsg.get().setDetail(resolutionMsg.get().getDetail()
                    .concat(", " + localeBean.getLanguage(dlb.getLocale())));
        } else {
            faceContext.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error!",
                    "The Resolution was too big for: " + localeBean.getLanguage(dlb.getLocale())));
        }
    }

    private void throwSizeTooBigError(DataverseLocalizedBanner dlb, FacesContext faceContext) {

        Optional<FacesMessage> resolutionMsg = findMessageWithContent(faceContext, "The image size was too big for");

        if (resolutionMsg.isPresent()) {
            resolutionMsg.get().setDetail(resolutionMsg.get().getDetail()
                    .concat(", " + localeBean.getLanguage(dlb.getLocale())));
        } else {
            faceContext.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error!",
                    "The image size was too big for: " + localeBean.getLanguage(dlb.getLocale())));
        }
    }

    private void throwImageWasMissing(DataverseLocalizedBanner dlb, FacesContext faceContext) {

        Optional<FacesMessage> resolutionMsg = findMessageWithContent(faceContext, "The image is missing ");

        if (resolutionMsg.isPresent()) {
            resolutionMsg.get().setDetail(resolutionMsg.get().getDetail()
                    .concat(", " + localeBean.getLanguage(dlb.getLocale())));
        } else {
            faceContext.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error!",
                    "The image was missing for: " + localeBean.getLanguage(dlb.getLocale())));
        }
    }

    private void validateEndDate(Date fromTime, Date toTime, FacesContext faceContext) {
        if (fromTime == null || toTime == null) {
            return;
        }
        if (toTime.before(fromTime) && !findMessageWithContent(faceContext, "End date must not be earlier").isPresent()) {
            faceContext.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error!",
                    "End date must not be earlier than starting date!"));
        }
        LocalDateTime now = DataverseClock.now();
        if (!toTime.after(DateUtil.convertToDate(now)) && !findMessageWithContent(faceContext, "End date must be a future date").isPresent()) {
            faceContext.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error!",
                    "End date must be a future date!"));
        }
    }

    private Optional<FacesMessage> findMessageWithContent(FacesContext faceContext, String message) {
        return faceContext.getMessageList().stream()
                .filter(facesMessage -> facesMessage.getDetail().contains(message))
                .findFirst();
    }
}
