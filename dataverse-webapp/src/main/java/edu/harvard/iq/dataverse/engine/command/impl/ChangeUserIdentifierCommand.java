package edu.harvard.iq.dataverse.engine.command.impl;

import edu.harvard.iq.dataverse.engine.command.AbstractVoidCommand;
import edu.harvard.iq.dataverse.engine.command.CommandContext;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.engine.command.RequiredPermissions;
import edu.harvard.iq.dataverse.engine.command.exception.CommandException;
import edu.harvard.iq.dataverse.engine.command.exception.IllegalCommandException;
import edu.harvard.iq.dataverse.persistence.DvObject;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUser;
import edu.harvard.iq.dataverse.persistence.user.AuthenticatedUserLookup;
import edu.harvard.iq.dataverse.persistence.user.BuiltinUser;
import edu.harvard.iq.dataverse.persistence.user.RoleAssignment;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.List;
import java.util.Set;

/**
 *
 * @author matthew
 */
@RequiredPermissions({})
public class ChangeUserIdentifierCommand extends AbstractVoidCommand {

    final AuthenticatedUser authenticatedUser;
    final String newIdentifier;
    final String oldIdentifier;

    public ChangeUserIdentifierCommand(DataverseRequest aRequest, AuthenticatedUser authenticatedUser, String newIdentifier) {
        super(
                aRequest,
                (DvObject) null
        );
        this.authenticatedUser = authenticatedUser;
        this.newIdentifier = newIdentifier;
        this.oldIdentifier = authenticatedUser.getUserIdentifier();
    }

    @Override
    public void executeImpl(CommandContext ctxt) throws CommandException {

        AuthenticatedUser authenticatedUserTestNewIdentifier = ctxt.authentication().getAuthenticatedUser(newIdentifier);
        if (authenticatedUserTestNewIdentifier != null) {
            String logMsg = " User " + newIdentifier + " already exists. Cannot use this as new identifier";
            throw new IllegalCommandException("Validation of submitted data failed. Details: " + logMsg, this);
        }

        BuiltinUser builtinUser = ctxt.builtinUsers().findByUserName(oldIdentifier);

        if (builtinUser != null) {
            builtinUser.setUserName(newIdentifier);
            //Validate the BuiltinUser change. Username validations are there.
            //If we have our validation errors pass up to commands, this could be removed
            ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
            Validator validator = factory.getValidator();

            Set<ConstraintViolation<BuiltinUser>> violations = validator.validate(builtinUser);
            if (violations.size() > 0) {
                StringBuilder logMsg = new StringBuilder();
                for (ConstraintViolation<?> violation : violations) {
                    logMsg.append(" Invalid value: >>>")
                            .append(violation.getInvalidValue())
                            .append("<<< for ")
                            .append(violation.getPropertyPath())
                            .append(" at ")
                            .append(violation.getLeafBean())
                            .append(" - ")
                            .append(violation.getMessage());
                }
                throw new IllegalCommandException("Validation of submitted data failed. Details: " + logMsg, this);
            }
        }

        authenticatedUser.setUserIdentifier(newIdentifier);

        AuthenticatedUserLookup authenticatedUserLookup = authenticatedUser.getAuthenticatedUserLookup();
        authenticatedUserLookup.setPersistentUserId(newIdentifier);

        List<RoleAssignment> roleAssignments = ctxt.roleAssignees().getAssignmentsFor(authenticatedUser.getIdentifier()); //only AuthenticatedUser supported
        for(RoleAssignment roleAssignment : roleAssignments) {
            roleAssignment.setAssigneeIdentifier("@" + newIdentifier);
        }
    }

    @Override
    public String describe() {
        return "User " + oldIdentifier + " renamed to " + newIdentifier;
    }
}
