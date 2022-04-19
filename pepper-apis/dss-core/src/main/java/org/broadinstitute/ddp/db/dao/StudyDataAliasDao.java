package org.broadinstitute.ddp.db.dao;

import java.util.List;
import org.broadinstitute.ddp.model.es.StudyDataAlias;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

public interface StudyDataAliasDao extends SqlObject {

    @SqlQuery("select * from study_data_alias where study_guid=:studyGuid")
    @RegisterConstructorMapper(StudyDataAlias.class)
    List<StudyDataAlias> findAliasesByStudy(@Bind("studyGuid") String studyGuid);
}
