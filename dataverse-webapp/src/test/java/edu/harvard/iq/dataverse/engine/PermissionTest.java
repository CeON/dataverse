package edu.harvard.iq.dataverse.engine;

import edu.harvard.iq.dataverse.persistence.DvObject;
import edu.harvard.iq.dataverse.persistence.datafile.DataFile;
import edu.harvard.iq.dataverse.persistence.dataset.Dataset;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.user.Permission;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author michael
 */
public class PermissionTest {

    /**
     * Test of appliesTo method, of class Permission.
     */
    @Test
    public void testAppliesTo() {
        assertFalse(Permission.EditDataverse.appliesTo(DvObject.class));
        assertTrue(Permission.EditDataverse.appliesTo(Dataverse.class));
        assertFalse(Permission.EditDataverse.appliesTo(DataFile.class));

        assertTrue(Permission.EditDataset.appliesTo(Dataset.class));
        assertFalse(Permission.EditDataset.appliesTo(DvObject.class));
        assertFalse(Permission.EditDataset.appliesTo(Dataverse.class));
    }

}
