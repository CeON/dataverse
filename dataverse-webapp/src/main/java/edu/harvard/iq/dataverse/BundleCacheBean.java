package edu.harvard.iq.dataverse;

import javax.ejb.Singleton;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The purpose of this bean is to have one instance of cache
 * of internal ResourceBundles for entire application.
 * It's strongly recommended to
 */
@Singleton
public class BundleCacheBean {
    private static final ConcurrentMap<String, ResourceBundle> cache = new ConcurrentHashMap<>();

    // -------------------- LOGIC --------------------

    public ConcurrentMap<String, ResourceBundle> acquireCache() {
        return cache;
    }
}
