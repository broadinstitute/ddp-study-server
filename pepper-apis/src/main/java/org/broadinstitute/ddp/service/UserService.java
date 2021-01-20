package org.broadinstitute.ddp.service;

import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dao.JdbiEventConfigurationOccurrenceCounter;
import org.broadinstitute.ddp.db.dao.JdbiMailAddress;
import org.broadinstitute.ddp.db.dao.JdbiMedicalProvider;
import org.broadinstitute.ddp.db.dao.JdbiQueuedEvent;
import org.broadinstitute.ddp.db.dao.JdbiTempMailAddress;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.StudyGovernanceSql;
import org.broadinstitute.ddp.db.dao.UserGovernanceDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.elastic.ElasticSearchIndexType;
import org.broadinstitute.ddp.model.governance.Governance;
import org.broadinstitute.ddp.model.governance.GrantedStudy;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.util.ConfigManager;
import org.broadinstitute.ddp.util.ElasticsearchServiceUtil;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Entry-point for back-end operations related to users.
 */
public class UserService {

    private static final Logger LOG = LoggerFactory.getLogger(UserService.class);

    private static final String REQUEST_TYPE = "_doc";

    public UserService() {
    }

    /**
     * Deletes the user with specified id
     *
     * @param user the user to be deleted
     */
    public void deleteUser(Handle handle, User user) throws IOException {
        Long userId = user.getId();
        String userGuid = user.getGuid();
        LOG.info("Deleting user with id={}", userId);
        // Removing user profile
        handle.attach(UserProfileDao.class).getUserProfileSql().deleteByUserId(userId);
        // Removing medical provider
        handle.attach(JdbiMedicalProvider.class).deleteByUserId(userId);
        // Removing user enrollment statuses
        handle.attach(JdbiUserStudyEnrollment.class).deleteByUserId(userId);
        // Removing user addresses
        handle.attach(JdbiTempMailAddress.class).deleteTempAddressByParticipantId(userId);
        JdbiMailAddress mailAddressDao = handle.attach(JdbiMailAddress.class);
        mailAddressDao.deleteDefaultAddressByParticipantId(userId);
        mailAddressDao.deleteAddressByParticipantId(userId);
        // Removing all the answers and activity instances
        ActivityInstanceDao instanceDao = handle.attach(ActivityInstanceDao.class);
        Set<Long> instanceIds = instanceDao.findAllInstanceIdsByUserIds(Collections.singleton(userId));
        LOG.info("Found {} activity instances to delete", instanceIds.size());
        DBUtils.checkDelete(instanceIds.size(), instanceDao.deleteAllByIds(instanceIds));
        // Removing counters, events
        handle.attach(JdbiEventConfigurationOccurrenceCounter.class).deleteAllByParticipantId(userId);
        JdbiQueuedEvent queueEvent = handle.attach(JdbiQueuedEvent.class);
        queueEvent.deleteQueuedNotificationSubstitutionsByUserIds(Collections.singleton(userId));
        queueEvent.deleteQueuedNotificationsByUserIds(Collections.singleton(userId));
        queueEvent.deleteQueuedEventsByUserIds(Collections.singleton(userId));
        // Removing governances
        UserGovernanceDao userGovernanceDao = handle.attach(UserGovernanceDao.class);
        List<Governance> userGovernances = userGovernanceDao.findGovernancesByParticipantGuid(user.getGuid()).collect(Collectors.toList());
        Set<String> studyGuids = userGovernances.stream()
                .map(Governance::getGrantedStudies).flatMap(Collection::stream).map(GrantedStudy::getStudyGuid)
                .collect(Collectors.toSet());
        userGovernanceDao.deleteAllGovernancesForProxy(userId);
        userGovernanceDao.deleteAllGovernancesForParticipant(userId);
        handle.attach(StudyGovernanceSql.class).deleteAgeUpCandidateByParticipantId(userId);
        // Removing user itself
        handle.attach(DataExportDao.class).deleteDataSyncRequestsForUser(userId);
        handle.attach(JdbiUser.class).deleteAllByIds(Collections.singleton(userId));

        // Cleaning up the elasticsearch data
        RestHighLevelClient esClient = ElasticsearchServiceUtil.getElasticsearchClient(ConfigManager.getInstance().getConfig());
        BulkRequest bulkRequest = new BulkRequest().timeout("2m");
        for (String studyGuid : studyGuids) {
            StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);

            String indexParticipant = ElasticsearchServiceUtil.getIndexForStudy(handle, studyDto,
                    ElasticSearchIndexType.PARTICIPANTS);
            String indexParticipantStructured = ElasticsearchServiceUtil.getIndexForStudy(handle, studyDto,
                    ElasticSearchIndexType.PARTICIPANTS_STRUCTURED);

            bulkRequest.add(new DeleteRequest()
                    .index(indexParticipant)
                    .type(REQUEST_TYPE)
                    .id(userGuid));

            bulkRequest.add(new DeleteRequest()
                    .index(indexParticipantStructured)
                    .type(REQUEST_TYPE)
                    .id(userGuid));
        }

        BulkResponse bulkResponse = esClient.bulk(bulkRequest, RequestOptions.DEFAULT);

        if (bulkResponse.hasFailures()) {
            LOG.error(bulkResponse.buildFailureMessage());
        }
        userGovernances.stream().map(Governance::getProxyUserGuid)
                .forEach(proxyGuid -> handle.attach(DataExportDao.class).queueDataSync(proxyGuid));

        LOG.info("User with id={} was successfully deleted", userId);
    }

}
