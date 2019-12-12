package org.broadinstitute.ddp.db.dao;

import java.util.List;

import org.broadinstitute.ddp.db.dto.RevisionDto;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.jdbi.v3.core.statement.UnableToExecuteStatementException;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.StringTemplateSqlLocator;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface JdbiRevision extends SqlObject {

    Logger LOG = LoggerFactory.getLogger(JdbiRevision.class);

    @GetGeneratedKeys
    @SqlUpdate("insert into revision (changed_by_user_id,start_date,end_date,change_reason) values (?,?,?,?)")
    long insert(long changedByUserId, long startEpochMillis, Long endEpochMillis, String changeReason);

    @GetGeneratedKeys
    @SqlUpdate("insert into revision (start_date, end_date, changed_by_user_id, change_reason, terminated_by_user_id, terminated_reason)"
            + " values (:start, :end, :changedByUserId, :changeReason, :terminatedByUserId, :terminatedReason)")
    long insertTerminated(@Bind("start") long startEpochMillis,
                          @Bind("end") long endEpochMillis,
                          @Bind("changedByUserId") long changedByUserId,
                          @Bind("changeReason") String changeReason,
                          @Bind("terminatedByUserId") long terminatedByUserId,
                          @Bind("terminatedReason") String terminatedReason);

    default long insertStart(long startEpochMillis, long userId, String reason) {
        return insert(userId, startEpochMillis, null, reason);
    }

    /**
     * Create a copy of given revision and terminate it by setting the end date.
     */
    @UseStringTemplateSqlLocator
    @SqlUpdate("copyAndTerminateByRevisionId")
    @GetGeneratedKeys
    long copyAndTerminate(@Bind("revisionId") long revisionId,
                          @Bind("endEpoch") long endEpochMillis,
                          @Bind("userId") long userId,
                          @Bind("reason") String reason);

    default long copyAndTerminate(long revisionId, RevisionMetadata meta) {
        return copyAndTerminate(revisionId, meta.getTimestamp(), meta.getUserId(), meta.getReason());
    }

    /**
     * Copies and terminates the revisions in bulk.
     */
    @UseStringTemplateSqlLocator
    @SqlBatch("copyAndTerminateByRevisionId")
    @GetGeneratedKeys
    long[] bulkCopyAndTerminate(@Bind("revisionId") List<Long> revisionIds,
                                @Bind("endEpoch") long endEpochMillis,
                                @Bind("userId") long userId,
                                @Bind("reason") String reason);

    default long[] bulkCopyAndTerminate(List<Long> revisionIds, RevisionMetadata meta) {
        return bulkCopyAndTerminate(revisionIds, meta.getTimestamp(), meta.getUserId(), meta.getReason());
    }

    default long[] bulkCopyAndTerminate(List<Long> revisionIds, RevisionDto revision) {
        return bulkCopyAndTerminate(revisionIds, revision.getStartMillis(), revision.getChangedUserId(),
                revision.getChangedReason());
    }

    /**
     * Attempts to delete the given revision. Operation may fail since revision may be "shared" and thus referenced
     * in other places. This failure is captured internally and returns false.
     */
    default boolean tryDeleteOrphanedRevision(long revisionId) {
        try {
            String stmt = StringTemplateSqlLocator
                    .findStringTemplate(JdbiRevision.class, "deleteRevisionById")
                    .render();
            int numDeleted = getHandle().createUpdate(stmt)
                    .bind("revisionId", revisionId)
                    .execute();
            if (numDeleted != 1) {
                LOG.warn("Deleted {} rows for revision id {}", numDeleted, revisionId);
                return false;
            }
            return true;
        } catch (UnableToExecuteStatementException e) {
            LOG.debug("Revision {} not deleted: {}", revisionId, e.getMessage());
            return false;
        }
    }
}
