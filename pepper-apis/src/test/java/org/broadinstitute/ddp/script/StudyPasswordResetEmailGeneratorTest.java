package org.broadinstitute.ddp.script;

import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.DDP_LINK;
import static org.broadinstitute.ddp.constants.NotificationTemplateVariables.DDP_PARTICIPANT_FIRST_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.auth0.exception.Auth0Exception;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.broadinstitute.ddp.util.Auth0Util;
import org.jdbi.v3.core.Handle;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentMatcher;

@Ignore
public class StudyPasswordResetEmailGeneratorTest extends TxnAwareBaseTest {

    @Test
    public void testSendingEmails() throws Auth0Exception {
        StudyPasswordResetEmailGenerator generator;
        generator = mock(StudyPasswordResetEmailGenerator.class);
        var male = UserProfile.SexType.MALE;
        //This is the one that we expect will get an email
        UserProfile profile1 = new UserProfile(1L, "Hulk", "Hogan", male, LocalDate.of(1953, 8, 11), 1L, "en", null, false, null);

        //This one should be filtered out. says do not contact
        UserProfile profile2 = new UserProfile(1L, "Jerome", "Salinger", male, LocalDate.of(1919, 1, 1), 1L, "en", null, true, null);
        ProfileWithEmail profileWithEmail1 = new ProfileWithEmail(profile1, "hulk.hogan@hulkomania.org");
        ProfileWithEmail profileWithEmail2 = new ProfileWithEmail(profile2, "jd@nyc.gov");


        when(generator.findUserProfilesForParticipantsNotExitedThatCanBeContacted(anyString(), any(),
                any(Handle.class))).thenReturn(Arrays.asList(profileWithEmail1, profileWithEmail2));
        Auth0Util auth0UtilMock = mock(Auth0Util.class);
        final String auth0Domain = "DUMMYDOMAIN";
        final String auth0Token = "DUMMYTOKEN";
        Auth0ManagementClient mockMgmtClient = mock(Auth0ManagementClient.class);
        when(mockMgmtClient.getDomain()).thenReturn(auth0Domain);
        when(mockMgmtClient.getToken()).thenReturn(auth0Token);
        when(generator.buildAuth0Util(auth0Domain)).thenReturn(auth0UtilMock);

        when(auth0UtilMock.getAuth0UserNamePasswordConnectionId(auth0Token)).thenReturn("DUMMYCONNECTIONID");
        final String redirectUrlAfterPasswordReset = "http://www.www.org";
        final String resetLink = "http://www.resetpassword.com?someParam=true&works=YOUBETCHA";
        when(auth0UtilMock.generatePasswordResetLink(anyString(), anyString(), anyString(), eq(redirectUrlAfterPasswordReset)))
                .thenReturn(resetLink);
        final String sendGridApiKey = "theKeyToSendGrid!";
        final String studyGuid = "DUMMYSTUDY";
        when(generator.getSendgridApiKey(eq(studyGuid), any(Handle.class))).thenReturn(sendGridApiKey);

        final String fromEmailAddress = "al@princeton.edu";
        final String emailSubject = "Salutations, my friends!";
        final String sendgridTemplateId = "6666";
        final String fromName = "Albert Einstein";

        // this is the method under test. Calling the real thing
        when(generator.sendPasswordResetEmails(anyString(), any(Set.class), anyString(), anyString(), anyString(), anyString(), anyString(),
                any()
        )).thenCallRealMethod();

        when(generator.sendPasswordResetEmails(anyString(), any(List.class), anyString(), anyString(), anyString(), anyString(),
                anyString(), any())).thenCallRealMethod();

        generator.sendPasswordResetEmails(studyGuid, new HashSet<String>(), fromName, fromEmailAddress, emailSubject,
                redirectUrlAfterPasswordReset, sendgridTemplateId, mockMgmtClient);

        ArgumentMatcher<Map<String, String>> substitutionsMapper = (valueMap) -> {
            // should include our link with the study guid
            return valueMap.get(DDP_LINK).equals(resetLink + "&study=" + studyGuid)
                    && valueMap.get(DDP_PARTICIPANT_FIRST_NAME).equals(profile1.getFirstName());
        };
        verify(generator, times(1)).sendEmailMessage(eq(fromName), eq(fromEmailAddress), eq(emailSubject),
                eq(sendgridTemplateId), eq(sendGridApiKey), eq("Hulk Hogan"), eq(profileWithEmail1.getEmailAddress()),
                argThat(substitutionsMapper));
    }
}
