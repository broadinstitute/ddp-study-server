package org.broadinstitute.ddp.service;

import static java.lang.String.format;
import static org.broadinstitute.ddp.util.MiscUtil.objToStr;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.AuthDao;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dao.DsmKitRequestDao;
import org.broadinstitute.ddp.db.dao.FileUploadDao;
import org.broadinstitute.ddp.db.dao.InvitationSql;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstanceStatus;
import org.broadinstitute.ddp.db.dao.JdbiEventConfigurationOccurrenceCounter;
import org.broadinstitute.ddp.db.dao.JdbiMailAddress;
import org.broadinstitute.ddp.db.dao.JdbiMedicalProvider;
import org.broadinstitute.ddp.db.dao.JdbiQueuedEvent;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiTempMailAddress;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.JdbiUserLegacyInfo;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyLegacyData;
import org.broadinstitute.ddp.db.dao.KitScheduleSql;
import org.broadinstitute.ddp.db.dao.StudyDao;
import org.broadinstitute.ddp.db.dao.StudyGovernanceSql;
import org.broadinstitute.ddp.db.dao.UserAnnouncementDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dao.UserGovernanceDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.elastic.ElasticSearchIndexType;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.governance.Governance;
import org.broadinstitute.ddp.model.governance.GrantedStudy;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.util.ElasticsearchServiceUtil;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deletion of a specified user and data connected to it.
 * It can be:
 * <ul>
 * <li><b>simple deletion</b> - (to be called from a rest service) this kind of deletion deletes not all
 * data. And before deletion it checks a possibility to delete (for example if a user connected to
 * auth0 account - if yes, then cannot be deleted).</li>
 *
 * <li><b>full deletion</b> - (to be called from PubSub) this kind of deletion deletes all user's data
 * (including the auth0 account).</li>
 * </ul>
 */
public class UserDeleteService {

    private static final Logger LOG = LoggerFactory.getLogger(UserDeleteService.class);

    private static final String REQUEST_TYPE = "_doc";

    private final RestHighLevelClient esClient;

    private static final String USER_DELETION_MESSAGE_PREFIX = "User [guid={}] deletion";
    private static final String LOG_MESSAGE_PREFIX__DELETE_FROM_TABLE = USER_DELETION_MESSAGE_PREFIX + ". From table(s): ";
    private static final String LOG_MESSAGE_PREFIX__DELETE_FROM_ES = USER_DELETION_MESSAGE_PREFIX + ". From ES indices: ";
    private static final String LOG_MESSAGE_PREFIX__DELETE_FROM_AUTH = USER_DELETION_MESSAGE_PREFIX + ". Auth0 data";

    private static final String EXCEPTION_MESSAGE_PREFIX__ERROR = "User [guid=%s] deletion is FAILED: ";

    public UserDeleteService(RestHighLevelClient esClient) {
        this.esClient = esClient;
    }

    
    /**
     * Used to delete a user which still not registered in auth0-system, not governed by others than a user
     * who deletes this user, not having status ENROLLED.
     *
     * @param handle     JDBI handle
     * @param user       User to be deleted
     * @param whoDeleted information about a person who deleted a user (for example it can be an operatorGuid)
     * @param comment    some additional comments if needed
     */
    public void simpleDelete(Handle handle, User user, String whoDeleted, String comment) throws IOException {
        delete(handle, user, whoDeleted, comment, false);
    }

    /**
     * User full delete (including auth0 data). Cannot be deleted if have governed users (such users should
     * be deleted first).
     *
     * @param handle     JDBI handle
     * @param user       User to be deleted
     * @param whoDeleted information about a person who deleted a user (for example it can be an operatorGuid)
     * @param comment    some additional comments if needed
     */
    public void fullDelete(Handle handle, User user, String whoDeleted, String comment) throws IOException {
        delete(handle, user, whoDeleted, comment, true);
    }

