package org.broadinstitute.ddp.datstat;

import com.google.api.client.util.Data;
import com.google.api.client.util.DateTime;
import com.google.gson.Gson;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class SurveyToJsonUtil {

    /**
     * Converts all fields marked with {@link UIAlias} in the given survey
     * to key/value pairs suitable for json serialization.
     */
    public Map<String,Object> convertUIFieldsToJson(SurveyInstance surveyInstance) throws IllegalAccessException{
        Gson gson = new Gson();
        Map<String, Object> jsonOfAllFields = new HashMap<>();
        Field[] fields = surveyInstance.getClass().getDeclaredFields();
        for (Field field : fields) {
            UIAlias[] uiFields = field.getAnnotationsByType(UIAlias.class);
            for (UIAlias uiField : uiFields) {
                String fieldName = uiField.value();
                field.setAccessible(true);
                Object rawValue = field.get(surveyInstance);

                if (rawValue == Data.nullOf(field.getType()))
                {
                    jsonOfAllFields.put(fieldName, null);
                }
                else
                {
                    Object value = rawValue;
                    if (rawValue instanceof DateTime)
                    {
                        value = ((DateTime) rawValue).toStringRfc3339();
                    }
                    jsonOfAllFields.put(fieldName, value);
                }
            }
        }
        //add UIAlias editable from AbstractSurveyInstance
        fields = surveyInstance.getClass().getSuperclass().getDeclaredFields();
        for (Field field : fields) {
            UIAlias[] uiFields = field.getAnnotationsByType(UIAlias.class);
            for (UIAlias uifield : uiFields) {
                String fieldName = uifield.value();
                field.setAccessible(true);
                Object rawValue = field.get(surveyInstance);
                jsonOfAllFields.put(fieldName, rawValue);
            }
        }
        return jsonOfAllFields;
    }
}

