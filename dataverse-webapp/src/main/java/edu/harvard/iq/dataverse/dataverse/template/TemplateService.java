package edu.harvard.iq.dataverse.dataverse.template;

import edu.harvard.iq.dataverse.DataverseRequestServiceBean;
import edu.harvard.iq.dataverse.EjbDataverseEngine;
import edu.harvard.iq.dataverse.common.BundleUtil;
import edu.harvard.iq.dataverse.engine.command.impl.CreateTemplateCommand;
import edu.harvard.iq.dataverse.engine.command.impl.DeleteTemplateCommand;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDataverseCommand;
import edu.harvard.iq.dataverse.persistence.dataset.Template;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import io.vavr.control.Try;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.sql.Timestamp;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

@Stateless
public class TemplateService {

    private static final Logger logger = Logger.getLogger(TemplateService.class.getCanonicalName());

    private EjbDataverseEngine engineService;
    private DataverseRequestServiceBean dvRequestService;
    private TemplateDao templateDao;

    // -------------------- CONSTRUCTORS --------------------

    @Deprecated
    public TemplateService() {
    }

    @Inject
    public TemplateService(EjbDataverseEngine engineService, DataverseRequestServiceBean dvRequestService,
                           TemplateDao templateDao) {
        this.engineService = engineService;
        this.dvRequestService = dvRequestService;
        this.templateDao = templateDao;
    }

    // -------------------- LOGIC --------------------

    public Try<Dataverse> saveDataverse(Dataverse dataverse) {
        return Try.of(() -> engineService.submit(new UpdateDataverseCommand(dataverse, null, null,
                                                                            dvRequestService.getDataverseRequest(), null)));
    }

    public Try<Dataverse> deleteTemplate(Dataverse dataverse, Template templateToDelete) {


        if (dataverse.getDefaultTemplate() != null && dataverse.getDefaultTemplate().equals(templateToDelete)) {
            dataverse.setDefaultTemplate(null);
        }

        dataverse.getTemplates().remove(templateToDelete);

        return Try.of(() -> engineService.submit(new DeleteTemplateCommand(dvRequestService.getDataverseRequest(),
                                                                           dataverse,
                                                                           templateToDelete,
                                                                           templateDao.findDataversesByDefaultTemplateId(templateToDelete.getId()))))
                .onFailure(throwable -> logger.log(Level.SEVERE, throwable.getMessage(), throwable));
    }

    public boolean cloneTemplate(Template templateIn, Dataverse dataverse) {
        Template newTemplate = templateIn.cloneNewTemplate(templateIn);

        newTemplate.setName(BundleUtil.getStringFromBundle("page.copy") + " " + templateIn.getName());
        newTemplate.setUsageCount(0L);
        newTemplate.setCreateTime(new Timestamp(new Date().getTime()));
        dataverse.getTemplates().add(newTemplate);

        boolean isCreateTemplateFailure = Try.of(() -> engineService.submit(new CreateTemplateCommand(newTemplate, dvRequestService.getDataverseRequest(), dataverse)))
                .onFailure(throwable -> logger.log(Level.SEVERE, throwable.getMessage(), throwable))
                .isFailure();

        boolean isSaveDataverseFailure = saveDataverse(dataverse)
                .onFailure(throwable -> logger.log(Level.SEVERE, throwable.getMessage(), throwable))
                .isFailure();

        return isCreateTemplateFailure || isSaveDataverseFailure;
    }
}
