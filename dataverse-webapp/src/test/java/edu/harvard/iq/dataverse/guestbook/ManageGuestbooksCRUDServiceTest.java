package edu.harvard.iq.dataverse.guestbook;

import edu.harvard.iq.dataverse.DataverseRequestServiceBean;
import edu.harvard.iq.dataverse.EjbDataverseEngine;
import edu.harvard.iq.dataverse.engine.command.exception.CommandException;
import edu.harvard.iq.dataverse.engine.command.impl.DeleteGuestbookCommand;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDataverseCommand;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDataverseGuestbookRootCommand;
import edu.harvard.iq.dataverse.persistence.dataverse.Dataverse;
import edu.harvard.iq.dataverse.persistence.guestbook.Guestbook;
import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
@RunWith(MockitoJUnitRunner.class)
public class ManageGuestbooksCRUDServiceTest {
    @InjectMocks
    private ManageGuestbooksCRUDService manageGuestbooksCRUDService;

    @Mock
    private EjbDataverseEngine engineService;
    @Mock
    private DataverseRequestServiceBean dvRequestService;


    @BeforeEach
    void setUp() throws CommandException {
        when(engineService.submit(Mockito.any(UpdateDataverseCommand.class))).thenReturn(new Dataverse());
        when(engineService.submit(Mockito.any(DeleteGuestbookCommand.class))).thenReturn(new Dataverse());
        when(engineService.submit(Mockito.any(UpdateDataverseGuestbookRootCommand.class))).thenReturn(new Dataverse());
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void createOrUpdate() throws CommandException {
        // given
        Dataverse dv = createTestDataverse();

        // when
        manageGuestbooksCRUDService.createOrUpdate(dv);

        // then
        verify(engineService, times(1)).submit(Mockito.any(UpdateDataverseCommand.class));
    }

    @Test
    public void createOrUpdate_withUpdateException() throws CommandException {
        // given
        Dataverse dv = createTestDataverse();

        // when
        when(engineService.submit(Mockito.any(UpdateDataverseCommand.class))).thenAnswer(Answers.CALLS_REAL_METHODS);

        // then
        assertThrows(CommandException.class, () -> manageGuestbooksCRUDService.createOrUpdate(dv));
    }

    @Test
    public void delete() throws CommandException {
        // given
        Dataverse dv = createTestDataverse();
        Guestbook gb = createTestGuestbook("testGuestbook");

        // when
        manageGuestbooksCRUDService.delete(dv, gb);

        // when & then
        verify(engineService, times(1)).submit(Mockito.any(DeleteGuestbookCommand.class));
    }


    @Test
    public void delete_withDeleteException() throws CommandException {
        // given
        Dataverse dv = createTestDataverse();
        Guestbook gb = createTestGuestbook("testGuestbook");

        // when
        when(engineService.submit(Mockito.any(DeleteGuestbookCommand.class))).thenAnswer(Answers.CALLS_REAL_METHODS);

        // then
        assertThrows(CommandException.class, () -> manageGuestbooksCRUDService.delete(dv, gb));
    }

    @Test
    public void updateRoot() throws CommandException {
        // given
        Dataverse dv = createTestDataverse();

        // when
        manageGuestbooksCRUDService.updateRoot(dv);

        // then
        verify(engineService, times(1)).submit(Mockito.any(UpdateDataverseGuestbookRootCommand.class));
    }

    @Test
    public void updateRoot_withUpdateException() throws CommandException {
        // given
        Dataverse dv = createTestDataverse();

        // when
        when(engineService.submit(Mockito.any(UpdateDataverseGuestbookRootCommand.class))).thenAnswer(Answers.CALLS_REAL_METHODS);

        // then
        assertThrows(CommandException.class, () -> manageGuestbooksCRUDService.updateRoot(dv));
    }
    // -------------------- PRIVATE --------------------

    private Guestbook createTestGuestbook(String guestBookName) {
        Guestbook gb = new Guestbook();
        gb.setName(guestBookName);

        return gb;
    }

    private Dataverse createTestDataverse() {
        Dataverse dv = new Dataverse();
        dv.setName("testDv");
        return dv;
    }
}
