package org.broadinstitute.ddp.db.dao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.broadinstitute.ddp.constants.SqlConstants;
import org.broadinstitute.ddp.db.dto.FormBlockDto;
import org.broadinstitute.ddp.db.dto.SectionBlockMembershipDto;
import org.jdbi.v3.core.statement.Query;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindMethods;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.StringTemplateSqlLocator;

public interface JdbiFormSectionBlock extends SqlObject {

    @SqlUpdate("insert into form_section__block(form_section_id,block_id,display_order,revision_id) values (?,?,?,?)")
    @GetGeneratedKeys
    long insert(long sectionId, long blockId, int blockOrder, long revisionId);

    @SqlBatch("insert into form_section__block(form_section_id,block_id,display_order,revision_id)"
            + " values (:dto.getSectionId, :dto.getBlockId, :dto.getDisplayOrder, :revisionId)")
    @GetGeneratedKeys
    long[] bulkInsert(@BindMethods("dto") List<SectionBlockMembershipDto> memberships,
                      @Bind("revisionId") long revisionId);


    @SqlQuery("select * from form_section__block as fsb"
            + " join revision as rev on rev.revision_id = fsb.revision_id"
            + " where fsb.form_section_id = :sectionId and rev.end_date is null"
            + " order by fsb.display_order asc")
    @RegisterRowMapper(SectionBlockMembershipDto.SectionBlockMembershipDtoMapper.class)
    List<SectionBlockMembershipDto> getOrderedActiveMemberships(long sectionId);

    @SqlQuery("select * from form_section__block as fsb"
            + " join revision as rev on rev.revision_id = fsb.revision_id"
            + " where fsb.block_id = :blockId and rev.end_date is null")
    @RegisterRowMapper(SectionBlockMembershipDto.SectionBlockMembershipDtoMapper.class)
    Optional<SectionBlockMembershipDto> getActiveMembershipByBlockId(long blockId);

    @SqlUpdate("update form_section__block set revision_id = :revisionId where form_section__block_id = :membershipId")
    int updateRevisionIdById(long membershipId, long revisionId);

    @SqlBatch("update form_section__block set revision_id = :revisionId"
            + " where form_section__block_id = :id")
    int[] bulkUpdateRevisionIdsByIds(@Bind("id") List<Long> membershipIds,
                                     @Bind("revisionId") long[] revisionIds);


    /**
     * Query for all the block data for given section ids, ordered by their display order.
     *
     * @param sectionIds   the list of section ids to get blocks for
     * @param instanceGuid the activity instance guid to narrow down the revision
     * @return mapping of section id to list of block data
     */
    default Map<Long, List<FormBlockDto>> findOrderedFormBlockDtosForSections(List<Long> sectionIds, String instanceGuid) {
        String query = StringTemplateSqlLocator
                .findStringTemplate(JdbiFormSectionBlock.class, "queryOrderedFormBlockDtosBySectionIdsAndInstanceGuid")
                .render();
        Query stmt = getHandle().createQuery(query);
        if (sectionIds == null || sectionIds.isEmpty()) {
            stmt.define("sectionIds", "null");
        } else {
            stmt.bindList("sectionIds", sectionIds);
        }
        return stmt.bind("instanceGuid", instanceGuid)
                .registerRowMapper(FormBlockDto.class, new FormBlockDto.FormBlockDtoMapper())
                .reduceRows(new HashMap<>(), (accumulator, row) -> {
                    long key = row.getColumn(SqlConstants.FormSectionTable.ID, Long.class);
                    List<FormBlockDto> blocks = accumulator.computeIfAbsent(key, k -> new ArrayList<>());
                    FormBlockDto current = row.getRow(FormBlockDto.class);
                    blocks.add(current);
                    return accumulator;
                });
    }

    default List<FormBlockDto> findOrderedFormBlockDtosForSection(long sectionId, String instanceGuid) {
        return findOrderedFormBlockDtosForSections(Collections.singletonList(sectionId), instanceGuid)
                .getOrDefault(sectionId, new ArrayList<>());
    }

    @SqlQuery("select bt.block_type_code, b.block_id, b.block_guid, e.expression_text"
            + "  from form_section__block as fsb"
            + "  join revision as rev on rev.revision_id = fsb.revision_id"
            + "  join block as b on b.block_id = fsb.block_id"
            + "  join block_type as bt on bt.block_type_id = b.block_type_id"
            + "  left join block__expression as be on be.block_id = b.block_id"
            + "  left join expression as e on e.expression_id = be.expression_id"
            + "  left join revision as be_rev on be_rev.revision_id = be.revision_id"
            + " where fsb.form_section_id = :sectionId"
            + "   and rev.start_date <= :timestamp"
            + "   and (rev.end_date is null or :timestamp < rev.end_date)"
            + "   and (be.block__expression_id is null or "
            + "       (be_rev.start_date <= :timestamp and (be_rev.end_date is null or :timestamp < be_rev.end_date)))"
            + " order by fsb.display_order asc")
    @RegisterRowMapper(FormBlockDto.FormBlockDtoMapper.class)
    List<FormBlockDto> findOrderedFormBlockDtosBySectionIdAndTimestamp(@Bind("sectionId") long sectionId,
                                                                       @Bind("timestamp") long timestamp);
}
