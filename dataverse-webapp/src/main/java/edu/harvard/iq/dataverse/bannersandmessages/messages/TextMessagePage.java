package edu.harvard.iq.dataverse.bannersandmessages.messages;

import edu.harvard.iq.dataverse.DataverseDao;
import edu.harvard.iq.dataverse.PermissionsWrapper;
import edu.harvard.iq.dataverse.bannersandmessages.messages.dto.DataverseTextMessageDto;
import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.util.JsfHelper;
import org.apache.commons.lang.StringUtils;
import org.omnifaces.util.Components;
import org.primefaces.component.datalist.DataList;
import org.primefaces.component.tabview.Tab;
import org.primefaces.component.tabview.TabView;
import org.primefaces.model.LazyDataModel;

import javax.ejb.EJB;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;

@ViewScoped
@Named("TextMessagePage")
public class TextMessagePage implements Serializable {

    private long dataverseId;
    private Dataverse dataverse;
    private DataverseTextMessageDto textMessageToDelete;

    @EJB
    private DataverseDao dataverseDao;

    @EJB
    private LazyDataverseTextMessage lazydataverseTextMessages;

    @EJB
    private DataverseTextMessageServiceBean textMessageService;

    @Inject
    private PermissionsWrapper permissionsWrapper;

    public String init() {
        lazydataverseTextMessages.setDataverseId(dataverseId);
        dataverse = dataverseDao.find(dataverseId);

        if (!permissionsWrapper.canEditDataverseTextMessagesAndBanners(dataverseId)) {
            return permissionsWrapper.notAuthorized();
        }

        return StringUtils.EMPTY;
    }

    public String newTextMessagePage() {
        return "/dataverse-editTextMessages.xhtml?dataverseId=" + dataverseId + "&faces-redirect=true";
    }

    public String reuseTextMessage(String textMessageId) {
        return "/dataverse-editTextMessages.xhtml?dataverseId=" + dataverseId +
                "&id=" + textMessageId + "&faces-redirect=true";
    }

    public void deleteTextMessage() {
        textMessageService.delete(textMessageToDelete.getId());
        JsfHelper.addSuccessMessage(BundleUtil.getStringFromBundle("dataversemessages.textmessages.delete.success"));
        
        Long allMessagesCount = textMessageService.countMessagesForDataverse(dataverseId);
        
        TabView tabView = Components.findComponentsInChildren(Components.getCurrentForm(), TabView.class).get(0);
        Tab textMessagesTab = Components.findComponentsInChildren(tabView, Tab.class).get(0);

        DataList dataListComponent = Components.findComponentsInChildren(textMessagesTab, DataList.class).get(0);
        if (dataListComponent.getFirst() >= allMessagesCount && dataListComponent.getFirst() >= dataListComponent.getRows()) {
            dataListComponent.setFirst(dataListComponent.getFirst() - dataListComponent.getRows());
        }
        lazydataverseTextMessages.setRowCount(allMessagesCount.intValue());
        
    }
    
    public void deactivateTextMessage(long textMessageId) {
        textMessageService.deactivate(textMessageId);
        JsfHelper.addSuccessMessage(BundleUtil.getStringFromBundle("dataversemessages.textmessages.deactivate.success"));
    }
    
    public long getDataverseId() {
        return dataverseId;
    }

    public Dataverse getDataverse() {
        return dataverse;
    }

    public LazyDataModel<DataverseTextMessageDto> getLazydataverseTextMessages() {
        return lazydataverseTextMessages;
    }

    public DataverseTextMessageServiceBean getTextMessageService() {
        return textMessageService;
    }

    public DataverseTextMessageDto getTextMessageToDelete() {
        return textMessageToDelete;
    }

    public void setDataverseId(long dataverseId) {
        this.dataverseId = dataverseId;
    }

    public void setLazydataverseTextMessages(LazyDataverseTextMessage lazydataverseTextMessages) {
        this.lazydataverseTextMessages = lazydataverseTextMessages;
    }

    public void setTextMessageToDelete(DataverseTextMessageDto textMessageToDelete) {
        this.textMessageToDelete = textMessageToDelete;
    }
}
