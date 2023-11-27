package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.db.dto.FormBlockDto;
import org.broadinstitute.ddp.db.dto.SectionBlockMembershipDto;
import org.broadinstitute.ddp.model.activity.instance.ActivityInstance;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ActivityInstanceFromDefinitionBuilder;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.customizer.BindList.EmptyHandling;
import org.jdbi.v3.sqlobject.customizer.BindMethods;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Collections;

public interface JdbiFormSectionBlock extends SqlObject {

    @SqlUpdate("insert into form_section__block(form_section_id,block_id,display_order,revision_id) values (?,?,?,?)")
    @GetGeneratedKeys
    long insert(long sectionId, long blockId, int blockOrder, long revisionId);

    @SqlBatch("insert into form_section__block(form_section_id,block_id,display_order,revision_id)"
            + " values (:dto.getSectionId, :dto.getBlockId, :dto.getDisplayOrder, :revisionId)")
    @GetGeneratedKeys
    long[] bulkInsert(@BindMethods("dto") List<SectionBlockMembershipDto> memberships,
                      @Bind("revisionId") long revisionId);


    @SqlQuery("select fsb.* from form_section__block as fsb"
            + " join revision as rev on rev.revision_id = fsb.revision_id"
            + " where fsb.form_section_id = :sectionId and rev.end_date is null"
            + " order by fsb.display_order asc")
    @RegisterConstructorMapper(SectionBlockMembershipDto.class)
    List<SectionBlockMembershipDto> getOrderedActiveMemberships(long sectionId);

    @SqlQuery("select fsb.* from form_section__block as fsb"
            + " join revision as rev on rev.revision_id = fsb.revision_id"
            + " where fsb.block_id = :blockId and rev.end_date is null")
    @RegisterConstructorMapper(SectionBlockMembershipDto.class)
    Optional<SectionBlockMembershipDto> getActiveMembershipByBlockId(long blockId);

    @SqlUpdate("update form_section__block set revision_id = :revisionId where form_section__block_id = :membershipId")
    int updateRevisionIdById(long membershipId, long revisionId);

    @SqlBatch("update form_section__block set revision_id = :revisionId"
            + " where form_section__block_id = :id")
    int[] bulkUpdateRevisionIdsByIds(@Bind("id") List<Long> membershipIds,
                                     @Bind("revisionId") long[] revisionIds);
    
    @UseStringTemplateSqlLocator
    @SqlQuery("queryOrderedFormBlockDtosBySectionIdsAndInstanceGuid")
    @RegisterConstructorMapper(FormBlockDto.class)
    List<FormBlockDto> findOrderedFormBlocksForSectionsAndInstance(
                @BindList(value = "sectionIds", onEmpty = EmptyHandling.NULL_VALUE) List<Long> sectionIds,
                @Bind("instanceGuid") String instanceGuid);

    /**
     * Query for all the block data for given section ids, ordered by their display order.
     *
     * @param sectionIds   the list of section ids to get blocks for
     * @param instanceGuid the activity instance guid to narrow down the revision
     * @return mapping of section id to list of block data
     *
     * @deprecated Not used anywhere except tests. It was used for {@link ActivityInstance} reading until
     *    {@link ActivityInstanceFromDefinitionBuilder} was implemented
     */
    default Map<Long, List<FormBlockDto>> findOrderedFormBlockDtosForSections(List<Long> sectionIds, String instanceGuid) {
        return findOrderedFormBlocksForSectionsAndInstance(sectionIds, instanceGuid).stream()
                .collect(Collectors.groupingBy(FormBlockDto::getSectionId));
    }

    /**
     * Find ordered {@link FormBlockDto}.
     *
     * @deprecated Not used anywhere except tests. It was used for {@link ActivityInstance} reading until
     *    {@link ActivityInstanceFromDefinitionBuilder} was implemented
     */
    @Deprecated
    default List<FormBlockDto> findOrderedFormBlockDtosForSection(long sectionId, String instanceGuid) {
        return findOrderedFormBlockDtosForSections(Collections.singletonList(sectionId), instanceGuid)
                .getOrDefault(sectionId, new ArrayList<>());
    }

    
    @UseStringTemplateSqlLocator
    @SqlQuery("queryOrderedFormBlockDtosBySectionIdsAndTimestamp")
    @RegisterConstructorMapper(FormBlockDto.class)
    List<FormBlockDto> findOrderedFormBlockDtosBySectionIdsAndTimestamp(
            @BindList(value = "sectionIds", onEmpty = EmptyHandling.NULL_VALUE) Iterable<Long> sectionIds,
            @Bind("timestamp") long timestamp);
}
