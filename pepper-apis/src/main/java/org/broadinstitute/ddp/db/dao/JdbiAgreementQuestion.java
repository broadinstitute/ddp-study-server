package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiAgreementQuestion extends SqlObject {

    @SqlUpdate("insert into agreement_question values(:questionId)")
    int insert(@Bind("questionId") long questionId);

}
