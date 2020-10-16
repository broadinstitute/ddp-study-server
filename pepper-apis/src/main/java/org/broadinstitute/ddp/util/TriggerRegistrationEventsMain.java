package org.broadinstitute.ddp.util;

import com.auth0.exception.Auth0Exception;
import com.typesafe.config.Config;
import org.apache.commons.io.IOUtils;
import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.*;
import org.broadinstitute.ddp.db.dto.Auth0TenantDto;
import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.event.EventSignal;
import org.broadinstitute.ddp.service.EventService;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class TriggerRegistrationEventsMain {
    
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
                String participantGuid = splitLine[0];
                String studyGuid = splitLine[1];
                String invitationGuid = splitLine[2];
                String postRestLink = splitLine[3];

                StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
                UserDto participant = handle.attach(JdbiUser.class).findByUserGuid(participantGuid);
                InvitationDao invitationDao = handle.attach(InvitationDao.class);
                InvitationDto invitationDto = invitationDao.findByInvitationGuid(studyDto.getId(), invitationGuid).get();
                Auth0TenantDto auth0TenantDto = handle.attach(JdbiAuth0Tenant.class).findById(studyDto.getAuth0TenantId()).get();
                Auth0Util auth0Util = new Auth0Util(auth0TenantDto.getDomain());
                Auth0ManagementClient mgmtClient = Auth0Util.getManagementClientForDomain(handle, auth0TenantDto.getDomain());
                Map<String, String> params = new HashMap<>();
                params.put("study", studyGuid);
                try {
                    String auth0UserNamePasswordConnectionId = auth0Util.getAuth0UserNamePasswordConnectionId(mgmtClient.getToken());
                    String participantEmail = auth0Util.getUserPassConnEmailsByAuth0UserIds(Set.of(participant.getAuth0UserId()), mgmtClient.getToken()).get(participant.getAuth0UserId());
                    String resetLink = auth0Util.generatePasswordResetLink(participantEmail, auth0UserNamePasswordConnectionId,
                            mgmtClient.getToken(), postRestLink, params);
                    System.out.println("Password reset for " + participantEmail + ":" + resetLink);
                } catch(Auth0Exception e) {
                    System.err.println("Could not generate password rest for " + auth0TenantDto.getDomain() + " " + participant.getAuth0UserId());
                }


                if (invitationDto.getUserId() != null && invitationDto.getUserId() != participant.getUserId()) {
                    System.err.println(invitationGuid + " is assigned to " + invitationDto.getUserId() + " instead of " + participant.getUserId());
                }
                invitationDao.assignAcceptingUser(invitationDto.getInvitationId(), participant.getUserId(), Instant.now());

                var signal = new EventSignal(
                        participant.getUserId(),
                        participant.getUserId(),
                        participant.getUserGuid(),
                        studyDto.getId(),
                        EventTriggerType.USER_REGISTERED);
                EventService.getInstance().processAllActionsForEventSignal(handle, signal);
            }

        });

    }
}
