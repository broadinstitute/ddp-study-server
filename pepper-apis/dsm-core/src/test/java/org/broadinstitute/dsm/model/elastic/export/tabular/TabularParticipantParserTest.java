package org.broadinstitute.dsm.model.elastic.export.tabular;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.ParticipantColumn;
import org.junit.Test;

public class TabularParticipantParserTest {
    private static final Filter DDP_FILTER = buildFilter("ddp", "data", null, "TEXT", "DDP");
    private static final Filter HRUID_FILTER = buildFilter("hruid", "data", "profile", "TEXT", "Short ID");
    private static final Filter FIRST_NAME_FILTER = buildFilter("firstName", "data", "profile", "TEXT", "First Name");
    private static final Filter EMAIL_FILTER = buildFilter("email", "data", "profile", "TEXT", "Email");
    private static final Filter INCONTINENCE_FILTER =
            buildFilter("INCONTINENCE", "MEDICAL_HISTORY", "questionsAnswers", "OPTIONS", "describe incontinence");
    private static final Filter TELANGIECTASIA_FILTER =
            buildFilter("TELANGIECTASIA", "MEDICAL_HISTORY", "questionsAnswers", "OPTIONS", "Telangiectasia: choose all that apply");
    private static final Filter MEDICATION_CATEGORY_FILTER =
            buildFilter("MEDICATION_CATEGORY", "MEDICAL_HISTORY", "questionsAnswers", "COMPOSITE", "MEDICATION_CATEGORY");

    private static final Filter SINGULAR_NOTES_FILTER = buildFilter("singularNotes", "r", null, "ADDITIONALVALUE", "Patient notes");
    private static final Filter PROXY_EMAIL_FILTER = buildFilter("email", "proxy", null, "TEXT", "Email");

    private static final Filter ALLERGIES_FILTER =
            buildFilter("ALLERGY_DESCRIPTION", "MEDICAL_HISTORY", "questionsAnswers", "TEXT", "describe any allergies");

    @Test
    public void testBasicConfigGeneration() {
        TabularParticipantParser parser = new TabularParticipantParser(
                Arrays.asList(DDP_FILTER, HRUID_FILTER, FIRST_NAME_FILTER, MEDICATION_CATEGORY_FILTER, INCONTINENCE_FILTER,
                        TELANGIECTASIA_FILTER), null, true, true, ATCP_ACTIVITY_DEFS);

        List<ModuleExportConfig> moduleConfigs = parser.generateExportConfigs();
        // should be sorted into two modules -- data and profile
        assertEquals("should be data, profile, and medical history modules", 3, moduleConfigs.size());
        assertEquals("profile module should have two associated questions", 2, moduleConfigs.get(1).getQuestions().size());
        assertEquals("the medical history module should have 3 associated questions", 3, moduleConfigs.get(2).getQuestions().size());
        List<String> historyQuestionIds =
                moduleConfigs.get(2).getQuestions().stream().map(config -> config.getColumn().getName()).collect(Collectors.toList());
        assertEquals("questions should be in order of appearance in activity def.",
                Arrays.asList("INCONTINENCE", "TELANGIECTASIA", "MEDICATION_CATEGORY"), historyQuestionIds);
    }

    @Test
    public void testDdpParsing() {
        TabularParticipantParser parser = new TabularParticipantParser(Arrays.asList(DDP_FILTER), null, true, true, ATCP_ACTIVITY_DEFS);
        List<ModuleExportConfig> moduleConfigs = parser.generateExportConfigs();
        List<Map<String, String>> participantValueMaps = parser.parse(moduleConfigs, Collections.singletonList(TEST_ATCP_PARTICIPANT));
        // should be sorted into two modules -- data and profile
        assertEquals("correct number of participants not extracted", 1, participantValueMaps.size());
        assertEquals("DDP instance name not parsed", "atcp", participantValueMaps.get(0).get("DATA.DDP"));
    }

