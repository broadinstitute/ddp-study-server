package org.broadinstitute.dsm.model.elastic.filter.splitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.broadinstitute.dsm.model.elastic.filter.AndOrFilterSeparator;
import org.junit.Assert;
import org.junit.Test;

public class AndOrFilterSeparatorTest {

    @Test
    public void parseFiltersByLogicalOperators() {

        String filter = "AND m.medicalRecordId = '15' " +
                "OR m.medicalRecordSomething LIKE '55555' " +
                "OR m.medicalRecordSomethingg = '55552' " +
                "AND t.tissueRecord IS NOT NULL " +
                "AND m.dynamicFields.ragac = '55' " +
                "AND ( t.tissue = 'review' OR t.tissue = 'no' OR t.tissue = 'bla' ) " +
                "OR m.medicalRecordName = '213' " +
                "AND STR_TO_DATE(m.fax_sent,'%Y-%m-%d') = STR_TO_DATE('2021-12-17','%Y-%m-%d') " +
                "AND JSON_CONTAINS ( k.test_result , JSON_OBJECT ( 'result' , 'result' ) ) " +
                "OR m.mrNotes = 'MEDICAL_RECORD_NOTESS' " +
                "AND m.medicalMedical = 'something AND something' " +
                "AND ( oD.request = 'review' OR oD.request = 'no' OR oD.request = 'bla' ) " +
                "OR t.tissueRecord = '225' " +
                "OR STR_TO_DATE(m.fax_sent,'%Y-%m-%d') = STR_TO_DATE('2021-12-17','%Y-%m-%d') " +
                "AND JSON_EXTRACT ( m.additiona`l_values_json , '$.seeingIfBugExists' )";
        AndOrFilterSeparator andOrFilterSeparator = new AndOrFilterSeparator(filter);
        Map<String, List<String>> parsedFilters = andOrFilterSeparator.parseFiltersByLogicalOperators();
        for (Map.Entry<String, List<String>> eachFilter: parsedFilters.entrySet()) {
            if (eachFilter.getKey().equals("AND")) {
                Assert.assertArrayEquals(new ArrayList<>(List.of("m.medicalRecordId = '15'",
                                "t.tissueRecord IS NOT NULL" ,"m.dynamicFields.ragac = '55'",
                                        "( t.tissue = 'review' OR t.tissue = 'no' OR t.tissue = 'bla' )",
                                        "STR_TO_DATE(m.fax_sent,'%Y-%m-%d') = STR_TO_DATE('2021-12-17','%Y-%m-%d')",
                                        "JSON_CONTAINS ( k.test_result , JSON_OBJECT ( 'result' , 'result' ) )",
                                        "m.medicalMedical = 'something AND something'", "( oD.request = 'review' OR oD.request = 'no' OR " +
                                        "oD.request = 'bla' )",
                                "JSON_EXTRACT ( m.additiona`l_values_json , '$.seeingIfBugExists' )")).toArray(),
                        eachFilter.getValue().toArray());
            } else {
                Assert.assertArrayEquals(new ArrayList<>(List.of("m.medicalRecordSomething LIKE '55555'", "m.medicalRecordSomethingg = " +
                                "'55552'", "m.medicalRecordName = '213'", "m.mrNotes = 'MEDICAL_RECORD_NOTESS'", "t.tissueRecord = '225'"
                                , "STR_TO_DATE(m.fax_sent,'%Y-%m-%d') = STR_TO_DATE('2021-12-17','%Y-%m-%d')")).toArray(),
                        eachFilter.getValue().toArray());
            }
        }
    }

    @Test
    public void parseFiltersByLogicalOperatorsSingle() {
        String filter = "AND oD.datePx = '15' ";
        Map<String, List<String>> stringListMap = new AndOrFilterSeparator(filter).parseFiltersByLogicalOperators();
        Assert.assertEquals("oD.datePx = '15'", stringListMap.get("AND").get(0));
    }

    @Test
    public void parseFiltersByLogicalOperatorsSingle2() {
        String filter = " AND NOT m.mr_problem <=> 1 ";
        Map<String, List<String>> stringListMap = new AndOrFilterSeparator(filter).parseFiltersByLogicalOperators();
        Assert.assertEquals("NOT m.mr_problem <=> 1", stringListMap.get("AND").get(0));
    }

}











