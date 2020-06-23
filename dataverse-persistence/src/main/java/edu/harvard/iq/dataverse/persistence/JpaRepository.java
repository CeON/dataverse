package edu.harvard.iq.dataverse.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import java.util.List;
import java.util.Optional;

import static java.util.Optional.ofNullable;

/**
 * Base repository (data access) class for JPA entities.
 * @param <ID> type of entity identifier.
 * @param <T> type of entity.
 *
 * @author kaczynskid
 */
public abstract class JpaRepository<ID, T extends JpaEntity<ID>> implements JpaOperations<ID, T> {

    protected static final Logger log = LoggerFactory.getLogger(JpaRepository.class);

    private final Class<T> entityClass;

    @PersistenceContext(unitName = "VDCNet-ejbPU")
    protected EntityManager em;

    // -------------------- CONSTRUCTORS --------------------

    public JpaRepository(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    // -------------------- LOGIC --------------------

    @Override
    public List<T> findAll() {
        CriteriaBuilder criteriaBuilder = em.getCriteriaBuilder();
        CriteriaQuery<T> query = criteriaBuilder.createQuery(entityClass);
        return em.createQuery(query).getResultList();
    }

    @Override
    public Optional<T> findById(ID id) {
        return ofNullable(em.find(entityClass, id));
    }

    @Override
    public T getById(ID id) {
        return findById(id)
                .orElseThrow(() -> new EntityNotFoundException(entityClass.getSimpleName() + " with ID " + id + " not found"));
    }

    @Override
    public T save(T entity) {
        if (entity.isNew()) {
            em.persist(entity);
            em.flush();
            return entity;
        } else {
            return em.merge(entity);
        }
    }

    @Override
    public T saveFlushAndClear(T entity) {
        T saved = save(entity);
        em.flush();
        em.clear();
        return saved;
    }

    @Override
    public void deleteById(ID id) {
        delete(em.find(entityClass, id));
    }

    @Override
    public void delete(T entity) {
        em.remove(entity);
    }
}