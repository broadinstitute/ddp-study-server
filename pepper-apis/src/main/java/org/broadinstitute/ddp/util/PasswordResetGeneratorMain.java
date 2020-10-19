package org.broadinstitute.ddp.util;

import com.auth0.exception.Auth0Exception;
import com.auth0.json.mgmt.users.User;
import com.typesafe.config.Config;
import org.apache.commons.io.IOUtils;
import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.InvitationDao;
import org.broadinstitute.ddp.db.dao.JdbiAuth0Tenant;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.Auth0TenantDto;
import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.event.EventSignal;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.broadinstitute.ddp.util.Auth0Util.USERNAME_PASSWORD_AUTH0_CONN_NAME;

public class PasswordResetGeneratorMain {

    public static void main(String[] args) {

        File participantsFile = new File(args[0]);
        AtomicReference<List> participantlines = new AtomicReference<>();
        try {
            participantlines.set(IOUtils.readLines(new FileReader(participantsFile)));
        } catch (IOException e) {
            System.err.println(e);
            System.exit(-1);
        }
        Config cfg = ConfigManager.getInstance().getConfig();
        int maxConnections = cfg.getInt(ConfigFile.NUM_POOLED_CONNECTIONS);
        String dbUrl = cfg.getString(ConfigFile.DB_URL);

        TransactionWrapper.init(
                new TransactionWrapper.DbConfiguration(TransactionWrapper.DB.APIS, maxConnections, dbUrl));
        TransactionWrapper.useTxn(handle -> {
            for (Object participantLine : participantlines.get()) {
                String[] splitLine = ((String)participantLine).split("\t");
                String emailAddress = splitLine[0];
                String studyGuid = splitLine[1];
                String postRestLink = splitLine[2];

                StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
                Auth0TenantDto auth0TenantDto = handle.attach(JdbiAuth0Tenant.class).findById(studyDto.getAuth0TenantId()).get();
                Auth0Util auth0Util = new Auth0Util(auth0TenantDto.getDomain());
                Auth0ManagementClient mgmtClient = Auth0Util.getManagementClientForDomain(handle, auth0TenantDto.getDomain());
                Map<String, String> params = new HashMap<>();
                params.put("study", studyGuid);
                try {
                    String auth0UserNamePasswordConnectionId = auth0Util.getAuth0UserNamePasswordConnectionId(mgmtClient.getToken());
                    List<User> auth0Users = auth0Util.getAuth0UsersByEmail(emailAddress, mgmtClient.getToken(), USERNAME_PASSWORD_AUTH0_CONN_NAME);
                    if (auth0Users.isEmpty()) {
                        System.err.println(emailAddress + " not found");
                    } else {
                        for (User auth0User : auth0Users) {
                            String participantEmail = auth0Util.getUserPassConnEmailsByAuth0UserIds(Set.of(emailAddress), mgmtClient.getToken()).get(auth0User.getId());
                            String resetLink = auth0Util.generatePasswordResetLink(emailAddress, auth0UserNamePasswordConnectionId,
                                    mgmtClient.getToken(), postRestLink, params);
                            System.out.println("Password reset for " + emailAddress + ":" + resetLink);
                        }
                    }
                } catch(Auth0Exception e) {
                    System.err.println("Could not generate password rest for " + auth0TenantDto.getDomain() + " " + emailAddress +":" + e.getMessage());
                }
            }

        });

    }

}
