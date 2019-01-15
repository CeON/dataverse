package edu.harvard.iq.dataverse.dataverse.messages;

import edu.harvard.iq.dataverse.Dataverse;
import edu.harvard.iq.dataverse.DataverseServiceBean;
import edu.harvard.iq.dataverse.PermissionsWrapper;
import edu.harvard.iq.dataverse.dataverse.messages.dto.DataverseTextMessageDto;
import edu.harvard.iq.dataverse.dataverse.validation.EndDateMustBeAFutureDate;
import edu.harvard.iq.dataverse.dataverse.validation.EndDateMustNotBeEarlierThanStartingDate;
import edu.harvard.iq.dataverse.util.JsfValidationHelper;
import edu.harvard.iq.dataverse.util.JsfValidationHelper.ValidationCondition;

import javax.ejb.EJB;
import javax.faces.component.UIInput;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;

import static edu.harvard.iq.dataverse.util.JsfValidationHelper.ValidationCondition.on;

@ViewScoped
@Named("EditTextMessagePage")
public class EditTextMessagePage implements Serializable {

    @Inject
    private DataverseTextMessageServiceBean textMessageService;

    @Inject
    PermissionsWrapper permissionsWrapper;

    @EJB
    private DataverseServiceBean dataverseServiceBean;

    private Long dataverseId;
    private Dataverse dataverse;
    private Long textMessageId;

    private DataverseTextMessageDto dto;

    private UIInput fromTimeInput;
    private UIInput toTimeInput;

    public String init() {
        if (!permissionsWrapper.canIssueEditDataverseTextMessages(dataverseId)) {
            return permissionsWrapper.notAuthorized();
        }

        if (dataverseId == null) {
            return permissionsWrapper.notFound();
        }

        dataverse = dataverseServiceBean.find(dataverseId);

        if (textMessageId != null) {
            dto = textMessageService.getTextMessage(textMessageId);
        } else {
            dto = textMessageService.newTextMessage(dataverseId);
        }
        if (!dto.getDataverseId().equals(dataverseId)) {
            return permissionsWrapper.notAuthorized();
        }
        return null;
    }

    public String save() {
        return JsfValidationHelper.execute(() -> {
            textMessageService.save(dto);
            return redirectToTextMessages();
        }, endDateMustNotBeEarlierThanStartingDate(), endDateMustBeAFutureDate());
    }

    public String cancel() {
        return redirectToTextMessages();
    }

    private String redirectToTextMessages() {
        return "/dataverse-textMessages.xhtml?dataverseId=" + dataverseId + "&faces-redirect=true";
    }

    private ValidationCondition endDateMustNotBeEarlierThanStartingDate() {
        return on(EndDateMustNotBeEarlierThanStartingDate.class, toTimeInput.getClientId(), "textmessages.enddate.valid");
    }

    private ValidationCondition endDateMustBeAFutureDate() {
        return on(EndDateMustBeAFutureDate.class, toTimeInput.getClientId(), "textmessages.enddate.future");
    }

    public Long getDataverseId() {
        return dataverseId;
    }

    public void setDataverseId(Long dataverseId) {
        this.dataverseId = dataverseId;
    }

    public Long getTextMessageId() {
        return textMessageId;
    }

    public Dataverse getDataverse() {
        return dataverse;
    }

    public void setTextMessageId(Long textMessageId) {
        this.textMessageId = textMessageId;
    }

    public DataverseTextMessageDto getDto() {
        return dto;
    }

    public void setDto(DataverseTextMessageDto dto) {
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