    @Test
    public void testTextQuestionParsing() throws IOException {
        TabularParticipantParser parser =
                new TabularParticipantParser(Arrays.asList(ALLERGIES_FILTER), null, true, true, SIMPLE_HISTORY_DEF);
        List<ModuleExportConfig> moduleConfigs = parser.generateExportConfigs();
        List<Map<String, String>> participantValueMaps = parser.parse(moduleConfigs, Collections.singletonList(SIMPLE_PARTICIPANT));
        assertEquals("text values not rendered correctly", "minor pollen allergy",
                participantValueMaps.get(0).get("MEDICAL_HISTORY.ALLERGY_DESCRIPTION"));
    }

    @Test
    public void testSingleSelectParsing() throws IOException {
        TabularParticipantParser parser =
                new TabularParticipantParser(Arrays.asList(INCONTINENCE_FILTER), null, true, true, ATCP_ACTIVITY_DEFS);
        List<ModuleExportConfig> moduleConfigs = parser.generateExportConfigs();
        List<Map<String, String>> participantValueMaps = parser.parse(moduleConfigs, Collections.singletonList(TEST_ATCP_PARTICIPANT));
        assertEquals("single select value not correct", "Occasional (up to two times per week)",
                participantValueMaps.get(0).get("MEDICAL_HISTORY.INCONTINENCE"));
    }

    @Test
    public void testSingleSelectAnalysisParsing() throws IOException {
        TabularParticipantParser parser =
                new TabularParticipantParser(Arrays.asList(INCONTINENCE_FILTER), null, false, true, ATCP_ACTIVITY_DEFS);
        List<ModuleExportConfig> moduleConfigs = parser.generateExportConfigs();
        List<Map<String, String>> participantValueMaps = parser.parse(moduleConfigs, Collections.singletonList(TEST_ATCP_PARTICIPANT));
        assertEquals("single select value not correct", "INCONTINENCE_OCCASIONAL",
                participantValueMaps.get(0).get("MEDICAL_HISTORY.INCONTINENCE"));
    }


    @Test
    public void testMultiselectAnalysisParsing() {
        TabularParticipantParser parser =
                new TabularParticipantParser(Arrays.asList(TELANGIECTASIA_FILTER), null, false, true, ATCP_ACTIVITY_DEFS);
        List<ModuleExportConfig> moduleConfigs = parser.generateExportConfigs();
        List<Map<String, String>> participantValueMaps = parser.parse(moduleConfigs, Collections.singletonList(TEST_ATCP_PARTICIPANT));
        Map<String, String> participantMap = participantValueMaps.get(0);
        assertEquals("Mutliselect value not rendered", "1", participantMap.get("MEDICAL_HISTORY.TELANGIECTASIA.TELANGIECTASIA_EYES"));
        assertEquals("Mutliselect value not rendered", "0", participantMap.get("MEDICAL_HISTORY.TELANGIECTASIA.TELANGIECTASIA_SKIN"));

        assertEquals("option details not rendered", "71", participantMap.get("MEDICAL_HISTORY.TELANGIECTASIA.TELANGIECTASIA_EYES_DETAIL"));
        assertEquals("option details not rendered", "", participantMap.get("MEDICAL_HISTORY.TELANGIECTASIA.TELANGIECTASIA_SKIN_DETAIL"));
    }

    @Test
    public void testMultiselectParsing() {
        TabularParticipantParser parser =
                new TabularParticipantParser(Arrays.asList(TELANGIECTASIA_FILTER), null, true, true, ATCP_ACTIVITY_DEFS);
        List<ModuleExportConfig> moduleConfigs = parser.generateExportConfigs();
        List<Map<String, String>> participantValueMaps = parser.parse(moduleConfigs, Collections.singletonList(TEST_ATCP_PARTICIPANT));
        Map<String, String> participantMap = participantValueMaps.get(0);
        assertEquals("Mutliselect value not rendered", "eye", participantMap.get("MEDICAL_HISTORY.TELANGIECTASIA"));
        assertEquals("option details not rendered", "71", participantMap.get("MEDICAL_HISTORY.TELANGIECTASIA_DETAIL"));
    }

