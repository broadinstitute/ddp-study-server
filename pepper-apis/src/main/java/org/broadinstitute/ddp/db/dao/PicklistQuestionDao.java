package org.broadinstitute.ddp.db.dao;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dto.PicklistGroupDto;
import org.broadinstitute.ddp.db.dto.PicklistOptionDto;
import org.broadinstitute.ddp.db.dto.RevisionDto;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistGroupDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.jdbi.v3.core.mapper.reflect.ConstructorMapper;
import org.jdbi.v3.core.statement.Query;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.stringtemplate4.StringTemplateSqlLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface PicklistQuestionDao extends SqlObject {

    Logger LOG = LoggerFactory.getLogger(PicklistQuestionDao.class);

    int DISPLAY_ORDER_GAP = 10;

    @CreateSqlObject
    JdbiPicklistGroup getJdbiPicklistGroup();

    @CreateSqlObject
    JdbiPicklistOption getJdbiPicklistOption();

    @CreateSqlObject
    JdbiPicklistGroupedOption getJdbiPicklistGroupedOption();

    @CreateSqlObject
    JdbiRevision getJdbiRevision();

    @CreateSqlObject
    TemplateDao getTemplateDao();


    /**
     * Create new picklist groups, along with their grouped options.
     *
     * @param questionId the associated picklist question
     * @param groups     the list of picklist group definitions
     * @param revisionId the revision to use, will be shared for all created data
     */
    default void insertGroups(long questionId, List<PicklistGroupDef> groups, long revisionId) {
        int order = 0;
        for (PicklistGroupDef group : groups) {
            order += DISPLAY_ORDER_GAP;
            insertGroup(questionId, group, order, revisionId);
        }
        LOG.info("Inserted {} picklist groups for picklist question id {}", groups.size(), questionId);
    }

    /**
     * Create a single new picklist group, along with its grouped options.
     *
     * @param questionId the associated picklist question
     * @param group      the picklist group definition
     * @param order      the display order number for the group
     * @param revisionId the revision to use, will be shared for all created data
     */
    default void insertGroup(long questionId, PicklistGroupDef group, int order, long revisionId) {
        if (group.getOptions().isEmpty()) {
            throw new IllegalStateException(String.format("picklist group %s needs to have at least one option", group.getStableId()));
        }

        JdbiPicklistGroup jdbiGroup = getJdbiPicklistGroup();
        TemplateDao templateDao = getTemplateDao();

        templateDao.insertTemplate(group.getNameTemplate(), revisionId);
        long nameTemplateId = group.getNameTemplate().getTemplateId();

        long groupId = jdbiGroup.insert(questionId, group.getStableId(), nameTemplateId, order, revisionId);
        group.setGroupId(groupId);

        List<Long> optionIds = insertOptions(questionId, group.getOptions(), revisionId);
        long[] ids = getJdbiPicklistGroupedOption().bulkInsert(groupId, optionIds);
        if (ids.length != optionIds.size()) {
            throw new DaoException("Could not add all options to group " + group.getStableId());
        }
    }

    /**
     * Create new picklist options for given question. The display order is not necessarily consecutive but follows the
     * ordering of the given list.
     *
     * @param questionId the associated picklist question
     * @param options    the list of picklist option definitions
     * @param revisionId the revision to use, will be shared for all created data
     * @return the option ids
     */
    default List<Long> insertOptions(long questionId, List<PicklistOptionDef> options, long revisionId) {
        List<Long> optionIds = new ArrayList<>();
        int displayOrder = 0;
        for (PicklistOptionDef option : options) {
            displayOrder += DISPLAY_ORDER_GAP;
            long optionId = insertOption(questionId, option, displayOrder, revisionId);
            optionIds.add(optionId);
        }
        LOG.info("Inserted {} picklist options for picklist question id {}", options.size(), questionId);
        return optionIds;
    }

    /**
     * Create a new picklist option for given question.
     *
     * @param questionId   the associated picklist question
     * @param option       the picklist option definition
     * @param displayOrder the display order number for the option
     * @param revisionId   the revision to use, will be shared for all created data
     * @return the option id
     */
    default long insertOption(long questionId, PicklistOptionDef option, int displayOrder, long revisionId) {
        JdbiPicklistOption jdbiOption = getJdbiPicklistOption();
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

        Long detailLabelTmplId = null;
        if (option.isDetailsAllowed()) {
            templateDao.insertTemplate(option.getDetailLabelTemplate(), revisionId);
            detailLabelTmplId = option.getDetailLabelTemplate().getTemplateId();
        }

        Long nestedOptionsTmplId = null;
        if (option.getNestedOptionsLabelTemplate() != null) {
            templateDao.insertTemplate(option.getNestedOptionsLabelTemplate(), revisionId);
            nestedOptionsTmplId = option.getNestedOptionsLabelTemplate().getTemplateId();
        }

        long optionId = jdbiOption.insert(questionId, option.getStableId(), optionLabelTmplId, tooltipTmplId,
                detailLabelTmplId, option.isDetailsAllowed(), option.isExclusive(), displayOrder, revisionId, nestedOptionsTmplId);
        option.setOptionId(optionId);

        if (CollectionUtils.isNotEmpty(option.getNestedOptions())) {
            int subOptionDisplayOrder = displayOrder;
            List<Long> nestedOptionIds = new ArrayList<>();
            for (PicklistOptionDef nestedOption : option.getNestedOptions()) {
                subOptionDisplayOrder += DISPLAY_ORDER_GAP;
                long nestedOptionId = insertOption(questionId, nestedOption, subOptionDisplayOrder, revisionId);
                nestedOptionIds.add(nestedOptionId);
            }
            //now populate nested options join table
            getJdbiPicklistOption().bulkInsertNestedOptions(optionId, nestedOptionIds);
            LOG.info("Inserted {} nested options for picklist option: {} optionId: {}",
                    nestedOptionIds.size(), option.getStableId(), optionId);
        }

        return optionId;
    }

    /**
     * Add a new picklist option to a picklist question based on revisioning. The position is a zero-indexed number
     * indicating where in the list of currently active options to insert the new option. Other picklist options will
     * be shifted as needed, using given revision data.
     *
     * @param questionId the associated picklist question
     * @param option     the option definition
     * @param revision   the revision data, the start metadata will be used to terminate shifted options
     */
    default void addOption(long questionId, PicklistOptionDef option, int position, RevisionDto revision) {
        JdbiPicklistOption jdbiOption = getJdbiPicklistOption();
        if (jdbiOption.isCurrentlyActive(questionId, option.getStableId())) {
            throw new IllegalStateException("Picklist option " + option.getStableId() + " is already active");
        }

        int displayOrder = _allocateOptionPosition(questionId, position, revision);
        insertOption(questionId, option, displayOrder, revision.getId());
        LOG.info("Added new picklist option {} to question {} using revision {}", option.getOptionId(), questionId, revision.getId());
    }

    /**
     * Adjust list of active picklist options for given picklist question to make room for a new option at the desired
     * position. The actual display order needed to put new option in the position will be computed and returned.
     *
     * <p>The position is a zero-indexed number into the list of active options. Other options will be adjusted and
     * shifted down if necessary. If position is greater than size of list (by any amount), we simply reserve a spot at
     * the end.
     *
     * <p>Note: clients should not use this method directly as it has side effects of changing options.
     *
     * @param questionId the associated picklist question
     * @param position   the desired position in list of active options, zero-indexed
     * @param revision   the revision data, the start metadata will be used to terminate shifted block memberships
     * @return computed display order to use for desired position
     */
    default int _allocateOptionPosition(long questionId, int position, RevisionDto revision) {
        if (position < 0) {
            throw new IllegalArgumentException("Desired picklist option position must be non-negative");
        }

        List<PicklistOptionDto> optionDtos = getJdbiPicklistOption().findAllActiveOrderedOptionsByQuestionId(questionId);
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
     * Ensure picklist options starting at given index all have a display order that is greater than the allocated
     * display order. Otherwise the options will be shifted "down" to accommodate it.
     *
     * <p>Note: clients should not use this method directly as it has side effects of changing options,
     * and some invariants are presumed to be true.
     *
     * @param questionId     the associated picklist question
     * @param optionDtos     the list of currently active picklist options, is ascending display order
     * @param startIdx       the starting index, inclusive
     * @param allocatedOrder the display order to accommodate
     * @param revision       the revision data, the start metadata will be used to terminate shifted block memberships
     */
    default void _shiftOptionOrderings(long questionId, List<PicklistOptionDto> optionDtos, int startIdx,
                                       int allocatedOrder, RevisionDto revision) {
        JdbiPicklistOption jdbiOption = getJdbiPicklistOption();
        JdbiRevision jdbiRev = getJdbiRevision();

        int idx = startIdx;
        int prevOrder = allocatedOrder;
        int size = optionDtos.size();
        while (idx < size) {
            PicklistOptionDto current = optionDtos.get(idx);
            if (prevOrder < current.getDisplayOrder()) {
                break;
            }
            int newOrder = prevOrder + 1;
            current.setDisplayOrder(newOrder);
            prevOrder = newOrder;
            idx += 1;
        }

        List<PicklistOptionDto> shifted = optionDtos.subList(startIdx, idx);
        if (!shifted.isEmpty()) {
            List<Long> oldRevIds = optionDtos.stream().map(PicklistOptionDto::getRevisionId).collect(Collectors.toList());
            long[] newRevIds = jdbiRev.bulkCopyAndTerminate(oldRevIds, revision);
            if (newRevIds.length != oldRevIds.size()) {
                throw new DaoException("Not all revisions of shifted picklist options were terminated");
            }

            int[] numUpdated = jdbiOption.bulkUpdateRevisionIdsByDtos(optionDtos, newRevIds);
            if (Arrays.stream(numUpdated).sum() != numUpdated.length) {
                throw new DaoException("Not all shifted picklist option revisions were updated");
            }

            Set<Long> maybeOrphanedIds = new HashSet<>(oldRevIds);
            for (long revId : maybeOrphanedIds) {
                if (jdbiRev.tryDeleteOrphanedRevision(revId)) {
                    LOG.info("Deleted orphaned revision {}", revId);
                }
            }

            long[] ids = jdbiOption.bulkInsertByDtos(shifted, questionId, revision.getId());
            if (ids.length != shifted.size()) {
                throw new DaoException("Not all shifted picklist options were updated");
            }
        }
    }

    /**
     * End all active picklist options for given question by terminating the options and their related data.
     *
     * @param questionId the associated picklist question
     * @param meta       the revision metadata used for terminating data
     */
    default void disableOptions(long questionId, RevisionMetadata meta) {
        JdbiPicklistOption jdbiOption = getJdbiPicklistOption();
        JdbiRevision jdbiRev = getJdbiRevision();
        TemplateDao tmplDao = getTemplateDao();

        List<PicklistOptionDto> options = jdbiOption.findAllActiveOrderedOptionsByQuestionId(questionId);
        List<Long> oldRevIds = options.stream().map(PicklistOptionDto::getRevisionId).collect(Collectors.toList());
        long[] newRevIds = jdbiRev.bulkCopyAndTerminate(oldRevIds, meta);
        if (newRevIds.length != oldRevIds.size()) {
            throw new DaoException("Not all revisions for picklist options were terminated");
        }
        int[] numUpdated = jdbiOption.bulkUpdateRevisionIdsByDtos(options, newRevIds);
        if (Arrays.stream(numUpdated).sum() != numUpdated.length) {
            throw new DaoException("Not all picklist option revisions were updated");
        }

        Set<Long> maybeOrphanedIds = new HashSet<>(oldRevIds);
        for (long revId : maybeOrphanedIds) {
            if (jdbiRev.tryDeleteOrphanedRevision(revId)) {
                LOG.info("Deleted orphaned revision {} by picklist question id {}", revId, questionId);
            }
        }

        for (PicklistOptionDto option : options) {
            tmplDao.disableTemplate(option.getOptionLabelTemplateId(), meta);
            if (option.getDetailLabelTemplateId() != null) {
                tmplDao.disableTemplate(option.getDetailLabelTemplateId(), meta);
            }
            if (option.getTooltipTemplateId() != null) {
                tmplDao.disableTemplate(option.getTooltipTemplateId(), meta);
            }
            if (option.getNestedOptionsTemplateId() != null) {
                tmplDao.disableTemplate(option.getNestedOptionsTemplateId(), meta);
            }
        }

        //disable any nested options
        List<PicklistOptionDto> nestedOptions = getJdbiPicklistOption().findAllActiveNestedOptionsByQuestionId(questionId);
        if (CollectionUtils.isNotEmpty(nestedOptions)) {
            nestedOptions.stream().forEach(nestedOpt -> disableOption(questionId, nestedOpt.getStableId(), meta, false));
        }

        LOG.info("Terminated {} picklist options for picklist question id {}", options.size(), questionId);
    }

    /**
     * End existing picklist option from a picklist question by terminating its active revision. This operation is a
     * multi-step process where we terminate the active option and then propagate to its related data.
     *
     * @param questionId     the associated picklist question
     * @param optionStableId the picklist option stable id
     * @param meta           the revision metadata to use for terminating data
     */
    default void disableOption(long questionId, String optionStableId, RevisionMetadata meta, boolean disableNested) {
        JdbiPicklistOption jdbiOption = getJdbiPicklistOption();
        JdbiRevision jdbiRev = getJdbiRevision();
        TemplateDao tmplDao = getTemplateDao();

        PicklistOptionDto optionDto = jdbiOption.getActiveByStableId(questionId, optionStableId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Active picklist option " + optionStableId + " is not found for question " + questionId));

        long oldRevId = optionDto.getRevisionId();
        long newRevId = jdbiRev.copyAndTerminate(oldRevId, meta.getTimestamp(), meta.getUserId(), meta.getReason());
        int numUpdated = jdbiOption.updateRevisionByOptionId(optionDto.getId(), newRevId);
        if (numUpdated != 1) {
            throw new DaoException("Unable to terminate active picklist option " + optionStableId);
        }

        tmplDao.disableTemplate(optionDto.getOptionLabelTemplateId(), meta);
        if (optionDto.getDetailLabelTemplateId() != null) {
            tmplDao.disableTemplate(optionDto.getDetailLabelTemplateId(), meta);
        }

        if (optionDto.getTooltipTemplateId() != null) {
            tmplDao.disableTemplate(optionDto.getTooltipTemplateId(), meta);
        }

        if (jdbiRev.tryDeleteOrphanedRevision(oldRevId)) {
            LOG.info("Deleted orphaned revision {} by picklist option {}", oldRevId, optionStableId);
        }

        if (disableNested) {
            List<PicklistOptionDto> nestedOptions = getJdbiPicklistOption().findActiveNestedOptions(questionId, optionDto.getId());
            if (CollectionUtils.isNotEmpty(nestedOptions)) {
                nestedOptions.stream().forEach(nestedOpt -> disableOption(questionId, nestedOpt.getStableId(), meta, false));
            }
        }

    }

    default GroupAndOptionDtos findOrderedGroupAndOptionDtos(long questionId, long timestamp) {
        return findOrderedGroupAndOptionDtos(Set.of(questionId), timestamp).get(questionId);
    }

    default Map<Long, GroupAndOptionDtos> findOrderedGroupAndOptionDtos(Iterable<Long> questionIds, long timestamp) {
        List<Long> ids = new ArrayList<>();
        if (questionIds != null) {
            questionIds.forEach(ids::add);
        }
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        String queryString = StringTemplateSqlLocator
                .findStringTemplate(PicklistQuestionDao.class, "queryAllOrderedGroupsAndOptionsByQuestionIdsAndTimestamp")
                .render();
        Query query = getHandle().createQuery(queryString)
                .bindList("questionIds", ids)
                .bind("timestamp", timestamp);
        return executeGroupAndOptionDtosByQuestionQuery(query);
    }

    private Map<Long, GroupAndOptionDtos> executeGroupAndOptionDtosByQuestionQuery(Query query) {
        Map<Long, List<PicklistOptionDto>> nestedOptMap = new HashMap<>();
        Map<Long, GroupAndOptionDtos> dtosMap = query
                .registerRowMapper(ConstructorMapper.factory(PicklistOptionDto.class, "po"))
                .registerRowMapper(ConstructorMapper.factory(PicklistGroupDto.class, "pg"))
                .reduceRows(new HashMap<>(), (result, row) -> {
                    PicklistOptionDto option = row.getRow(PicklistOptionDto.class);
                    long questionId = row.getColumn("question_id", Long.class);
                    Long groupId = row.getColumn("pg_picklist_group_id", Long.class);
                    Long parentOptionId = row.getColumn("pno_parent_option_id", Long.class);
                    var container = result.computeIfAbsent(questionId, k -> new GroupAndOptionDtos(questionId));

                    if (groupId == null) {
                        if (parentOptionId == null) {
                            container.getUngroupedOptions().add(option);
                        } else {
                            nestedOptMap.computeIfAbsent(parentOptionId, k -> new ArrayList<>());
                            nestedOptMap.get(parentOptionId).add(option);
                        }
                    } else {
                        PicklistGroupDto group = row.getRow(PicklistGroupDto.class);
                        if (container.getGroupIdToOptions().containsKey(groupId)) {
                            container.getGroupIdToOptions().get(groupId).add(option);
                        } else {
                            List<PicklistOptionDto> options = new ArrayList<>();
                            options.add(option);

                            container.getGroupIdToOptions().put(groupId, options);
                            container.getGroups().add(group);
                        }
                    }

                    return result;
                });

        dtosMap.values().forEach(dto -> {
            dto.getUngroupedOptions().forEach(parentDto -> {
                if (nestedOptMap.containsKey(parentDto.getId())) {
                    parentDto.getNestedOptions().addAll(nestedOptMap.get(parentDto.getId()));
                }
            });
        });

        return dtosMap;
    }

    class GroupAndOptionDtos implements Serializable {

        private long questionId;
        private List<PicklistGroupDto> groups = new ArrayList<>();
        private List<PicklistOptionDto> ungroupedOptions = new ArrayList<>();
        private Map<Long, List<PicklistOptionDto>> groupIdToOptions = new HashMap<>();

        public GroupAndOptionDtos(long questionId) {
            this.questionId = questionId;
        }

        public long getQuestionId() {
            return questionId;
        }

        public List<PicklistGroupDto> getGroups() {
            return groups;
        }

        public List<PicklistOptionDto> getUngroupedOptions() {
            return ungroupedOptions;
        }

        public Map<Long, List<PicklistOptionDto>> getGroupIdToOptions() {
            return groupIdToOptions;
        }

        public Set<Long> getTemplateIds() {
            var ids = new HashSet<Long>();
            for (var group : groups) {
                ids.add(group.getNameTemplateId());
            }

            var options = new ArrayList<>(ungroupedOptions);
            groupIdToOptions.values().forEach(options::addAll);

            for (var option : options) {
                ids.addAll(option.getTemplateIds());
            }

            return ids;
        }
    }
}
