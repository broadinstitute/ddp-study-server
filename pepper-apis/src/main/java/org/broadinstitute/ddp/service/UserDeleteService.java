package org.broadinstitute.ddp.service;

import static java.lang.String.format;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.AnswerSql;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dao.DsmKitRequestDao;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstanceStatus;
import org.broadinstitute.ddp.db.dao.JdbiEventConfigurationOccurrenceCounter;
import org.broadinstitute.ddp.db.dao.JdbiMailAddress;
import org.broadinstitute.ddp.db.dao.JdbiMedicalProvider;
import org.broadinstitute.ddp.db.dao.JdbiQueuedEvent;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiTempMailAddress;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyLegacyData;
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
 * <li><b>full deletion</b> - (to be called from PubSub) this kind of deletion deletes all user's data
 * (including the auth0 account).</li>
 * </ul>
 *
 * <p><b>Algorithm:</b>
 * <ul>
 *  <li>check if a user not refers to any governed user (if any references exist - cancel deletion);</li>
 *  <li>delete auth0 data;</li>
 *  <li>delete kit requests data;</li>
 *  <li>delete user_study_legacy_data;</li>
 *  <li>delete user_announcement;</li>
 *  <li>delete activity_instance_status;</li>
 *  <li>delete from answer where operator_user_id in (select user_id from delete_users);</li>
 *  <li>delete user_profile;</li>
 *  <li>delete user_medical_provider;</li>
 *  <li>delete user_study_enrollment;</li>
 *  <li>delete temp_mailing_address, default_mailing_address, mailing_address;</li>
 *  <li>delete picklist_option__answer, agreement_answer, text_answer, boolean_answer, date_answer, composite_answer_item, answer;</li>
 *  <li>delete activity_instance_status;</li>
 *  <li>delete activity_instance;</li>
 *  <li>delete event_configuration_occurrence_counter;</li>
 *  <li>delete queued_notification_template_substitution;</li>
 *  <li>delete queued_notification;</li>
 *  <li>delete queued_event;</li>
 *  <li>delete user_governance;</li>
 *  <li>delete age_up_candidate;</li>
 *  <li>delete user;</li>
 *  <li>delete ES data;</li>
 *  <li>delete auth0;</li>
 *  <li>send data-sync request.</li>
 * </ul>
 */
public class UserDeleteService {

    private static final Logger LOG = LoggerFactory.getLogger(UserDeleteService.class);

    private static final String REQUEST_TYPE = "_doc";

    private final RestHighLevelClient esClient;

    private static final String ERROR_PREFIX_USER_DELETE = "User [guid=%s] deletion is FAILED: ";

    public UserDeleteService(RestHighLevelClient esClient) {
        this.esClient = esClient;
    }

    /**
     * Used to delete a user which still not registered in auth0-system, not governed by others than a user
     * who deletes this user, not having status ENROLLED.
     */
    public void simpleDelete(Handle handle, User user) throws IOException {
        delete(handle, user, false);
    }

    /**
     * User full delete (including auth0 data). Cannot be deleted if have governed users (such users should
     * be deleted first).
     */
    public void fullDelete(Handle handle, User user) throws IOException {
        delete(handle, user, true);
    }

    private void delete(Handle handle, User user, boolean fullDelete) throws IOException {
        LOG.info("User {} deletion is STARTED. guid:{}", fullDelete ? "FULL" : "SIMPLE", user.getGuid());
        if (fullDelete) {
            checkBeforeDelete(handle, user);
        }
        LogInfo logInfo = deleteUserSteps(handle, user, fullDelete);
        LOG.warn("User {} deletion is completed SUCCESSFULLY. "
                + "guid:{}, hruid:{}, e-mail:{}, name:{} {}, dob:{} "
                + "\nDeleted data:\n{}",
                fullDelete ? "FULL" : "SIMPLE",
                user.getGuid(),
                user.getHruid(),
                user.getEmail(),
                user.getProfile() != null ? user.getProfile().getFirstName() : "",
                user.getProfile() != null ? user.getProfile().getLastName() : "",
                user.getProfile() != null ? user.getProfile().getBirthDate() : "",
                logInfo.getInfo());
    }

