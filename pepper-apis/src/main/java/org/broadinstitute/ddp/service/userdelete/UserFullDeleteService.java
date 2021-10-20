package org.broadinstitute.ddp.service.userdelete;

import static java.lang.String.format;
import static org.broadinstitute.ddp.service.userdelete.UserDeleteUtil.getUser;
import static org.broadinstitute.ddp.service.userdelete.UserDeleteUtil.hasGovernedUsers;

import java.io.IOException;

import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.AnswerSql;
import org.broadinstitute.ddp.db.dao.DsmKitRequestDao;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstanceStatus;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyLegacyData;
import org.broadinstitute.ddp.db.dao.UserAnnouncementDao;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.user.User;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Full deletion of a specified user and data connected to it.
 *
 * <br><b>Algorithm:</b>
 * <ul>
 *  <li>check if a user not refers to any governed user (if any references exist - cancel deletion);</li>
 *  <li>delete auth0 data;</li>
 *  <li>delete kit requests data;</li>
 *  <li>delete user_study_legacy_data;</li>
 *  <li>delete user_announcement;</li>
 *  <li>delete activity_instance_status;</li>
 *  <li>delete from answer where operator_user_id in (select user_id from delete_users);</li>
 *  <li>delete all other data using {@link UserService#deleteUser(Handle, User)}:
 *     <pre>
 *       - user_profile;
 *       - user_medical_provider;
 *       - user_study_enrollment;
 *       - temp_mailing_address, default_mailing_address, mailing_address;
 *       - picklist_option__answer, agreement_answer, text_answer, boolean_answer, date_answer, composite_answer_item, answer;
 *       - activity_instance_status;
 *       - activity_instance;
 *       - event_configuration_occurrence_counter;
 *       - queued_notification_template_substitution;
 *       - queued_notification;
 *       - queued_event;
 *       - user_governance;
 *       - age_up_candidate;
 *       - user;
 *     </pre>
 *  </li>
 * </ul>
 */
public class UserFullDeleteService {

    private static final Logger LOG = LoggerFactory.getLogger(UserFullDeleteService.class);

    private final UserService userService;

    public UserFullDeleteService(UserService userService) {
        this.userService = userService;
    }

    public void deleteUser(String studyGuid, String userGuid) throws IOException {
        LOG.info("Started full deletion of user with GUID: {}", userGuid);
        TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, handle -> {
            User user = getUser(handle, userGuid);
            checkBeforeDelete(handle, user);
            deleteUserSteps(handle, user);
        });
        LOG.info("Completed full deletion of user with GUID: {}", userGuid);
    }

    private void checkBeforeDelete(Handle handle, User user) {
        if (hasGovernedUsers(handle, user.getGuid())) {
            throw new DDPException(format("The user with GUID=%s has governed users and cannot be deleted", user.getGuid()));
        }
    }

    private void deleteUserSteps(Handle handle, User user) throws IOException {
        deleteAuth0User(handle, user);
        deleteKitRequests(handle, user);
        deleteUserStudyLegacyData(handle, user);
        deleteUserAnnouncement(handle, user);
        deleteActivityInstanceStatus(handle, user);
        deleteAnswersByOperator(handle, user);

        userService.deleteUser(handle, user);
    }

    private void deleteAuth0User(Handle handle, User user) {
        if (user.getAuth0UserId() != null) {
            var result = Auth0ManagementClient.forUser(handle, user.getGuid()).deleteAuth0User(user.getAuth0UserId());
            if (result.hasFailure()) {
                throw new DDPException(result.hasThrown() ? result.getThrown() : result.getError());
            }
        }
    }

    private void deleteKitRequests(Handle handle, User user) {
        handle.attach(DsmKitRequestDao.class).deleteKitRequestByParticipantId(user.getId());
    }

    private void deleteUserStudyLegacyData(Handle handle, User user) {
        handle.attach(JdbiUserStudyLegacyData.class).deleteByUserId(user.getId());
    }

    private void deleteUserAnnouncement(Handle handle, User user) {
        handle.attach(UserAnnouncementDao.class).deleteAllForUser(user.getId());
    }

    private void deleteActivityInstanceStatus(Handle handle, User user) {
        handle.attach(JdbiActivityInstanceStatus.class).deleteStatusByOperatorId(user.getId());
    }

    private void deleteAnswersByOperator(Handle handle, User user) {
        handle.attach(AnswerSql.class).deleteAnswerByOperatorId(user.getId());
    }
}
