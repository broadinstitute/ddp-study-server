package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dto.MatrixRowDto;
import org.broadinstitute.ddp.db.dto.AnswerDto;
import org.broadinstitute.ddp.db.dto.MatrixOptionDto;
import org.broadinstitute.ddp.db.dto.MatrixGroupDto;
import org.broadinstitute.ddp.db.dto.MatrixQuestionDto;
import org.broadinstitute.ddp.exception.OperationNotAllowedException;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixGroupDef;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixRowDef;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedMatrixCell;
import org.broadinstitute.ddp.model.activity.types.MatrixSelectMode;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.NoSuchElementException;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

public interface MatrixAnswerDao extends SqlObject {

    Logger LOG = LoggerFactory.getLogger(MatrixAnswerDao.class);

    @CreateSqlObject
    JdbiMatrixOption getJdbiMatrixOption();

    @CreateSqlObject
    JdbiMatrixRow getJdbiMatrixRow();

    @CreateSqlObject
    JdbiMatrixGroup getJdbiMatrixGroup();

    @CreateSqlObject
    AnswerSql getAnswerSql();


    default void assignOptionsToAnswerId(long answerId, List<SelectedMatrixCell> selected, String instanceGuid) {
        if (selected == null || selected.isEmpty()) {
            LOG.info("List of selected options is empty; no options will be assigned to answer id {}", answerId);
            return;
        }

        AnswerDto answerDto = getAnswerSql().findDtoById(answerId)
                .orElseThrow(() -> new NoSuchElementException("Could not find answer with id " + answerId));

        MatrixQuestionDto questionDto = (MatrixQuestionDto) new JdbiQuestionCached(getHandle())
                .findQuestionDtoById(answerDto.getQuestionId())
                .orElseThrow(() -> new NoSuchElementException("Could not find question id " + answerDto.getQuestionId()
                        + " for answer id " + answerId));

        if (questionDto.getSelectMode() == MatrixSelectMode.SINGLE && isSingleModeMatrixSelectionInvalid(selected)) {
            throw new OperationNotAllowedException("Single-select matrix question does not allow more than one selected options per row");
        }

        List<String> optionStableIds = selected.stream().map(SelectedMatrixCell::getOptionStableId).collect(Collectors.toList());
        List<String> rowStableIds = selected.stream().map(SelectedMatrixCell::getRowStableId).collect(Collectors.toList());
        List<String> groupStableIds = selected.stream().map(SelectedMatrixCell::getGroupStableId).collect(Collectors.toList());

        Map<String, MatrixOptionDto> selectedOptionMap = new HashMap<>();
        Map<String, MatrixRowDto> selectedRowMap = new HashMap<>();
        Map<String, MatrixGroupDto> selectedGroupMap = new HashMap<>();

        getJdbiMatrixOption()
                .findOptions(answerDto.getQuestionId(), optionStableIds, instanceGuid)
                .forEach(dto -> selectedOptionMap.put(dto.getStableId(), dto));

        getJdbiMatrixRow()
                .findRows(answerDto.getQuestionId(), rowStableIds, instanceGuid)
                .forEach(dto -> selectedRowMap.put(dto.getStableId(), dto));

        getJdbiMatrixGroup()
                .findGroupsByStableIds(answerDto.getQuestionId(), groupStableIds, instanceGuid)
                .forEach(dto -> selectedGroupMap.put(dto.getStableId(), dto));

        List<Long> optionIds = new ArrayList<>();
        List<Long> rowIds = new ArrayList<>();
        Map<String, Boolean> selectedCellsMap = new HashMap<>();

        for (SelectedMatrixCell cell : selected) {
            var groupSid = String.valueOf(cell.getGroupStableId());

            if (!selectedRowMap.containsKey(cell.getRowStableId())) {
                throw new NoSuchElementException("Could not find matrix row stable id " + cell.getRowStableId());
            }
            if (!selectedOptionMap.containsKey(cell.getOptionStableId())) {
                throw new NoSuchElementException("Could not find matrix option stable id " + cell.getOptionStableId());
            }
            if (cell.getGroupStableId() != null && !selectedGroupMap.containsKey(groupSid)) {
                throw new NoSuchElementException("Could not find matrix group stable id " + cell.getGroupStableId());
            }

            String address = groupSid + ":" + cell.getRowStableId();
            if (selectedCellsMap.containsKey(address)
                    && (selectedOptionMap.get(cell.getOptionStableId()).isExclusive() || selectedCellsMap.get(address))) {
                throw new OperationNotAllowedException("Only one selected option allowed when list contains an exclusive option");
            } else {
                selectedCellsMap.put(address, selectedOptionMap.get(cell.getOptionStableId()).isExclusive());
            }

            optionIds.add(selectedOptionMap.get(cell.getOptionStableId()).getId());
            rowIds.add(selectedRowMap.get(cell.getRowStableId()).getId());
        }

        long[] ids = getAnswerSql().bulkInsertMatrixSelected(answerId, optionIds, rowIds);
        if (ids.length != selected.size()) {
            throw new DaoException("Not all selected Matrix options were assigned to answer " + answerId);
        }
    }

