package org.broadinstitute.ddp.db;

import org.broadinstitute.ddp.db.dao.JdbiActivityMapping;
import org.broadinstitute.ddp.model.dsm.StudyActivityMapping;
import org.jdbi.v3.core.Handle;

public class StudyActivityMappingDao {
    public static int insertStudyActivityMapping(Handle handle, StudyActivityMapping studyActivityMapping) {
        JdbiActivityMapping jdbiActivityMapping = handle.attach(JdbiActivityMapping.class);
        return jdbiActivityMapping.insert(studyActivityMapping.getStudyGuid(),
                studyActivityMapping.getActivityMappingType().toString(),
                studyActivityMapping.getStudyActivityId(),
                studyActivityMapping.getSubActivityStableId());
    }
}