    private void delete(Handle handle, User user, String whoDeleted, String comment, boolean fullDelete) throws IOException {
        LOG.info("User {} deletion is STARTED. guid:{}", fullDelete ? "FULL" : "SIMPLE", user.getGuid());
        if (fullDelete) {
            checkBeforeDelete(handle, user);
        }
        try {
            deleteUserData(handle, user, fullDelete);
            LOG.warn("User {} deletion is completed SUCCESSFULLY. "
                            + "guid:{}, hruid:{}, e-mail:{}, name:{} {}, dob:{}\n"
                            + "Who deleted: {}\n"
                            + "Comment: {}\n",
                    fullDelete ? "FULL" : "SIMPLE",
                    user.getGuid(),
                    user.getHruid(),
                    user.getEmail(),
                    user.getProfile() != null ? objToStr(user.getProfile().getFirstName()) : "",
                    user.getProfile() != null ? objToStr(user.getProfile().getLastName()) : "",
                    user.getProfile() != null ? objToStr(user.getProfile().getBirthDate()) : "",
                    objToStr(whoDeleted),
                    objToStr(comment));
        } catch (Throwable e) {
            LOG.error(format(EXCEPTION_MESSAGE_PREFIX__ERROR + e.getMessage(), user.getGuid()), e);
            throw new DDPException(format(EXCEPTION_MESSAGE_PREFIX__ERROR + e.getMessage(), user.getGuid()));
        }
    }

    private void checkBeforeDelete(Handle handle, User user) {
        if (hasGovernedUsers(handle, user.getGuid())) {
            throw new DDPException(format(EXCEPTION_MESSAGE_PREFIX__ERROR + "the user has governed users", user.getGuid()));
        }

        // check if user refers to revisions
        long[] revisionIds = handle.attach(JdbiRevision.class).findByUserId(user.getId());
        if (revisionIds.length > 0) {
            throw new DDPException(format(EXCEPTION_MESSAGE_PREFIX__ERROR + "the user has references to a revision history",
                    user.getGuid()));
        }
    }

    private void deleteUserData(Handle handle, User user, boolean fullDelete) throws IOException {
        UserCollectedData userCollectedData = new UserCollectedData();

        if (fullDelete) {
            deleteKitRequests(handle, user);
            deleteUserStudyLegacyData(handle, user);
            deleteUserAnnouncement(handle, user);
            deleteActivityInstanceStatusByOperator(handle, user);
            deleteInvitation(handle, user);
            deleteStudyAdmin(handle, user);
            deleteStudyExitRequest(handle, user);
            deleteUserLegacyInfo(handle, user);
        }

        deleteUserProfile(handle, user);
        deleteMedicalProvider(handle, user);
        deleteEnrollmentStatuses(handle, user);
        deleteUserAddresses(handle, user);
        deleteParticipantAnswersAndActivityInstances(handle, user);
        deleteActivityInstanceCreationMutex(handle, user);
        deleteFileUpload(handle, user);
        deleteCountersAndEvents(handle, user);
        deleteGovernances(handle, user, userCollectedData);
        deleteAgeUpCandidates(handle, user);
        deleteDataSyncRequest(handle, user);
        deleteUser(handle, user);
        deleteElasticSearchData(handle, user, userCollectedData, fullDelete);

        if (fullDelete) {
            deleteAuth0User(handle, user);
        }

        dataSyncRequest(handle, userCollectedData);
    }

    private void deleteKitRequests(Handle handle, User user) {
        log("kit_request", user);
        handle.attach(DsmKitRequestDao.class).deleteKitRequestByParticipantId(user.getId());

        log("kit_schedule_record", user);
        handle.attach(KitScheduleSql.class).deleteByUserId(user.getId());
    }

    private void deleteUserStudyLegacyData(Handle handle, User user) {
        log("user_study_legacy_data", user);
        handle.attach(JdbiUserStudyLegacyData.class).deleteByUserId(user.getId());
    }

    private void deleteUserAnnouncement(Handle handle, User user) {
        log("user_announcement", user);
        handle.attach(UserAnnouncementDao.class).deleteAllForUser(user.getId());
    }

    private void deleteActivityInstanceStatusByOperator(Handle handle, User user) {
        log("activity_instance_status (by operator_id)", user);
        handle.attach(JdbiActivityInstanceStatus.class).deleteStatusByOperatorId(user.getId());
    }

    private void deleteUserProfile(Handle handle, User user) {
        log("user_profile", user);
        handle.attach(UserProfileDao.class).getUserProfileSql().deleteByUserId(user.getId());
    }

    private void deleteMedicalProvider(Handle handle, User user) {
        log("user_medical_provider", user);
        handle.attach(JdbiMedicalProvider.class).deleteByUserId(user.getId());
    }

