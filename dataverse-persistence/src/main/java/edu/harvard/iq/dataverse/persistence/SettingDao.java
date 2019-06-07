package edu.harvard.iq.dataverse.persistence;

import java.util.List;

import javax.ejb.Stateless;
import javax.enterprise.context.ApplicationScoped;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

@Stateless
public class SettingDao {

    @PersistenceContext
    private EntityManager em;
    
    
    public Setting find(String name) {
        return em.find( Setting.class, name );
    }
    
    public List<Setting> findAll() {
        return em.createNamedQuery("Setting.findAll", Setting.class).getResultList();
    }
    
    public Setting save(Setting setting) {
        return em.merge(setting);
    }
    
    public void deleteSetting(String name) {
        em.createNamedQuery("Setting.deleteByName")
        .setParameter("name", name)
        .executeUpdate();
    }
}
