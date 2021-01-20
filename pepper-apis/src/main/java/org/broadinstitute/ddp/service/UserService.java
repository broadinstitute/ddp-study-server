package org.broadinstitute.ddp.service;

import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dao.DsmKitRequestDao;
import org.broadinstitute.ddp.db.dao.JdbiEventConfigurationOccurrenceCounter;
import org.broadinstitute.ddp.db.dao.JdbiMailAddress;
import org.broadinstitute.ddp.db.dao.JdbiMedicalProvider;
import org.broadinstitute.ddp.db.dao.JdbiQueuedEvent;
import org.broadinstitute.ddp.db.dao.JdbiTempMailAddress;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.StudyGovernanceSql;
import org.broadinstitute.ddp.db.dao.UserGovernanceSql;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Set;

/**
 * Entry-point for back-end operations related to users.
 */
public class UserService {

    private static final Logger LOG = LoggerFactory.getLogger(UserService.class);

    public UserService() {
    }

    /**
     * Deletes the user with specified id
     *
     * @param userId id of the user to be deleted
     */
    public void deleteUser(Handle handle, Long userId) {
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
        UserGovernanceSql userGovernanceSql = handle.attach(UserGovernanceSql.class);
        userGovernanceSql.deleteAllGovernancesByOperatorUserId(userId);
        userGovernanceSql.deleteAllGovernancesByParticipantUserId(userId);
        handle.attach(StudyGovernanceSql.class).deleteAgeUpCandidateByParticipantId(userId);
        // Removing user itself
        handle.attach(DataExportDao.class).deleteDataSyncRequestsForUser(userId);
        handle.attach(JdbiUser.class).deleteAllByIds(Collections.singleton(userId));
        LOG.info("User with id={} was successfully deleted", userId);
    }

}
