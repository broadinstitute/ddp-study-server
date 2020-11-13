package org.broadinstitute.ddp.db.dao;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dto.BlockContentDto;
import org.broadinstitute.ddp.db.dto.FormBlockDto;
import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;

public interface ContentBlockDao extends SqlObject {

    @CreateSqlObject
    JdbiBlockContent getJdbiBlockContent();

    @CreateSqlObject
    JdbiRevision getJdbiRevision();

    @CreateSqlObject
    TemplateDao getTemplateDao();


    /**
     * Create the new content templates and associate them to given content block.
     *
     * @param block      the content block definition, with the block id
     * @param revisionId the revision to use, will be shared by all created data
     */
    default void insertContentBlock(ContentBlockDef block, long revisionId) {
        long blockId = block.getBlockId();
        TemplateDao templateDao = getTemplateDao();

        long bodyTemplateId = templateDao.insertTemplate(block.getBodyTemplate(), revisionId);
        Long titleTemplateId = (block.getTitleTemplate() == null ? null
                : templateDao.insertTemplate(block.getTitleTemplate(), revisionId));

        getJdbiBlockContent().insert(blockId, bodyTemplateId, titleTemplateId, revisionId);
    }

    /**
     * End currently active content block by finding its associated templates and terminating it.
     *
     * @param blockId the associated block
     * @param meta    the revision metadata used for terminating data
     */
    default void disableContentBlock(long blockId, RevisionMetadata meta) {
        JdbiBlockContent jdbiBlockContent = getJdbiBlockContent();
        JdbiRevision jdbiRevision = getJdbiRevision();
        TemplateDao templateDao = getTemplateDao();

        BlockContentDto blockDto = jdbiBlockContent.findActiveDtoByBlockId(blockId)
                .orElseThrow(() -> new NoSuchElementException("Cannot find active content block with id " + blockId));

        long oldRevId = blockDto.getRevisionId();
        long newRevId = jdbiRevision.copyAndTerminate(oldRevId, meta);
        int numUpdated = jdbiBlockContent.updateRevisionById(blockDto.getId(), newRevId);
        if (numUpdated != 1) {
            throw new DaoException("Unable to terminate active block content " + blockDto.getId());
        }
        jdbiRevision.tryDeleteOrphanedRevision(oldRevId);

        templateDao.disableTemplate(blockDto.getBodyTemplateId(), meta);
        if (blockDto.getTitleTemplateId() != null) {
            templateDao.disableTemplate(blockDto.getTitleTemplateId(), meta);
        }
    }

    default Map<Long, ContentBlockDef> collectBlockDefs(Collection<FormBlockDto> blockDtos, long timestamp) {
        if (blockDtos == null || blockDtos.isEmpty()) {
            return new HashMap<>();
        }

        Set<Long> blockIds = new HashSet<>();
        for (var blockDto : blockDtos) {
            blockIds.add(blockDto.getId());
        }

        Map<Long, BlockContentDto> contentDtos;
        try (var stream = getJdbiBlockContent().findDtosByBlockIdsAndTimestamp(blockIds, timestamp)) {
            contentDtos = stream.collect(Collectors.toMap(BlockContentDto::getBlockId, Function.identity()));
        }

        Map<Long, ContentBlockDef> blockDefs = new HashMap<>();
        for (var blockDto : blockDtos) {
            BlockContentDto contentDto = contentDtos.get(blockDto.getId());

            var blockDef = new ContentBlockDef(null);
            blockDef.setTitleTemplateId(contentDto.getTitleTemplateId());
            blockDef.setBodyTemplateId(contentDto.getBodyTemplateId());
            blockDef.setBlockId(blockDto.getId());
            blockDef.setBlockGuid(blockDto.getGuid());
            blockDef.setShownExpr(blockDto.getShownExpr());

            blockDefs.put(blockDto.getId(), blockDef);
        }

        return blockDefs;
    }
}
