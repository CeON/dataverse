package edu.harvard.iq.dataverse.dataverse.banners;

import edu.harvard.iq.dataverse.Dataverse;
import edu.harvard.iq.dataverse.DataverseServiceBean;
import edu.harvard.iq.dataverse.PermissionsWrapper;
import edu.harvard.iq.dataverse.dataverse.banners.dto.BannerMapper;
import edu.harvard.iq.dataverse.dataverse.banners.dto.DataverseBannerDto;
import edu.harvard.iq.dataverse.dataverse.validation.EndDateMustBeAFutureDate;
import edu.harvard.iq.dataverse.dataverse.validation.EndDateMustNotBeEarlierThanStartingDate;
import edu.harvard.iq.dataverse.util.JsfValidationHelper;
import org.apache.commons.lang.StringUtils;

import javax.ejb.EJB;
import javax.faces.component.UIInput;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;

import static edu.harvard.iq.dataverse.util.JsfValidationHelper.ValidationCondition.on;

@ViewScoped
@Named("EditBannerPage")
public class EditBannerPage {

    @EJB
    private BannerDAO dao;

    @Inject
    private PermissionsWrapper permissionsWrapper;

    @Inject
    private BannerMapper mapper;

    @EJB
    private DataverseServiceBean dataverseServiceBean;

    private Long dataverseId;
    private Dataverse dataverse;
    private Long bannerId;
    private UIInput fromTimeInput;
    private UIInput toTimeInput;

    private DataverseBannerDto dto;

    public String init() {
        if (!permissionsWrapper.canIssueEditDataverseTextMessages(dataverseId)) {
            return permissionsWrapper.notAuthorized();
        }

        if (dataverseId == null) {
            return permissionsWrapper.notFound();
        }

        dataverse = dataverseServiceBean.find(dataverseId);

        dto = bannerId != null ?
                mapper.mapToDto(dao.getTextMessage(bannerId)) :
                mapper.mapToNewBanner(dataverseId);

        if (!dto.getDataverseId().equals(dataverseId)) {
            return permissionsWrapper.notAuthorized();
        }

        return StringUtils.EMPTY;
    }

    public String save() {
        return JsfValidationHelper.execute(() -> {
            dao.save(dto);
            return redirectToTextMessages();
        }, endDateMustNotBeEarlierThanStartingDate(), endDateMustBeAFutureDate());
    }

    public String cancel() {
        return redirectToTextMessages();
    }

    private String redirectToTextMessages() {
        return "/dataverse-textMessages.xhtml?dataverseId=" + dataverseId + "&faces-redirect=true";
    }

    private JsfValidationHelper.ValidationCondition endDateMustNotBeEarlierThanStartingDate() {
        return on(EndDateMustNotBeEarlierThanStartingDate.class, toTimeInput.getClientId(), "textmessages.enddate.valid");
    }

    private JsfValidationHelper.ValidationCondition endDateMustBeAFutureDate() {
        return on(EndDateMustBeAFutureDate.class, toTimeInput.getClientId(), "textmessages.enddate.future");
    }

    public BannerDAO getDao() {
        return dao;
    }

    public void setDao(BannerDAO dao) {
        this.dao = dao;
    }

    public PermissionsWrapper getPermissionsWrapper() {
        return permissionsWrapper;
    }

    public void setPermissionsWrapper(PermissionsWrapper permissionsWrapper) {
        this.permissionsWrapper = permissionsWrapper;
    }

    public DataverseServiceBean getDataverseServiceBean() {
        return dataverseServiceBean;
    }

    public void setDataverseServiceBean(DataverseServiceBean dataverseServiceBean) {
        this.dataverseServiceBean = dataverseServiceBean;
    }

    public Long getDataverseId() {
        return dataverseId;
    }

    public void setDataverseId(Long dataverseId) {
        this.dataverseId = dataverseId;
    }

    public Dataverse getDataverse() {
        return dataverse;
    }

    public void setDataverse(Dataverse dataverse) {
        this.dataverse = dataverse;
    }

    public Long getBannerId() {
        return bannerId;
    }

    public void setBannerId(Long bannerId) {
        this.bannerId = bannerId;
    }

    public DataverseBannerDto getDto() {
        return dto;
    }

    public void setDto(DataverseBannerDto dto) {
        this.dto = dto;
    }

    public UIInput getFromTimeInput() {
        return fromTimeInput;
    }

    public void setFromTimeInput(UIInput fromTimeInput) {
        this.fromTimeInput = fromTimeInput;
    }

    public UIInput getToTimeInput() {
        return toTimeInput;
    }

    public void setToTimeInput(UIInput toTimeInput) {
        this.toTimeInput = toTimeInput;
    }
}
