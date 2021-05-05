package edu.harvard.iq.dataverse.engine.command.impl;

import edu.harvard.iq.dataverse.RoleAssigneeServiceBean;
import edu.harvard.iq.dataverse.authorization.AuthenticationServiceBean;
import edu.harvard.iq.dataverse.authorization.providers.builtin.BuiltinUserServiceBean;
import edu.harvard.iq.dataverse.engine.TestCommandContext;
import edu.harvard.iq.dataverse.engine.TestDataverseEngine;
import edu.harvard.iq.dataverse.engine.command.exception.CommandException;
import edu.harvard.iq.dataverse.engine.command.exception.IllegalCommandException;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUserLookup;
import edu.harvard.iq.dataverse.persistence.user.BuiltinUser;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static edu.harvard.iq.dataverse.mocks.MockRequestFactory.makeRequest;

@RunWith(MockitoJUnitRunner.class)
public class ChangeUserIdentifierCommandTest {

    @Mock
    private AuthenticationServiceBean authenticationServiceBean;

    @Mock
    private BuiltinUserServiceBean builtinUserServiceBean;

    @Mock
    private RoleAssigneeServiceBean roleAssigneeServiceBean;

    @Test(expected = IllegalCommandException.class)
    public void testNewIdentifierExists() throws CommandException {
        // given
        Mockito.when(authenticationServiceBean.getAuthenticatedUser("testIdentifier")).thenReturn(new AuthenticatedUser());

        // when
        ChangeUserIdentifierCommand changeUserIdentifierCommand = new ChangeUserIdentifierCommand(makeRequest(), new AuthenticatedUser(), "testIdentifier");
        TestDataverseEngine testEngine = new TestDataverseEngine(new TestCommandContext() {
            @Override
            public AuthenticationServiceBean authentication() {
                return authenticationServiceBean;
            }
        });
        testEngine.submit(changeUserIdentifierCommand);

        // assert
        Mockito.verify(authenticationServiceBean, Mockito.times(1)).getAuthenticatedUser(Mockito.any());
    }

    @Test(expected = IllegalCommandException.class)
    public void testBuiltInUserIdentifierIncorrect() throws CommandException {
        // given
        Mockito.when(builtinUserServiceBean.findByUserName("testIdentifier")).thenReturn(new BuiltinUser());

        String ILLEGAL_ID = "x";
        AuthenticatedUser authenticatedUser = new AuthenticatedUser();
        authenticatedUser.setUserIdentifier("testIdentifier");

        // when
        ChangeUserIdentifierCommand changeUserIdentifierCommand = new ChangeUserIdentifierCommand(makeRequest(), authenticatedUser, ILLEGAL_ID);
        TestDataverseEngine testEngine = new TestDataverseEngine(new TestCommandContext() {
            @Override
            public AuthenticationServiceBean authentication() {
                return authenticationServiceBean;
            }

            @Override
            public BuiltinUserServiceBean builtinUsers() {
                return builtinUserServiceBean;
            }
        });
        testEngine.submit(changeUserIdentifierCommand);

        // assert
        Mockito.verify(builtinUserServiceBean, Mockito.times(1)).findByUserName(Mockito.any());
    }

    @Test
    public void testChangeUserIdCommandSuccess() throws CommandException {
        // given
        AuthenticatedUser authenticatedUser = new AuthenticatedUser();
        authenticatedUser.setUserIdentifier("testIdentifier");
        authenticatedUser.setAuthenticatedUserLookup(new AuthenticatedUserLookup());

        // when
        ChangeUserIdentifierCommand changeUserIdentifierCommand = new ChangeUserIdentifierCommand(makeRequest(), authenticatedUser, "newTestIdentifier");
        TestDataverseEngine testEngine = new TestDataverseEngine(new TestCommandContext() {
            @Override
            public AuthenticationServiceBean authentication() {
                return authenticationServiceBean;
            }

            @Override
            public BuiltinUserServiceBean builtinUsers() {
                return builtinUserServiceBean;
            }

            @Override
            public RoleAssigneeServiceBean roleAssignees() {
                return roleAssigneeServiceBean;
            }
        });
        testEngine.submit(changeUserIdentifierCommand);

        // assert
        Mockito.verify(authenticationServiceBean, Mockito.times(1)).getAuthenticatedUser(Mockito.any());
        Mockito.verify(builtinUserServiceBean, Mockito.times(1)).findByUserName(Mockito.any());
        Mockito.verify(roleAssigneeServiceBean, Mockito.times(1)).getAssignmentsFor(Mockito.any());
    }

}
