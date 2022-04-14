package org.broadinstitute.ddp.util;

import org.broadinstitute.ddp.db.dao.JdbiInstitutionType;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.jdbi.v3.core.Handle;

public class MedicalProviderUtil {
    public static long getTestUserIdByGuid(Handle handle, String userGuid) {
        return handle.attach(JdbiUser.class).findByUserGuid(userGuid).getUserId();
    }

    public static long getTestUmbrellaStudyIdByGuid(Handle handle, String studyGuid) {
        return handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid).getId();
    }

    public static long getInstitutionTypeByCode(Handle handle, InstitutionType type) {
        return handle.attach(JdbiInstitutionType.class).getIdByType(type).get();
    }
}
