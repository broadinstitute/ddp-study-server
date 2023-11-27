package org.broadinstitute.ddp.db.dao;

import static org.broadinstitute.ddp.constants.SqlConstants.FormSectionIconSourceTable;
import static org.broadinstitute.ddp.constants.SqlConstants.FormSectionIconTable;
import static org.broadinstitute.ddp.constants.SqlConstants.FormSectionTable;
import static org.broadinstitute.ddp.constants.SqlConstants.ScaleFactorTable;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import one.util.streamex.StreamEx;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.BlockGroupHeaderDto;
import org.broadinstitute.ddp.db.dto.FormBlockDto;
import org.broadinstitute.ddp.db.dto.FormSectionDto;
import org.broadinstitute.ddp.db.dto.NestedActivityBlockDto;
import org.broadinstitute.ddp.db.dto.BlockTabularQuestionDto;
import org.broadinstitute.ddp.db.dto.RevisionDto;
import org.broadinstitute.ddp.db.dto.SectionBlockMembershipDto;
import org.broadinstitute.ddp.model.activity.definition.ConditionalBlockDef;
import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.TabularBlockDef;
import org.broadinstitute.ddp.model.activity.definition.GroupBlockDef;
import org.broadinstitute.ddp.model.activity.definition.MailingAddressComponentDef;
import org.broadinstitute.ddp.model.activity.definition.NestedActivityBlockDef;
import org.broadinstitute.ddp.model.activity.definition.PhysicianInstitutionComponentDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.SectionIcon;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.tabular.TabularHeaderDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.FormSection;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.model.pex.Expression;
import org.jdbi.v3.core.mapper.reflect.ConstructorMapper;
import org.jdbi.v3.core.result.RowView;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.stringtemplate4.StringTemplateSqlLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface SectionBlockDao extends SqlObject {

    Logger LOG = LoggerFactory.getLogger(SectionBlockDao.class);

    int DISPLAY_ORDER_GAP = 10;

    @CreateSqlObject
    JdbiActivity getJdbiActivity();

    @CreateSqlObject
    JdbiFormActivityFormSection getJdbiFormActivityFormSection();

    @CreateSqlObject
    JdbiFormSection getJdbiFormSection();

    @CreateSqlObject
    JdbiFormSectionBlock getJdbiFormSectionBlock();

    @CreateSqlObject
    JdbiListStyleHint getJdbiListStyleHint();

    @CreateSqlObject
    JdbiBlock getJdbiBlock();

    @CreateSqlObject
    JdbiBlockType getJdbiBlockType();

    @CreateSqlObject
    JdbiBlockExpression getJdbiBlockExpression();

    @CreateSqlObject
    JdbiBlockEnabledExpression getJdbiBlockEnabledExpression();

    @CreateSqlObject
    JdbiBlockConditionalControl getJdbiBlockConditionalControl();

    @CreateSqlObject
    JdbiBlockGroupHeader getJdbiBlockGroupHeader();

    @CreateSqlObject
    JdbiBlockTabular getJdbiBlockTabular();

    @CreateSqlObject
    JdbiBlockNesting getJdbiBlockNesting();

    @CreateSqlObject
    JdbiExpression getJdbiExpression();

    @CreateSqlObject
    JdbiRevision getJdbiRevision();

    @CreateSqlObject
    ContentBlockDao getContentBlockDao();

    @CreateSqlObject
    TemplateDao getTemplateDao();

    @CreateSqlObject
    QuestionDao getQuestionDao();

    @CreateSqlObject
    ComponentDao getComponentDao();

    @CreateSqlObject
    FormSectionIconDao getFormSectionIconDao();


    /**
     * Create new sections and their related block data for given activity body. The display order of sections and blocks
     * will be the order as given in the lists. The numbering used for display order is ascending but not necessarily
     * consecutive. If a section code is not provided, it will be generated.
     *
     * @param activityId the associated activity
     * @param sections   the list of section definitions, without generated things like ids
     * @param revisionId the revision to use, will be shared by all created data
     */
    default void insertBodySections(long activityId, List<FormSectionDef> sections, long revisionId) {
        JdbiFormActivityFormSection jdbiFormActivityFormSection = getJdbiFormActivityFormSection();
        int sectionOrder = 0;
        for (FormSectionDef section : sections) {
            sectionOrder += DISPLAY_ORDER_GAP;
            long formSectionId = insertSection(activityId, section, revisionId);
            jdbiFormActivityFormSection.insert(activityId, formSectionId, revisionId, sectionOrder);
        }
    }

    /**
     * Create a single form section and it's related blocks. Display order of blocks is in given list order.
     *
     * @param activityId the associated activity
     * @param section    the section definition
     * @param revisionId the revision to use, will be shared by all created data
     * @return id of newly created section, which has already be assigned to given section
     */
    default long insertSection(long activityId, FormSectionDef section, long revisionId) {
        if (section.getSectionId() != null) {
            throw new IllegalStateException("Form section id already set to " + section.getSectionId());
        }

        JdbiFormSection jdbiFormSection = getJdbiFormSection();
        if (section.getSectionCode() == null) {
            section.setSectionCode(jdbiFormSection.generateUniqueCode());
        }

        Long nameTemplateId = null;
        if (section.getNameTemplate() != null) {
            nameTemplateId = getTemplateDao().insertTemplate(section.getNameTemplate(), revisionId);
        }

        long formSectionId = jdbiFormSection.insert(section.getSectionCode(), nameTemplateId);
        section.setSectionId(formSectionId);

        if (section.hasIcons()) {
            for (SectionIcon icon : section.getIcons()) {
                if (!icon.hasRequiredScaleFactor()) {
                    String msg = String.format("Icon for state %s is missing url source for required scale factor %s",
                            icon.getState(), SectionIcon.REQUIRED_SCALE_FACTOR);
                    throw new IllegalArgumentException(msg);
                }
            }
            getFormSectionIconDao().insertIcons(formSectionId, section.getIcons());
        }

        int blockOrder = 0;
        for (FormBlockDef block : section.getBlocks()) {
            blockOrder += DISPLAY_ORDER_GAP;
            insertBlockForSection(activityId, formSectionId, blockOrder, block, revisionId);
        }

        return formSectionId;
    }

    /**
     * Create a new block with its related data, and associate it with given section.
     *
     * @param activityId   the associated activity
     * @param sectionId    the associated section
     * @param displayOrder the display order number for the block
     * @param block        the block definition, without generated things like ids
     * @param revisionId   the revision to use, will be shared by all created data
     */
    default void insertBlockForSection(long activityId, long sectionId, int displayOrder, FormBlockDef block, long revisionId) {
        insertBlockByType(activityId, block, revisionId);
        getJdbiFormSectionBlock().insert(sectionId, block.getBlockId(), displayOrder, revisionId);
    }

    /**
     * Create a new block and its related data.
     *
     * @param activityId the associated activity
     * @param block      the block definition
     * @param revisionId the revision to use, will be shared by all created data
     */
    default void insertBlockByType(long activityId, FormBlockDef block, long revisionId) {
        if (block.getBlockId() != null) {
            throw new IllegalStateException("Block id already set to " + block.getBlockId());
        }
        if (block.getBlockGuid() != null) {
            throw new IllegalStateException("Block guid already set to " + block.getBlockGuid());
        }

        ComponentDao componentDao = getComponentDao();
        JdbiBlock jdbiBlock = getJdbiBlock();
        QuestionDao questionDao = getQuestionDao();

        String blockGuid = jdbiBlock.generateUniqueGuid();
        long blockId = jdbiBlock.insert(getJdbiBlockType().getTypeId(block.getBlockType()), blockGuid);
        block.setBlockGuid(blockGuid);
        block.setBlockId(blockId);

        if (block.getShownExpr() != null) {
            if (block.getShownExprId() != null) {
                throw new IllegalStateException("Block shown expr id already set to " + block.getShownExprId());
            }
            Expression expr = getJdbiExpression().insertExpression(block.getShownExpr());
            block.setShownExprId(expr.getId());
            getJdbiBlockExpression().insert(blockId, expr.getId(), revisionId);
        }
        if (block.getEnabledExpr() != null) {
            if (block.getEnabledExprId() != null) {
                throw new IllegalStateException("Block enabled expr id already set to " + block.getEnabledExprId());
            }
            Expression expr = getJdbiExpression().insertExpression(block.getEnabledExpr());
            block.setEnabledExprId(expr.getId());
            getJdbiBlockEnabledExpression().insert(blockId, expr.getId(), revisionId);
        }

        BlockType blockType = block.getBlockType();
        if (blockType == null) {
            throw new DaoException("The block type is not defined");
        }

        switch (blockType) {
            case CONTENT:
                getContentBlockDao().insertContentBlock((ContentBlockDef) block, revisionId);
                break;
            case QUESTION:
                questionDao.insertQuestionBlock(activityId, (QuestionBlockDef) block, revisionId);
                break;
            case COMPONENT:
                insertComponentBlock(block, revisionId, componentDao, blockId);
                break;
            case CONDITIONAL:
                insertConditionalBlock(activityId, (ConditionalBlockDef) block, revisionId);
                break;
            case GROUP:
                insertGroupBlock(activityId, (GroupBlockDef) block, revisionId);
                break;
            case TABULAR:
                insertTabularBlock(activityId, (TabularBlockDef) block, revisionId);
                break;
            case ACTIVITY:
                insertNestedActivityBlock(activityId, (NestedActivityBlockDef) block, revisionId);
                break;
            default:
                throw new DaoException("Unhandled block type " + blockType);
        }
    }

    private void insertComponentBlock(FormBlockDef block, long revisionId, ComponentDao componentDao, long blockId) {
        if (block instanceof MailingAddressComponentDef) {
            componentDao.insertComponentDef(blockId, (MailingAddressComponentDef) block, revisionId);
        } else if (block instanceof PhysicianInstitutionComponentDef) {
            componentDao.insertComponentDef(blockId, (PhysicianInstitutionComponentDef) block, revisionId);
        } else {
            throw new DaoException("Unknown component type " + block.getClass().getName());
        }
    }

    private void insertNestedActivityBlock(long parentActivityId, NestedActivityBlockDef block, long revisionId) {
        ActivityDto nestedActivityDto = getJdbiActivity()
                .findActivityByParentActivityIdAndActivityCode(parentActivityId, block.getActivityCode())
                .orElseThrow(() -> new DaoException("Could not find child nested activity " + block.getActivityCode()));

        Long addButtonTemplateId = block.getAddButtonTemplate() == null ? null
                : getTemplateDao().insertTemplate(block.getAddButtonTemplate(), revisionId);

        getJdbiBlock().insertNestedActivityBlock(
                block.getBlockId(),
                nestedActivityDto.getActivityId(),
                block.getRenderHint(),
                block.isAllowMultiple(),
                addButtonTemplateId,
                revisionId);
    }

    /**
     * Create a new conditional block. This assumes that the parent block container has already been created.
     *
     * @param activityId the associated activity
     * @param block      the conditional block definition
     * @param revisionId the revision to use, will be shared by all created data
     */
    default void insertConditionalBlock(long activityId, ConditionalBlockDef block, long revisionId) {
        if (block.getNested().isEmpty()) {
            throw new IllegalArgumentException("Conditional block requires at least one nested child block");
        }

        getQuestionDao().insertQuestionByType(activityId, block.getControl(), revisionId);
        getJdbiBlockConditionalControl().insert(block.getBlockId(), block.getControl().getQuestionId());
        LOG.info("Inserted control question id {} for block id {}", block.getControl().getQuestionId(), block.getBlockId());

        insertNestedBlocks(activityId, block.getBlockId(), block.getNested(), revisionId);
    }

    default void insertTabularBlock(long activityId, TabularBlockDef block, long revisionId) {
        if (block.getColumnsCount() <= 0) {
            throw new IllegalArgumentException("The count of columns must be a positive number");
        }

        final long tabularId = getJdbiBlockTabular().insert(block.getBlockId(), block.getColumnsCount(), revisionId);
        LOG.info("Inserted tabular block id {} for block id {}", tabularId, block.getBlockId());

        for (final TabularHeaderDef header : block.getHeaders()) {
            final long templateId = getTemplateDao().insertTemplate(header.getLabel(), revisionId);
            getJdbiBlockTabular().insertHeader(tabularId, header.getColumnSpan(), templateId);
        }
        LOG.info("Inserted {} headers for tabular block {}", block.getHeaders().size(), tabularId);
        for (FormBlockDef formBlockDef : block.getBlocks()) {
            insertBlockByType(activityId, formBlockDef, revisionId);
            if (formBlockDef.getBlockType().isQuestionBlock()) {
                getJdbiBlockTabular().insertQuestion(tabularId, ((QuestionBlockDef) formBlockDef).getQuestion().getQuestionId(),
                        ((QuestionBlockDef) formBlockDef).getColumnSpan());
            }
        }
        LOG.info("Inserted {} question blocks for tabular block {}", block.getBlocks().size(), tabularId);
    }

    /**
     * Create a new group block. This assumes that the parent block container has already been created.
     *
     * @param activityId the associated activity
     * @param block      the group block definition
     * @param revisionId the revision to use, will be shared by all created data
     */
    default void insertGroupBlock(long activityId, GroupBlockDef block, long revisionId) {
        if (block.getNested().isEmpty()) {
            throw new IllegalArgumentException("Group block requires at least one nested child block");
        }

        Long listStyleHintId = null;
        if (block.getListStyleHint() != null) {
            listStyleHintId = getJdbiListStyleHint().getHintId(block.getListStyleHint());
        }

        Long titleTemplateId = null;
        if (block.getTitleTemplate() != null) {
            titleTemplateId = getTemplateDao().insertTemplate(block.getTitleTemplate(), revisionId);
        }

        long headerId = getJdbiBlockGroupHeader().insert(block.getBlockId(), listStyleHintId, titleTemplateId, revisionId,
                block.getPresentationHint());
        LOG.info("Inserted group header id {} for block id {}", headerId, block.getBlockId());

        insertNestedBlocks(activityId, block.getBlockId(), block.getNested(), revisionId);
    }

    /**
     * Create the children blocks and associate with parent block.
     *
     * @param activityId    the associated activity
     * @param parentBlockId the id of parent block
     * @param children      the list of children blocks, in desired display order
     * @param revisionId    the revision to use, will be shared by all created data
     */
    default void insertNestedBlocks(long activityId, long parentBlockId, List<FormBlockDef> children, long revisionId) {
        JdbiBlockNesting jdbiBlockNesting = getJdbiBlockNesting();
        int nestedBlockOrder = 0;
        for (FormBlockDef nested : children) {
            if (nested.getBlockType().isContainerBlock()) {
                throw new IllegalStateException("Nesting container blocks is not allowed");
            }
            nestedBlockOrder += DISPLAY_ORDER_GAP;
            insertBlockByType(activityId, nested, revisionId);
            jdbiBlockNesting.insert(parentBlockId, nested.getBlockId(), nestedBlockOrder, revisionId);
            LOG.info("Inserted nested block id {} for parent block id {}", nested.getBlockId(), parentBlockId);
        }
    }

    /**
     * Adjust list of block memberships for given section to make room for a new block at desired position. The
     * actual display order needed to put new block in desired position will be computed and returned.
     *
     * <p>The position is a zero-indexed number into the list of blocks. Other block memberships will be adjusted and
     * shifted down if necessary. For example, if we have a list of 3 blocks [b0, b1, b2], and we desire position 1,
     * then we will shift things so we get [b0, _, b1, b2] and return the display order number needed to put a new
     * block in the new slot. If position is greater than size of list (by any amount), we simply reserve a spot at
     * the end.
     *
     * <p>Note: clients should not use this method directly as it has side effects of changing block memberships.
     *
     * @param sectionId the associated section
     * @param position  the desired position in list of blocks, zero-indexed
     * @param revision  the revision data, the start metadata will be used to terminate shifted block memberships
     * @return computed display order to use for desired position
     */
    default int allocateBlockPosition(long sectionId, int position, RevisionDto revision) {
        if (position < 0) {
            throw new IllegalArgumentException("Desired position must be non-negative");
        }

        List<SectionBlockMembershipDto> memberships = getJdbiFormSectionBlock().getOrderedActiveMemberships(sectionId);
        int size = memberships.size();

        int displayOrderToUse;
        if (size == 0) {
            // This will be the only block in section.
            displayOrderToUse = DISPLAY_ORDER_GAP;
        } else if (size <= position) {
            // This will be the last block in section, just take the current last and add a delta.
            displayOrderToUse = memberships.get(size - 1).getDisplayOrder() + DISPLAY_ORDER_GAP;
        } else {
            // Insert the new block after the previous one.
            int prevOrder = (position == 0 ? 0 : memberships.get(position - 1).getDisplayOrder());
            displayOrderToUse = prevOrder + 1;
            shiftBlockMemberships(memberships, position, displayOrderToUse, revision);
        }

        return displayOrderToUse;
    }

    /**
     * Ensure memberships starting at given index all have a display order that is greater than the allocated
     * display order. Otherwise the block memberships will be shifted "down" to accommodate it.
     *
     * <p>Note: clients should not use this method directly as it has side effects of changing block memberships,
     * and some invariants are presumed to be true.
     *
     * @param memberships    block memberships in ascending order by display order
     * @param startIdx       the starting index, inclusive
     * @param allocatedOrder the display order to accommodate
     * @param revision       the revision data, the start metadata will be used to terminate shifted block memberships
     */
    default void shiftBlockMemberships(List<SectionBlockMembershipDto> memberships, int startIdx, int allocatedOrder,
                                       RevisionDto revision) {
        JdbiFormSectionBlock jdbiSectionBlock = getJdbiFormSectionBlock();
        JdbiRevision jdbiRev = getJdbiRevision();

        // Find blocks that needs to be shifted.
        int idx = startIdx;
        int prevOrder = allocatedOrder;
        int size = memberships.size();
        while (idx < size) {
            SectionBlockMembershipDto curr = memberships.get(idx);
            if (prevOrder < curr.getDisplayOrder()) {
                break;
            }
            int newOrder = prevOrder + 1;
            curr.setDisplayOrder(newOrder);
            prevOrder = newOrder;
            idx += 1;
        }

        // Update the shifted blocks, if any.
        List<SectionBlockMembershipDto> shifted = memberships.subList(startIdx, idx);
        if (!shifted.isEmpty()) {
            List<Long> shiftedIds = new ArrayList<>();
            List<Long> oldRevIds = new ArrayList<>();
            for (SectionBlockMembershipDto dto : shifted) {
                shiftedIds.add(dto.getId());
                oldRevIds.add(dto.getRevisionId());
            }

            long[] newRevIds = jdbiRev.bulkCopyAndTerminate(oldRevIds, revision);
            if (newRevIds.length != oldRevIds.size()) {
                throw new DaoException("Not all revisions for shifted block memberships were terminated");
            }

            int[] numUpdated = jdbiSectionBlock.bulkUpdateRevisionIdsByIds(shiftedIds, newRevIds);
            if (Arrays.stream(numUpdated).sum() != numUpdated.length) {
                throw new DaoException("Not all shifted block membership revisions were updated");
            }

            Set<Long> maybeOrphanedIds = new HashSet<>(oldRevIds);
            for (long revId : maybeOrphanedIds) {
                if (jdbiRev.tryDeleteOrphanedRevision(revId)) {
                    LOG.info("Deleted orphaned revision {}", revId);
                }
            }

            // Create new membership entries with shifted display orders.
            long[] ids = jdbiSectionBlock.bulkInsert(shifted, revision.getId());
            if (ids.length != shifted.size()) {
                throw new DaoException("Not all shifted block memberships were updated");
            }
        }
    }

    /**
     * Add a new block to given section. The position is a zero-indexed number indicating where in the list of blocks to
     * insert new block. Other block memberships will be shifted as needed, using given revision data.
     *
     * @param activityId the associated activity
     * @param sectionId  the associated section
     * @param position   the desired position, zero-indexed
     * @param block      the block definition
     * @param revision   the revision data
     */
    default void addBlock(long activityId, long sectionId, int position, FormBlockDef block, RevisionDto revision) {
        int displayOrder = allocateBlockPosition(sectionId, position, revision);
        insertBlockForSection(activityId, sectionId, displayOrder, block, revision.getId());
    }

    /**
     * End currently active block by terminating its block membership and propagate to terminate all its related data.
     *
     * @param blockId the block id
     * @param meta    the revision metadata used for terminating data
     */
    default void disableBlock(long blockId, RevisionMetadata meta) {
        JdbiFormSectionBlock jdbiSectionBlock = getJdbiFormSectionBlock();
        JdbiBlockExpression jdbiBlockExpr = getJdbiBlockExpression();
        JdbiBlock jdbiBlock = getJdbiBlock();
        JdbiRevision jdbiRev = getJdbiRevision();

        SectionBlockMembershipDto membership = jdbiSectionBlock.getActiveMembershipByBlockId(blockId).orElseThrow(() ->
                new NoSuchElementException("Cannot find active block membership for " + blockId));
        long newRevId = jdbiRev.copyAndTerminate(membership.getRevisionId(), meta);
        int numUpdated = jdbiSectionBlock.updateRevisionIdById(membership.getId(), newRevId);
        if (numUpdated != 1) {
            throw new DaoException("Cannot update revision for block membership " + membership.getId());
        }

        jdbiBlockExpr.getActiveByBlockId(blockId).ifPresent(dto -> {
            long revId = jdbiRev.copyAndTerminate(dto.getRevisionId(), meta);
            int updated = jdbiBlockExpr.updateRevisionIdById(dto.getId(), revId);
            if (updated != 1) {
                throw new DaoException("Cannot update revision for block expression " + dto.getId());
            }
        });

        BlockType blockType = jdbiBlock.findById(blockId).getType();
        if (BlockType.CONTENT.equals(blockType)) {
            getContentBlockDao().disableContentBlock(blockId, meta);
        } else if (BlockType.QUESTION.equals(blockType)) {
            getQuestionDao().disableQuestionBlock(blockId, meta);
        } else if (BlockType.COMPONENT.equals(blockType)) {
            getComponentDao().disableComponentBlock(blockId, meta);
        } else {
            throw new DaoException("Unhandled block type " + blockType);
        }
    }

    default Map<Long, FormSection> findAllInstanceSectionsById(List<Long> sectionIds) {
        if (sectionIds == null || sectionIds.isEmpty()) {
            return new HashMap<>();
        }
        String query = StringTemplateSqlLocator
                .findStringTemplate(SectionBlockDao.class, "queryAllInstanceSectionsWithIconsByIds")
                .render();
        return getHandle().createQuery(query)
                .bindList("sectionIds", sectionIds)
                .registerRowMapper(ConstructorMapper.factory(FormSection.class))
                .registerRowMapper(ConstructorMapper.factory(SectionIcon.class))
                .reduceRows(new HashMap<>(), new SectionsWithIconsRowReducer());
    }

    class SectionsWithIconsRowReducer implements BiFunction<Map<Long, FormSection>, RowView, Map<Long, FormSection>> {
        @Override
        public Map<Long, FormSection> apply(Map<Long, FormSection> accumulator, RowView row) {
            long sectionId = row.getColumn(FormSectionTable.ID, Long.class);
            FormSection section = accumulator.computeIfAbsent(sectionId, id -> row.getRow(FormSection.class));

            Long iconId = row.getColumn(FormSectionIconTable.ID, Long.class);
            if (iconId != null) {
                SectionIcon icon = section.getIconById(iconId);
                if (icon == null) {
                    icon = row.getRow(SectionIcon.class);
                    section.addIcon(icon);
                }

                String scale = row.getColumn(ScaleFactorTable.NAME, String.class);
                String rawUrl = row.getColumn(FormSectionIconSourceTable.URL, String.class);

                URL url;
                try {
                    url = new URL(rawUrl);
                } catch (MalformedURLException e) {
                    String msg = String.format("Encountered malformed url '%s' while processing scale factor '%s' for"
                            + " icon id %d and section id %d", rawUrl, scale, iconId, sectionId);
                    throw new DaoException(msg, e);
                }

                icon.putSource(scale, url);
            }

            return accumulator;
        }
    }

    default Map<Long, FormSectionDef> collectSectionDefs(Collection<Long> sectionIds, long timestamp) {
        if (sectionIds == null || sectionIds.isEmpty()) {
            return new HashMap<>();
        }

        List<FormBlockDto> orderedBlockDtos = getJdbiFormSectionBlock()
                .findOrderedFormBlockDtosBySectionIdsAndTimestamp(sectionIds, timestamp);
        Map<Long, FormBlockDef> allBlockDefs = collectBlockDefs(orderedBlockDtos, timestamp);

        List<FormSectionDto> sectionDtos = getJdbiFormSection().findByIds(sectionIds);
        Map<Long, List<FormBlockDef>> sectionIdToBlockDefs = new HashMap<>();
        for (var blockDto : orderedBlockDtos) {
            sectionIdToBlockDefs
                    .computeIfAbsent(blockDto.getSectionId(), id -> new ArrayList<>())
                    .add(allBlockDefs.get(blockDto.getId()));
        }

        Set<Long> templateIds = new HashSet<>();
        for (var sectionDto : sectionDtos) {
            Long nameTemplateId = sectionDto.getNameTemplateId();
            if (nameTemplateId != null) {
                templateIds.add(nameTemplateId);
            }
        }
        Map<Long, Template> templates = getTemplateDao().collectTemplatesByIdsAndTimestamp(templateIds, timestamp);

        Map<Long, FormSectionDef> sectionDefs = new HashMap<>();
        for (var sectionDto : sectionDtos) {
            List<FormBlockDef> blockDefs = sectionIdToBlockDefs.getOrDefault(sectionDto.getId(), new ArrayList<>());

            var sectionDef = new FormSectionDef(sectionDto.getSectionCode(), blockDefs);
            sectionDef.setSectionId(sectionDto.getId());
            sectionDef.setNameTemplate(templates.getOrDefault(sectionDto.getNameTemplateId(), null));

            Collection<SectionIcon> sectionIcons = getFormSectionIconDao().findAllBySectionId(sectionDto.getId());
            for (SectionIcon sectionIcon : sectionIcons) {
                sectionDef.getIcons().add(sectionIcon);
            }
            sectionDefs.put(sectionDto.getId(), sectionDef);
        }

        return sectionDefs;
    }

    default Map<Long, FormBlockDef> collectBlockDefs(Collection<FormBlockDto> blockDtos, long timestamp) {
        if (blockDtos == null || blockDtos.isEmpty()) {
            return new HashMap<>();
        }

        List<FormBlockDto> contentBlockDtos = new ArrayList<>();
        List<FormBlockDto> questionBlockDtos = new ArrayList<>();
        List<FormBlockDto> componentBlockDtos = new ArrayList<>();
        List<FormBlockDto> conditionalBlockDtos = new ArrayList<>();
        List<FormBlockDto> tabularBlockDtos = new ArrayList<>();
        List<FormBlockDto> groupBlockDtos = new ArrayList<>();
        List<FormBlockDto> nestedActivityBlockDtos = new ArrayList<>();

        for (var blockDto : blockDtos) {
            switch (blockDto.getType()) {
                case ACTIVITY:
                    nestedActivityBlockDtos.add(blockDto);
                    break;
                case CONTENT:
                    contentBlockDtos.add(blockDto);
                    break;
                case QUESTION:
                    questionBlockDtos.add(blockDto);
                    break;
                case COMPONENT:
                    componentBlockDtos.add(blockDto);
                    break;
                case CONDITIONAL:
                    conditionalBlockDtos.add(blockDto);
                    break;
                case TABULAR:
                    tabularBlockDtos.add(blockDto);
                    break;
                case GROUP:
                    groupBlockDtos.add(blockDto);
                    break;
                default:
                    throw new DaoException("Unhandled block type " + blockDto.getType());
            }
        }

        Map<Long, FormBlockDef> blockDefs = new HashMap<>();
        blockDefs.putAll(getContentBlockDao().collectBlockDefs(contentBlockDtos, timestamp));
        blockDefs.putAll(new QuestionCachedDao(getHandle()).collectBlockDefs(questionBlockDtos, timestamp));
        blockDefs.putAll(getComponentDao().collectBlockDefs(componentBlockDtos, timestamp));
        blockDefs.putAll(collectConditionalBlockDefs(conditionalBlockDtos, timestamp));
        blockDefs.putAll(collectTabularBlockDefs(tabularBlockDtos, timestamp));
        blockDefs.putAll(collectGroupBlockDefs(groupBlockDtos, timestamp));
        blockDefs.putAll(collectNestedActivityBlockDefs(nestedActivityBlockDtos, timestamp));

        return blockDefs;
    }

    private Map<Long, NestedActivityBlockDef> collectNestedActivityBlockDefs(Collection<FormBlockDto> blockDtos, long timestamp) {
        if (blockDtos == null || blockDtos.isEmpty()) {
            return new HashMap<>();
        }

        Set<Long> blockIds = new HashSet<>();
        for (var blockDto : blockDtos) {
            blockIds.add(blockDto.getId());
        }

        Map<Long, NestedActivityBlockDto> nestedActBlockDtos;
        try (var stream = getJdbiBlock().findNestedActivityBlockDtos(blockIds, timestamp)) {
            nestedActBlockDtos = stream.collect(
                    Collectors.toMap(NestedActivityBlockDto::getBlockId, Function.identity()));
        }

        Set<Long> templateIds = new HashSet<>();
        for (var nestedActBlockDto : nestedActBlockDtos.values()) {
            if (nestedActBlockDto.getAddButtonTemplateId() != null) {
                templateIds.add(nestedActBlockDto.getAddButtonTemplateId());
            }
        }
        Map<Long, Template> templates = getTemplateDao().collectTemplatesByIdsAndTimestamp(templateIds, timestamp);

        Map<Long, NestedActivityBlockDef> blockDefs = new HashMap<>();
        for (var blockDto : blockDtos) {
            NestedActivityBlockDto nestedActBlockDto = nestedActBlockDtos.get(blockDto.getId());
            Template addButtonTemplate = templates.getOrDefault(nestedActBlockDto.getAddButtonTemplateId(), null);

            var blockDef = new NestedActivityBlockDef(
                    nestedActBlockDto.getNestedActivityCode(),
                    nestedActBlockDto.getRenderHint(),
                    nestedActBlockDto.isAllowMultiple(),
                    addButtonTemplate);
            blockDef.setBlockId(blockDto.getId());
            blockDef.setBlockGuid(blockDto.getGuid());
            blockDef.setShownExpr(blockDto.getShownExpr());
            blockDef.setEnabledExpr(blockDto.getEnabledExpr());

            blockDefs.put(blockDto.getId(), blockDef);
        }

        return blockDefs;
    }

    default Map<Long, ConditionalBlockDef> collectConditionalBlockDefs(Collection<FormBlockDto> blockDtos, long timestamp) {
        if (blockDtos == null || blockDtos.isEmpty()) {
            return new HashMap<>();
        }

        Set<Long> blockIds = new HashSet<>();
        for (var blockDto : blockDtos) {
            blockIds.add(blockDto.getId());
        }

        Map<Long, Long> blockIdToControlQuestionId = getJdbiBlockConditionalControl()
                .findControlQuestionIdsByBlockIdsAndTimestamp(blockIds, timestamp);
        Map<Long, QuestionDef> questionDefs = new QuestionCachedDao(getHandle())
                .collectQuestionDefs(blockIdToControlQuestionId.values(), timestamp);
        Map<Long, List<FormBlockDef>> parentIdToNestedDefs = collectNestedBlockDefs(blockIds, timestamp);

        Map<Long, ConditionalBlockDef> blockDefs = new HashMap<>();
        for (var blockDto : blockDtos) {
            long controlQuestionId = blockIdToControlQuestionId.get(blockDto.getId());
            QuestionDef controlQuestionDef = questionDefs.get(controlQuestionId);
            List<FormBlockDef> nestedDefs = parentIdToNestedDefs.getOrDefault(blockDto.getId(), new ArrayList<>());

            var blockDef = new ConditionalBlockDef(controlQuestionDef);
            blockDef.getNested().addAll(nestedDefs);
            blockDef.setBlockId(blockDto.getId());
            blockDef.setBlockGuid(blockDto.getGuid());
            
            var shownExpression = blockDto.getShownExpression();
            if (shownExpression != null) {
                blockDef.setShownExprId(shownExpression.getId());
                blockDef.setShownExpr(shownExpression.getText());
            }

            var enabledExpression = blockDto.getEnabledExpression();
            if (enabledExpression != null) {
                blockDef.setEnabledExprId(enabledExpression.getId());
                blockDef.setEnabledExpr(enabledExpression.getText());
            }

            blockDefs.put(blockDto.getId(), blockDef);
        }

        return blockDefs;
    }

    default Map<Long, TabularBlockDef> collectTabularBlockDefs(Collection<FormBlockDto> blockDtos, long timestamp) {
        if (blockDtos == null || blockDtos.isEmpty()) {
            return new HashMap<>();
        }

        Set<Long> blockIds = new HashSet<>();
        for (var blockDto : blockDtos) {
            blockIds.add(blockDto.getId());
        }

        Map<Long, List<BlockTabularQuestionDto>> questionsByBlocks = StreamEx.of(getJdbiBlockTabular()
                        .findQuestionsByBlockIdsAndTimestamp(blockIds, timestamp))
                .groupingBy(BlockTabularQuestionDto::getBlockId);

        Map<Long, QuestionBlockDef> questionBlockDefs = new QuestionCachedDao(getHandle())
                .collectTabularBlockDefs(StreamEx.of(questionsByBlocks.values()).flatMap(Collection::stream)
                        .collect(Collectors.toList()), timestamp);

        Map<Long, TabularBlockDef> blockDefs = new HashMap<>();
        for (var blockDto : blockDtos) {
            List<QuestionBlockDef> blockDefList = new ArrayList<>();
            for (final BlockTabularQuestionDto tabularQuestion : questionsByBlocks.get(blockDto.getId())) {
                blockDefList.add(questionBlockDefs.get(tabularQuestion.getQuestionBlockId()));
            }

            final var tabularBlock = getJdbiBlockTabular().findByBlockIdAndTimestamp(blockDto.getId(), timestamp);
            final var blockDef = new TabularBlockDef(tabularBlock.getColumnsCount());
            blockDef.setBlockId(blockDto.getId());
            blockDef.setBlockGuid(blockDto.getGuid());
            blockDef.setShownExpr(blockDto.getShownExpr());
            blockDef.setEnabledExpr(blockDto.getEnabledExpr());

            blockDef.getHeaders().addAll(StreamEx.of(getJdbiBlockTabular()
                            .findHeadersByBlockIdAndTimestamp(blockDto.getId(), timestamp))
                    .map(tabularHeader -> new TabularHeaderDef(tabularHeader.getColumnSpan(),
                            getTemplateDao().loadTemplateByIdAndTimestamp(tabularHeader.getTemplateId(), timestamp)))
                    .toList());

            blockDef.getBlocks().addAll(blockDefList);
            blockDefs.put(blockDto.getId(), blockDef);
        }

        return blockDefs;
    }

    default Map<Long, GroupBlockDef> collectGroupBlockDefs(Collection<FormBlockDto> blockDtos, long timestamp) {
        if (blockDtos == null || blockDtos.isEmpty()) {
            return new HashMap<>();
        }

        Set<Long> blockIds = new HashSet<>();
        for (var blockDto : blockDtos) {
            blockIds.add(blockDto.getId());
        }

        Map<Long, BlockGroupHeaderDto> groupDtos;
        try (var stream = getJdbiBlockGroupHeader().findDtosByBlockIdsAndTimestamp(blockIds, timestamp)) {
            groupDtos = stream.collect(Collectors.toMap(BlockGroupHeaderDto::getBlockId, Function.identity()));
        }
        Map<Long, List<FormBlockDef>> parentIdToNestedDefs = collectNestedBlockDefs(blockIds, timestamp);

        Set<Long> templateIds = new HashSet<>();
        for (var groupDto : groupDtos.values()) {
            Long titleTemplateId = groupDto.getTitleTemplateId();
            if (titleTemplateId != null) {
                templateIds.add(titleTemplateId);
            }
        }
        Map<Long, Template> templates = getTemplateDao().collectTemplatesByIdsAndTimestamp(templateIds, timestamp);

        Map<Long, GroupBlockDef> blockDefs = new HashMap<>();
        for (var blockDto : blockDtos) {
            BlockGroupHeaderDto groupDto = groupDtos.get(blockDto.getId());
            Template titleTemplate = templates.getOrDefault(groupDto.getTitleTemplateId(), null);
            List<FormBlockDef> nestedDefs = parentIdToNestedDefs.getOrDefault(blockDto.getId(), new ArrayList<>());

            var blockDef = new GroupBlockDef(groupDto.getListStyleHint(), titleTemplate);
            blockDef.setPresentationHint(groupDto.getPresentationHint());
            blockDef.getNested().addAll(nestedDefs);
            blockDef.setBlockId(blockDto.getId());
            blockDef.setBlockGuid(blockDto.getGuid());
            blockDef.setShownExpr(blockDto.getShownExpr());
            blockDef.setEnabledExpr(blockDto.getEnabledExpr());

            blockDefs.put(blockDto.getId(), blockDef);
        }

        return blockDefs;
    }

    default Map<Long, List<FormBlockDef>> collectNestedBlockDefs(Collection<Long> parentBlockIds, long timestamp) {
        if (parentBlockIds == null || parentBlockIds.isEmpty()) {
            return new HashMap<>();
        }

        List<FormBlockDto> orderedNestedDtos = getJdbiBlockNesting()
                .findOrderedNestedDtosByParentIdsAndTimestamp(parentBlockIds, timestamp);
        Map<Long, FormBlockDef> allNestedDefs = collectBlockDefs(orderedNestedDtos, timestamp);

        Map<Long, List<FormBlockDef>> parentIdToNestedBlockDefs = new HashMap<>();
        for  (var nestedDto : orderedNestedDtos) {
            parentIdToNestedBlockDefs
                    .computeIfAbsent(nestedDto.getParentBlockId(), id -> new ArrayList<>())
                    .add(allNestedDefs.get(nestedDto.getId()));
        }

        return parentIdToNestedBlockDefs;
    }
}
