/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.harvard.iq.dataverse.engine.command.impl;

import edu.harvard.iq.dataverse.engine.command.AbstractVoidCommand;
import edu.harvard.iq.dataverse.engine.command.CommandContext;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.engine.command.RequiredPermissions;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.guestbook.GuestbookResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.Date;

/**
 * @author skraffmiller
 */
@RequiredPermissions({})
public class CreateGuestbookResponseCommand extends AbstractVoidCommand {
    private static final Logger log = LoggerFactory.getLogger(CreateGuestbookResponseCommand.class);

    private final GuestbookResponse response;

    public CreateGuestbookResponseCommand(DataverseRequest aRequest, GuestbookResponse responseIn, Dataset affectedDataset) {
        super(aRequest, affectedDataset);
        response = responseIn;
    }

    @Override
    protected void executeImpl(CommandContext ctxt) {
        Timestamp createDate = new Timestamp(new Date().getTime());
        response.setResponseTime(createDate);

        ctxt.responses().save(response);
    }

}
