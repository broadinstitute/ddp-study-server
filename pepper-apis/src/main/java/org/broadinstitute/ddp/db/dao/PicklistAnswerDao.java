package org.broadinstitute.ddp.db.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dto.AnswerDto;
import org.broadinstitute.ddp.db.dto.PicklistOptionDto;
import org.broadinstitute.ddp.db.dto.PicklistQuestionDto;
import org.broadinstitute.ddp.exception.OperationNotAllowedException;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.types.PicklistSelectMode;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface PicklistAnswerDao extends SqlObject {

    Logger LOG = LoggerFactory.getLogger(PicklistAnswerDao.class);

    @CreateSqlObject
    JdbiPicklistOption getJdbiPicklistOption();

    @CreateSqlObject
    JdbiPicklistQuestion getJdbiPicklistQuestion();

    @CreateSqlObject
    AnswerSql getAnswerSql();


    default void assignOptionsToAnswerId(long answerId, List<SelectedPicklistOption> selected, String instanceGuid) {
        if (selected == null || selected.isEmpty()) {
            LOG.info("List of selected options is empty; no options will be assigned to answer id {}", answerId);
            return;
        }

        AnswerDto answerDto = getAnswerSql().findDtoById(answerId)
                .orElseThrow(() -> new NoSuchElementException("Could not find answer with id " + answerId));


        PicklistQuestionDto questionDto = getJdbiPicklistQuestion().findDtoByQuestionId(answerDto.getQuestionId())
                .orElseThrow(() -> new NoSuchElementException("Could not find question id " + answerDto.getQuestionId()
                        + " for answer id " + answerId));

        if (questionDto.getSelectMode() == PicklistSelectMode.SINGLE && selected.size() > 1) {
            throw new OperationNotAllowedException("Single-select picklist question does not allow more than one selected options");
        }

        List<String> selectedStableIds = selected.stream()
                .map(SelectedPicklistOption::getStableId)
                .collect(Collectors.toList());


        Map<String, PicklistOptionDto> dtoMap = new HashMap<>();
        getJdbiPicklistOption()
                .findOptions(selectedStableIds, answerDto.getQuestionId(), instanceGuid)
                .forEach(dto -> dtoMap.put(dto.getStableId(), dto));

        boolean hasExclusive = dtoMap.values().stream().anyMatch(PicklistOptionDto::isExclusive);
        if (hasExclusive && selected.size() > 1) {
            throw new OperationNotAllowedException("Only one selected option allowed when list contains an exclusive option");
        }

        List<Long> selectedIds = new ArrayList<>();
        List<String> detailTexts = new ArrayList<>();
        for (SelectedPicklistOption option : selected) {
            if (!dtoMap.containsKey(option.getStableId())) {
                throw new NoSuchElementException("Could not find picklist option id for " + option.getStableId());
            }

            PicklistOptionDto dto = dtoMap.get(option.getStableId());
            if (!dto.getAllowDetails() && option.getDetailText() != null) {
                throw new OperationNotAllowedException("Picklist option " + dto.getStableId() + " does not allow details");
            }

            detailTexts.add(option.getDetailText());
            selectedIds.add(dto.getId());
        }

        long[] ids = getAnswerSql().bulkInsertPicklistSelected(answerId, selectedIds, detailTexts);
        if (ids.length != selected.size()) {
            throw new DaoException("Not all selected picklist options were assigned to answer " + answerId);
        }
    }
}
