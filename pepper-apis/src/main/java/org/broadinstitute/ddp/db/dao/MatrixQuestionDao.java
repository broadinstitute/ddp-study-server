package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dto.MatrixOptionDto;
import org.broadinstitute.ddp.db.dto.MatrixRowDto;
import org.broadinstitute.ddp.db.dto.MatrixGroupDto;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixGroupDef;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixRowDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.jdbi.v3.core.mapper.reflect.ConstructorMapper;
import org.jdbi.v3.core.statement.Query;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.stringtemplate4.StringTemplateSqlLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.stream.Stream;
import java.util.LinkedHashMap;
import java.util.Collection;

import static java.util.stream.Collectors.toList;

public interface MatrixQuestionDao extends SqlObject {

    Logger LOG = LoggerFactory.getLogger(MatrixQuestionDao.class);

    int DISPLAY_ORDER_GAP = 10;

    @CreateSqlObject
    JdbiMatrixGroup getJdbiMatrixGroup();

    @CreateSqlObject
    JdbiMatrixOption getJdbiMatrixOption();

    @CreateSqlObject
    JdbiMatrixRow getJdbiMatrixQuestion();

    @CreateSqlObject
    JdbiRevision getJdbiRevision();

    @CreateSqlObject
    TemplateDao getTemplateDao();


    /**
     * Create new matrix groups, along with their grouped options.
     *
     * @param questionId the associated matrix question
     * @param groups     the list of matrix group definitions
     * @param revisionId the revision to use, will be shared for all created data
     */
    default void insertGroups(long questionId, List<MatrixGroupDef> groups, long revisionId) {
        int order = 0;
        for (MatrixGroupDef group : groups) {
            order += DISPLAY_ORDER_GAP;
            insertGroup(questionId, group, order, revisionId);
        }
        LOG.info("Inserted {} matrix groups for matrix question id {}", groups.size(), questionId);
    }

    /**
     * Create a single new matrix group, along with its grouped options.
     *
     * @param questionId the associated matrix question
     * @param group      the matrix group definition
     * @param order      the display order number for the group
     * @param revisionId the revision to use, will be shared for all created data
     */
    default void insertGroup(long questionId, MatrixGroupDef group, int order, long revisionId) {

        JdbiMatrixGroup jdbiGroup = getJdbiMatrixGroup();
        TemplateDao templateDao = getTemplateDao();

        templateDao.insertTemplate(group.getNameTemplate(), revisionId);
        long nameTemplateId = group.getNameTemplate().getTemplateId();

        jdbiGroup.insert(questionId, group.getStableId(), nameTemplateId, order, revisionId);
    }

    /**
     * Create new matrix options for given question. The display order is not necessarily consecutive but follows the
     * ordering of the given list.
     *
     * @param questionId the associated matrix question
     * @param options    the list of matrix option definitions
     * @param revisionId the revision to use, will be shared for all created data
     * @return the option ids
     */
    default List<Long> insertOptions(long questionId, List<MatrixOptionDef> options, long revisionId) {
        boolean nonTextTemplateFound = options.stream()
                .anyMatch(o -> o.getTooltipTemplate() != null && o.getTooltipTemplate().getTemplateType() != TemplateType.TEXT);

        if (nonTextTemplateFound) {
            throw new DaoException("Only TEXT template type is supported for tooltips");
        }

        // Let's try to get all the template for all the options in a single batch
        List<Template> templateList = Stream.of(
                options.stream().map(MatrixOptionDef::getOptionLabelTemplate),
                options.stream().map(MatrixOptionDef::getTooltipTemplate)
        ).flatMap(i -> i).collect(toList());

        Long[] templateIds = getTemplateDao().insertTemplates(templateList, revisionId);
        List<Long> templateIdList = Arrays.asList(templateIds);

        getJdbiMatrixOption().insert(questionId,
                options.stream().map(MatrixOptionDef::getStableId).iterator(),
                templateIdList.listIterator(),
                templateIdList.listIterator(options.size()),
                options.stream().map(MatrixOptionDef::isExclusive).iterator(),
                options.stream().map(g -> getJdbiMatrixGroup().findGroupIdByCode(g.getGroupStableId())).iterator(),
                Stream.iterate(0, i -> i + DISPLAY_ORDER_GAP).iterator(),
                revisionId);

        LOG.info("Inserted {} matrix options for matrix question id {}", options.size(), questionId);
        return options.stream().map(MatrixOptionDef::getOptionId).collect(toList());
    }

