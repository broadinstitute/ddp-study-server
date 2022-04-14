package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiKitRequest extends SqlObject {

    @SqlUpdate("delete from kit_request where study_id = :studyId and mailing_address_id = :mailAddressId"
            + " and kit_type_id = :kitTypeId and participant_user_id = :userId")
    void deleteKitRequestByStudyMailingAddressKitTypeAndUser(@Bind Long studyId,
                                                             @Bind Long mailAddressId,
                                                             @Bind Long kitTypeId,
                                                             @Bind Long userId);

}