    private void deleteEnrollmentStatuses(Handle handle, User user) {
        log("user_study_enrollment", user);
        handle.attach(JdbiUserStudyEnrollment.class).deleteByUserId(user.getId());
    }

    private void deleteUserAddresses(Handle handle, User user) {
        log("temp_mailing_address", user);
        handle.attach(JdbiTempMailAddress.class).deleteTempAddressByParticipantId(user.getId());

        log("default_mailing_address", user);
        JdbiMailAddress mailAddressDao = handle.attach(JdbiMailAddress.class);
        mailAddressDao.deleteDefaultAddressByParticipantId(user.getId());

        log("mailing_address", user);
        mailAddressDao.deleteAddressByParticipantId(user.getId());
    }

    private void deleteParticipantAnswersAndActivityInstances(Handle handle, User user) {
        log("answer, activity_instance_status, activity_instance", user);
        ActivityInstanceDao instanceDao = handle.attach(ActivityInstanceDao.class);
        List<ActivityInstanceDto> instances = instanceDao.findAllInstancesByUserIds(Collections.singleton(user.getId()));
        instanceDao.deleteInstances(instances);
    }

    private void deleteActivityInstanceCreationMutex(Handle handle, User user) {
        log("activity_instance_creation_mutex", user);
        handle.attach(ActivityInstanceDao.class).deleteActivityInstanceCreationMutex(user.getId());
    }

    private void deleteCountersAndEvents(Handle handle, User user) {
        log("event_configuration_occurrence_counter", user);
        handle.attach(JdbiEventConfigurationOccurrenceCounter.class).deleteAllByParticipantId(user.getId());
        JdbiQueuedEvent queueEvent = handle.attach(JdbiQueuedEvent.class);
        queueEvent.deleteQueuedNotificationSubstitutionsByUserIds(Collections.singleton(user.getId()));

        log("queued_notification", user);
        queueEvent.deleteQueuedNotificationsByUserIds(Collections.singleton(user.getId()));

        log("queued_event", user);
        queueEvent.deleteQueuedEventsByUserIds(Collections.singleton(user.getId()));
    }

    private void deleteGovernances(Handle handle, User user, UserCollectedData userCollectedData) {
        UserGovernanceDao userGovernanceDao = handle.attach(UserGovernanceDao.class);
        List<Governance> userGovernances = userGovernanceDao.findGovernancesByParticipantGuid(user.getGuid()).collect(Collectors.toList());
        Set<String> studyGuids = userGovernances.stream()
                .map(Governance::getGrantedStudies).flatMap(Collection::stream).map(GrantedStudy::getStudyGuid)
                .collect(Collectors.toSet());

        log("user_governance (by operator_user_id)", user);
        userGovernanceDao.deleteAllGovernancesForProxy(user.getId());

        log("user_governance (by participant_user_id)", user);
        userGovernanceDao.deleteAllGovernancesForParticipant(user.getId());

        userCollectedData.setStudyGuids(studyGuids);
        userCollectedData.setUserGovernances(userGovernances);
    }

    private void deleteAgeUpCandidates(Handle handle, User user) {
        log("age_up_candidate", user);
        handle.attach(StudyGovernanceSql.class).deleteAgeUpCandidateByParticipantId(user.getId());
    }

    private void deleteFileUpload(Handle handle, User user) {
        log("file_upload", user);
        handle.attach(FileUploadDao.class).deleteByParticipantOrOperatorId(user.getId());
    }

    private void deleteInvitation(Handle handle, User user) {
        log("invitation", user);
        handle.attach(InvitationSql.class).deleteByUserId(user.getId());
    }

    private void deleteStudyAdmin(Handle handle, User user) {
        log("invitation", user);
        handle.attach(AuthDao.class).removeAdminFromAllStudies(user.getId());
    }

    private void deleteStudyExitRequest(Handle handle, User user) {
        log("study_exit_request", user);
        handle.attach(StudyDao.class).deleteExitRequest(user.getId());
    }

    private void deleteUserLegacyInfo(Handle handle, User user) {
        log("user_legacy_info", user);
        handle.attach(JdbiUserLegacyInfo.class).deleteByUserId(user.getId());
    }

    private void deleteDataSyncRequest(Handle handle, User user) {
        log("data_sync_request", user);
        handle.attach(DataExportDao.class).deleteDataSyncRequestsForUser(user.getId());
    }

