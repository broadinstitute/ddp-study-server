package org.broadinstitute.ddp.route;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ClientDao;
import org.broadinstitute.ddp.db.dao.JdbiAuth0Tenant;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.json.UserCreationPayload;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import spark.Request;
import spark.Response;

@Slf4j
@AllArgsConstructor
public class UserCreationRoute extends ValidatedJsonInputRoute<UserCreationPayload> {
    @Override
    protected int getValidationErrorStatus() {
        return HttpStatus.SC_BAD_REQUEST;
    }

    @Override
    public Object handle(Request request, Response response, UserCreationPayload payload) throws Exception {
        log.info("Attempt to create the user for study {}", payload.getStudyGuid());

        return TransactionWrapper.withTxn(handle -> {
            final var tenantDto = handle.attach(JdbiAuth0Tenant.class).findByStudyGuid(payload.getStudyGuid());
            if (tenantDto == null) {
                throw new RuntimeException("Tenant wasn't found for study + " + payload.getStudyGuid());
            }

            final var clientId = handle.attach(ClientDao.class).getClientDao().insertClient(
                    "DON'T KNOW", "DON'T KNOW", tenantDto.getId(), "DON'T KNOW");

            final var user = handle.attach(UserDao.class).createUser(clientId, "DON'T KNOW");

            handle.attach(UserProfileDao.class).createProfile(UserProfile.builder()
                            .userId(user.getId())
                            .lastName(payload.getLastName())
                            .firstName(payload.getFirstName())
                            .sexType(payload.getSex())
                            .birthDate(payload.getBirthDate())
                    .build());

            return null;
        });
    }
}
