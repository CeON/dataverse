package edu.harvard.iq.dataverse.api.datadeposit;

import org.swordapp.server.CollectionAPI;
import org.swordapp.server.servlets.SwordServlet;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

public class SWORDv2CollectionServlet extends SwordServlet {

    @Inject
    private CollectionDepositManagerImpl collectionDepositManagerImpl;
    @Inject
    private CollectionListManagerImpl collectionListManagerImpl;
    @Inject
    private SwordConfigurationFactory swordConfigurationFactory;

    protected CollectionAPI api;

    private final ReentrantLock lock = new ReentrantLock();

    @Override
    public void init() throws ServletException {
        super.init();
        this.api = new CollectionAPI(collectionListManagerImpl, collectionDepositManagerImpl, swordConfigurationFactory.createSwordConfiguration());
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            lock.lock();
            setRequest(req);
            this.api.get(req, resp);
            setRequest(null);
        } finally {
            lock.unlock();
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            lock.lock();
            setRequest(req);
            this.api.post(req, resp);
            setRequest(null);
        } finally {
            lock.unlock();
        }
    }

    private void setRequest(HttpServletRequest r) {
        collectionDepositManagerImpl.setRequest(r);
        collectionListManagerImpl.setRequest(r);
    }
}
