package org.broadinstitute.ddp.db.dao;

import java.util.List;
import java.util.Optional;
import javax.cache.Cache;
import javax.cache.expiry.Duration;

import org.broadinstitute.ddp.cache.CacheService;
import org.broadinstitute.ddp.cache.ModelChangeType;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.TextQuestionDto;
import org.broadinstitute.ddp.model.activity.types.SuggestionType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.jdbi.v3.core.Handle;

public class JdbiTextQuestionCached extends SQLObjectWrapper<JdbiTextQuestion> implements JdbiTextQuestion {
    private static Cache<Long, TextQuestionDto> questionIdToTextQuestionCache;

    public JdbiTextQuestionCached(Handle handle) {
        super(handle, JdbiTextQuestion.class);
        initializeCache();
    }

    private void initializeCache() {
        if (questionIdToTextQuestionCache == null) {
            questionIdToTextQuestionCache = CacheService.getInstance().getOrCreateCache("questionIdToTextQuestionCache",
                    new Duration(),
                    ModelChangeType.STUDY,
                    this.getClass());
        }
    }

    private void initializeCacheData(long activityId) {
        delegate.findDtoByActivityId(activityId).forEach(dto -> questionIdToTextQuestionCache.put(dto.getId(), dto));
    }

    @Override
    public int insert(long questionId, TextInputType inputType, SuggestionType suggestionType, Long placeholderTemplateId,
                      boolean confirmEntry, Long confirmPromptTemplateId, Long mismatchMessageTemplateId) {
        return delegate.insert(questionId, inputType, suggestionType, placeholderTemplateId, confirmEntry, confirmPromptTemplateId,
                mismatchMessageTemplateId);
    }

    @Override
    public boolean update(long questionId, TextInputType inputType, SuggestionType suggestionType, Long placeholderTemplateId) {
        return delegate.update(questionId, inputType, suggestionType, placeholderTemplateId);
    }

    public Optional<TextQuestionDto> findDtoByQuestionId(QuestionDto questionDto) {
        if (isNullCache(questionIdToTextQuestionCache)) {
            return delegate.findDtoByQuestionId(questionDto.getId());
        } else {
            TextQuestionDto dto = questionIdToTextQuestionCache.get(questionDto.getId());
            if (dto == null) {
                initializeCacheData(questionDto.getActivityId());
                dto = questionIdToTextQuestionCache.get(questionDto.getId());
            }
            return Optional.ofNullable(dto);
        }
    }


    @Override
    public Optional<TextQuestionDto> findDtoByQuestionId(long questionId) {
        return delegate.findDtoByQuestionId(questionId);
    }

    @Override
    public List<TextQuestionDto> findDtoByActivityId(long activityId) {
        return delegate.findDtoByActivityId(activityId);
    }
}
