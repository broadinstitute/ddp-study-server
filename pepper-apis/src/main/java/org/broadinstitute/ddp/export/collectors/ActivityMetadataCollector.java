package org.broadinstitute.ddp.export.collectors;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.broadinstitute.ddp.db.dto.ActivityInstanceStatusDto;
import org.broadinstitute.ddp.elastic.MappingUtil;
import org.broadinstitute.ddp.export.DataExporter;
import org.broadinstitute.ddp.model.activity.instance.ActivityResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ActivityMetadataCollector {

    private static final Logger LOG = LoggerFactory.getLogger(ActivityMetadataCollector.class);

    public List<String> emptyRow() {
        return Arrays.asList("", "", "", "", "");
    }

    public Map<String, String> emptyRecord(String activityTag) {
        return records(activityTag, null, true);
    }

    public Map<String, String> records(String activityTag, ActivityResponse instance) {
        return records(activityTag, instance, false);
    }

    private Map<String, String> records(String activityTag,
                                        ActivityResponse instance,
                                        boolean isEmpty) {
        List<String> headers = headers(activityTag);
        List<String> values = null;
        if (!isEmpty) {
            values = format(instance);
        }

        Map<String, String> records = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            records.put(headers.get(i), isEmpty ? null : values.get(i));
        }

        return records;
    }

    public Map<String, Object> mappings(String activityTag) {
        String timestampFormats = MappingUtil.appendISOTimestampFormats(DataExporter.TIMESTAMP_PATTERN);
        Map<String, Object> props = new LinkedHashMap<>();
        props.put(activityTag, MappingUtil.newKeywordType());
        props.put(activityTag + "_status", MappingUtil.newKeywordType());
        props.put(activityTag + "_created_at", MappingUtil.newDateType(timestampFormats, false));
        props.put(activityTag + "_updated_at", MappingUtil.newDateType(timestampFormats, false));
        props.put(activityTag + "_completed_at", MappingUtil.newDateType(timestampFormats, false));
        return props;
    }

    public List<String> headers(String activityTag) {
        return Arrays.asList(activityTag,
                activityTag + "_status",
                activityTag + "_created_at",
                activityTag + "_updated_at",
                activityTag + "_completed_at");
    }

    public List<String> format(ActivityResponse instance) {
        Instant createdAtMillis = Instant.ofEpochMilli(instance.getCreatedAt());

        Instant firstCompletedAtMillis = null;
        if (instance.getFirstCompletedAt() != null) {
            firstCompletedAtMillis = Instant.ofEpochMilli(instance.getFirstCompletedAt());
        }

        ActivityInstanceStatusDto latestStatusDto = instance.getLatestStatus();
        Instant updatedAtMillis = Instant.ofEpochMilli(latestStatusDto.getUpdatedAt());

        return Arrays.asList(
                instance.getGuid(),
                latestStatusDto.getType().name(),
                DataExporter.TIMESTAMP_FMT.format(createdAtMillis),
                DataExporter.TIMESTAMP_FMT.format(updatedAtMillis),
                firstCompletedAtMillis == null ? null : DataExporter.TIMESTAMP_FMT.format(firstCompletedAtMillis));
    }
}
