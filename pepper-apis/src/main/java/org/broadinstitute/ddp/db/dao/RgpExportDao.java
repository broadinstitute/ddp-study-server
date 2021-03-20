package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dto.RgpExportDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface RgpExportDao extends SqlObject {

    default long getLastCompleted(long studyId) {
        Optional<Long> optionalLastCompleted = findExportLastCompletedByStudyId(studyId);
        if (optionalLastCompleted.isPresent()) {
            return optionalLastCompleted.get();
        } else {
            // Return 0 but make sure to add this to the database
            long exportId = insertExport(studyId, 0);
            findExportDtoById(exportId).orElseThrow(() ->
                    new DaoException("Could not find newly created RGP export with id " + exportId));
            return (long)0;
        }
    }

    default void updateLastCompleted(long lastCompleted, long studyId) {
        DBUtils.checkUpdate(1, updateLastCompletedByStudyId(lastCompleted, studyId));
    }

    @SqlUpdate("insert into rgp_export (umbrella_study_id, rgp_export_last_completed) values (:studyId, :lastCompleted)")
    @GetGeneratedKeys
    long insertExport(
            @Bind("studyId") long studyId,
            @Bind("lastCompleted") long lastCompleted
    );

    @SqlQuery("select * from rgp_export where rgp_export_id = :id")
    @RegisterConstructorMapper(RgpExportDto.class)
    Optional<RgpExportDto> findExportDtoById(@Bind("id") long id);

    @SqlQuery("select rgp_export_last_completed from rgp_export where umbrella_study_id = :id")
    Optional<Long> findExportLastCompletedByStudyId(@Bind("id") long id);

    @SqlUpdate("update rgp_export set rgp_export_last_completed = :lastCompleted where umbrella_study_id = :studyId")
    int updateLastCompletedByStudyId(@Bind("lastCompleted") long lastCompleted, @Bind("studyId") long studyId);
}
