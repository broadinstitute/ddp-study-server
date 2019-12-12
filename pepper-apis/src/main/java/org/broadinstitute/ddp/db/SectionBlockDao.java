package org.broadinstitute.ddp.db;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.content.I18nContentRenderer;
import org.broadinstitute.ddp.db.dao.ComponentDao;
import org.broadinstitute.ddp.db.dao.JdbiBlockContent;
import org.broadinstitute.ddp.db.dao.JdbiBlockGroupHeader;
import org.broadinstitute.ddp.db.dao.JdbiBlockNesting;
import org.broadinstitute.ddp.db.dao.JdbiFormSectionBlock;
import org.broadinstitute.ddp.db.dao.QuestionDao;
import org.broadinstitute.ddp.db.dto.BlockContentDto;
import org.broadinstitute.ddp.db.dto.BlockGroupHeaderDto;
import org.broadinstitute.ddp.db.dto.FormBlockDto;
import org.broadinstitute.ddp.model.activity.instance.ComponentBlock;
import org.broadinstitute.ddp.model.activity.instance.ConditionalBlock;
import org.broadinstitute.ddp.model.activity.instance.ContentBlock;
import org.broadinstitute.ddp.model.activity.instance.FormBlock;
import org.broadinstitute.ddp.model.activity.instance.FormComponent;
import org.broadinstitute.ddp.model.activity.instance.GroupBlock;
import org.broadinstitute.ddp.model.activity.instance.QuestionBlock;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SectionBlockDao {

    private static final Logger LOG = LoggerFactory.getLogger(SectionBlockDao.class);

    private final I18nContentRenderer i18nRenderer;

    public SectionBlockDao(I18nContentRenderer i18nRenderer) {
        this.i18nRenderer = i18nRenderer;
    }

    /**
     * Find and build all blocks for given sections, respecting the display order of blocks within each section.
     * If there's no blocks for a section, it will be an empty list.
     *
     * @param handle       the jdbi handle
     * @param sectionIds   the sections to lookup blocks for
     * @param instanceGuid the activity instance guid
     * @param langCodeId   the language code id
     * @return mapping of section id to ordered list of blocks
     */
    public Map<Long, List<FormBlock>> getBlocksForSections(Handle handle, List<Long> sectionIds, String instanceGuid, long langCodeId) {
        return getBlocksForSections(handle, sectionIds, instanceGuid, langCodeId, false);
    }

    // This allows fetching blocks with deprecated questions. Prefer the other method that excludes them.
    public Map<Long, List<FormBlock>> getBlocksForSections(Handle handle, List<Long> sectionIds, String instanceGuid, long langCodeId,
                                                           boolean includeDeprecated) {
        Map<Long, List<FormBlock>> result = new HashMap<>();
        Map<Long, List<FormBlockDto>> mapping = handle.attach(JdbiFormSectionBlock.class)
                .findOrderedFormBlockDtosForSections(sectionIds, instanceGuid);
        for (long id : sectionIds) {
            List<FormBlockDto> dtos = mapping.getOrDefault(id, new ArrayList<>());
            List<FormBlock> blocks = dtos.stream()
                    .map(dto -> getBlockByDto(handle, dto, instanceGuid, langCodeId, includeDeprecated))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            result.put(id, blocks);
        }
        return result;
    }

    /**
     * Build the full block from the dto. Blocks with deprecated questions are null-ed out and should be filtered out by caller.
     * Prefer to exclude deprecated questions unless needed, as in the case of data export.
     *
     * @param handle            the jdbi handle
     * @param dto               the block dto
     * @param instanceGuid      the activity instance guid
     * @param langCodeId        the language code id
     * @param includeDeprecated whether to include deprecated questions or not
     * @return the block, or null if contains deprecated question
     */
    private FormBlock getBlockByDto(Handle handle, FormBlockDto dto, String instanceGuid, long langCodeId, boolean includeDeprecated) {
        FormBlock block;
        switch (dto.getType()) {
            case CONTENT:
                BlockContentDto blockContentDto = handle.attach(JdbiBlockContent.class)
                        .findDtoByBlockIdAndInstanceGuid(dto.getId(), instanceGuid)
                        .orElseThrow(() -> new DaoException(String.format(
                                "No content block found for block %d and activity instance %s", dto.getId(), instanceGuid)));
                block = new ContentBlock(blockContentDto.getTitleTemplateId(), blockContentDto.getBodyTemplateId());
                break;
            case QUESTION:
                block = handle.attach(QuestionDao.class).getQuestionByBlockId(dto.getId(), instanceGuid, includeDeprecated, langCodeId)
                        .map(QuestionBlock::new)
                        .orElse(null);
                break;
            case COMPONENT:
                ComponentDao componentDao = handle.attach(ComponentDao.class);
                FormComponent formComponent = componentDao.findByBlockId(instanceGuid, dto.getId(), i18nRenderer, langCodeId);
                block = new ComponentBlock(formComponent);
                break;
            case CONDITIONAL:
                block = handle.attach(QuestionDao.class)
                        .getControlQuestionByBlockId(dto.getId(), instanceGuid, includeDeprecated, langCodeId)
                        .map(control -> {
                            List<FormBlock> nested = handle.attach(JdbiBlockNesting.class)
                                    .findOrderedNestedFormBlockDtos(dto.getId(), instanceGuid)
                                    .stream()
                                    .map(nestedDto -> getBlockByDto(handle, nestedDto, instanceGuid, langCodeId, includeDeprecated))
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toList());
                            ConditionalBlock condBlock = new ConditionalBlock(control);
                            condBlock.getNested().addAll(nested);
                            return condBlock;
                        })
                        .orElse(null);
                break;
            case GROUP:
                BlockGroupHeaderDto headerDto = handle.attach(JdbiBlockGroupHeader.class)
                        .findGroupHeaderDto(dto.getId(), instanceGuid)
                        .orElseThrow(() -> new DaoException("No group header found for block id " + dto.getId()));
                List<FormBlock> children = handle.attach(JdbiBlockNesting.class)
                        .findOrderedNestedFormBlockDtos(dto.getId(), instanceGuid)
                        .stream()
                        .map(nestedDto -> getBlockByDto(handle, nestedDto, instanceGuid, langCodeId, includeDeprecated))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                GroupBlock groupBlock = new GroupBlock(headerDto.getListStyleHint(), headerDto.getPresentationHint(),
                        headerDto.getTitleTemplateId());
                groupBlock.getNested().addAll(children);
                block = groupBlock;
                break;
            default:
                throw new DaoException("Unknown block type " + dto.getType());
        }
        if (block != null) {
            block.setBlockId(dto.getId());
            block.setGuid(dto.getGuid());
            block.setShownExpr(dto.getShownExpr());
        }
        return block;
    }
}
