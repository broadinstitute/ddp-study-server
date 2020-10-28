package org.broadinstitute.ddp.db.dao;

import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.db.dto.PicklistOptionDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.customizer.BindMethods;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.StringTemplateSqlLocator;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface JdbiPicklistOption extends SqlObject {

    @UseStringTemplateSqlLocator
    @SqlUpdate("insertOption")
    @GetGeneratedKeys
    long insert(@Bind("picklistQuestionId") long picklistQuestionId,
                @Bind("stableId") String stableId,
                @Bind("optionLabelTemplateId") long optionLabelTemplateId,
                @Bind("tooltipTemplateId") Long tooltipTemplateId,
                @Bind("detailLabelTemplateId") Long detailLabelTemplateId,
                @Bind("allowDetails") boolean allowDetails,
                @Bind("isExclusive") boolean isExclusive,
                @Bind("displayOrder") int displayOrder,
                @Bind("revisionId") long revisionId);

    @UseStringTemplateSqlLocator
    @SqlUpdate("insertOption")
    @GetGeneratedKeys
    long insert(@Bind("picklistQuestionId") long picklistQuestionId,
                @Bind("stableId") String stableId,
                @Bind("optionLabelTemplateId") long optionLabelTemplateId,
                @Bind("tooltipTemplateId") Long tooltipTemplateId,
                @Bind("detailLabelTemplateId") Long detailLabelTemplateId,
                @Bind("allowDetails") boolean allowDetails,
                @Bind("isExclusive") boolean isExclusive,
                @Bind("displayOrder") int displayOrder,
                @Bind("revisionId") long revisionId,
                @Bind("nestedOptionsTemplateId") Long nestedOptionsTemplateId);

    @UseStringTemplateSqlLocator
    @SqlBatch("insertOptionByDto")
    @GetGeneratedKeys
    long[] bulkInsertByDtos(@BindMethods("dto") List<PicklistOptionDto> optionDtos,
                            @Bind("picklistQuestionId") long picklistQuestionId,
                            @Bind("revisionId") long revisionId);


    /**
     * Get all picklist options for given picklist question that are/were active at creation time of given
     * activity instance, in proper display order.
     */
    @UseStringTemplateSqlLocator
    @SqlQuery("queryAllOrderedOptionsByQuestionIdAndRevision")
    @RegisterConstructorMapper(PicklistOptionDto.class)
    List<PicklistOptionDto> findAllOrderedOptions(@Bind("questionId") long questionId,
                                                  @Bind("instanceGuid") String instanceGuid);

    /**
     * Get a subset of picklist options for given question that are/were active at time of activity instance.
     * The given list of option stable ids are not guaranteed to be found.
     *
     * <p>Note: list of stable ids should not be empty or the query will fail during parameter binding.
     */
    @UseStringTemplateSqlLocator
    @SqlQuery("queryOptionsByStableIdsQuestionIdAndRevision")
    @RegisterConstructorMapper(PicklistOptionDto.class)
    List<PicklistOptionDto> findOptions(@BindList("stableIds") List<String> stableIds,
                                        @Bind("questionId") long questionId,
                                        @Bind("instanceGuid") String instanceGuid);

    /**
     * Get specific picklist option of picklist question that is/was active at creation time of given activity instance.
     * The question id is needed since picklist option stable ids are uniquely scoped to the question.
     */
    @UseStringTemplateSqlLocator
    @SqlQuery("queryInfoByOptionStableIdAndRevision")
    @RegisterConstructorMapper(PicklistOptionDto.class)
    Optional<PicklistOptionDto> getByStableId(@Bind("questionId") long questionId,
                                              @Bind("stableId") String stableId,
                                              @Bind("instanceGuid") String instanceGuid);

    /**
     * Get the active revision of a picklist option by its stable id.
     */
    @UseStringTemplateSqlLocator
    @SqlQuery("queryActiveInfoByOptionStableId")
    @RegisterConstructorMapper(PicklistOptionDto.class)
    Optional<PicklistOptionDto> getActiveByStableId(@Bind("questionId") long questionId,
                                                    @Bind("stableId") String stableId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryAllActiveOrderedOptionsByQuestionId")
    @RegisterConstructorMapper(PicklistOptionDto.class)
    List<PicklistOptionDto> findAllActiveOrderedOptionsByQuestionId(@Bind("questionId") long questionId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryAllOrderedNestedPicklistOptionsByOptionId")
    @RegisterConstructorMapper(PicklistOptionDto.class)
    List<PicklistOptionDto> findActiveNestedPicklistOptions(@Bind("questionId") long questionId, @Bind("optionId") long optionId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryAllActiveNestedPicklistOptionsByQuestionId")
    @RegisterConstructorMapper(PicklistOptionDto.class)
    List<PicklistOptionDto> findAllActiveNestedPicklistOptionsByQuestionId(@Bind("questionId") long questionId);

    @SqlUpdate("update picklist_option set revision_id = :revisionId where picklist_option_id = :optionId")
    int updateRevisionByOptionId(@Bind("optionId") long optionId, @Bind("revisionId") long revisionId);

    @SqlBatch("update picklist_option set revision_id = :revisionId where picklist_option_id = :dto.getId")
    int[] bulkUpdateRevisionIdsByDtos(@BindMethods("dto") List<PicklistOptionDto> options,
                                      @Bind("revisionId") long[] revisionIds);

    @SqlBatch("insert into picklist_nested_option (parent_option_id, nested_option_id) values (:optionId, :nestedOptionId)")
    int[] bulkInsertNestedOptions(@Bind("optionId") long optionId, @Bind("nestedOptionId") List<Long> nestedOptionIds);

    /**
     * Checks if stable id is already used with a picklist question, and if so, is it currently active.
     */
    default boolean isCurrentlyActive(long questionId, String stableId) {
        String query = StringTemplateSqlLocator
                .findStringTemplate(JdbiPicklistOption.class, "isStableIdCurrentlyActive")
                .render();
        return getHandle().createQuery(query)
                .bind("questionId", questionId)
                .bind("stableId", stableId)
                .mapTo(Boolean.class)
                .findFirst()
                .isPresent();
    }
}
