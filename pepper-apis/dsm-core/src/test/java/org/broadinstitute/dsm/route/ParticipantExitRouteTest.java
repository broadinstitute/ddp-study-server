package org.broadinstitute.dsm.route;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import liquibase.pro.packaged.E;
import org.broadinstitute.dsm.db.KitDiscard;
import org.broadinstitute.dsm.exception.AuthorizationException;
import org.broadinstitute.dsm.exception.EntityNotFound;
import org.broadinstitute.dsm.route.request.ParticipantExitRequest;
import org.broadinstitute.dsm.service.participant.ParticipantExitService;
import org.broadinstitute.dsm.util.UserUtil;
import org.junit.Assert;
import org.junit.Test;


public class ParticipantExitRouteTest {

    @Test
    public void testParticipantExitGet() {
        ParticipantExitService exitService = mock(ParticipantExitService.class);
        when(exitService.getExitedParticipants(anyString())).thenReturn(Collections.emptyMap());

        UserUtil userUtil = mock(UserUtil.class);
        when(userUtil.userHasRole(anyString(), anyString(), anyString(), any())).thenReturn(true);

        try {
            ParticipantExitRoute.handleGetRequest("realm", "userId", userUtil, exitService);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception: " + e.getMessage());
        }

        when(userUtil.userHasRole(anyString(), anyString(), anyString(), any())).thenReturn(false);
        try {
            ParticipantExitRoute.handleGetRequest("realm", "userId", userUtil, exitService);
            Assert.fail("Expected AuthorizationException");
        } catch (Exception e) {
            Assert.assertTrue(e instanceof AuthorizationException);
        }
    }

    @Test
    public void testParticipantExitPost() {
        KitDiscard kitDiscard = new KitDiscard("discardId", "kitType", "action");
        ParticipantExitService exitService = mock(ParticipantExitService.class);
        when(exitService.exitParticipant(eq("realm"), eq("participantId"), eq("userId"), anyBoolean()))
                .thenReturn(List.of(kitDiscard));

        // note the spaces in the string fields
        ParticipantExitRequest participantExitRequest =
                new ParticipantExitRequest("realm ", " participantId", " userId", true);
        ObjectMapper objectMapper = new ObjectMapper();

        UserUtil userUtil = mock(UserUtil.class);
        when(userUtil.userHasRole(anyString(), anyString(), anyString(), anyString())).thenReturn(false);
        try {
            ParticipantExitRoute.handlePostRequest(objectMapper.writeValueAsString(participantExitRequest),
                    "userId", userUtil, exitService);
            Assert.fail("Expected AuthorizationException");
        } catch (Exception e) {
            Assert.assertTrue(e instanceof AuthorizationException);
        }

        when(userUtil.userHasRole(anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        try {
            List<KitDiscard> res = ParticipantExitRoute.handlePostRequest(objectMapper.writeValueAsString(participantExitRequest),
                    "userId", userUtil, exitService);
            Assert.assertTrue(res.contains(kitDiscard));
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception: " + e.getMessage());
        }

        when(exitService.exitParticipant(anyString(), anyString(), anyString(), anyBoolean()))
                .thenThrow(new EntityNotFound("Participant not found"));
        try {
            ParticipantExitRoute.handlePostRequest(objectMapper.writeValueAsString(participantExitRequest),
                    "userId", userUtil, exitService);
            Assert.fail("Expected EntityNotFound");
        } catch (Exception e) {
            Assert.assertTrue(e instanceof EntityNotFound);
        }
    }
}
