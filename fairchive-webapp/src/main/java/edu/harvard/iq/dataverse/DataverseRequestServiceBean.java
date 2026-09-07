package edu.harvard.iq.dataverse;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;

import edu.harvard.iq.dataverse.engine.command.DataverseRequest;

/**
 * The service bean to go to when one needs the current {@link DataverseRequest}.
 *
 * @author michael
 */
@Named
@RequestScoped
public class DataverseRequestServiceBean {

    private DataverseSession session;
    private HttpServletRequest request;
    
    public DataverseRequestServiceBean() {}
    
    @Inject
    public DataverseRequestServiceBean(final DataverseSession session, 
    								   final HttpServletRequest request) {
		this.session = session;
		this.request = request;
	}

    public DataverseRequest getDataverseRequest() {
    	return new DataverseRequest(this.session.getUser(), this.request);
    }
}
