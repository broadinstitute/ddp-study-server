package org.broadinstitute.ddp.db.dao;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.copy.CopyAnswerLocation;
import org.broadinstitute.ddp.model.copy.CopyConfiguration;
import org.broadinstitute.ddp.model.copy.CopyConfigurationPair;
import org.broadinstitute.ddp.model.copy.CopyLocation;
import org.broadinstitute.ddp.model.copy.CopyLocationType;
import org.jdbi.v3.core.result.LinkedHashMapRowReducer;
import org.jdbi.v3.core.result.RowView;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.UseRowReducer;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface CopyConfigurationDao extends SqlObject {

    @CreateSqlObject
    CopyConfigurationSql getCopyConfigurationSql();

    default CopyConfiguration createCopyConfig(CopyConfiguration config) {
        long configId = getCopyConfigurationSql().insertCopyConfig(config.getStudyId(), config.shouldCopyFromPreviousInstance());
        int order = 1;
        for (var pair : config.getPairs()) {
            pair.setOrder(order);
            createCopyPair(config.getStudyId(), configId, pair);
            order += 1;
        }
        return findCopyConfigById(configId).orElseThrow(() ->
                new DaoException("Could not find newly created copy configuration with id " + configId));
    }

    private long createCopyPair(long studyId, long configId, CopyConfigurationPair pair) {
        if (pair.getSource().getType() != CopyLocationType.ANSWER) {
            throw new DaoException("Currently only support answer source locations");
        }

        if (pair.getTarget().getType() == CopyLocationType.ANSWER) {
            var jdbiQuestion = getHandle().attach(JdbiQuestion.class);
            var source = (CopyAnswerLocation) pair.getSource();
            QuestionDto sourceQuestionDto = jdbiQuestion
                    .findLatestDtoByStudyIdAndQuestionStableId(studyId, source.getQuestionStableId())
                    .orElseThrow(() -> new DaoException(String.format(
                            "Could not find source question with stable id %s in study %d",
                            source.getQuestionStableId(), studyId)));
            var target = (CopyAnswerLocation) pair.getTarget();
            QuestionDto targetQuestionDto = jdbiQuestion
                    .findLatestDtoByStudyIdAndQuestionStableId(studyId, target.getQuestionStableId())
                    .orElseThrow(() -> new DaoException(String.format(
                            "Could not find target question with stable id %s in study %d",
                            target.getQuestionStableId(), studyId)));

            if (sourceQuestionDto.getType() != targetQuestionDto.getType()) {
                throw new DaoException("Currently no support for copying answers between different question types");
            } else if (sourceQuestionDto.getType() == QuestionType.COMPOSITE) {
                throw new DaoException("Currently no support for copying answers for top-level composite question,"
                        + "configure copying for individual child questions instead");
            }
        }

        long sourceLocId = createCopyLocation(studyId, pair.getSource());
        long targetLocId = createCopyLocation(studyId, pair.getTarget());

        return getCopyConfigurationSql().insertCopyConfigPair(configId, sourceLocId, targetLocId, pair.getOrder());
    }

    private long createCopyLocation(long studyId, CopyLocation loc) {
        var copyConfigSql = getCopyConfigurationSql();
        long locId = copyConfigSql.insertCopyLocation(loc.getType());
        if (loc.getType() == CopyLocationType.ANSWER) {
            var ansLoc = (CopyAnswerLocation) loc;
            DBUtils.checkInsert(1, copyConfigSql.insertCopyAnswerLocationByQuestionStableId(
                    locId, studyId, ansLoc.getQuestionStableId()));
        }
        return locId;
    }

    default void removeCopyConfig(long configId) {
        CopyConfiguration config = findCopyConfigById(configId)
                .orElseThrow(() -> new DaoException("Copy configuration with id " + configId + " does not exist"));

        Set<Long> locationIds = new HashSet<>();
        for (var pair : config.getPairs()) {
            locationIds.add(pair.getSource().getId());
            locationIds.add(pair.getTarget().getId());
        }

        CopyConfigurationSql copyConfigurationSql = getCopyConfigurationSql();
        DBUtils.checkDelete(1, copyConfigurationSql.deleteCopyConfig(configId));
        DBUtils.checkDelete(locationIds.size(), copyConfigurationSql.bulkDeleteCopyLocations(locationIds));
    }

    @UseStringTemplateSqlLocator
    @SqlQuery("queryCopyConfigById")
    @RegisterConstructorMapper(CopyConfiguration.class)
    @UseRowReducer(CopyConfigurationWithPairsReducer.class)
    Optional<CopyConfiguration> findCopyConfigById(@Bind("id") long configId);

    class CopyConfigurationWithPairsReducer implements LinkedHashMapRowReducer<Long, CopyConfiguration> {

        @Override
        public void accumulate(Map<Long, CopyConfiguration> container, RowView view) {
            long id = view.getColumn("copy_configuration_id", Long.class);
            CopyConfiguration config = container
                    .computeIfAbsent(id, key -> view.getRow(CopyConfiguration.class));

            Long pairId = view.getColumn("copy_configuration_pair_id", Long.class);
            if (pairId != null) {
                CopyLocation source = reduceLocation(view, "source");
                CopyLocation target = reduceLocation(view, "target");
                int order = view.getColumn("execution_order", Integer.class);
                var pair = new CopyConfigurationPair(pairId, source, target, order);
                config.addPairs(List.of(pair));
            }
        }

        private CopyLocation reduceLocation(RowView view, String prefix) {
            long locId = view.getColumn(columnName(prefix, "copy_location_id"), Long.class);
            var type = CopyLocationType.valueOf(view.getColumn(columnName(prefix, "copy_location_type"), String.class));
            if (type == CopyLocationType.ANSWER) {
                return new CopyAnswerLocation(locId,
                        view.getColumn(columnName(prefix, "question_stable_code_id"), Long.class),
                        view.getColumn(columnName(prefix, "question_stable_id"), String.class));
            } else {
                return new CopyLocation(locId, type);
            }
        }

        private String columnName(String prefix, String name) {
            return prefix + "_" + name;
        }
    }
}
