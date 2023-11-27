package org.broadinstitute.ddp.migration;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import com.auth0.exception.Auth0Exception;
import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiClient;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.db.dto.ClientDto;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.broadinstitute.ddp.util.Auth0Util;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.jdbi.v3.core.Handle;

@Slf4j
class UserLoader {
    private static final SecureRandom PW_RANDOMIZER = NanoIdUtils.DEFAULT_NUMBER_GENERATOR;
    private static final String PW_SPECIALS = "!@#$%^&*";
    private static final char[] PW_NUMS = "0123456789".toCharArray();
    private static final char[] PW_UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final char[] PW_LOWER = "abcdefghijklmnopqrstuvwxyz".toCharArray();

    private final Config cfg;
    private final ClientDto clientDto;
    private final Auth0Util auth0Util;
    private final Auth0ManagementClient mgmtClient;

    UserLoader(Config cfg) {
        this.cfg = cfg;
        this.clientDto = initAuth0ClientDto();
        this.mgmtClient = initManagementClient();
        this.auth0Util = new Auth0Util(clientDto.getAuth0Domain());
    }

    private ClientDto initAuth0ClientDto() {
        String auth0Domain = cfg.getString(LoaderConfigFile.AUTH0_DOMAIN);
        String auth0ClientId = cfg.getString(LoaderConfigFile.AUTH0_CLIENT_ID);
        var clientDto = TransactionWrapper.withTxn(handle -> handle
                .attach(JdbiClient.class)
                .getClientByAuth0ClientAndDomain(auth0ClientId, auth0Domain)
                .orElseThrow(() -> new LoaderException("Could not load client " + auth0ClientId)));
        log.info("Using auth0 client: id={}, tenantId={}, domain={}, clientId={}",
                clientDto.getId(), clientDto.getAuth0TenantId(), clientDto.getAuth0Domain(), clientDto.getAuth0ClientId());
        return clientDto;
    }

    private Auth0ManagementClient initManagementClient() {
        return TransactionWrapper.withTxn(handle ->
                Auth0ManagementClient.forStudy(handle, cfg.getString(LoaderConfigFile.STUDY_GUID)));
    }

    public String getOrGenerateDummyEmail(MemberWrapper participant) {
        String dummyEmail = ConfigUtil.getStrIfPresent(cfg, LoaderConfigFile.DUMMY_EMAIL);
        if (dummyEmail != null && !dummyEmail.isBlank()) {
            String[] parts = dummyEmail.split("@");
            String separator = parts[0].contains("+") ? "." : "+";
            return String.format("%s%s%s@%s", parts[0], separator, Instant.now().toEpochMilli(), parts[1]);
        } else {
            return participant.getEmail();
        }
    }

    public User findUserByAltPid(Handle handle, String altpid) {
        String guid = handle.attach(JdbiUser.class).getUserGuidByAltpid(altpid);
        if (guid != null) {
            return handle.attach(UserDao.class).findUserByGuid(guid).orElse(null);
        } else {
            return null;
        }
    }

    public com.auth0.json.mgmt.users.User findAuth0UserByEmail(String connection, String email) {
        try {
            var users = auth0Util.getAuth0UsersByEmail(email, mgmtClient.getToken(), connection);
            if (users == null || users.isEmpty()) {
                return null;
            } else if (users.size() > 1) {
                throw new LoaderException("More than one auth0 user found for email: " + email);
            } else {
                return users.get(0);
            }
        } catch (Auth0Exception e) {
            throw new LoaderException("Error while finding user by email: " + email, e);
        }
    }

