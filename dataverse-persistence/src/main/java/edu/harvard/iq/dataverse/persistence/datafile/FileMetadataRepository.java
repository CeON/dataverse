package edu.harvard.iq.dataverse.persistence.datafile;

import edu.harvard.iq.dataverse.persistence.JpaRepository;
import edu.harvard.iq.dataverse.persistence.datafile.dto.FileMetadataRestrictionDTO;
import edu.harvard.iq.dataverse.persistence.datafile.license.FileTermsOfUse;

import javax.ejb.Stateless;
import java.util.List;

@Stateless
public class FileMetadataRepository extends JpaRepository<Long, FileMetadata> {

    // -------------------- CONSTRUCTORS --------------------

    public FileMetadataRepository() {
        super(FileMetadata.class);
    }

    // -------------------- LOGIC --------------------

    /**
     * Retrieves fileMetadata with pagination.
     * @param pageNumber page number that starts with 0 (important for calculation).
     * @return List of fileMetadata
     */
    public List<FileMetadata> findFileMetadataByDatasetVersionIdWithPagination(long dsvId, int pageNumber, int amountToFetch) {
        return em.createQuery("SELECT f FROM FileMetadata f JOIN f.datasetVersion v " +
                                      " WHERE v.id = :dsvId ORDER BY f.displayOrder", FileMetadata.class)
                 .setParameter("dsvId", dsvId)
                 .setFirstResult(pageNumber * amountToFetch)
                 .setMaxResults(amountToFetch)
                 .getResultList();
    }

    /**
     * Retrieves fileMetadata with pagination and search term.
     * @param pageNumber page number that starts with 0 (important for calculation).
     * @return List of fileMetadata
     */
    public List<FileMetadata> findSearchedFileMetadataByDatasetVersionIdWithPagination(long dsvId, int pageNumber, int amountToFetch, String searchTerm) {
        return em.createQuery("SELECT f FROM FileMetadata f JOIN f.datasetVersion v " +
                                      " WHERE v.id = :dsvId AND (lower(f.label) LIKE :searchTerm OR lower(f.description) LIKE :searchTerm)" +
                                      " ORDER BY f.displayOrder asc", FileMetadata.class)
                 .setParameter("dsvId", dsvId)
                 .setParameter("searchTerm", "%" + searchTerm + "%")
                 .setFirstResult(pageNumber * amountToFetch)
                 .setMaxResults(amountToFetch)
                 .getResultList();
    }

    public List<FileMetadata> findFileMetadataByFileMetadataIds(List<Long> fileMetadataIds) {
        return em.createQuery("SELECT f FROM FileMetadata f" +
                                      " WHERE f.id IN :fileIds", FileMetadata.class)
                 .setParameter("fileIds", fileMetadataIds)
                 .getResultList();
    }

    public List<String> findFileMetadataLabelsByFileMetadataIds(List<Long> fileMetadataIds) {
        return em.createQuery("SELECT f.label FROM FileMetadata f" +
                                      " WHERE f.id IN :fileIds", String.class)
                 .setParameter("fileIds", fileMetadataIds)
                 .getResultList();
    }

    public List<Long> findFileMetadataIdsByDatasetVersionId(long dsvId) {
        return em.createQuery("SELECT f.id FROM FileMetadata f JOIN f.datasetVersion v WHERE v.id = :dsvId", Long.class)
                 .setParameter("dsvId", dsvId)
                 .getResultList();
    }

    public List<FileMetadataRestrictionDTO> findRestrictedFileMetadataLabels(long dsvId, List<Long> filteredFileIds) {
        return em
                .createQuery("SELECT new edu.harvard.iq.dataverse.persistence.datafile.dto.FileMetadataRestrictionDTO(v.id, " +
                                     "FileTermsOfUse.TermsOfUseType.RESTRICTED," +
                                     " t.restrictType, " +
                                     " d)" +
                                     " FROM FileMetadata f JOIN f.datasetVersion v JOIN f.termsOfUse t JOIN f.dataFile d " +
                                     "WHERE v.id = :dsvId AND f.id IN :fileIds AND  t.restrictType != null", FileMetadataRestrictionDTO.class)
                .setParameter("dsvId", dsvId)
                .setParameter("fileIds", filteredFileIds)
                .getResultList();
    }

    public List<FileMetadataRestrictionDTO> findAllRightReservedFileMetadata(long dsvId, List<Long> filteredFileIds) {
        return em
                .createQuery("SELECT new edu.harvard.iq.dataverse.persistence.datafile.dto.FileMetadataRestrictionDTO(v.id, " +
                                     "FileTermsOfUse.TermsOfUseType.ALL_RIGHTS_RESERVED, " +
                                     " d)" +
                                     " FROM FileMetadata f JOIN f.datasetVersion v JOIN f.termsOfUse t JOIN f.dataFile d " +
                                     "WHERE v.id = :dsvId AND f.id IN :fileIds AND t.allRightsReserved != null", FileMetadataRestrictionDTO.class)
                .setParameter("dsvId", dsvId)
                .setParameter("fileIds", filteredFileIds)
                .getResultList();
    }

    public List<FileMetadataRestrictionDTO> findLicenseBasedFileMetadata(long dsvId, List<Long> filteredFileIds) {
        return em
                .createQuery("SELECT new edu.harvard.iq.dataverse.persistence.datafile.dto.FileMetadataRestrictionDTO(v.id, " +
                                     "FileTermsOfUse.TermsOfUseType.LICENSE_BASED, " +
                                     " d)" +
                                     " FROM FileMetadata f JOIN f.datasetVersion v JOIN f.termsOfUse t JOIN f.dataFile d " +
                                     "WHERE v.id = :dsvId AND f.id IN :fileIds AND t.license != null", FileMetadataRestrictionDTO.class)
                .setParameter("dsvId", dsvId)
                .setParameter("fileIds", filteredFileIds)
                .getResultList();
    }

    public List<String> findRestrictedFileMetadataLabels(List<Long> filteredFileIds) {
        return em.createQuery("SELECT f.label" +
                                      " FROM FileMetadata f JOIN f.termsOfUse t " +
                                      "WHERE f.id IN :fileIds AND  t.restrictType != null", String.class)
                 .setParameter("fileIds", filteredFileIds)
                 .getResultList();
    }

    public List<String> findFileMetadataCategoriesByName(List<Long> filteredFileIds) {
        return em.createQuery("SELECT distinct fc.name" +
                                      " FROM FileMetadata f JOIN f.fileCategories fc " +
                                      "WHERE f.id IN :fileIds", String.class)
                 .setParameter("fileIds", filteredFileIds)
                 .getResultList();
    }

    public void updateFileMetadataTermsOfUse(List<Long> fileIds, FileTermsOfUse fileTermsOfUse) {
        em.createQuery("UPDATE FileMetadata f SET f.termsOfUse = :fileTermsOfUse WHERE f.id IN :fileIds")
                .setParameter("fileTermsOfUse", fileTermsOfUse)
                .setParameter("fileIds", fileIds);
    }
}
