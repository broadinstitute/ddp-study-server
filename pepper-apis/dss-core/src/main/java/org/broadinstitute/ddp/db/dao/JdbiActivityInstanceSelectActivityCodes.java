package org.broadinstitute.ddp.db.dao;

import java.util.Iterator;
import java.util.List;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlBatch;

public interface JdbiActivityInstanceSelectActivityCodes extends SqlObject {
    @SqlBatch("insert into activity_instance_select_activity_code (activity_instance_select_question_id,"
            + " study_activity_code, display_order) values (:questionId, :activityCode, :displayOrder)")
    int[] insert(@Bind("questionId") long questionId,
               @Bind("activityCode") List<String> activityCodes,
               @Bind("displayOrder") Iterator<Integer> displayOrder);
}