    public com.auth0.json.mgmt.users.User createAuth0Account(String connection, String email) {
        String temp = NanoIdUtils.randomNanoId(PW_RANDOMIZER, PW_NUMS, 10);
        temp += NanoIdUtils.randomNanoId(PW_RANDOMIZER, PW_UPPER, 10);
        temp += NanoIdUtils.randomNanoId(PW_RANDOMIZER, PW_LOWER, 10);
        temp += PW_SPECIALS;
        char[] chars = temp.toCharArray();
        ArrayUtils.shuffle(chars, PW_RANDOMIZER);
        String randomPassword = new String(chars);

        var result = mgmtClient.createAuth0User(connection, email, randomPassword, true);
        if (result.hasThrown()) {
            throw new LoaderException("Error while creating auth0 account for email: " + email, result.getThrown());
        } else if (result.hasError()) {
            log.error("Received auth0 response: status={}", result.getStatusCode(), result.getError());
            throw new LoaderException("Unable to create auth0 account for email: " + email);
        } else {
            return result.getBody();
        }
    }

    public boolean updateAuth0UserMetadata(com.auth0.json.mgmt.users.User auth0User, String languageCode) {
        Map<String, Object> metadata = auth0User.getUserMetadata();
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        if (metadata.containsKey(User.METADATA_LANGUAGE)) {
            return false;
        }

        metadata.put(User.METADATA_LANGUAGE, languageCode);
        var result = mgmtClient.updateUserMetadata(auth0User.getId(), metadata);
        if (result.hasThrown()) {
            throw new LoaderException("Error while updating auth0 user metadata for: " + auth0User.getId(), result.getThrown());
        } else if (result.hasError()) {
            log.error("Received auth0 response: status={}", result.getStatusCode(), result.getError());
            throw new LoaderException("Unable to update auth0 user metadata for: " + auth0User.getId());
        } else {
            return true;
        }
    }

    public User createLegacyUser(Handle handle, MemberWrapper participant, String auth0UserId, String languageCode) {
        String legacyAltPid = participant.getAltPid();
        String legacyShortId = participant.getShortId();
        long createdAtMillis = participant.getCreated().toEpochMilli();

        var userDao = handle.attach(UserDao.class);
        String userGuid = DBUtils.uniqueUserGuid(handle);
        // Per RGP's request, legacy short ids will be migrated as HRUIDs. Since hruids so far are generated
        // alphanumerics, this shouldn't cause any conflicts since legacy ones are just numbers.
        String userHruid = legacyShortId;
        long userId = userDao.getUserSql().insertByClientIdOrAuth0Ids(
                true, clientDto.getId(), null, null, auth0UserId,
                userGuid, userHruid, legacyAltPid, legacyShortId, false,
                createdAtMillis, createdAtMillis, null);

        String lastName = participant.getLastName();
        if (lastName != null && lastName.equals(participant.getEmail())) {
            lastName = ""; // RGP collects first name but not last name, so clear this if it was set to email.
        }

        var langDto = LanguageStore.get(languageCode);
        var profile = UserProfile.builder()
                .userId(userId)
                .firstName(participant.getFirstName())
                .lastName(lastName)
                .preferredLangId(langDto.getId())
                .preferredLangCode(null)
                .build();
        handle.attach(UserProfileDao.class).createProfile(profile);

        return userDao.findUserById(userId).orElseThrow(() ->
                new LoaderException("Could not find newly created user with altpid" + legacyAltPid));
    }

    public void registerUserInStudy(Handle handle, String studyGuid, String userGuid, MemberWrapper participant) {
        long registeredAt = participant.getCreated().toEpochMilli();
        handle.attach(JdbiUserStudyEnrollment.class).changeUserStudyEnrollmentStatus(
                userGuid, studyGuid, EnrollmentStatusType.REGISTERED, registeredAt);
    }

    public void withdrawUserFromStudy(Handle handle, String studyGuid, String userGuid, MemberWrapper participant) {
        // No timestamp for withdrawal so we use last modified as an approximation.
        long lastModifiedMillis = participant.getLastModified().toEpochMilli();
        handle.attach(JdbiUserStudyEnrollment.class).changeUserStudyEnrollmentStatus(
                userGuid, studyGuid, EnrollmentStatusType.EXITED_BEFORE_ENROLLMENT, lastModifiedMillis);
    }
}
