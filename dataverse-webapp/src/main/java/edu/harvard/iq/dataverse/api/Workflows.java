package edu.harvard.iq.dataverse.api;

import edu.harvard.iq.dataverse.persistence.group.IpAddress;
import edu.harvard.iq.dataverse.persistence.group.IpAddressRange;
import edu.harvard.iq.dataverse.persistence.group.IpGroup;
import edu.harvard.iq.dataverse.workflow.execution.WorkflowExecutionServiceBean;

import javax.ejb.EJB;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * API Endpoint for external systems to report the results of workflow step
 * execution. Pending workflows wait for external systems to post a result on this endpoint.
 *
 * @author michael
 */
@Path("workflows")
public class Workflows extends AbstractApiBean {

    @EJB
    WorkflowExecutionServiceBean workflowExecutions;

    private IpGroup whitelist = new IpGroup();
    private long lastWhitelistUpdate = 0;

    @Path("{invocationId}")
    @POST
    public Response resumeWorkflow(@PathParam("invocationId") String invocationId, String body) {
        String remoteAddrStr = httpRequest.getRemoteAddr();
        IpAddress remoteAddr = IpAddress.valueOf((remoteAddrStr != null) ? remoteAddrStr : "0.0.0.0");
        if (!isAllowed(remoteAddr)) {
            return unauthorized("Sorry, your IP address is not authorized to send resume requests. Please contact an admin.");
        }
        Logger.getLogger(Workflows.class.getName()).log(Level.INFO, "Resume request from: {0}", httpRequest.getRemoteAddr());

        return workflowExecutions.resume(invocationId, body).map(execution -> {
            return Response.accepted("/api/datasets/" + execution.getDatasetId()).build();
        }).orElseGet(() ->
                notFound("Cannot find workflow invocation with id " + invocationId)
        );
    }

    private boolean isAllowed(IpAddress addr) {
        if (System.currentTimeMillis() - lastWhitelistUpdate > 60 * 1000) {
            updateWhitelist();
        }
        return whitelist.containsAddress(addr);
    }

    private void updateWhitelist() {
        IpGroup updatedList = new IpGroup();
        String[] ips = settingsSvc.get(WorkflowsAdmin.IP_WHITELIST_KEY).split(";");
        Arrays.stream(ips)
                .forEach(str -> updatedList.add(
                        IpAddressRange.makeSingle(
                                IpAddress.valueOf(str))));
        whitelist = updatedList;
        lastWhitelistUpdate = System.currentTimeMillis();
    }
}
