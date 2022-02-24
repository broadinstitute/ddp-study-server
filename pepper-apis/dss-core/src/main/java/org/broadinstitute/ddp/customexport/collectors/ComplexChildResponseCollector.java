package org.broadinstitute.ddp.customexport.collectors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import org.broadinstitute.ddp.export.ComponentDataSupplier;
import org.broadinstitute.ddp.export.collectors.ActivityResponseCollector;
import org.broadinstitute.ddp.model.activity.definition.ActivityDef;
import org.broadinstitute.ddp.model.activity.instance.ActivityResponse;

public class ComplexChildResponseCollector {
    private final ActivityResponseCollector activityResponseCollector;

    public ComplexChildResponseCollector(ActivityDef definition) {
        // For child activities that can have multiple instances, we want to create a json string with data for all children
        activityResponseCollector = new ActivityResponseCollector(definition);
    }

    public String records(ActivityResponse instance, String defaultRecordValue) {
        Map<String, String> fieldValues = activityResponseCollector.records(instance, null, defaultRecordValue);
        return new Gson().toJson(fieldValues);
    }

    public Map<String, String> format(ActivityResponse childSubInstance, ComponentDataSupplier supplier, String s) {
        List<String> headers = activityResponseCollector.getHeaders();
        List<String> data = activityResponseCollector.format(childSubInstance, supplier, s);
        if (data.stream().allMatch(String::isEmpty)) {
            return null;
        }

        Map<String, String> values = new HashMap<>();

        for (int i = 0; i < data.size(); i++) {
            values.put(headers.get(i), data.get(i));
        }

        return values;
    }

}
