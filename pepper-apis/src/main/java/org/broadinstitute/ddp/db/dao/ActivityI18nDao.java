package org.broadinstitute.ddp.db.dao;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.model.activity.definition.i18n.ActivityI18nDetail;
import org.broadinstitute.ddp.model.activity.definition.i18n.SummaryTranslation;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface ActivityI18nDao extends SqlObject {

    @CreateSqlObject
    ActivityI18nSql getActivityI18nSql();

    //
    // inserts
    //

    default long[] insertDetails(List<ActivityI18nDetail> details) {
        long[] ids = getActivityI18nSql().bulkInsertDetails(details);
        DBUtils.checkInsert(details.size(), ids.length);
        return ids;
    }

    default long[] insertSummaries(long activityId, List<SummaryTranslation> summaries) {
        long[] ids = getActivityI18nSql().bulkInsertSummaries(activityId, summaries);
        DBUtils.checkInsert(summaries.size(), ids.length);
        return ids;
    }

    //
    // updates
    //

    default void updateDetails(List<ActivityI18nDetail> details) {
        int[] updatedCounts = getActivityI18nSql().bulkUpdateDetails(details);
        DBUtils.checkUpdate(details.size(), Arrays.stream(updatedCounts).sum());
    }

    //
    // queries
    //

    @UseStringTemplateSqlLocator
    @SqlQuery("findDetailById")
    @RegisterConstructorMapper(ActivityI18nDetail.class)
    Optional<ActivityI18nDetail> findDetailById(@Bind("id") long id);

    @UseStringTemplateSqlLocator
    @SqlQuery("findDetailsByActivityId")
    @RegisterConstructorMapper(ActivityI18nDetail.class)
    List<ActivityI18nDetail> findDetailsByActivityId(@Bind("activityId") long activityId);
}
