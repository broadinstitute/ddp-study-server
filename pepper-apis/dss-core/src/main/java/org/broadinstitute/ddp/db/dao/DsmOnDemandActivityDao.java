package org.broadinstitute.ddp.db.dao;

import java.util.List;

import org.broadinstitute.ddp.model.dsm.OnDemandActivity;
import org.broadinstitute.ddp.model.dsm.TriggeredInstance;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface DsmOnDemandActivityDao extends SqlObject {

    @UseStringTemplateSqlLocator
    @SqlQuery("queryAllOrderedOnDemandActivitiesByStudyId")
    @RegisterConstructorMapper(OnDemandActivity.class)
    List<OnDemandActivity> findAllOrderedOndemandActivitiesByStudy(@Bind("studyId") long studyId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryAllTriggeredInstancesByStudyIdAndActivityId")
    @RegisterConstructorMapper(TriggeredInstance.class)
    List<TriggeredInstance> findAllTriggeredInstancesByStudyAndActivity(@Bind("studyId") long studyId,
                                                                        @Bind("activityId") long activityId);
}
