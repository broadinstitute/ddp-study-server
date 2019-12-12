package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

public interface JdbiDsmNotificationEventType extends SqlObject {

    /**
     * Message sent by DSM when saliva kit has been returned to Broad
     */
    public static final String SALIVA_RECEIVED = "SALIVA_RECEIVED";

    /**
     * Message sent by DSM when blood kit has been returned to Broad
     */
    public static final String BLOOD_RECEIVED = "BLOOD_RECEIVED";

    /**
     * Message sent by DSM when blood kit has been sent
     */
    public static final String BLOOD_SENT = "BLOOD_SENT";

    /**
     * Message sent when blood kit has not been returned to Broad within 4 weeks
     */
    public static final String BLOOD_NOT_RECEIVED_4_WEEKS = "BLOOD_SENT_4WK";

    public static final String USER_NOT_ENROLLED_IN_STUDY = "USER_NOT_ENROLLED_IN_STUDY";

    @SqlQuery(
            "select dsm_notification_event_type_id from dsm_notification_event_type"
                + " where dsm_notification_event_type_code = :code"
    )
    Optional<Long> findIdByCode(@Bind("code") String code);
}
