package org.broadinstitute.ddp.event.pubsubtask.impl.updateprofile;

import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskException.Severity.WARN;
import static org.broadinstitute.ddp.event.pubsubtask.impl.updateprofile.UpdateProfileConstants.FIELD_FIRST_NAME;
import static org.broadinstitute.ddp.event.pubsubtask.impl.updateprofile.UpdateProfileConstants.FIELD_LAST_NAME;
import static org.broadinstitute.ddp.event.pubsubtask.impl.updateprofile.UpdateProfileConstants.FIELD_DO_NOT_CONTACT;

import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.TransactionWrapper.DB;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskException;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateProfileDataHandler {

    private static final Logger LOG = LoggerFactory.getLogger(UpdateProfileDataHandler.class);

    public void updateProfileData(String userGuid, Properties payload) {
        TransactionWrapper.useTxn(DB.APIS, handle -> updateProfileData(handle, userGuid, payload));
    }

    private void updateProfileData(Handle handle, String userGuid, Properties payload) {
        var profileDao = handle.attach(UserProfileDao.class);
        var profile = profileDao.findProfileByUserGuid(userGuid).orElse(null);
        if (profile == null) {
            throwUserNotFoundException(userGuid);
        }
        String firstName = detectFieldValueForUpdate(payload, FIELD_FIRST_NAME, profile.getFirstName());
        String lastName = detectFieldValueForUpdate(payload, FIELD_LAST_NAME, profile.getLastName());
        boolean needElasticUpdate = false;
        if (StringUtils.isNotBlank(firstName) || StringUtils.isNotBlank(lastName)) {
            int count = profileDao.getUserProfileSql().updateFirstAndLastNameByUserGuid(userGuid, firstName, lastName);
            if (count > 0) {
                needElasticUpdate = true;
                LOG.info("Updated firstName & lastName for user {} ", userGuid);
            } else {
                throwUserNotFoundException(userGuid);
            }
        }

        //update doNotContact
        Boolean doNotContact = detectBooleanFieldValueForUpdate(payload, FIELD_DO_NOT_CONTACT);
        if (doNotContact != null && doNotContact != profile.getDoNotContact()) {
            int count = profileDao.getUserProfileSql().updateDoNotContact(profile.getUserId(), doNotContact);
            if (count > 0) {
                LOG.info("Updated DoNotContact to {} for user {} ", doNotContact, userGuid);
                needElasticUpdate = true;
            } else {
                throwUserNotFoundException(userGuid);
            }
        }

        if (needElasticUpdate) {
            syncToElastic(handle, userGuid);
        }
    }

    private String detectFieldValueForUpdate(Properties payload, String fieldName, String currentValue) {
        return payload.containsKey(fieldName) ? payload.getProperty(fieldName) : currentValue;
    }

    private Boolean detectBooleanFieldValueForUpdate(Properties payload, String fieldName) {
        return payload.containsKey(fieldName) ? Boolean.valueOf(payload.getProperty(fieldName)) : null;
    }

    private void throwUserNotFoundException(String userGuid) {
        throw new PubSubTaskException("User profile is not found for guid=" + userGuid, WARN);
    }

    private void syncToElastic(Handle handle, String userGuid) {
        handle.attach(DataExportDao.class).queueDataSync(userGuid);
    }
}
