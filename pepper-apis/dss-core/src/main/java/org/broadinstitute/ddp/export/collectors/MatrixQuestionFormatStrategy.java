package org.broadinstitute.ddp.export.collectors;

import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.elastic.MappingUtil;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixGroupDef;
import org.broadinstitute.ddp.model.activity.definition.question.MatrixRowDef;
import org.broadinstitute.ddp.model.activity.instance.answer.MatrixAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedMatrixCell;
import org.broadinstitute.ddp.model.activity.types.MatrixSelectMode;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * Rule:
 * - one column for selected options, additional column per option that allows details
 * - selected options is comma-separated in single column, sorted in option definition order
 * - no selected options will result in empty cell
 * - null or empty detail text results in empty cell
 */
public class MatrixQuestionFormatStrategy implements ResponseFormatStrategy<MatrixQuestionDef, MatrixAnswer> {

    @Override
    public Map<String, Object> mappings(MatrixQuestionDef definition) {
        Map<String, Object> props = new LinkedHashMap<>();
        if (definition.getSelectMode() == MatrixSelectMode.SINGLE) {
            props.put(definition.getStableId(), MappingUtil.newKeywordType());
        } else {
            props.put(definition.getStableId(), MappingUtil.newTextType());
        }

        return props;
    }

    @Override
    public Map<String, Object> questionDef(MatrixQuestionDef definition) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("stableId", definition.getStableId());
        props.put("questionType", definition.getQuestionType().name());
        props.put("questionText", HtmlConverter.getPlainText(definition.getPromptTemplate().renderWithDefaultValues("en")));
        props.put("selectMode", definition.getSelectMode());

        //add PL Groups
        List<Object> groups = new ArrayList<>();
        for (MatrixGroupDef group : definition.getGroups()) {
            Map<String, Object> groupDef = new HashMap<>();
            String groupStableId = group.getStableId();
            if (group.getNameTemplate() != null) {
                groupDef.put("groupText", HtmlConverter.getPlainText(group.getNameTemplate().renderWithDefaultValues("en")));
            }

            groupDef.put("groupStableId", groupStableId);

            groups.add(groupDef);
        }
        props.put("groups", groups);

        List<Object> options = new ArrayList<>();
        for (MatrixOptionDef optionDef : definition.getOptions()) {
            Map<String, Object> stableIdTxt = new HashMap<>();
            stableIdTxt.put("optionStableId", optionDef.getStableId());
            stableIdTxt.put("optionText", HtmlConverter.getPlainText(optionDef.getOptionLabelTemplate().renderWithDefaultValues("en")));

            options.add(stableIdTxt);
        }
        props.put("options", options);

        List<Object> rows = new ArrayList<>();
        for (MatrixRowDef rowDef : definition.getRows()) {
            Map<String, Object> stableIdTxt = new HashMap<>();
            stableIdTxt.put("rowStableId", rowDef.getStableId());
            stableIdTxt.put("rowText", HtmlConverter.getPlainText(rowDef.getRowLabelTemplate().renderWithDefaultValues("en")));

            rows.add(stableIdTxt);
        }
        props.put("rows", rows);

        return props;
    }

    @Override
    public List<String> headers(MatrixQuestionDef definition) {
        List<String> headers = new ArrayList<>();
        headers.add(definition.getStableId());
        return headers;
    }

    @Override
    public Map<String, String> collect(MatrixQuestionDef question, MatrixAnswer answer) {
        Map<String, String> record = new HashMap<>();
        List<String> selectedIds = new ArrayList<>();

        for (SelectedMatrixCell cell : answer.getValue()) {
            selectedIds.add(cell.getRowStableId() + ":" + cell.getOptionStableId());
        }

        record.put(question.getStableId(), String.join(",", selectedIds));
        return record;
    }
}
