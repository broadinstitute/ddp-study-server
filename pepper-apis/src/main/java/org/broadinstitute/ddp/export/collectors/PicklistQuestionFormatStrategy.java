package org.broadinstitute.ddp.export.collectors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.content.HtmlConverter;
import org.broadinstitute.ddp.elastic.MappingUtil;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistGroupDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.types.PicklistSelectMode;

/**
 * Rule:
 * - one column for selected options, additional column per option that allows details
 * - selected options is comma-separated in single column, sorted in option definition order
 * - no selected options will result in empty cell
 * - null or empty detail text results in empty cell
 */
public class PicklistQuestionFormatStrategy implements ResponseFormatStrategy<PicklistQuestionDef, PicklistAnswer> {

    @Override
    public Map<String, Object> mappings(PicklistQuestionDef definition) {
        Map<String, Object> props = new LinkedHashMap<>();
        if (definition.getSelectMode() == PicklistSelectMode.SINGLE) {
            props.put(definition.getStableId(), MappingUtil.newKeywordType());
        } else {
            props.put(definition.getStableId(), MappingUtil.newTextType());
        }

        for (PicklistOptionDef optionDef : definition.getAllPicklistOptions()) {
            if (optionDef.isDetailsAllowed()) {
                String key = detailHeader(definition.getStableId(), optionDef.getStableId());
                props.put(key, MappingUtil.newTextType());
            }
        }

        return props;
    }

    @Override
    public Map<String, Object> questionDef(PicklistQuestionDef definition) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("stableId", definition.getStableId());
        props.put("questionType", definition.getQuestionType().name());
        props.put("questionText", HtmlConverter.getPlainText(definition.getPromptTemplate().render("en")));
        props.put("selectMode", definition.getSelectMode());
        //add PL Groups
        List<Object> groups = new ArrayList<>();
        for (PicklistGroupDef group : definition.getGroups()) {
            Map<String, Object> groupDef = new HashMap<>();
            String groupStableId = group.getStableId();
            String groupTxt = HtmlConverter.getPlainText(group.getNameTemplate().render("en"));
            List<Object> options = new ArrayList<>();
            for (PicklistOptionDef optionDef : group.getOptions()) {
                Map<String, String> stableIdTxt = new HashMap<>();
                stableIdTxt.put("optionStableId", optionDef.getStableId());
                stableIdTxt.put("optionText", HtmlConverter.getPlainText(optionDef.getOptionLabelTemplate().render("en")));
                options.add(stableIdTxt);
            }
            groupDef.put("groupStableId", groupStableId);
            groupDef.put("groupText", groupTxt);
            groupDef.put("options", options);
            groups.add(groupDef);
        }
        props.put("groups", groups);

        List<Object> options = new ArrayList<>();
        for (PicklistOptionDef optionDef : definition.getPicklistOptions()) {
            Map<String, Object> stableIdTxt = new HashMap<>();
            stableIdTxt.put("optionStableId", optionDef.getStableId());
            stableIdTxt.put("optionText", HtmlConverter.getPlainText(optionDef.getOptionLabelTemplate().render("en")));

            //add nested options
            if (CollectionUtils.isNotEmpty(optionDef.getNestedOptions())) {
                if (optionDef.getNestedOptionsLabelTemplate() != null) {
                    stableIdTxt.put("nestedOptionsText", HtmlConverter.getPlainText(
                            optionDef.getNestedOptionsLabelTemplate().render("en")));
                }
                List<Object> nestedOptions = new ArrayList<>();
                for (PicklistOptionDef suboptionDef : optionDef.getNestedOptions()) {
                    Map<String, String> suboptStableIdTxt = new HashMap<>();
                    suboptStableIdTxt.put("optionStableId", suboptionDef.getStableId());
                    suboptStableIdTxt.put("optionText", HtmlConverter.getPlainText(suboptionDef.getOptionLabelTemplate().render("en")));
                    nestedOptions.add(suboptStableIdTxt);
                }
                stableIdTxt.put("nestedOptions", nestedOptions);
            }
            options.add(stableIdTxt);
        }
        props.put("options", options);
        return props;
    }

    @Override
    public List<String> headers(PicklistQuestionDef definition) {
        List<String> headers = new ArrayList<>();
        headers.add(definition.getStableId());

        for (PicklistOptionDef optionDef : definition.getAllPicklistOptions()) {
            if (optionDef.isDetailsAllowed()) {
                headers.add(detailHeader(definition.getStableId(), optionDef.getStableId()));
            }
        }

        return headers;
    }

    @Override
    public Map<String, String> collect(PicklistQuestionDef question, PicklistAnswer answer) {
        Map<String, String> record = new HashMap<>();

        Map<String, SelectedPicklistOption> mapping = new HashMap<>();
        for (SelectedPicklistOption selected : answer.getValue()) {
            mapping.put(selected.getStableId(), selected);
        }

        List<PicklistOptionDef> options = new ArrayList<>();
        List<String> selectedIds = new ArrayList<>();

        options.addAll(question.getAllPicklistOptions());

        for (PicklistOptionDef option : options) {
            String osid = option.getStableId();
            if (mapping.containsKey(osid)) {
                SelectedPicklistOption selected = mapping.get(osid);
                if (option.isDetailsAllowed() && StringUtils.isNotBlank(selected.getDetailText())) {
                    String key = detailHeader(question.getStableId(), osid);
                    record.put(key, selected.getDetailText());
                }
                selectedIds.add(osid);
            }
        }

        record.put(question.getStableId(), String.join(",", selectedIds));
        return record;
    }

    private String detailHeader(String questionStableId, String optionStableId) {
        return String.format("%s_%s_DETAILS", questionStableId, optionStableId);
    }
}
