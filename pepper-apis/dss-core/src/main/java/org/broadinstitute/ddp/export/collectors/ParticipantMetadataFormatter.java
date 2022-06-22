package org.broadinstitute.ddp.export.collectors;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.dto.EnrollmentStatusDto;
import org.broadinstitute.ddp.elastic.MappingUtil;
import org.broadinstitute.ddp.export.DataExporter;
import org.broadinstitute.ddp.model.user.User;

public class ParticipantMetadataFormatter {
    private List<String> exclude = new ArrayList<>();

    public ParticipantMetadataFormatter() {}

    public ParticipantMetadataFormatter(List<String> exclude) {
        if (exclude != null && exclude.size() > 0) {
            this.exclude = exclude;
        }
    }

    public Map<String, String> records(EnrollmentStatusDto statusDto, User user) {
        List<String> headers = headers();
        List<String> values = format(statusDto, user);
        Map<String, String> records = new LinkedHashMap<>();

        for (int i = 0; i < headers.size(); i++) {
            records.put(headers.get(i), values.get(i));
        }

        return records;
    }

    public Map<String, Object> mappings() {
        String timestampFormats = MappingUtil.appendISOTimestampFormats(DataExporter.TIMESTAMP_PATTERN);
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("participant_guid", MappingUtil.newKeywordType());
        props.put("participant_hruid", MappingUtil.newKeywordType());
        props.put("legacy_altpid", MappingUtil.newKeywordType());
        props.put("legacy_shortid", MappingUtil.newKeywordType());
        props.put("first_name", MappingUtil.newTextType());
        props.put("last_name", MappingUtil.newTextType());
        props.put("email", MappingUtil.newKeywordType());
        props.put("do_not_contact", MappingUtil.newBoolType());
        props.put("created_at", MappingUtil.newDateType(timestampFormats, false));
        props.put("status", MappingUtil.newKeywordType());
        props.put("status_timestamp", MappingUtil.newDateType(timestampFormats, false));
        if (exclude != null) {
            for (String s : exclude) {
                props.remove(s);
            }
        }
        return props;
    }

    public List<String> headers() {
        String[] headerArray = {"participant_guid", "participant_hruid",
                "legacy_altpid", "legacy_shortid",
                "first_name", "last_name", "email", "do_not_contact", "created_at",
                "status", "status_timestamp"};
        List<String> headers = new ArrayList<>();
        for (String header : headerArray) {
            if (!exclude.contains(header)) {
                headers.add(header);
            }
        }
        return headers;
    }

    public List<String> format(EnrollmentStatusDto statusDto, User user) {
        Instant createdAtMillis = Instant.ofEpochMilli(user.getCreatedAt());
        Instant statusMillis = Instant.ofEpochMilli(statusDto.getValidFromMillis());
        Boolean doNotContact = user.hasProfile() ? user.getProfile().getDoNotContact() : null;
        List<String> formatted = new ArrayList<>();
        if (!exclude.contains("participant_guid")) {
            formatted.add(user.getGuid());
        }
        if (!exclude.contains("participant_hruid")) {
            formatted.add(user.getHruid());
        }
        if (!exclude.contains("legacy_altpid")) {
            formatted.add(StringUtils.defaultString(user.getLegacyAltPid(), ""));
        }
        if (!exclude.contains("legacy_shortid")) {
            formatted.add(StringUtils.defaultString(user.getLegacyShortId(), ""));
        }
        if (!exclude.contains("first_name")) {
            formatted.add(user.hasProfile() ? user.getProfile().getFirstName() : "");
        }
        if (!exclude.contains("last_name")) {
            formatted.add(user.hasProfile() ? user.getProfile().getLastName() : "");
        }
        if (!exclude.contains("email")) {
            formatted.add(user.getEmail().orElse(StringUtils.EMPTY));
        }
        if (!exclude.contains("do_not_contact")) {
            formatted.add(doNotContact == null ? "false" : doNotContact.toString());
        }
        if (!exclude.contains("created_at")) {
            formatted.add(createdAtMillis == null ? null : DataExporter.TIMESTAMP_FMT.format(createdAtMillis));
        }
        if (!exclude.contains("status")) {
            formatted.add(statusDto.getEnrollmentStatus().name());
        }
        if (!exclude.contains("status_timestamp")) {
            formatted.add(statusMillis == null ? null : DataExporter.TIMESTAMP_FMT.format(statusMillis));
        }

        return formatted;
    }
}
