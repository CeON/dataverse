package edu.harvard.iq.dataverse.dataverse.messages;

import edu.harvard.iq.dataverse.dataverse.messages.dto.DataverseTextMessageDto;

import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;

@ViewScoped
@Named("EditTextMessagePage")
public class EditTextMessagePage implements Serializable {

    @Inject
    private DataverseTextMessageServiceBean textMessageService;

    private Long dataverseId;
    private Long textMessageId;

    private DataverseTextMessageDto dto;

    public void init() {
        if (dataverseId == null) {
            throw new IllegalArgumentException("DataverseId cannot be null!");
        }
        dto = (textMessageId != null) ?
                textMessageService.getTextMessage(textMessageId) :
                textMessageService.newTextMessage(dataverseId);
    }

    public String save() {
        return redirectToTextMessages();
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

    private String redirectToTextMessages(){
        return "/dataverse-textMessages.xhtml?dataverseId=" + dataverseId + "&faces-redirect=true";
    }

}
