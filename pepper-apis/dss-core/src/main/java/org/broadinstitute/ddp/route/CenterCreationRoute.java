package org.broadinstitute.ddp.route;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.validator.routines.EmailValidator;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.CenterProfileDao;
import org.broadinstitute.ddp.db.dao.JdbiClient;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.db.dao.UserRoleDao;
import org.broadinstitute.ddp.db.dao.RoleDao;
import org.broadinstitute.ddp.db.dao.CenterUserDao;
import org.broadinstitute.ddp.db.dto.CenterProfileDto;
import org.broadinstitute.ddp.db.dto.CenterUserDto;
import org.broadinstitute.ddp.db.dto.UserRoleDto;
import org.broadinstitute.ddp.json.CenterCreationPayload;
import org.broadinstitute.ddp.json.CenterCreationResponse;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import spark.Request;
import spark.Response;

@Slf4j
public class CenterCreationRoute extends ValidatedJsonInputRoute<CenterCreationPayload> {
    @Override
    protected int getValidationErrorStatus() {
        return HttpStatus.SC_UNPROCESSABLE_ENTITY;
    }

    @Override
    public Object handle(Request request, Response response, CenterCreationPayload payload) throws Exception {
        var auth = RouteUtil.getDDPAuth(request);
        /*
        * Grab the requesting client id out of the token
        * so it can be referred back to after we create the user
        */ 
        final var requestorClientId = auth.getClient();
        final var domain = auth.getDomain();

        final var email = payload.getEmail();
        final var emailValidator = EmailValidator.getInstance();

        /* 
        * It's not absolutely necessary to check here- the ParticipantsCreateService will also
        *  check that the email is correctly formatted (you'd need to update its associated
        *  switch statement below in the transaction)
        *
        * Checking here is in order to save a few cycles & connections to the DB if it's
        *  immediately clear the client gave us bad data.
        */
        if (!emailValidator.isValid(email)) {
            var error = new ApiError(ErrorCodes.MALFORMED_EMAIL, "the email address is not in a valid format.");
            throw ResponseUtil.haltError(HttpStatus.SC_UNPROCESSABLE_ENTITY, error);
        }

        log.info("attempting to create a center {}", payload.getName());

        return TransactionWrapper.withTxn(handle -> {
            // If we've made it this far in the request, it's unlikely the Auth0 client is not
            // going to be recognized but handle things just in case. Save the ID since it'll be
            // needed below to set the `created_by_client_id` field in the user.
            final var internalClientId = handle.attach(JdbiClient.class)
                    .getClientIdByAuth0ClientAndDomain(requestorClientId, domain);
            if (internalClientId.isEmpty()) {
                var error = new ApiError(ErrorCodes.NOT_FOUND,
                        String.format("Auth0 client '%s' is not authorized for '%s'.",
                            requestorClientId, domain));
                throw ResponseUtil.haltError(HttpStatus.SC_UNAUTHORIZED, error);
            }

            if (handle.attach(RoleDao.class).findById(payload.getRoleId()) == null) {
                var error = new ApiError(ErrorCodes.INVALID_ROLE, "the role does not exist");
                throw ResponseUtil.haltError(HttpStatus.SC_UNPROCESSABLE_ENTITY, error);
            }

            final var primaryContact = handle.attach(UserDao.class).createUserByEmail(email);
            handle.attach(UserProfileDao.class).createProfile(UserProfile.builder()
                    .userId(primaryContact.getId())
                    .firstName(payload.getFirstName())
                    .lastName(payload.getLastName())
                    .build());

            final var centerId = handle.attach(CenterProfileDao.class).insert(CenterProfileDto.builder()
                    .address1(payload.getAddress1())
                    .address1(payload.getAddress2())
                    .cityId(payload.getCityId())
                    .primaryContactId(primaryContact.getId())
                    .build());

            handle.attach(CenterUserDao.class).insert(CenterUserDto.builder()
                    .centerId(centerId)
                    .userId(primaryContact.getId())
                    .build());

            handle.attach(UserRoleDao.class).insert(UserRoleDto.builder()
                    .roleId(payload.getRoleId())
                    .userId(primaryContact.getId())
                    .build());

            // Suggested by @ssettipalli
            handle.attach(DataExportDao.class).queueDataSync(primaryContact.getGuid());

            return new CenterCreationResponse(payload.getName());
        });
    }
}
