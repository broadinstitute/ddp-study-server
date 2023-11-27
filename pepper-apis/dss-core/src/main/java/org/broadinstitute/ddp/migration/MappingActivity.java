package org.broadinstitute.ddp.migration;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.SerializedName;

/**
 * Represents mappings for an activity.
 */
class MappingActivity {

    // The name in the source data file.
    // For nested activities, we assume things are in the parent object, so this is not used.
    @SerializedName("source")
    private String source;
    @SerializedName("activity_code")
    private String activityCode;
    @SerializedName("questions")
    private List<MappingQuestion> questions = new ArrayList<>();

    // List of nested activities in the parent.
    @SerializedName("nested_activities")
    private List<MappingActivity> nestedActivities = new ArrayList<>();

    // For describing the nested activity and where its data comes from.
    @SerializedName("nested_type")
    private NestedType nestedType;
    @SerializedName("nested_list")
    private String nestedList;

    public String getSource() {
        return source;
    }

    public String getActivityCode() {
        return activityCode;
    }

    public List<MappingQuestion> getQuestions() {
        return questions;
    }

    public List<MappingActivity> getNestedActivities() {
        return nestedActivities;
    }

    public NestedType getNestedType() {
        return nestedType;
    }

    public String getNestedList() {
        return nestedList;
    }

    enum NestedType {
        KEYS,   // Data for nested activity is from top-level keys in parent object.
        LIST,   // Data is a list of objects from `nested_list`, each representing a nested instance.
    }
}
