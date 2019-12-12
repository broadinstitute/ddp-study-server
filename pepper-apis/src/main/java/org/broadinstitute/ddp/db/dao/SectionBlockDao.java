package org.broadinstitute.ddp.db.dao;

import static org.broadinstitute.ddp.constants.SqlConstants.FormSectionIconSourceTable;
import static org.broadinstitute.ddp.constants.SqlConstants.FormSectionIconTable;
import static org.broadinstitute.ddp.constants.SqlConstants.FormSectionTable;
import static org.broadinstitute.ddp.constants.SqlConstants.ScaleFactorTable;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dto.FormBlockDto;
import org.broadinstitute.ddp.db.dto.RevisionDto;
import org.broadinstitute.ddp.db.dto.SectionBlockMembershipDto;
import org.broadinstitute.ddp.model.activity.definition.ConditionalBlockDef;
import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.GroupBlockDef;
import org.broadinstitute.ddp.model.activity.definition.MailingAddressComponentDef;
import org.broadinstitute.ddp.model.activity.definition.PhysicianInstitutionComponentDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.SectionIcon;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
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
    JdbiBlockConditionalControl getJdbiBlockConditionalControl();

    @CreateSqlObject
    JdbiBlockGroupHeader getJdbiBlockGroupHeader();

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

        BlockType blockType = block.getBlockType();
        if (BlockType.CONTENT.equals(blockType)) {
            getContentBlockDao().insertContentBlock((ContentBlockDef) block, revisionId);
        } else if (BlockType.QUESTION.equals(blockType)) {
            questionDao.insertQuestionBlock(activityId, (QuestionBlockDef) block, revisionId);
        } else if (BlockType.COMPONENT.equals(blockType)) {
            if (block instanceof MailingAddressComponentDef) {
                componentDao.insertComponentDef(blockId, (MailingAddressComponentDef) block, revisionId);
            } else if (block instanceof PhysicianInstitutionComponentDef) {
                componentDao.insertComponentDef(blockId, (PhysicianInstitutionComponentDef) block, revisionId);
            } else {
                throw new DaoException("Unknown component type " + block.getClass().getName());
            }
        } else if (BlockType.CONDITIONAL.equals(blockType)) {
            insertConditionalBlock(activityId, (ConditionalBlockDef) block, revisionId);
        } else if (BlockType.GROUP.equals(blockType)) {
            insertGroupBlock(activityId, (GroupBlockDef) block, revisionId);
        } else {
            throw new DaoException("Unhandled block type " + blockType);
        }
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

    default FormSectionDef findSectionDefByIdAndTimestamp(long sectionId, long timestamp) {
        List<FormBlockDef> blockDefs = getJdbiFormSectionBlock()
                .findOrderedFormBlockDtosBySectionIdAndTimestamp(sectionId, timestamp)
                .stream().map(dto -> findBlockDefByDtoAndTimestamp(dto, timestamp))
                .collect(Collectors.toList());

        // todo: query sectionCode, templates, icons

        return new FormSectionDef(null, blockDefs);
    }

    default FormBlockDef findBlockDefByDtoAndTimestamp(FormBlockDto blockDto, long timestamp) {
        FormBlockDef blockDef;
        switch (blockDto.getType()) {
            case CONTENT:
                blockDef = getContentBlockDao().findDefByBlockIdAndTimestamp(blockDto.getId(), timestamp);
                break;
            case QUESTION:
                blockDef = getQuestionDao().findBlockDefByBlockIdAndTimestamp(blockDto.getId(), timestamp);
                break;
            case COMPONENT:
                blockDef = getComponentDao().findDefByBlockIdAndTimestamp(blockDto.getId(), timestamp);
                break;
            case CONDITIONAL:
                blockDef = findConditionalBlockDefByBlockIdAndTimestamp(blockDto.getId(), timestamp);
                break;
            case GROUP:
                blockDef = findGroupBlockDefByBlockIdAndTimestamp(blockDto.getId(), timestamp);
                break;
            default:
                throw new DaoException("Unhandled block type " + blockDto.getType());
        }
        blockDef.setBlockId(blockDto.getId());
        blockDef.setBlockGuid(blockDto.getGuid());
        blockDef.setShownExpr(blockDto.getShownExpr());
        return blockDef;
    }

    default ConditionalBlockDef findConditionalBlockDefByBlockIdAndTimestamp(long blockId, long timestamp) {
        QuestionDef control = getJdbiBlockConditionalControl()
                .findControlQuestionDtoByBlockIdAndTimestamp(blockId, timestamp)
                .map(dto -> getQuestionDao().findQuestionDefByDtoAndTimestamp(dto, timestamp))
                .orElseThrow(() -> new DaoException(String.format(
                        "Could not find control question definition for block id %d and timestamp %d", blockId, timestamp)));
        ConditionalBlockDef condBlockDef = new ConditionalBlockDef(control);
        condBlockDef.getNested().addAll(findNestedBlockDefsByBlockIdAndTimestamp(blockId, timestamp));
        return condBlockDef;
    }

    default GroupBlockDef findGroupBlockDefByBlockIdAndTimestamp(long blockId, long timestamp) {
        // todo: query group header, template
        GroupBlockDef groupBlockDef = new GroupBlockDef();
        groupBlockDef.getNested().addAll(findNestedBlockDefsByBlockIdAndTimestamp(blockId, timestamp));
        return groupBlockDef;
    }

    default List<FormBlockDef> findNestedBlockDefsByBlockIdAndTimestamp(long parentBlockId, long timestamp) {
        return getJdbiBlockNesting()
                .findOrderedNestedFormBlockDtosByBlockIdAndTimestamp(parentBlockId, timestamp)
                .stream().map(nested -> findBlockDefByDtoAndTimestamp(nested, timestamp))
                .collect(Collectors.toList());
    }
}
