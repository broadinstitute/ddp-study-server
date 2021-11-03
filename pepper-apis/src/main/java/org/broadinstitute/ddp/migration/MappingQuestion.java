package org.broadinstitute.ddp.migration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;

/**
 * Represents mappings for a question.
 */
class MappingQuestion {

    @SerializedName("source")
    private String source;
    @SerializedName("target")
    private String target;
    @SerializedName("type")
    private SourceType type;

    // Some of the below might be null depending on what the question type is.
    // We group them all here instead of in subclasses so to simplify deserialization.

    // Picklists
    @SerializedName("option_type")
    private OptionType optionType;
    @SerializedName("option_keys_separator")
    private String optionKeysSeparator = ".";
    @SerializedName("option_enum")
    private List<String> optionEnum = new ArrayList<>();
    @SerializedName("option_pairs")
    private Map<String, String> optionPairs = new HashMap<>();
    @SerializedName("option_details")
    private Map<String, String> optionDetails = new HashMap<>();

    public String getSource() {
        return source;
    }

    public String getTarget() {
        return target;
    }

    public SourceType getType() {
        return type;
    }

    public OptionType getOptionType() {
        return optionType;
    }

    public String getOptionKeysSeparator() {
        return optionKeysSeparator;
    }

    public List<String> getOptionEnum() {
        return optionEnum;
    }

    public Map<String, String> getOptionPairs() {
        return optionPairs;
    }

    public Map<String, String> getOptionDetails() {
        return optionDetails;
    }

    /**
     * The main entry point for pulling out migration data and converting to answer objects.
     *
     * @param survey the migration survey data object
     * @return an answer object, or null
     */
    public Answer extractAnswer(ObjectWrapper survey) {
        switch (type) {
            case TEXT:
                return mapText(survey);
            case PICKLIST:
                return mapPicklist(survey);
            default:
                throw new LoaderException("Unhandled question mapping type: " + type);
        }
    }

    private TextAnswer mapText(ObjectWrapper survey) {
        String value = survey.getString(source);
        if (value == null) {
            return null;
        }
        return new TextAnswer(null, target, null, value);
    }

    private PicklistAnswer mapPicklist(ObjectWrapper survey) {
        switch (optionType) {
            case ENUM:
                return mapOptionEnum(survey);
            case KEYS:
                return mapOptionKeys(survey);
            case LIST:
                return mapOptionList(survey);
            case STR:
                return mapOptionStr(survey);
            default:
                throw new LoaderException("Unhandled picklist option mapping type: " + optionType);
        }
    }

    private PicklistAnswer mapOptionEnum(ObjectWrapper survey) {
        Integer index = survey.getInt(source);
        if (index == null) {
            return null;
        } else if (index < 0 || index >= optionEnum.size()) {
            throw new LoaderException("Invalid index value from source " + source + ": " + index);
        }

        String stableId = optionEnum.get(index);
        if (stableId == null || stableId.isBlank()) {
            // Target option stable id is not specified (null or empty),
            // so it doesn't correspond to an answer.
            return null;
        }

        String details = null;
        if (optionDetails.containsKey(stableId)) {
            details = survey.getString(optionDetails.get(stableId));
        }

        var option = new SelectedPicklistOption(stableId, details);
        return new PicklistAnswer(null, target, null, List.of(option));
    }

    private PicklistAnswer mapOptionKeys(ObjectWrapper survey) {
        String fmt = "%s%s%s";
        List<SelectedPicklistOption> options = new ArrayList<>();

        for (var entry : optionPairs.entrySet()) {
            String optionSource = entry.getKey();
            String stableId = entry.getValue();
            String keyName = String.format(fmt, source, optionKeysSeparator, optionSource);

            Integer toggle = survey.getInt(keyName);
            if (toggle == null || toggle == 0) {
                continue;
            } else if (toggle != 1) {
                throw new LoaderException("Invalid picklist option value from source " + keyName + ": " + toggle);
            }

            String details = null;
            if (optionDetails.containsKey(stableId)) {
                details = survey.getString(optionDetails.get(stableId));
            }

            options.add(new SelectedPicklistOption(stableId, details));
        }

        return options.isEmpty() ? null : new PicklistAnswer(null, target, null, options);
    }

    private PicklistAnswer mapOptionList(ObjectWrapper survey) {
        List<String> values = survey.getStringList(source);
        if (values == null || values.isEmpty()) {
            return null;
        }

        List<SelectedPicklistOption> options = new ArrayList<>();
        for (var value : values) {
            String stableId = value;
            if (optionPairs.containsKey(value)) {
                stableId = optionPairs.get(value);
            }
            String details = null;
            if (optionDetails.containsKey(stableId)) {
                details = survey.getString(optionDetails.get(stableId));
            }
            options.add(new SelectedPicklistOption(stableId, details));
        }

        return new PicklistAnswer(null, target, null, options);
    }

    private PicklistAnswer mapOptionStr(ObjectWrapper survey) {
        String stableId = survey.getString(source);
        if (stableId == null || stableId.isBlank()) {
            return null;
        }

        if (optionPairs.containsKey(stableId)) {
            stableId = optionPairs.get(stableId);
        }

        String details = null;
        if (optionDetails.containsKey(stableId)) {
            details = survey.getString(optionDetails.get(stableId));
        }

        var option = new SelectedPicklistOption(stableId, details);
        return new PicklistAnswer(null, target, null, List.of(option));
    }

    enum SourceType {
        PICKLIST,   // Choice question, need to look at option_* properties.
        TEXT,       // Source data is a string.
    }

    enum OptionType {
        ENUM,   // Data is an index into option_enum list. If element is null or empty, then no answer is added.
        KEYS,   // Options have their own keys, with 0/1/null as data. See option_pairs for mapping.
        LIST,   // Source data is a list of strings. See option_pairs for mapping.
        STR,    // Source data is a string, which is the option stable id to use (can be mapped using option_pairs).
    }
}