    private void checkBeforeDelete(Handle handle, User user) {
        if (hasGovernedUsers(handle, user.getGuid())) {
            throw new DDPException(format(ERROR_PREFIX_USER_DELETE + "the user has governed users", user.getGuid()));
        }

        // check if user refers to revisions
        long[] revisionIds = handle.attach(JdbiRevision.class).findByUserId(user.getId());
        if (revisionIds.length > 0) {
            throw new DDPException(format(ERROR_PREFIX_USER_DELETE + "the user has references to a revision history", user.getGuid()));
        }
    }

    private LogInfo deleteUserSteps(Handle handle, User user, boolean fullDelete) throws IOException {
        LogInfo logInfo = new LogInfo();
        UserCollectedData userCollectedData = new UserCollectedData();

        if (fullDelete) {
            deleteKitRequests(handle, user, logInfo);
            deleteUserStudyLegacyData(handle, user, logInfo);
            deleteUserAnnouncement(handle, user, logInfo);
            deleteActivityInstanceStatusByOperator(handle, user, logInfo);
            deleteAnswersByOperator(handle, user, logInfo);
        }

        deleteUserProfile(handle, user, logInfo);
        deleteMedicalProvider(handle, user, logInfo);
        deleteEnrollmentStatuses(handle, user, logInfo);
        deleteUserAddresses(handle, user, logInfo);
        deleteAnswersAndActivityInstances(handle, user, logInfo);
        deleteCountersAndEvents(handle, user, logInfo);
        deleteGovernances(handle, user, logInfo, userCollectedData);
        deleteAgeUpCandidates(handle, user, logInfo);
        deleteUser(handle, user, logInfo);
        deleteElasticSearchData(handle, user, logInfo, userCollectedData);

        if (fullDelete) {
            deleteAuth0User(handle, user, logInfo);
        }

        dataSyncRequest(handle, userCollectedData);

        return logInfo;
    }

    private void deleteKitRequests(Handle handle, User user, LogInfo logInfo) {
        logInfo.add("kit_request");
        handle.attach(DsmKitRequestDao.class).deleteKitRequestByParticipantId(user.getId());
    }

    private void deleteUserStudyLegacyData(Handle handle, User user, LogInfo logInfo) {
        logInfo.add("user_study_legacy_data");
        handle.attach(JdbiUserStudyLegacyData.class).deleteByUserId(user.getId());
    }

    private void deleteUserAnnouncement(Handle handle, User user, LogInfo logInfo) {
        logInfo.add("user_announcement");
        handle.attach(UserAnnouncementDao.class).deleteAllForUser(user.getId());
    }

    private void deleteActivityInstanceStatusByOperator(Handle handle, User user, LogInfo logInfo) {
        logInfo.add("activity_instance_status (by operator_id)");
        handle.attach(JdbiActivityInstanceStatus.class).deleteStatusByOperatorId(user.getId());
    }

    private void deleteAnswersByOperator(Handle handle, User user, LogInfo logInfo) {
        logInfo.add("answer (by operator_user_id)");
        handle.attach(AnswerSql.class).deleteAnswerByOperatorId(user.getId());
    }

    private void deleteUserProfile(Handle handle, User user, LogInfo logInfo) {
        logInfo.add("user_profile");
        handle.attach(UserProfileDao.class).getUserProfileSql().deleteByUserId(user.getId());
    }

    private void deleteMedicalProvider(Handle handle, User user, LogInfo logInfo) {
        logInfo.add("user_medical_provider");
        handle.attach(JdbiMedicalProvider.class).deleteByUserId(user.getId());
    }

    private void deleteEnrollmentStatuses(Handle handle, User user, LogInfo logInfo) {
        logInfo.add("user_study_enrollment");
        handle.attach(JdbiUserStudyEnrollment.class).deleteByUserId(user.getId());
    }

    private void deleteUserAddresses(Handle handle, User user, LogInfo logInfo) {
        logInfo.add("temp_mailing_address, default_mailing_address, mailing_address");
        handle.attach(JdbiTempMailAddress.class).deleteTempAddressByParticipantId(user.getId());
        JdbiMailAddress mailAddressDao = handle.attach(JdbiMailAddress.class);
        mailAddressDao.deleteDefaultAddressByParticipantId(user.getId());
        mailAddressDao.deleteAddressByParticipantId(user.getId());
    }

