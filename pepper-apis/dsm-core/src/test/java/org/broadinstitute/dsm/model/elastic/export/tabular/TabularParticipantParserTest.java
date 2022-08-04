package org.broadinstitute.dsm.model.elastic.export.tabular;

import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.ParticipantColumn;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TabularParticipantParserTest {
    private static final Filter DDP_FILTER = buildFilter("ddp", "data", null, "TEXT", "DDP");
    private static final Filter HRUID_FILTER = buildFilter("hruid", "data", "profile", "TEXT", "Short ID");
    private static final Filter FIRST_NAME_FILTER= buildFilter("firstName", "data", "profile", "TEXT", "First Name");
    private static final Filter INCONTINENCE_FILTER = buildFilter("INCONTINENCE",
            "MEDICAL_HISTORY", "questionsAnswers", "OPTIONS", "describe incontinence");
    private static final Filter TELANGIECTASIA_FILTER = buildFilter("TELANGIECTASIA",
            "MEDICAL_HISTORY", "questionsAnswers", "OPTIONS", "Telangiectasia: choose all that apply");
    private static final Filter MEDICATION_CATEGORY_FILTER = buildFilter("MEDICATION_CATEGORY",
            "MEDICAL_HISTORY", "questionsAnswers", "COMPOSITE", "MEDICATION_CATEGORY");

    @Test
    public void testBasicConfigGeneration() throws IOException {
        Assert.assertNotEquals(2, 1);

        TabularParticipantParser parser = new TabularParticipantParser(Arrays.asList(DDP_FILTER, HRUID_FILTER, FIRST_NAME_FILTER,
                MEDICATION_CATEGORY_FILTER, INCONTINENCE_FILTER, TELANGIECTASIA_FILTER), null,
                true, true, ATCP_ACTIVITY_DEFS);

        List<ModuleExportConfig> moduleConfigs = parser.generateExportConfigs();
        // should be sorted into two modules -- data and profile
        Assert.assertEquals("should be data, profile, and medical history modules", 3, moduleConfigs.size());
        Assert.assertEquals("profile module should have two associated questions", 2, moduleConfigs.get(1).getQuestions().size());
        Assert.assertEquals("the medical history module should have 3 associated questions", 3, moduleConfigs.get(2).getQuestions().size());
        List<String> historyQuestionIds = moduleConfigs.get(2).getQuestions().stream().map(config -> config.getColumn().getName())
                .collect(Collectors.toList());
        Assert.assertEquals("questions should be in order of appearance in activity def.",
                Arrays.asList("INCONTINENCE", "TELANGIECTASIA", "MEDICATION_CATEGORY"), historyQuestionIds);
    }

    private static Filter buildFilter(String colName, String tableAlias, String objectName, String type, String display) {
        ParticipantColumn column = new ParticipantColumn(colName, tableAlias);
        column.setObject(objectName);
        column.setDisplay(display);
        Filter filter = new Filter(false, false, false, false, type, null, null, null, null, column);
        return filter;
    }



    private static final Map<String, Object> ATCP_MEDICAL_HISTORY_DEF = Map.of(
            "activityCode", "MEDICAL_HISTORY",
            "questions", Arrays.asList(
                    new HashMap(Map.of(
                            "stableId", "INCONTINENCE",
                            "selectMode", "SINGLE",
                            "questionType", "PICKLIST",
                            "questionText", "Please describe $ddp.participantFirstName()'s incontinence",
                            "options", Arrays.asList(
                                    Map.of("optionStableId", "INCONTINENCE_OCCASIONAL",
                                    "optionText", "Occasional (up to two times per week)"),
                                    Map.of("optionStableId", "FREQUENT",
                                            "optionText", "Frequent (more than two times per week)")
                            )

                    )),
                    new HashMap(Map.of(
                            "stableId", "TELANGIECTASIA",
                            "selectMode", "MULTIPLE",
                            "questionType", "PICKLIST",
                            "questionText", "Telangiectasia (choose all that apply)",
                            "options", Arrays.asList(
                                    Map.of("optionStableId", "TELANGIECTASIA_EYES",
                                            "optionText", "eye"),
                                    Map.of("optionStableId", "TELANGIECTASIA_SKIN",
                                            "optionText", "skin")
                                    )

                    )),
                    new HashMap(Map.of(
                            "stableId", "MEDICATION_CATEGORY",
                            "questionType", "COMPOSITE",
                            "questionText", "",
                            "allowMultiple", true,
                            "childQuestions", Arrays.asList(
                                    Map.of("stableId", "MEDICATION_NAME",
                                            "questionType", "TEXT",
                                            "questionText", ""),
                                    Map.of("stableId", "BEGAN_TAKING_AT_AGE",
                                            "questionType", "TEXT",
                                            "questionText", "")
                            )

                    ))
            )
    );

    private static final Map<String, Map<String, Object>> ATCP_ACTIVITY_DEFS = Map.of(
            "MEDICAL_HISTORY_V1", ATCP_MEDICAL_HISTORY_DEF
    );


}