    @Test
    public void testMultiselectMultiAnswerParsing() {
        TabularParticipantParser parser =
                new TabularParticipantParser(Arrays.asList(TELANGIECTASIA_FILTER), null, true, true, ATCP_ACTIVITY_DEFS);
        List<ModuleExportConfig> moduleConfigs = parser.generateExportConfigs();
        List<Map<String, String>> participantValueMaps = parser.parse(moduleConfigs, Collections.singletonList(TEST_ATCP_PARTICIPANT_2));
        Map<String, String> participantMap = participantValueMaps.get(0);
        assertEquals("Mutliselect value not rendered", "eye, skin", participantMap.get("MEDICAL_HISTORY.TELANGIECTASIA"));
        assertEquals("option details not rendered", "71; 47", participantMap.get("MEDICAL_HISTORY.TELANGIECTASIA_DETAIL"));
    }


    @Test
    public void testCompositeParsing() {
        TabularParticipantParser parser =
                new TabularParticipantParser(Arrays.asList(MEDICATION_CATEGORY_FILTER), null, true, true, ATCP_ACTIVITY_DEFS);
        List<ModuleExportConfig> moduleConfigs = parser.generateExportConfigs();
        List<Map<String, String>> participantValueMaps = parser.parse(moduleConfigs, Collections.singletonList(TEST_ATCP_PARTICIPANT));
        Map<String, String> participantMap = participantValueMaps.get(0);
        assertEquals("Composite value not rendered", "med1", participantMap.get("MEDICAL_HISTORY.MEDICATION_CATEGORY.MEDICATION_NAME"));
        assertEquals("Composite value not rendered", "39", participantMap.get("MEDICAL_HISTORY.MEDICATION_CATEGORY.BEGAN_TAKING_AT_AGE"));

        assertEquals("Composite value not rendered", "med2", participantMap.get("MEDICAL_HISTORY.MEDICATION_CATEGORY.MEDICATION_NAME_2"));
        assertEquals("Composite value not rendered", "18", participantMap.get("MEDICAL_HISTORY.MEDICATION_CATEGORY.BEGAN_TAKING_AT_AGE_2"));
    }

    @Test
    public void testProxyParsing() {
        TabularParticipantParser parser =
                new TabularParticipantParser(Arrays.asList(PROXY_EMAIL_FILTER, EMAIL_FILTER), null, true, true, ATCP_ACTIVITY_DEFS);
        List<ModuleExportConfig> moduleConfigs = parser.generateExportConfigs();
        List<Map<String, String>> participantValueMaps = parser.parse(moduleConfigs, Collections.singletonList(TEST_SINGULAR_PARTICIPANT));
        Map<String, String> participantMap = participantValueMaps.get(0);
        assertEquals("proxy email not rendered", "iamaproxy@gmail.com", participantMap.get("PROFILE.PROXY.EMAIL"));
        assertEquals("email not rendered", "participantEmail@gmail.com", participantMap.get("PROFILE.EMAIL"));
    }

    @Test
    public void testDsmDynamicValuesParsing() {
        TabularParticipantParser parser =
                new TabularParticipantParser(Arrays.asList(SINGULAR_NOTES_FILTER), null, true, true, ATCP_ACTIVITY_DEFS);
        List<ModuleExportConfig> moduleConfigs = parser.generateExportConfigs();
        List<Map<String, String>> participantValueMaps = parser.parse(moduleConfigs, Collections.singletonList(TEST_SINGULAR_PARTICIPANT));
        Map<String, String> participantMap = participantValueMaps.get(0);
        assertEquals("dynamic field not rendered", "admin entered notes", participantMap.get("DSM.PARTICIPANT.RECORD.SINGULARNOTES"));
    }

