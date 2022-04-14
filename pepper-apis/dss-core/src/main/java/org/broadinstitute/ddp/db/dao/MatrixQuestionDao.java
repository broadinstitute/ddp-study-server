package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dto.MatrixOptionDto;
import org.broadinstitute.ddp.db.dto.MatrixRowDto;
import org.broadinstitute.ddp.db.dto.MatrixGroupDto;
import org.broadinstitute.ddp.db.dto.RevisionDto;
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

        Long nameTemplateId = null;

        if (group.getNameTemplate() != null) {
            nameTemplateId = templateDao.insertTemplate(group.getNameTemplate(), revisionId);
        }

        long groupId = jdbiGroup.insert(questionId, group.getStableId(), nameTemplateId, order, revisionId);
        group.setGroupId(groupId);
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
    default List<Long> insertOptions(long questionId, List<MatrixOptionDef> options, Map<String, Long> groupsMap, long revisionId) {
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
                options.stream().map(g -> groupsMap.get(g.getGroupStableId())).iterator(),
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
     * Create a new matrix option for given question.
     *
     * @param questionId   the associated matrix question
     * @param option       the matrix option definition
     * @param displayOrder the display order number for the option
     * @param revisionId   the revision to use, will be shared for all created data
     * @return the option id
     */
    default long insertOption(long questionId, MatrixOptionDef option, int displayOrder, long revisionId) {
        JdbiMatrixOption jdbiOption = getJdbiMatrixOption();
        TemplateDao templateDao = getTemplateDao();

        templateDao.insertTemplate(option.getOptionLabelTemplate(), revisionId);
        long optionLabelTmplId = option.getOptionLabelTemplate().getTemplateId();

        Long tooltipTmplId = null;
        if (option.getTooltipTemplate() != null) {
            if (option.getTooltipTemplate().getTemplateType() != TemplateType.TEXT) {
                throw new DaoException("Only TEXT template type is supported for tooltips");
            }
            tooltipTmplId = templateDao.insertTemplate(option.getTooltipTemplate(), revisionId);
        }

        JdbiMatrixGroup jdbiGroup = getJdbiMatrixGroup();
        Long groupId = jdbiGroup.findGroupIdByCodeAndQuestionId(questionId, option.getGroupStableId());
        if (groupId == null) {
            throw new DaoException("Cannot find group with stableId " + option.getGroupStableId());
        }
        long optionId = jdbiOption.insert(questionId, option.getStableId(), optionLabelTmplId, tooltipTmplId,
                option.isExclusive(), groupId, displayOrder, revisionId);
        option.setOptionId(optionId);
        return optionId;
    }

    /**
     * Add a new matrix option to a matrix question based on revisioning. The position is a zero-indexed number
     * indicating where in the list of currently active options to insert the new option. Other matrix options will
     * be shifted as needed, using given revision data.
     *
     * @param questionId the associated matrix question
     * @param option     the option definition
     * @param revision   the revision data, the start metadata will be used to terminate shifted options
     */
    default void addOption(long questionId, MatrixOptionDef option, int position, RevisionDto revision) {
        JdbiMatrixOption jdbiOption = getJdbiMatrixOption();
        if (jdbiOption.isCurrentlyActive(questionId, option.getStableId())) {
            throw new IllegalStateException("Matrix potion " + option.getStableId() + " is already active");
        }

        int displayOrder = _allocateOptionPosition(questionId, position, revision);
        insertOption(questionId, option, displayOrder, revision.getId());
        LOG.info("Added new matrix option {} to question {} using revision {}", option.getOptionId(), questionId, revision.getId());
    }

    /**
     * Adjust list of active matrix options for given matrix question to make room for a new option at the desired
     * position. The actual display order needed to put new option in the position will be computed and returned.
     *
     * <p>The position is a zero-indexed number into the list of active options. Other options will be adjusted and
     * shifted down if necessary. If position is greater than size of list (by any amount), we simply reserve a spot at
     * the end.
     *
     * <p>Note: clients should not use this method directly as it has side effects of changing options.
     *
     * @param questionId the associated matrix question
     * @param position   the desired position in list of active options, zero-indexed
     * @param revision   the revision data, the start metadata will be used to terminate shifted block memberships
     * @return computed display order to use for desired position
     */
    default int _allocateOptionPosition(long questionId, int position, RevisionDto revision) {
        if (position < 0) {
            throw new IllegalArgumentException("Desired matrix option position must be non-negative");
        }

        List<MatrixOptionDto> optionDtos = getJdbiMatrixOption().findAllActiveOrderedMatrixOptionsByQuestionId(questionId);
        int size = optionDtos.size();

        int displayOrderToUse;
        if (size == 0) {
            displayOrderToUse = DISPLAY_ORDER_GAP;
        } else if (size <= position) {
            displayOrderToUse = optionDtos.get(size - 1).getDisplayOrder() + DISPLAY_ORDER_GAP;
        } else {
            int prevOrder = (position == 0 ? 0 : optionDtos.get(position - 1).getDisplayOrder());
            displayOrderToUse = prevOrder + 1;
            _shiftOptionOrderings(questionId, optionDtos, position, displayOrderToUse, revision);
        }

        return displayOrderToUse;
    }

    /**
     * Ensure matrix options starting at given index all have a display order that is greater than the allocated
     * display order. Otherwise the options will be shifted "down" to accommodate it.
     *
     * <p>Note: clients should not use this method directly as it has side effects of changing options,
     * and some invariants are presumed to be true.
     *
     * @param questionId     the associated matrix question
     * @param optionDtos     the list of currently active matrix options, is ascending display order
     * @param startIdx       the starting index, inclusive
     * @param allocatedOrder the display order to accommodate
     * @param revision       the revision data, the start metadata will be used to terminate shifted block memberships
     */
    default void _shiftOptionOrderings(long questionId, List<MatrixOptionDto> optionDtos, int startIdx,
                                       int allocatedOrder, RevisionDto revision) {
        JdbiMatrixOption jdbiOption = getJdbiMatrixOption();
        JdbiRevision jdbiRev = getJdbiRevision();

        int idx = startIdx;
        int prevOrder = allocatedOrder;
        int size = optionDtos.size();
        while (idx < size) {
            MatrixOptionDto current = optionDtos.get(idx);
            if (prevOrder < current.getDisplayOrder()) {
                break;
            }
            int newOrder = prevOrder + 1;
            current.setDisplayOrder(newOrder);
            prevOrder = newOrder;
            idx += 1;
        }

        List<MatrixOptionDto> shifted = optionDtos.subList(startIdx, idx);
        if (!shifted.isEmpty()) {
            List<Long> oldRevIds = optionDtos.stream().map(MatrixOptionDto::getRevisionId).collect(toList());
            long[] newRevIds = jdbiRev.bulkCopyAndTerminate(oldRevIds, revision);
            if (newRevIds.length != oldRevIds.size()) {
                throw new DaoException("Not all revisions of shifted matrix options were terminated");
            }

            int[] numUpdated = jdbiOption.bulkUpdateRevisionIdsByDtos(optionDtos, newRevIds);
            if (Arrays.stream(numUpdated).sum() != numUpdated.length) {
                throw new DaoException("Not all shifted matrix option revisions were updated");
            }

            Set<Long> maybeOrphanedIds = new HashSet<>(oldRevIds);
            for (long revId : maybeOrphanedIds) {
                if (jdbiRev.tryDeleteOrphanedRevision(revId)) {
                    LOG.info("Deleted orphaned revision {}", revId);
                }
            }

            long[] ids = jdbiOption.bulkInsertByDtos(shifted, questionId, revision.getId());
            if (ids.length != shifted.size()) {
                throw new DaoException("Not all shifted matrix options were updated");
            }
        }
    }
    
    /**
     * End all active matrix options, rows (questions), groups for given question by terminating
     * the options/rows/groups and their related data.
     *
     * @param questionId the associated matrix question
     * @param meta       the revision metadata used for terminating data
     */
    default void disableOptionsGroupsRowQuestions(long questionId, RevisionMetadata meta) {
        JdbiMatrixOption jdbiOption = getJdbiMatrixOption();
        JdbiMatrixRow jdbiRow = getJdbiMatrixQuestion();
        JdbiMatrixGroup jdbiGroup = getJdbiMatrixGroup();
        JdbiRevision jdbiRev = getJdbiRevision();
        TemplateDao tmplDao = getTemplateDao();

        List<MatrixOptionDto> options = jdbiOption.findAllActiveOrderedMatrixOptionsByQuestionId(questionId);
        List<MatrixRowDto> rows = jdbiRow.findAllActiveOrderedMatrixRowsByQuestionId(questionId);
        List<MatrixGroupDto> groups = jdbiGroup.findAllActiveOrderedMatrixGroupsQuestionId(questionId);

        List<Long> oldRevIds = options.stream().map(MatrixOptionDto::getRevisionId).collect(toList());
        long[] newRevIds = jdbiRev.bulkCopyAndTerminate(oldRevIds, meta);
        if (newRevIds.length != oldRevIds.size()) {
            throw new DaoException("Not all revisions for matrix options were terminated");
        }
        int[] numOptsUpdated = jdbiOption.bulkUpdateRevisionIdsByDtos(options, newRevIds);
        if (Arrays.stream(numOptsUpdated).sum() != numOptsUpdated.length) {
            throw new DaoException("Not all matrix option revisions were updated");
        }
        int[] numRowsUpdated = jdbiRow.bulkUpdateRevisionIdsByDtos(rows, newRevIds);
        if (Arrays.stream(numRowsUpdated).sum() != numRowsUpdated.length) {
            throw new DaoException("Not all matrix row revisions were updated");
        }
        int[] numGroupsUpdated = jdbiGroup.bulkUpdateRevisionIdsByDtos(groups, newRevIds);
        if (Arrays.stream(numGroupsUpdated).sum() != numGroupsUpdated.length) {
            throw new DaoException("Not all matrix groups revisions were updated");
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
        for (MatrixRowDto row : rows) {
            tmplDao.disableTemplate(row.getQuestionLabelTemplateId(), meta);
            if (row.getTooltipTemplateId() != null) {
                tmplDao.disableTemplate(row.getTooltipTemplateId(), meta);
            }
        }
        for (MatrixGroupDto group : groups) {
            if (group.getNameTemplateId() != null) {
                tmplDao.disableTemplate(group.getNameTemplateId(), meta);
            }
        }

        LOG.info("Terminated {} matrix options, {} rows, {} groups for matrix question id {}",
                options.size(), rows.size(), groups.size(), questionId);
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
                if (group.getNameTemplateId() != null) {
                    ids.add(group.getNameTemplateId());
                }
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
