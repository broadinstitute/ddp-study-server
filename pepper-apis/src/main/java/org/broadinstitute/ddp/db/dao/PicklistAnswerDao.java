package org.broadinstitute.ddp.db.dao;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dto.AnswerDto;
import org.broadinstitute.ddp.db.dto.PicklistOptionDto;
import org.broadinstitute.ddp.db.dto.PicklistQuestionDto;
import org.broadinstitute.ddp.exception.OperationNotAllowedException;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
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
    AnswerSql getAnswerSql();


    default void assignOptionsToAnswerId(long answerId, List<SelectedPicklistOption> selected, String instanceGuid) {
        if (selected == null || selected.isEmpty()) {
            LOG.info("List of selected options is empty; no options will be assigned to answer id {}", answerId);
            return;
        }

        AnswerDto answerDto = getAnswerSql().findDtoById(answerId)
                .orElseThrow(() -> new NoSuchElementException("Could not find answer with id " + answerId));

        PicklistQuestionDto questionDto = (PicklistQuestionDto) new JdbiQuestionCached(getHandle())
                .findQuestionDtoById(answerDto.getQuestionId())
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

    default void assignOptionsToAnswerId(long answerId, List<SelectedPicklistOption> selected, PicklistQuestionDef picklistQuestionDef) {
        if (selected == null || selected.isEmpty()) {
            LOG.info("List of selected options is empty; no options will be assigned to answer id {}", answerId);
            return;
        }

        if (picklistQuestionDef.getSelectMode() == PicklistSelectMode.SINGLE && selected.size() > 1) {
            throw new OperationNotAllowedException("Single-select picklist question does not allow more than one selected options");
        }
        Set<String> selectedStableIds = selected.stream().map(sel -> sel.getStableId()).collect(toSet());
        Map<String, PicklistOptionDef> selectedStableIdToOptionDef =
                picklistQuestionDef.getAllPicklistOptions().stream()
                        .filter(option -> selectedStableIds.contains(option.getStableId()))
                        .collect(toMap(def -> def.getStableId(), def -> def));

        boolean hasExclusive = selectedStableIdToOptionDef.values().stream().anyMatch(def -> def.isExclusive());
        if (hasExclusive && selected.size() > 1) {
            throw new OperationNotAllowedException("Only one selected option allowed when list contains an exclusive option");
        }

        List<Long> selectedIds = new ArrayList<>();
        List<String> detailTexts = new ArrayList<>();
        for (SelectedPicklistOption option : selected) {
            if (!selectedStableIdToOptionDef.containsKey(option.getStableId())) {
                throw new NoSuchElementException("Could not find picklist option id for " + option.getStableId());
            }

            PicklistOptionDef dto = selectedStableIdToOptionDef.get(option.getStableId());
            if (!dto.isDetailsAllowed() && option.getDetailText() != null) {
                throw new OperationNotAllowedException("Picklist option " + dto.getStableId() + " does not allow details");
            }

            detailTexts.add(option.getDetailText());
            selectedIds.add(dto.getOptionId());
        }

        long[] ids = getAnswerSql().bulkInsertPicklistSelected(answerId, selectedIds, detailTexts);
        if (ids.length != selected.size()) {
            throw new DaoException("Not all selected picklist options were assigned to answer " + answerId);
        }
    }
}