    @Test
    public void testExport() throws IOException {
        TabularParticipantParser parser = new TabularParticipantParser(
                Arrays.asList(DDP_FILTER, HRUID_FILTER, FIRST_NAME_FILTER, MEDICATION_CATEGORY_FILTER, INCONTINENCE_FILTER,
                        TELANGIECTASIA_FILTER), null, false, true, ATCP_ACTIVITY_DEFS);

        List<ModuleExportConfig> exportConfigs = parser.generateExportConfigs();
        List<Map<String, String>> participantValueMaps = parser.parse(exportConfigs, Collections.singletonList(TEST_ATCP_PARTICIPANT));

        TabularParticipantExporter participantExporter =
                TabularParticipantExporter.getExporter(exportConfigs, participantValueMaps, ".tsv");
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        participantExporter.export(os);
        String exportText = os.toString("UTF-8");

        String[] rows = exportText.split("\n");
        assertEquals(3, rows.length);
        String[] firstRowVals = rows[2].split("\t");
        List<String> expectedHeaders = Arrays.asList("DATA.DDP", "PROFILE.HRUID", "PROFILE.FIRSTNAME", "MEDICAL_HISTORY.INCONTINENCE",
                "MEDICAL_HISTORY.TELANGIECTASIA.TELANGIECTASIA_EYES", "MEDICAL_HISTORY.TELANGIECTASIA.TELANGIECTASIA_EYES_DETAIL",
                "MEDICAL_HISTORY.TELANGIECTASIA.TELANGIECTASIA_SKIN", "MEDICAL_HISTORY.TELANGIECTASIA.TELANGIECTASIA_SKIN_DETAIL",
                "MEDICAL_HISTORY.MEDICATION_CATEGORY.MEDICATION_NAME", "MEDICAL_HISTORY.MEDICATION_CATEGORY.BEGAN_TAKING_AT_AGE",
                "MEDICAL_HISTORY.MEDICATION_CATEGORY.MEDICATION_NAME_2", "MEDICAL_HISTORY.MEDICATION_CATEGORY.BEGAN_TAKING_AT_AGE_2");
        assertEquals(expectedHeaders, Arrays.asList(rows[0].split(TsvParticipantExporter.DELIMITER)));

        List<String> expectedSubeaders =
                Arrays.asList("DDP", "Short ID", "First Name", "describe incontinence", "eye", "age of onset", "skin", "age of onset",
                        "MEDICATION_NAME", "BEGAN_TAKING_AT_AGE", "MEDICATION_NAME", "BEGAN_TAKING_AT_AGE");
        assertEquals(expectedSubeaders, Arrays.asList(rows[1].split(TsvParticipantExporter.DELIMITER)));

        List<String> expectedValues =
                Arrays.asList("atcp", "PKG8PA", "Tester", "INCONTINENCE_OCCASIONAL", "1", "71", "0", "", "med1", "39", "med2", "18");
        assertEquals(expectedValues, Arrays.asList(rows[2].split(TsvParticipantExporter.DELIMITER)));
    }

    private static Filter buildFilter(String colName, String tableAlias, String objectName, String type, String display) {
        ParticipantColumn column = new ParticipantColumn(colName, tableAlias);
        column.setObject(objectName);
        column.setDisplay(display);
        Filter filter = new Filter(false, false, false, false, type, null, null, null, null, column);
        return filter;
    }

    private static final Map<String, Map<String, Object>> SIMPLE_HISTORY_DEF = Map.of("MEDICAL_HISTORY_V1",
            Map.of("activityCode", "MEDICAL_HISTORY", "questions", Arrays.asList(new HashMap(
                    Map.of("stableId", "ALLERGY_DESCRIPTION", "questionType", "TEXT", "questionText", "Describe any allergies")))));

    private static final Map<String, Object> SIMPLE_PARTICIPANT = new HashMap(
            Map.of("ddp", "basic", "profile", Map.of("firstName", "Simple", "lastName", "Person", "hruid", "PKSSSS"), "activities",
                    Arrays.asList(Map.of("activityCode", "MEDICAL_HISTORY", "questionsAnswers",
                            Arrays.asList(Map.of("stableId", "ALLERGY_DESCRIPTION", "answer", "minor pollen allergy"))))));