    default void assignOptionsToAnswerId(long answerId, List<SelectedMatrixCell> selected, MatrixQuestionDef questionDef) {
        if (selected == null || selected.isEmpty()) {
            LOG.info("List of selected options is empty; no options will be assigned to answer id {}", answerId);
            return;
        }

        if (questionDef.getSelectMode() == MatrixSelectMode.SINGLE && isSingleModeMatrixSelectionInvalid(selected)) {
            throw new OperationNotAllowedException("Single-select matrix question does not allow more than one selected options per row");
        }

        Set<String> optionStableIds = selected.stream().map(SelectedMatrixCell::getOptionStableId).collect(toSet());
        Set<String> rowStableIds = selected.stream().map(SelectedMatrixCell::getRowStableId).collect(toSet());
        Set<String> groupStableIds = selected.stream().map(SelectedMatrixCell::getGroupStableId).collect(toSet());

        Map<String, MatrixOptionDef> selectedOptionMap = questionDef.getOptions().stream()
                        .filter(option -> optionStableIds.contains(option.getStableId()))
                        .collect(toMap(MatrixOptionDef::getStableId, def -> def));

        Map<String, MatrixRowDef> selectedRowMap = questionDef.getRows().stream()
                .filter(option -> rowStableIds.contains(option.getStableId()))
                .collect(toMap(MatrixRowDef::getStableId, def -> def));

        Map<String, MatrixGroupDef> selectedGroupMap = questionDef.getGroups().stream()
                .filter(group -> groupStableIds.contains(group.getStableId()))
                .collect(toMap(g -> String.valueOf(g.getStableId()), def -> def));

        List<Long> optionIds = new ArrayList<>();
        List<Long> rowIds = new ArrayList<>();
        Map<String, Boolean> selectedCellsMap = new HashMap<>();

        for (SelectedMatrixCell cell : selected) {
            var groupSid = String.valueOf(cell.getGroupStableId());

            if (!selectedRowMap.containsKey(cell.getRowStableId())) {
                throw new NoSuchElementException("Could not find matrix row stable id " + cell.getRowStableId());
            }
            if (!selectedOptionMap.containsKey(cell.getOptionStableId())) {
                throw new NoSuchElementException("Could not find matrix option stable id " + cell.getOptionStableId());
            }
            if (cell.getGroupStableId() != null && !selectedGroupMap.containsKey(groupSid)) {
                throw new NoSuchElementException("Could not find matrix group stable id " + cell.getGroupStableId());
            }

            String address = groupSid + ":" + cell.getRowStableId();
            if (selectedCellsMap.containsKey(address)
                    && (selectedOptionMap.get(cell.getOptionStableId()).isExclusive() || selectedCellsMap.get(address))) {
                throw new OperationNotAllowedException("Only one selected option allowed when list contains an exclusive option");
            } else {
                selectedCellsMap.put(address, selectedOptionMap.get(cell.getOptionStableId()).isExclusive());
            }

            optionIds.add(selectedOptionMap.get(cell.getOptionStableId()).getOptionId());
            rowIds.add(selectedRowMap.get(cell.getRowStableId()).getRowId());
        }

        long[] ids = getAnswerSql().bulkInsertMatrixSelected(answerId, optionIds, rowIds);
        if (ids.length != selected.size()) {
            throw new DaoException("Not all selected matrix options were assigned to answer " + answerId);
        }
    }

    private boolean isSingleModeMatrixSelectionInvalid(List<SelectedMatrixCell> selected) {
        Set<String> rows = new HashSet<>();
        for (SelectedMatrixCell cell : selected) {
            if (!rows.add(cell.getRowStableId())) {
                return true;
            }
        }
        return false;
    }
}
