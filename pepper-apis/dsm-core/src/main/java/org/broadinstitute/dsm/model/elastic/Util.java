package org.broadinstitute.dsm.model.elastic;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.OncHistory;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.Participant;
import org.broadinstitute.dsm.db.ParticipantData;
import org.broadinstitute.dsm.db.SmId;
import org.broadinstitute.dsm.db.Tissue;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.broadinstitute.dsm.db.structure.DBElement;
import org.broadinstitute.dsm.db.structure.TableName;
import org.broadinstitute.dsm.model.elastic.export.generate.BaseGenerator;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.ParticipantUtil;
import org.broadinstitute.dsm.util.PatchUtil;

public class Util {

    public static final Map<String, BaseGenerator.PropertyInfo> TABLE_ALIAS_MAPPINGS = new HashMap<>(
            Map.of(DBConstants.DDP_MEDICAL_RECORD_ALIAS, new BaseGenerator.PropertyInfo(MedicalRecord.class, true),
                    DBConstants.DDP_TISSUE_ALIAS, new BaseGenerator.PropertyInfo(Tissue.class, true),
                    DBConstants.DDP_ONC_HISTORY_DETAIL_ALIAS, new BaseGenerator.PropertyInfo(OncHistoryDetail.class, true),
                    DBConstants.DDP_PARTICIPANT_DATA_ALIAS, new BaseGenerator.PropertyInfo(ParticipantData.class, true),
                    DBConstants.DDP_PARTICIPANT_RECORD_ALIAS, new BaseGenerator.PropertyInfo(Participant.class, false),
                    DBConstants.DDP_PARTICIPANT_ALIAS, new BaseGenerator.PropertyInfo(Participant.class, false),
                    DBConstants.DDP_ONC_HISTORY_ALIAS, new BaseGenerator.PropertyInfo(OncHistory.class, false),
                    DBConstants.SM_ID_TABLE_ALIAS, new BaseGenerator.PropertyInfo(SmId.class, true), DBConstants.COHORT_ALIAS,
                    new BaseGenerator.PropertyInfo(CohortTag.class, true), DBConstants.DDP_KIT_REQUEST_ALIAS,
                    new BaseGenerator.PropertyInfo(KitRequestShipping.class, true)));
    public static final int FIRST_ELEMENT_INDEX = 0;
    public static final String UNDERSCORE_SEPARATOR = "_";
    public static final String COMMA_SEPARATOR = ",";
    public static final String DOC = "_doc";
    public static final String ESCAPE_CHARACTER = "\\";
    public static final String FORWARD_SLASH_SEPARATOR = "/";
    private static final Pattern CAMEL_CASE_REGEX = Pattern.compile("(([a-z])+([A-z])+(\\.)*)*");
    private static final Pattern UPPER_CASE_REGEX = Pattern.compile("(?=\\p{Upper})");

    public static String getQueryTypeFromId(String id) {
        String type;
        if (ParticipantUtil.isHruid(id)) {
            type = Constants.PROFILE_HRUID;
        } else if (ParticipantUtil.isGuid(id)) {
            type = Constants.PROFILE_GUID;
        } else if (ParticipantUtil.isLegacyAltPid(id)) {
            type = Constants.PROFILE_LEGACYALTPID;
        } else {
            type = Constants.PROFILE_LEGACYSHORTID;
        }
        return type;
    }

    public static DBElement getDBElement(String fieldName) {
        return PatchUtil.getColumnNameMap().get(Objects.requireNonNull(fieldName));
    }

    public static String underscoresToCamelCase(String fieldName) {
        String[] splittedWords = fieldName.split(UNDERSCORE_SEPARATOR);
        if (hasNoUnderscores(splittedWords)) {
            return handleAllUppercase(fieldName);
        }
        return makeCamelCaseFrom(makeWordsLowerCase(splittedWords));
    }

    private static String makeCamelCaseFrom(List<StringBuilder> words) {
        for (int i = FIRST_ELEMENT_INDEX; i < words.size(); i++) {
            StringBuilder word = words.get(i);
            if (isNotFirstWord(i, word)) {
                makeFirstLetterUpperCase(word);
            }
        }
        return String.join(StringUtils.EMPTY, words);
    }

    private static StringBuilder makeFirstLetterUpperCase(StringBuilder word) {
        return word.replace(FIRST_ELEMENT_INDEX, 1, String.valueOf(word.charAt(FIRST_ELEMENT_INDEX)).toUpperCase());
    }

    private static boolean isNotFirstWord(int i, StringBuilder word) {
        return i != FIRST_ELEMENT_INDEX && word.length() > FIRST_ELEMENT_INDEX;
    }

    private static List<StringBuilder> makeWordsLowerCase(String[] splittedWords) {
        return Arrays.stream(splittedWords).map(word -> new StringBuilder(word.toLowerCase())).collect(Collectors.toList());
    }

    private static String handleAllUppercase(String word) {
        return CAMEL_CASE_REGEX.matcher(word).matches() ? word : word.toLowerCase();
    }

    public static String getPrimaryKeyFromClass(Class<?> clazz) {
        TableName tableName = Objects.requireNonNull(clazz.getAnnotation(TableName.class));
        return underscoresToCamelCase(tableName.primaryKey());
    }

    private static boolean hasNoUnderscores(String[] splittedWords) {
        return splittedWords.length < 2;
    }


    public static String camelCaseToPascalSnakeCase(String camelCase) {
        String[] words = camelCase.split(UPPER_CASE_REGEX.toString());
        String pascalSnakeCase = Arrays.stream(words).map(String::toUpperCase).collect(Collectors.joining(UNDERSCORE_SEPARATOR));
        return pascalSnakeCase;
    }

    public static String capitalCamelCaseToLowerCamelCase(String capitalCamelCase) {
        StringBuilder className = new StringBuilder(capitalCamelCase);
        StringBuilder camelCaseClassName = className.replace(0, 1, String.valueOf(className.charAt(0)).toLowerCase());
        return camelCaseClassName.toString();
    }

    public static class Constants {
        public static final String PROFILE = "profile";
        public static final String PROFILE_HRUID = PROFILE + ".hruid";
        public static final String PROFILE_GUID = PROFILE + ".guid";
        public static final String PROFILE_LEGACYALTPID = PROFILE + ".legacyAltPid";
        public static final String PROFILE_LEGACYSHORTID = PROFILE + ".legacyShortId";
    }
}
