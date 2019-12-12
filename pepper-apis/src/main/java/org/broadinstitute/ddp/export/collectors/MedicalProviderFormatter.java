package org.broadinstitute.ddp.export.collectors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.dto.MedicalProviderDto;
import org.broadinstitute.ddp.elastic.MappingUtil;
import org.broadinstitute.ddp.model.activity.definition.PhysicianInstitutionComponentDef;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;

/**
 * Rule:
 * - one column for entire list of medical providers
 * - each provider entry is pipe-separated
 * - each field of a provider is semicolon-separated
 * - only considers some fields of a provider and not other legacy fields
 * - missing values are skipped but delimiters are kept
 * - null or empty list results in empty cell
 */
public class MedicalProviderFormatter {

    public Map<String, Object> mappings(PhysicianInstitutionComponentDef definition) {
        Map<String, Object> props = new HashMap<>();
        props.put(definition.getInstitutionType().name(), MappingUtil.newTextType());
        return props;
    }

    public List<String> headers(PhysicianInstitutionComponentDef definition) {
        return Arrays.asList(definition.getInstitutionType().name());
    }

    //used for elastic search activity_definition index
    //stableIds, definitions really don't exist in db, added to cater dsm search.
    public Map<String, Object> definition(PhysicianInstitutionComponentDef def, String text) {
        String stableId = def.getInstitutionType().name();
        Map<String, Object> props = new HashMap<>();
        props.put("stableId", stableId);
        props.put("questionType", "COMPOSITE");
        props.put("questionText", text);
        props.put("allowMultiple", def.allowMultiple());

        String[] childQuestionsText;
        String[] instQuestionsText = {"Institution", "City", "State"};
        String[] physicianQuestionsText = {"Physician Name", "Institution", "City", "State"};
        if (def.getInstitutionType().name().equalsIgnoreCase("PHYSICIAN")) {
            childQuestionsText = physicianQuestionsText;
        } else {
            childQuestionsText = instQuestionsText;
        }

        List<Object> childQuestionDefs = new ArrayList<>();
        for (String childTxt : childQuestionsText) {
            Map<String, Object> childrenMap = new HashMap<>();
            childrenMap.put("stableId", "");
            childrenMap.put("questionType", "TEXT");
            childrenMap.put("questionText", childTxt);
            childQuestionDefs.add(childrenMap);
        }
        props.put("childQuestions", childQuestionDefs);
        return props;
    }

    public List<List<String>> collectAsAnswer(InstitutionType type, List<MedicalProviderDto> providers) {
        List<List<String>> answer = new ArrayList<>();

        for (MedicalProviderDto provider : providers) {
            List<String> values = new ArrayList<>();
            if (provider.getInstitutionType() != type || provider.isBlank()) {
                continue;
            }

            if (type == InstitutionType.PHYSICIAN) {
                values.add(StringUtils.defaultString(provider.getPhysicianName(), ""));
            }
            values.add(StringUtils.defaultString(provider.getInstitutionName(), ""));
            values.add(StringUtils.defaultString(provider.getCity(), ""));
            values.add(StringUtils.defaultString(provider.getState(), ""));

            answer.add(values);
        }

        return answer;
    }

    public Map<String, String> collect(InstitutionType type, List<MedicalProviderDto> providers) {
        Map<String, String> record = new HashMap<>();
        if (providers != null && !providers.isEmpty()) {
            List<String> entries = new ArrayList<>();

            for (MedicalProviderDto provider : providers) {
                List<String> values = new ArrayList<>();
                if (provider.getInstitutionType() != type || provider.isBlank()) {
                    continue;
                }

                if (type == InstitutionType.PHYSICIAN) {
                    values.add(StringUtils.defaultString(provider.getPhysicianName(), ""));
                }
                values.add(StringUtils.defaultString(provider.getInstitutionName(), ""));
                values.add(StringUtils.defaultString(provider.getCity(), ""));
                values.add(StringUtils.defaultString(provider.getState(), ""));

                entries.add(String.join(";", values));
            }

            if (!entries.isEmpty()) {
                record.put(type.name(), String.join("|", entries));
            }
        }
        return record;
    }
}
