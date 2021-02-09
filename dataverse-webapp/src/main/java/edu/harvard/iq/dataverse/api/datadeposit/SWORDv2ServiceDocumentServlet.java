package edu.harvard.iq.dataverse.api.datadeposit;

import org.swordapp.server.ServiceDocumentAPI;
import org.swordapp.server.servlets.SwordServlet;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class SWORDv2ServiceDocumentServlet extends SwordServlet {

    @Inject
    private ServiceDocumentManagerImpl serviceDocumentManagerImpl;

    @Inject
    private SwordConfigurationFactory swordConfigurationFactory;


    protected ServiceDocumentAPI api;

    @Override
    public void init() throws ServletException {
        super.init();
        this.api = new ServiceDocumentAPI(serviceDocumentManagerImpl, swordConfigurationFactory.createSwordConfiguration());
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.api.get(req, resp);
    }

}
