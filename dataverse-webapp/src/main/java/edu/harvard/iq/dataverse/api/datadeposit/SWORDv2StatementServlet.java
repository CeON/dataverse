package edu.harvard.iq.dataverse.api.datadeposit;

import org.swordapp.server.StatementAPI;
import org.swordapp.server.StatementManager;
import org.swordapp.server.servlets.SwordServlet;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class SWORDv2StatementServlet extends SwordServlet {

    @Inject
    private StatementManagerImpl statementManagerImpl;
    @Inject
    private SwordConfigurationFactory swordConfigurationFactory;
    
    private StatementManager sm;
    private StatementAPI statementApi;

    @Override
    public void init() throws ServletException {
        super.init();

        // load the container manager implementation
        this.sm = statementManagerImpl;

        // initialise the underlying servlet processor
        this.statementApi = new StatementAPI(this.sm, swordConfigurationFactory.createSwordConfiguration());
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.statementApi.get(req, resp);
    }

}
