package org.broadinstitute.ddp.script;

import static java.util.stream.Collectors.toList;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.auth0.exception.Auth0Exception;
import com.auth0.json.mgmt.users.User;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiAuth0Tenant;
import org.broadinstitute.ddp.db.dao.JdbiSendgridConfiguration;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.db.dto.Auth0TenantDto;
import org.broadinstitute.ddp.db.dto.EnrollmentStatusDto;
import org.broadinstitute.ddp.db.dto.SendgridConfigurationDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.broadinstitute.ddp.util.Auth0Util;
import org.broadinstitute.ddp.util.DdpParticipantSendGridEmailPersonalization;
import org.broadinstitute.ddp.util.SendGridMailUtil;
import org.jdbi.v3.core.Handle;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Useful script to help migrate existing studies.
public class StudyPasswordResetEmailGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(StudyPasswordResetEmailGenerator.class);

    public List<ProfileWithEmail> getProfileWithEmailForEmailAddresses(Handle handle,
                                                                       List<String> recipientEmailAddresses,
                                                                       String auth0Domain,
                                                                       Auth0ManagementClient mgmtClient) throws Auth0Exception {
        List<ProfileWithEmail> recipientProfiles = new ArrayList<>();
        Auth0Util auth0Util = buildAuth0Util(auth0Domain);
        for (String emailAddress : recipientEmailAddresses) {
            List<User> auth0Users = auth0Util.getAuth0UsersByEmail(
                    emailAddress,
                    mgmtClient.getToken());
            if (auth0Users.size() != 1) {
                Assert.fail(auth0Users.size() + " users for " + emailAddress);
            } else {
                User auth0User = auth0Users.iterator().next();
                Auth0TenantDto auth0TenantDto = handle.attach(JdbiAuth0Tenant.class).findByDomain(auth0Domain);
                UserDto userDto = handle.attach(JdbiUser.class).findByAuth0UserId(auth0User.getId(), auth0TenantDto.getId());
                UserProfile userProfile = handle.attach(UserProfileDao.class).findProfileByUserGuid(userDto.getUserGuid()).orElse(null);

                if (userProfile == null) {
                    throw new DaoException("Could not find profile for " + emailAddress);
                } else {
                    recipientProfiles.add(new ProfileWithEmail(userProfile, emailAddress));
                }
            }
        }
        return recipientProfiles;
    }

    /**
     * Send emails with reset links to specified addressees using the SendGrid API key associated with the study.
     * @param studyGuid the study GUID
     * @param addresseeProfiles profiles for addresees
     * @param fromEmailName the name to show in the FROM field of the emails
     * @param fromEmailAddress the email to show in the FROM field of the emails
     * @param emailSubject the subject line
     * @param redirectUrlAfterPasswordReset URL to redirect User after succesful password reset
     * @param sendgridTemplateId the SendGrid template id for the message
     * @param auth0Domain the Auth0 domain that holds the accounts
     * @param mgmtAuth0Token Auth0 token for client with necessary mgmt level grants
     * @return
     */
    public boolean sendPasswordResetEmails(String studyGuid, List<ProfileWithEmail> addresseeProfiles, String fromEmailName,
                                           String fromEmailAddress, String emailSubject, String redirectUrlAfterPasswordReset,
                                           String sendgridTemplateId, String auth0Domain, String mgmtAuth0Token) {
        final Auth0Util auth0Util = buildAuth0Util(auth0Domain);
        String auth0UserNamePasswordConnectionId;
        try {
            auth0UserNamePasswordConnectionId = auth0Util.getAuth0UserNamePasswordConnectionId(mgmtAuth0Token);
        } catch (Auth0Exception e) {
            LOG.error("Could not obtain connection id", e);
            return false;
        }
        TransactionWrapper.useTxn(handle -> {
            String sendGridApiKey = getSendgridApiKey(studyGuid, handle);

            for (ProfileWithEmail profileWithEmail : addresseeProfiles) {
                UserProfile profile = profileWithEmail.getProfile();
                String userEmail = profileWithEmail.getEmailAddress();
                if (userEmail == null) {
                    LOG.error("Could not look up email address for user with id: " + profile.getUserId());
                    continue;
                }

                if (StringUtils.isBlank(profile.getLastName()) || StringUtils.isBlank(profile.getFirstName())) {
                    LOG.error("Missing full name for user with with email: " + userEmail + " and user id: " + profile.getUserId());
                    continue;
                }

                String originalAuth0ResetLink;
                try {
                    originalAuth0ResetLink = auth0Util.generatePasswordResetLink(userEmail, auth0UserNamePasswordConnectionId,
                            mgmtAuth0Token, redirectUrlAfterPasswordReset);
                } catch (Auth0Exception e) {
                    LOG.error("Could not generate password reset link for email: " + userEmail + " and user id: " + profile.getUserId());
                    continue;
                }

                String resetLinkWithStudyParam = addParamToUrlString(originalAuth0ResetLink, "study", studyGuid);

                Map<String, String> templateSubstitutions = new DdpParticipantSendGridEmailPersonalization()
                        .setLinkValue(resetLinkWithStudyParam)
                        .setParticipantFirstName(profile.getFirstName())
                        .setBaseWebUrl(redirectUrlAfterPasswordReset)
                        .toMap();

                sendEmailMessage(fromEmailName, fromEmailAddress, emailSubject, sendgridTemplateId, sendGridApiKey,
                        profileWithEmail.getFullName(), userEmail, templateSubstitutions);
            }
        });

        return true;
    }

    /**
     * Send emails with reset links to all the participants in a study
     * @param studyGuid the study GUID
     * @param userEmailBlackList list of user emails that should be excluded. SHOULD BE IN LOWERCASE!
     * @param fromEmailName the name to show in the FROM field of the emails
     * @param fromEmailAddress the email to show in the FROM field of the emails
     * @param emailSubject the subject line
     * @param redirectUrlAfterPasswordReset URL to redirect User after succesful password reset
     * @param sendgridTemplateId the SendGrid template id for the message
     * @param auth0Domain the Auth0 domain that holds the accounts
     * @param mgmtAuth0Token Auth0 token for client with necessary mgmt level grants
     * @return true if got to end, false otherwise
     */
    public boolean sendPasswordResetEmails(String studyGuid, Set<String> userEmailBlackList, String fromEmailName, String fromEmailAddress,
                                           String emailSubject, String redirectUrlAfterPasswordReset, String sendgridTemplateId,
                                           String auth0Domain, String mgmtAuth0Token) {
        final Auth0Util auth0Util = buildAuth0Util(auth0Domain);
        List<ProfileWithEmail> profilesAndEmailsMinusBlackList = TransactionWrapper.withTxn(handle ->
                getProfilesWithEmailsExcludingBlacklisted(studyGuid, userEmailBlackList, mgmtAuth0Token, auth0Util, handle));

        return sendPasswordResetEmails(studyGuid, profilesAndEmailsMinusBlackList, fromEmailName, fromEmailAddress, emailSubject,
                redirectUrlAfterPasswordReset, sendgridTemplateId, auth0Domain, mgmtAuth0Token
        );
    }

    void sendEmailMessage(String fromEmailName, String fromEmailAddress, String emailSubject, String sendgridTemplateId,
                          String sendGridApiKey, String toEmailName, String toUserEmail, Map<String, String> templateSubstitutions) {
        SendGridMailUtil.sendEmailMessage(fromEmailName, fromEmailAddress, toEmailName, toUserEmail, emailSubject, sendgridTemplateId,
                templateSubstitutions, sendGridApiKey);
    }

    private List<ProfileWithEmail> getProfilesWithEmailsExcludingBlacklisted(String studyGuid, Set<String> userEmailBlackList,
                                                                             String mgmtAuth0Token, Auth0Util auth0Util, Handle handle) {
        List<ProfileWithEmail> profilesAndEmailFromDb = findUserProfilesForParticipantsNotExitedThatCanBeContacted(studyGuid, auth0Util,
                mgmtAuth0Token,
                handle);

        return profilesAndEmailFromDb.stream()
                .filter(profileWithEmail -> profileWithEmail.getEmailAddress() != null
                        && !(userEmailBlackList.contains(profileWithEmail.getEmailAddress().toLowerCase())))
                .collect(toList());
    }

    Auth0Util buildAuth0Util(String auth0Domain) {
        return new Auth0Util(auth0Domain);
    }

    List<ProfileWithEmail> findUserProfilesForParticipantsNotExitedThatCanBeContacted(String studyGuid,
                                                                                              Auth0Util auth0Util,
                                                                                              String auth0MgmtToken,
                                                                                              Handle handle) {
        List<EnrollmentStatusDto> allStudyEnrollments = handle.attach(JdbiUserStudyEnrollment.class).findByStudyGuid(studyGuid);

        final UserProfileDao profileDao = handle.attach(UserProfileDao.class);
        final JdbiUser userDao = handle.attach(JdbiUser.class);
        return allStudyEnrollments.stream()
                .filter(each -> !each.getEnrollmentStatus().isExited() && each.getEnrollmentStatus().shouldReceiveCommunications())
                .map(userEnrollment -> {
                    UserProfile profile = profileDao.findProfileByUserId(userEnrollment.getUserId()).orElse(null);
                    UserDto userDto = userDao.findByUserId(userEnrollment.getUserId());
                    String userEmail = userDto != null ? getUserEmail(userDto.getAuth0UserId(), auth0Util, auth0MgmtToken) : null;
                    return new ProfileWithEmail(profile, userEmail);
                })
                .filter(profileWithEmail -> !profileWithEmail.getProfile().getDoNotContact())
                .collect(toList());
    }

    String getUserEmail(String auth0UserId, Auth0Util auth0Util, String mgmtAuth0Token) {

        try {
            User auth0User = auth0Util.getAuth0User(auth0UserId, mgmtAuth0Token);
            if (auth0User == null) {
                LOG.error("Could not retrieve the Auth0User info for auth0UserId=" + auth0UserId);
                return null;
            } else {
                if (StringUtils.isBlank(auth0User.getEmail())) {
                    LOG.error("Could not find email address in Auth0 for auth0UserId=" + auth0UserId);
                    return null;
                } else {
                    return auth0User.getEmail();
                }
            }
        } catch (Auth0Exception e) {
            throw new DDPException("Auth0 user lookup failed", e);
        }
    }

    String getSendgridApiKey(String studyGuid, Handle apisHandle) {
        SendgridConfigurationDto sendgridConf = apisHandle.attach(JdbiSendgridConfiguration.class)
                .findByStudyGuid(studyGuid).orElseThrow(
                        () -> {
                            return new DDPException("No Sendgrid configuration exists for the study" + " with GUID " + studyGuid);
                        }
                );

        String sendgridApiKey = sendgridConf.getApiKey();
        if (sendgridApiKey == null) {
            throw new DDPException("No Sendgrid API key exists for the study guid " + studyGuid);
        }
        return sendgridApiKey;
    }

    private String addParamToUrlString(String urlString, String paramName, String paramVal) {
        try {
            URIBuilder builder = new URIBuilder(urlString);
            builder.addParameter(paramName, paramVal);
            return builder.build().toString();
        } catch (URISyntaxException e) {
            throw new DDPException("Problem processing URI for resetlink: " + urlString, e);
        }
    }
}