    private void deleteUser(Handle handle, User user) {
        log("user", user);
        handle.attach(JdbiUser.class).deleteAllByIds(Collections.singleton(user.getId()));
    }

    private void deleteElasticSearchData(Handle handle, User user, UserCollectedData userCollectedData, boolean fullDelete)
            throws IOException {
        if (esClient != null && userCollectedData.getStudyGuids().size() > 0) {
            LOG.info(LOG_MESSAGE_PREFIX__DELETE_FROM_ES + "participants, participants_structured, users", user.getGuid());
            BulkRequest bulkRequest = new BulkRequest().timeout("2m");
            for (String studyGuid : userCollectedData.getStudyGuids()) {
                StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);

                String indexParticipant = ElasticsearchServiceUtil.getIndexForStudy(handle, studyDto,
                        ElasticSearchIndexType.PARTICIPANTS);
                String indexParticipantStructured = ElasticsearchServiceUtil.getIndexForStudy(handle, studyDto,
                        ElasticSearchIndexType.PARTICIPANTS_STRUCTURED);
                String indexUsers = ElasticsearchServiceUtil.getIndexForStudy(handle, studyDto,
                        ElasticSearchIndexType.USERS);

                bulkRequest.add(new DeleteRequest()
                        .index(indexParticipant)
                        .type(REQUEST_TYPE)
                        .id(user.getGuid()));

                bulkRequest.add(new DeleteRequest()
                        .index(indexParticipantStructured)
                        .type(REQUEST_TYPE)
                        .id(user.getGuid()));

                bulkRequest.add(new DeleteRequest()
                        .index(indexUsers)
                        .type(REQUEST_TYPE)
                        .id(user.getGuid()));
            }

            BulkResponse bulkResponse = esClient.bulk(bulkRequest, RequestOptions.DEFAULT);

            if (bulkResponse != null && bulkResponse.hasFailures()) {
                if (fullDelete) {
                    throw new DDPException(bulkResponse.buildFailureMessage());
                } else {
                    LOG.error(bulkResponse.buildFailureMessage());
                }
            }
        }
    }

    private void deleteAuth0User(Handle handle, User user) {
        if (user.getAuth0UserId() != null) {
            LOG.info(LOG_MESSAGE_PREFIX__DELETE_FROM_AUTH, user.getGuid());
            var result = Auth0ManagementClient.forUser(handle, user.getGuid()).deleteAuth0User(user.getAuth0UserId());
            if (result.hasFailure()) {
                throw new DDPException(result.hasThrown() ? result.getThrown() : result.getError());
            }
        }
    }

    private void dataSyncRequest(Handle handle, UserCollectedData userCollectedData) {
        DataExportDao dataExportDao = handle.attach(DataExportDao.class);
        userCollectedData.getUserGovernances().forEach(governance -> governance.getGrantedStudies().forEach(
                gs -> dataExportDao.queueDataSync(governance.getProxyUserGuid(), gs.getStudyGuid())));
    }

    /**
     * Find user by GUID. If not found - throw an error
     */
    public static User getUser(Handle handle, String userGuid) {
        UserDao userDao = handle.attach(UserDao.class);
        return userDao.findUserByGuid(userGuid)
                .orElseThrow(() -> new DDPException(format(EXCEPTION_MESSAGE_PREFIX__ERROR + "the user not found", userGuid)));
    }

    /**
     * Check if user has governed users
     */
    public static boolean hasGovernedUsers(Handle handle, String userGuid) {
        UserGovernanceDao userGovernanceDao = handle.attach(UserGovernanceDao.class);
        return userGovernanceDao.findActiveGovernancesByProxyGuid(userGuid).count() > 0;
    }

    private static void log(String tablesToDelete, User user) {
        LOG.info(LOG_MESSAGE_PREFIX__DELETE_FROM_TABLE + tablesToDelete, user.getGuid());
    }

    private static class UserCollectedData {

        Set<String> studyGuids;
        List<Governance> userGovernances;

        public Set<String> getStudyGuids() {
            return studyGuids;
        }

        public void setStudyGuids(Set<String> studyGuids) {
            this.studyGuids = studyGuids;
        }

        public List<Governance> getUserGovernances() {
            return userGovernances;
        }

        public void setUserGovernances(List<Governance> userGovernances) {
            this.userGovernances = userGovernances;
        }
    }
}
