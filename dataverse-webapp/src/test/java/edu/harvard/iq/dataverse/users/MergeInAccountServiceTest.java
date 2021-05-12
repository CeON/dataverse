package edu.harvard.iq.dataverse.users;

import edu.harvard.iq.dataverse.authorization.AuthenticationServiceBean;
import edu.harvard.iq.dataverse.persistence.MocksFactory;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
public class MergeInAccountServiceTest {

    @InjectMocks
    private MergeInAccountService mergeInAccountService;

    @Mock
    private AuthenticationServiceBean authenticationService;

    @Test
    public void mergeAccounts_notSuperuser() {
        // given
        AuthenticatedUser user = MocksFactory.makeAuthenticatedUser("Jurek","Kiler");
        user.setSuperuser(false);

        // when
        Exception exception = Assertions.assertThrows(SecurityException.class, () -> {
            mergeInAccountService.mergeAccounts(user, "consumedId", "baseId");
        });
        Assertions.assertEquals("Only superusers can merge accounts.", exception.getMessage());
    }

    @Test
    public void mergeAccounts_NoBaseIdProvided() {
        // given
        AuthenticatedUser user = MocksFactory.makeAuthenticatedUser("Jurek","Kiler");
        user.setSuperuser(true);

        // when
        Exception exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {
            mergeInAccountService.mergeAccounts(user, "consumedId", null);
        });
        Assertions.assertEquals("Base identifier provided to change is empty.",
                exception.getMessage());

        exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {
            mergeInAccountService.mergeAccounts(user, "consumedId", "");
        });
        Assertions.assertEquals("Base identifier provided to change is empty.",
                exception.getMessage());
    }

    @Test
    public void mergeAccounts_NoConsumedIdProvided() {
        // given
        AuthenticatedUser user = MocksFactory.makeAuthenticatedUser("Jurek","Kiler");
        user.setSuperuser(true);

        // when
        Exception exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {
            mergeInAccountService.mergeAccounts(user, null, "baseId");
        });
        Assertions.assertEquals("Identifier to merge in is empty.",
                exception.getMessage());

        exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {
            mergeInAccountService.mergeAccounts(user, "", "baseId");
        });
        Assertions.assertEquals("Identifier to merge in is empty.",
                exception.getMessage());
    }

    @Test
    public void mergeAccounts_toItself() {
        // given
        AuthenticatedUser user = MocksFactory.makeAuthenticatedUser("Jurek","Kiler");
        user.setSuperuser(true);

        // when
        Exception exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {
            mergeInAccountService.mergeAccounts(user, "testId", "testId");
        });
        Assertions.assertEquals("You cannot merge account to itself.",
                exception.getMessage());
    }

    @Test
    public void mergeAccounts_baseUserDoesNotExist() {
        // given
        AuthenticatedUser user = MocksFactory.makeAuthenticatedUser("Jurek","Kiler");
        user.setSuperuser(true);
        Mockito.when(authenticationService.getAuthenticatedUser("baseId")).thenReturn(null);

        // when
        Exception exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {
            mergeInAccountService.mergeAccounts(user, "consumedId", "baseId");
        });
        Assertions.assertEquals("User baseId not found in AuthenticatedUser",
                exception.getMessage());
    }

    @Test
    public void mergeAccounts_consumedUserDoesNotExist() {
        // given
        AuthenticatedUser user = MocksFactory.makeAuthenticatedUser("Jurek","Kiler");
        user.setSuperuser(true);
        Mockito.when(authenticationService.getAuthenticatedUser(anyString())).thenReturn(new AuthenticatedUser(), null);

        // when
        Exception exception = Assertions.assertThrows(IllegalArgumentException.class, () -> {
            mergeInAccountService.mergeAccounts(user, "consumedId", "baseId");
        });
        Assertions.assertEquals("User consumedId not found in AuthenticatedUser",
                exception.getMessage());
    }
}