    /**
     * Create new matrix rows (questions) for given matrix question. The display order is not necessarily consecutive but follows the
     * ordering of the given list.
     *
     * @param questionId    the associated matrix question
     * @param rows          the list of matrix option definitions
     * @param revisionId    the revision to use, will be shared for all created data
     * @return the rows ids
     */
    default List<Long> insertRowsQuestions(long questionId, List<MatrixRowDef> rows, long revisionId) {
        boolean nonTextTemplateFound = rows.stream()
                .anyMatch(o -> o.getTooltipTemplate() != null && o.getTooltipTemplate().getTemplateType() != TemplateType.TEXT);

        if (nonTextTemplateFound) {
            throw new DaoException("Only TEXT template type is supported for tooltips");
        }

        // Let's try to get all the template for all the options in a single batch
        List<Template> templateList = Stream.of(
                rows.stream().map(MatrixRowDef::getRowLabelTemplate),
                rows.stream().map(MatrixRowDef::getTooltipTemplate)
        ).flatMap(i -> i).collect(toList());

        Long[] templateIds = getTemplateDao().insertTemplates(templateList, revisionId);
        List<Long> templateIdList = Arrays.asList(templateIds);

        getJdbiMatrixQuestion().insert(questionId,
                rows.stream().map(MatrixRowDef::getStableId).iterator(),
                templateIdList.listIterator(),
                templateIdList.listIterator(rows.size()),
                Stream.iterate(0, i -> i + DISPLAY_ORDER_GAP).iterator(),
                revisionId);

        LOG.info("Inserted {} matrix rows (questions) for matrix question id {}", rows.size(), questionId);
        return rows.stream().map(MatrixRowDef::getRowId).collect(toList());
    }

    /**
     * End all active matrix options and rows (questions) for given question by terminating
     * the options/rows and their related data.
     *
     * @param questionId the associated matrix question
     * @param meta       the revision metadata used for terminating data
     */
    default void disableOptionsAndRowQuestions(long questionId, RevisionMetadata meta) {
        JdbiMatrixOption jdbiOption = getJdbiMatrixOption();
        JdbiMatrixRow jdbiRow = getJdbiMatrixQuestion();
        JdbiRevision jdbiRev = getJdbiRevision();
        TemplateDao tmplDao = getTemplateDao();

        List<MatrixOptionDto> options = jdbiOption.findAllActiveOrderedMatrixOptionsByQuestionId(questionId);
        List<MatrixRowDto> questions = jdbiRow.findAllActiveOrderedMatrixRowsByQuestionId(questionId);

        List<Long> oldRevIds = options.stream().map(MatrixOptionDto::getRevisionId).collect(toList());
        long[] newRevIds = jdbiRev.bulkCopyAndTerminate(oldRevIds, meta);
        if (newRevIds.length != oldRevIds.size()) {
            throw new DaoException("Not all revisions for matrix options were terminated");
        }
        int[] numOptsUpdated = jdbiOption.bulkUpdateRevisionIdsByDtos(options, newRevIds);
        if (Arrays.stream(numOptsUpdated).sum() != numOptsUpdated.length) {
            throw new DaoException("Not all matrix option revisions were updated");
        }
        int[] numRowsUpdated = jdbiRow.bulkUpdateRevisionIdsByDtos(questions, newRevIds);
        if (Arrays.stream(numRowsUpdated).sum() != numRowsUpdated.length) {
            throw new DaoException("Not all matrix row revisions were updated");
        }

        Set<Long> maybeOrphanedIds = new HashSet<>(oldRevIds);
        for (long revId : maybeOrphanedIds) {
            if (jdbiRev.tryDeleteOrphanedRevision(revId)) {
                LOG.info("Deleted orphaned revision {} by matrix question id {}", revId, questionId);
            }
        }

        for (MatrixOptionDto option : options) {
            tmplDao.disableTemplate(option.getOptionLabelTemplateId(), meta);
            if (option.getTooltipTemplateId() != null) {
                tmplDao.disableTemplate(option.getTooltipTemplateId(), meta);
            }
        }

        for (MatrixRowDto question : questions) {
            tmplDao.disableTemplate(question.getQuestionLabelTemplateId(), meta);
            if (question.getTooltipTemplateId() != null) {
                tmplDao.disableTemplate(question.getTooltipTemplateId(), meta);
            }
        }

        LOG.info("Terminated {} matrix options and rows (questions) for matrix question id {}",
                options.size(), questionId);
    }

