package org.broadinstitute.ddp.export.collectors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.elastic.MappingUtil;
import org.broadinstitute.ddp.model.address.MailAddress;

/**
 * Rule:
 * - one column per address field
 * - null values result in empty cell
 */
public class MailingAddressFormatter {

    public Map<String, Object> mappings() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put(fieldHeader("FULLNAME"), MappingUtil.newTextType());
        props.put(fieldHeader("STREET1"), MappingUtil.newTextType());
        props.put(fieldHeader("STREET2"), MappingUtil.newTextType());
        props.put(fieldHeader("CITY"), MappingUtil.newTextType());
        props.put(fieldHeader("STATE"), MappingUtil.newTextType());
        props.put(fieldHeader("ZIP"), MappingUtil.newTextType());
        props.put(fieldHeader("COUNTRY"), MappingUtil.newKeywordType());
        props.put(fieldHeader("PHONE"), MappingUtil.newTextType());
        props.put(fieldHeader("PLUSCODE"), MappingUtil.newKeywordType());
        props.put(fieldHeader("STATUS"), MappingUtil.newKeywordType());
        return props;
    }

    //used for elastic search activity_definition index
    //stableIds, definitions really don't exist in db, added to cater dsm search.
    public Map<String, Object> definition(String text) {
        String stableId = "MAILING_ADDRESS";
        Map<String, Object> props = new HashMap<>();
        props.put("stableId", stableId);
        props.put("questionType", "COMPOSITE");
        props.put("questionText", text);
        props.put("allowMultiple", false);

        String[] childQuestionsText = {"Full Name", "Street Address", "Apt/Floor#", "City", "State",
                "Zip Code", "Country/Territory", "Phone Number"};
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

    public List<String> headers() {
        return Arrays.asList(
                fieldHeader("FULLNAME"),
                fieldHeader("STREET1"),
                fieldHeader("STREET2"),
                fieldHeader("CITY"),
                fieldHeader("STATE"),
                fieldHeader("ZIP"),
                fieldHeader("COUNTRY"),
                fieldHeader("PHONE"),
                fieldHeader("PLUSCODE"),
                fieldHeader("STATUS"));
    }

    public Map<String, String> collect(MailAddress address) {
        Map<String, String> record = new HashMap<>();
        if (address != null) {
            String status = address.getStatusType() == null ? "" : address.getStatusType().getShortName();
            record.put(fieldHeader("FULLNAME"), StringUtils.defaultString(address.getName(), ""));
            record.put(fieldHeader("STREET1"), StringUtils.defaultString(address.getStreet1(), ""));
            record.put(fieldHeader("STREET2"), StringUtils.defaultString(address.getStreet2(), ""));
            record.put(fieldHeader("CITY"), StringUtils.defaultString(address.getCity(), ""));
            record.put(fieldHeader("STATE"), StringUtils.defaultString(address.getState(), ""));
            record.put(fieldHeader("ZIP"), StringUtils.defaultString(address.getZip(), ""));
            record.put(fieldHeader("COUNTRY"), StringUtils.defaultString(address.getCountry(), ""));
            record.put(fieldHeader("PHONE"), StringUtils.defaultString(address.getPhone(), ""));
            record.put(fieldHeader("PLUSCODE"), StringUtils.defaultString(address.getPlusCode(), ""));
            record.put(fieldHeader("STATUS"), status);
        }
        return record;
    }

    public List<List<String>> collectAsAnswer(MailAddress address) {
        List<List<String>> values = new ArrayList<>();
        if (address != null) {
            List<String> childAnswer = new ArrayList<>();
            childAnswer.add(StringUtils.defaultString(address.getName(), ""));
            childAnswer.add(StringUtils.defaultString(address.getStreet1(), ""));
            childAnswer.add(StringUtils.defaultString(address.getStreet2(), ""));
            childAnswer.add(StringUtils.defaultString(address.getCity(), ""));
            childAnswer.add(StringUtils.defaultString(address.getState(), ""));
            childAnswer.add(StringUtils.defaultString(address.getZip(), ""));
            childAnswer.add(StringUtils.defaultString(address.getCountry(), ""));
            childAnswer.add(StringUtils.defaultString(address.getPhone(), ""));
            values.add(childAnswer);
        }
        return values;
    }

    private String fieldHeader(String fieldName) {
        return "ADDRESS" + "_" + fieldName;
    }
}
