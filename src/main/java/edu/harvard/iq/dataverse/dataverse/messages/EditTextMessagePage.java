package edu.harvard.iq.dataverse.dataverse.messages;

import edu.harvard.iq.dataverse.dataverse.messages.dto.DataverseTextMessageDto;
import edu.harvard.iq.dataverse.util.BundleUtil;

import javax.faces.application.FacesMessage;
import javax.faces.component.UIInput;
import javax.faces.context.FacesContext;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.validation.ValidationException;
import java.io.Serializable;

import static javax.faces.application.FacesMessage.SEVERITY_ERROR;

@ViewScoped
@Named("EditTextMessagePage")
public class EditTextMessagePage implements Serializable {

    @Inject
    private DataverseTextMessageServiceBean textMessageService;

    private Long dataverseId;
    private Long textMessageId;

    private DataverseTextMessageDto dto;

    private UIInput fromTimeInput;

    public void init() {
        if (dataverseId == null) {
            throw new IllegalArgumentException("DataverseId cannot be null!");
        }
        if (textMessageId != null) {
            dto = textMessageService.getTextMessage(textMessageId);
        } else {
            dto = textMessageService.newTextMessage(dataverseId);
        }
        if (!dto.getDataverseId().equals(dataverseId)) {
            throw new IllegalArgumentException("Text message is not from given dataverse!");
        }
    }

    public String save() {
        FacesContext.getCurrentInstance().addMessage(fromTimeInput.getClientId(),
                new FacesMessage(SEVERITY_ERROR, "", BundleUtil.getStringFromBundle("textmessages.enddate.valid")));
        return null;
//        textMessageService.save(dto);
//        return redirectToTextMessages();
    }

    public String cancel() {
        return redirectToTextMessages();
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

    private String redirectToTextMessages(){
        return "/dataverse-textMessages.xhtml?dataverseId=" + dataverseId + "&faces-redirect=true";
    }

}
