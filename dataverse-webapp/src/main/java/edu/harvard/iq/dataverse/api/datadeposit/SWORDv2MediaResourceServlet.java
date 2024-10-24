package edu.harvard.iq.dataverse.api.datadeposit;

import org.swordapp.server.MediaResourceAPI;
import org.swordapp.server.servlets.SwordServlet;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

public class SWORDv2MediaResourceServlet extends SwordServlet {

    @Inject
    private MediaResourceManagerImpl mediaResourceManagerImpl;

    @Inject
    private SwordConfigurationFactory swordConfigurationFactory;

    protected MediaResourceAPI api;

    private final ReentrantLock lock = new ReentrantLock();

    @Override
    public void init() throws ServletException {
        super.init();

        // load the api
        this.api = new MediaResourceAPI(mediaResourceManagerImpl, swordConfigurationFactory.createSwordConfiguration());
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            lock.lock();
            mediaResourceManagerImpl.setHttpRequest(req);
            this.api.get(req, resp);
            mediaResourceManagerImpl.setHttpRequest(null);
        } finally {
            lock.unlock();
        }
    }

    @Override
    protected void doHead(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            lock.lock();
            mediaResourceManagerImpl.setHttpRequest(req);
            this.api.head(req, resp);
            mediaResourceManagerImpl.setHttpRequest(null);
        } finally {
            lock.unlock();
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            lock.lock();
            mediaResourceManagerImpl.setHttpRequest(req);
            this.api.post(req, resp);
            mediaResourceManagerImpl.setHttpRequest(null);
        } finally {
            lock.unlock();
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            lock.lock();
            mediaResourceManagerImpl.setHttpRequest(req);
            this.api.put(req, resp);
            mediaResourceManagerImpl.setHttpRequest(null);
        } finally {
            lock.unlock();
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            lock.lock();
            mediaResourceManagerImpl.setHttpRequest(req);
            this.api.delete(req, resp);
            mediaResourceManagerImpl.setHttpRequest(null);
        } finally {
            lock.unlock();
        }
    }
}