    private static final Map<String, Object> ATCP_MEDICAL_HISTORY_DEF = Map.of("activityCode", "MEDICAL_HISTORY", "questions",
            Arrays.asList(new HashMap(Map.of("stableId", "INCONTINENCE", "selectMode", "SINGLE", "questionType", "PICKLIST", "questionText",
                    "Please describe $ddp.participantFirstName()'s incontinence", "options", Arrays.asList(
                            Map.of("optionStableId", "INCONTINENCE_OCCASIONAL", "optionText", "Occasional (up to two times per week)"),
                            Map.of("optionStableId", "FREQUENT", "optionText", "Frequent (more than two times per week)"))

            )), new HashMap(Map.of("stableId", "TELANGIECTASIA", "selectMode", "MULTIPLE", "questionType", "PICKLIST", "questionText",
                    "Telangiectasia (choose all that apply)", "options", Arrays.asList(
                            Map.of("optionStableId", "TELANGIECTASIA_EYES", "optionText", "eye", "isDetailsAllowed", true, "detailsText",
                                    "age of onset"),
                            Map.of("optionStableId", "TELANGIECTASIA_SKIN", "optionText", "skin", "isDetailsAllowed", true, "detailsText",
                                    "age of onset"))

            )), new HashMap(
                    Map.of("stableId", "MEDICATION_CATEGORY", "questionType", "COMPOSITE", "questionText", "", "allowMultiple", true,
                            "childQuestions",
                            Arrays.asList(Map.of("stableId", "MEDICATION_NAME", "questionType", "TEXT", "questionText", ""),
                                    Map.of("stableId", "BEGAN_TAKING_AT_AGE", "questionType", "TEXT", "questionText", ""))

                    ))));

    private static final Map<String, Map<String, Object>> ATCP_ACTIVITY_DEFS = Map.of("MEDICAL_HISTORY_V1", ATCP_MEDICAL_HISTORY_DEF);

    private static final Map<String, Object> TEST_ATCP_PARTICIPANT = new HashMap(
            Map.of("ddp", "atcp", "profile", Map.of("firstName", "Tester", "lastName", "atStudy", "hruid", "PKG8PA"), "activities",
                    Arrays.asList(Map.of("activityCode", "MEDICAL_HISTORY", "questionsAnswers",
                            Arrays.asList(Map.of("stableId", "INCONTINENCE", "answer", Arrays.asList("INCONTINENCE_OCCASIONAL")),
                                    Map.of("stableId", "TELANGIECTASIA", "answer", Arrays.asList("TELANGIECTASIA_EYES"), "optionDetails",
                                            Arrays.asList(Map.of("details", "71", "option", "TELANGIECTASIA_EYES"))),
                                    Map.of("stableId", "MEDICATION_CATEGORY", "answer",
                                            Arrays.asList(Arrays.asList("med1", "39"), Arrays.asList("med2", "18"))),
                                    Map.of("stableId", "ALLERGY_DESCRIPTION", "answer", "minor pollen allergy"))))));

    private static final Map<String, Object> TEST_ATCP_PARTICIPANT_2 = new HashMap(
            Map.of("ddp", "atcp", "profile", Map.of("firstName", "Tester", "lastName", "atStudy", "hruid", "PKG8PA"), "activities",
                    Arrays.asList(Map.of("activityCode", "MEDICAL_HISTORY", "questionsAnswers", Arrays.asList(
                            Map.of("stableId", "TELANGIECTASIA", "answer", Arrays.asList("TELANGIECTASIA_EYES", "TELANGIECTASIA_SKIN"),
                                    "optionDetails", Arrays.asList(Map.of("details", "71", "option", "TELANGIECTASIA_EYES"),
                                            Map.of("details", "47", "option", "TELANGIECTASIA_SKIN"))))))));


    private static final Map<String, Object> TEST_SINGULAR_PARTICIPANT = new HashMap(Map.of("ddp", "atcp", "profile",
            Map.of("firstName", "Tester", "lastName", "atStudy", "hruid", "PKG8PS", "email", "participantEmail@gmail.com"), "dsm",
            Map.of("participant",
                    Map.of("dynamicFields", Map.of("singularNotes", "admin entered notes", "singularEnrollmentStatus", "NOT_ENROLLED"))),
            "proxyData",
            Arrays.asList(Map.of("ddp", "singular", "profile", Map.of("firstName", "Proxier", "email", "iamaproxy@gmail.com")))));
}
