package org.broadinstitute.ddp.db.dao;

import java.util.List;
import org.broadinstitute.ddp.model.es.HiddenAlias;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

public interface EsHiddenValuesDao extends SqlObject {

    @SqlQuery("select * from es_hidden_alias where study_guid=:studyGuid")
    @RegisterConstructorMapper(HiddenAlias.class)
    List<HiddenAlias> findAliasesByStudy(@Bind("studyGuid") String studyGuid);
}