    private void deleteAnswersAndActivityInstances(Handle handle, User user, LogInfo logInfo) {
        logInfo.add("answer, activity_instance_status (by activity_instance_id), activity_instance");
        ActivityInstanceDao instanceDao = handle.attach(ActivityInstanceDao.class);
        List<ActivityInstanceDto> instances = instanceDao.findAllInstancesByUserIds(Collections.singleton(user.getId()));
        instanceDao.deleteInstances(instances);
    }

    private void deleteCountersAndEvents(Handle handle, User user, LogInfo logInfo) {
        logInfo.add("event_configuration_occurrence_counter, queued_event");
        handle.attach(JdbiEventConfigurationOccurrenceCounter.class).deleteAllByParticipantId(user.getId());
        JdbiQueuedEvent queueEvent = handle.attach(JdbiQueuedEvent.class);
        queueEvent.deleteQueuedNotificationSubstitutionsByUserIds(Collections.singleton(user.getId()));
        queueEvent.deleteQueuedNotificationsByUserIds(Collections.singleton(user.getId()));
        queueEvent.deleteQueuedEventsByUserIds(Collections.singleton(user.getId()));
    }

    private void deleteGovernances(Handle handle, User user, LogInfo logInfo, UserCollectedData userCollectedData) {
        logInfo.add("user_governance (by operator_user_id), user_governance (by participant_user_id)");
        UserGovernanceDao userGovernanceDao = handle.attach(UserGovernanceDao.class);
        List<Governance> userGovernances = userGovernanceDao.findGovernancesByParticipantGuid(user.getGuid()).collect(Collectors.toList());
        Set<String> studyGuids = userGovernances.stream()
                .map(Governance::getGrantedStudies).flatMap(Collection::stream).map(GrantedStudy::getStudyGuid)
                .collect(Collectors.toSet());
        userGovernanceDao.deleteAllGovernancesForProxy(user.getId());
        userGovernanceDao.deleteAllGovernancesForParticipant(user.getId());

        userCollectedData.setStudyGuids(studyGuids);
        userCollectedData.setUserGovernances(userGovernances);
    }

    private void deleteAgeUpCandidates(Handle handle, User user, LogInfo logInfo) {
        logInfo.add("age_up_candidate");
        handle.attach(StudyGovernanceSql.class).deleteAgeUpCandidateByParticipantId(user.getId());
    }

    private void deleteUser(Handle handle, User user, LogInfo logInfo) {
        logInfo.add("data_sync_request, user");
        handle.attach(DataExportDao.class).deleteDataSyncRequestsForUser(user.getId());
        handle.attach(JdbiUser.class).deleteAllByIds(Collections.singleton(user.getId()));
    }

    private void deleteElasticSearchData(Handle handle, User user, LogInfo logInfo, UserCollectedData userCollectedData)
            throws IOException {
        if (esClient != null) {
            logInfo.add("ES indices: participants, participants_structured, users");
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
                LOG.error(bulkResponse.buildFailureMessage());
            }
        }
    }

    private void deleteAuth0User(Handle handle, User user, LogInfo logInfo) {
        if (user.getAuth0UserId() != null) {
            logInfo.add("auth0 data");
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
                .orElseThrow(() -> new DDPException(format(ERROR_PREFIX_USER_DELETE + "the user not found", userGuid)));
    }

    /**
     * Check if user has governed users
     */
    public static boolean hasGovernedUsers(Handle handle, String userGuid) {
        UserGovernanceDao userGovernanceDao = handle.attach(UserGovernanceDao.class);
        return userGovernanceDao.findActiveGovernancesByProxyGuid(userGuid).count() > 0;
    }

    private static class LogInfo {
        final StringBuilder logInfo = new StringBuilder();

        void add(String info) {
            logInfo.append(" -- ").append(info).append('\n');
        }

        String getInfo() {
            return logInfo.toString();
        }
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
