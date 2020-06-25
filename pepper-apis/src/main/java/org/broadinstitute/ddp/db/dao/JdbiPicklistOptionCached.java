package org.broadinstitute.ddp.db.dao;

import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.db.dto.PicklistOptionDto;
import org.jdbi.v3.core.Handle;

public class JdbiPicklistOptionCached extends SQLObjectWrapper<JdbiPicklistOption> implements JdbiPicklistOption {

    public JdbiPicklistOptionCached(Handle handle) {
        super(handle, JdbiPicklistOption.class);
    }

    @Override
    public long insert(long picklistQuestionId, String stableId, long optionLabelTemplateId, Long tooltipTemplateId,
                       Long detailLabelTemplateId, boolean allowDetails, boolean isExclusive, int displayOrder, long revisionId) {
        return delegate.insert(picklistQuestionId, stableId, optionLabelTemplateId, tooltipTemplateId,
                detailLabelTemplateId, allowDetails, isExclusive, displayOrder, revisionId);
    }

    @Override
    public long[] bulkInsertByDtos(List<PicklistOptionDto> optionDtos, long picklistQuestionId, long revisionId) {
        return delegate.bulkInsertByDtos(optionDtos, picklistQuestionId, revisionId);
    }

    @Override
    public List<PicklistOptionDto> findAllOrderedOptions(long questionId, String instanceGuid) {
        return delegate.findAllOrderedOptions(questionId, instanceGuid);
    }

    @Override
    public List<PicklistOptionDto> findOptions(List<String> stableIds, long questionId, String instanceGuid) {
        return delegate.findOptions(stableIds, questionId, instanceGuid);
    }

    @Override
    public Optional<PicklistOptionDto> getByStableId(long questionId, String stableId, String instanceGuid) {
        return delegate.getByStableId(questionId, stableId, instanceGuid);
    }

    @Override
    public Optional<PicklistOptionDto> getActiveByStableId(long questionId, String stableId) {
        return delegate.getActiveByStableId(questionId, stableId);
    }

    @Override
    public List<PicklistOptionDto> findAllActiveOrderedOptionsByQuestionId(long questionId) {
        return delegate.findAllActiveOrderedOptionsByQuestionId(questionId);
    }

    @Override
    public int updateRevisionByOptionId(long optionId, long revisionId) {
        return delegate.updateRevisionByOptionId(optionId, revisionId);
    }

    @Override
    public int[] bulkUpdateRevisionIdsByDtos(List<PicklistOptionDto> options, long[] revisionIds) {
        return delegate.bulkUpdateRevisionIdsByDtos(options, revisionIds);
    }
}
