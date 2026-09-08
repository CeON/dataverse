package edu.harvard.iq.dataverse;

import edu.harvard.iq.dataverse.engine.command.DataverseRequest;

import javax.annotation.PostConstruct;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;

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

    private DataverseRequest dataverseRequest;
    
    public DataverseRequestServiceBean() {}
    
    @Inject
    public DataverseRequestServiceBean(final DataverseSession session, 
    								   final HttpServletRequest request) {
		this.session = session;
		this.request = request;
	}

	@PostConstruct
    public void setup() {
        this.dataverseRequest = new DataverseRequest(this.session.getUser(), 
        		this.request);
    }

    public DataverseRequest getDataverseRequest() {
        return this.dataverseRequest;
    }

}