    default GroupOptionRowDtos findOrderedGroupOptionRowDtos(long questionId, long timestamp) {
        return findOrderedGroupOptionRowDtos(Set.of(questionId), timestamp).get(questionId);
    }

    default Map<Long, GroupOptionRowDtos> findOrderedGroupOptionRowDtos(Iterable<Long> questionIds, long timestamp) {
        List<Long> ids = new ArrayList<>();
        if (questionIds != null) {
            questionIds.forEach(ids::add);
        }
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        String queryString = StringTemplateSqlLocator
                .findStringTemplate(MatrixQuestionDao.class, "queryAllOrderedGroupsOptionsRowsByQuestionIdsAndTimestamp")
                .render();
        Query query = getHandle().createQuery(queryString)
                .bindList("questionIds", ids)
                .bind("timestamp", timestamp);
        return executeGroupOptionQuestionDtosByQuestionQuery(query);
    }

    private Map<Long, GroupOptionRowDtos> executeGroupOptionQuestionDtosByQuestionQuery(Query query) {
        return query
                .registerRowMapper(ConstructorMapper.factory(MatrixOptionDto.class, "mo"))
                .registerRowMapper(ConstructorMapper.factory(MatrixGroupDto.class, "mg"))
                .registerRowMapper(ConstructorMapper.factory(MatrixRowDto.class, "mr"))
                .reduceRows(new HashMap<>(), (result, row) -> {
                    MatrixOptionDto option = row.getRow(MatrixOptionDto.class);
                    MatrixRowDto questionRow = row.getRow(MatrixRowDto.class);
                    MatrixGroupDto group = row.getRow(MatrixGroupDto.class);

                    long questionId = row.getColumn("question_id", Long.class);
                    var container = result.computeIfAbsent(questionId, k -> new GroupOptionRowDtos(questionId));

                    container.put(option.getStableId(), option);
                    container.put(questionRow.getStableId(), questionRow);
                    container.put(group.getStableId(), group);

                    return result;
                });
    }

    class GroupOptionRowDtos implements Serializable {

        private final long questionId;
        private final Map<String, MatrixGroupDto> groups = new LinkedHashMap<>();
        private final Map<String, MatrixOptionDto> options = new LinkedHashMap<>();
        private final Map<String, MatrixRowDto> rows = new LinkedHashMap<>();

        public GroupOptionRowDtos(long questionId) {
            this.questionId = questionId;
        }

        public long getQuestionId() {
            return questionId;
        }

        public void put(String key, MatrixGroupDto value) {
            groups.putIfAbsent(key, value);
        }

        public void put(String key, MatrixOptionDto value) {
            options.putIfAbsent(key, value);
        }

        public void put(String key, MatrixRowDto value) {
            rows.putIfAbsent(key, value);
        }

        public Collection<MatrixGroupDto> getGroups() {
            return groups.values();
        }

        public Collection<MatrixOptionDto> getOptions() {
            return options.values();
        }

        public Collection<MatrixRowDto> getRows() {
            return rows.values();
        }

        public Set<Long> getTemplateIds() {
            var ids = new HashSet<Long>();
            for (var group : groups.values()) {
                ids.add(group.getNameTemplateId());
            }
            for (var option : options.values()) {
                ids.addAll(option.getTemplateIds());
            }
            for (var row : rows.values()) {
                ids.addAll(row.getTemplateIds());
            }
            return ids;
        }
    }
}
