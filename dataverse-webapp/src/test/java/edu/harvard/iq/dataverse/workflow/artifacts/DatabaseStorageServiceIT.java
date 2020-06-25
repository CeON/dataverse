package edu.harvard.iq.dataverse.workflow.artifacts;

import edu.harvard.iq.dataverse.arquillian.arquillianexamples.WebappArquillianDeployment;
import edu.harvard.iq.dataverse.persistence.workflow.WorkflowArtifact;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.transaction.api.annotation.TransactionMode;
import org.jboss.arquillian.transaction.api.annotation.Transactional;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Arquillian.class)
@Transactional(TransactionMode.ROLLBACK)
public class DatabaseStorageServiceIT extends WebappArquillianDeployment {

    private static final byte[] TEST_DATA = new byte[] { 0, 1, 2, 3, 4, 5, 6, 7 };

    @PersistenceContext(unitName = "VDCNet-ejbPU")
    private EntityManager em;

    @Inject
    private DatabaseStorageService storageService;

    @Test
    public void shouldStoreAndRetrieveArtifactData() {
        // given & when
        String location = storageService.save(createArtifactWithId(), () -> new ByteArrayInputStream(TEST_DATA));
        byte[] read = readStream(storageService.readAsStream(location).get());

        // then
        assertThat(read).isEqualTo(read);
    }

    @Test
    public void shouldBeAbleToDeleteStoredData() {
        // given & when
        String location = storageService.save(createArtifactWithId(), () -> new ByteArrayInputStream(TEST_DATA));
        storageService.delete(location);
        Optional<InputStream> read = storageService.readAsStream(location);

        // then
        assertThat(read.isPresent()).isFalse();
    }


    // -------------------- PRIVATE --------------------

    private WorkflowArtifact createArtifactWithId() {
        WorkflowArtifact artifact = new WorkflowArtifact();
        artifact.setId(1L);
        return artifact;
    }

    private byte[] readStream(InputStream stream) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             BufferedInputStream input = new BufferedInputStream(stream)) {
            int count = 0;
            byte[] buffer = new byte[32];
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }
}