package org.broadinstitute.ddp.export.collectors;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.dto.EnrollmentStatusDto;
import org.broadinstitute.ddp.elastic.MappingUtil;
import org.broadinstitute.ddp.export.DataExporter;
import org.broadinstitute.ddp.model.user.User;

public class ParticipantMetadataFormatter {

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
        return props;
    }

    public List<String> headers() {
        return Arrays.asList(
                "participant_guid", "participant_hruid",
                "legacy_altpid", "legacy_shortid",
                "first_name", "last_name", "email", "do_not_contact", "created_at",
                "status", "status_timestamp");
    }

    public List<String> format(EnrollmentStatusDto statusDto, User user) {
        Instant createdAtMillis = Instant.ofEpochMilli(user.getCreatedAt());
        Instant statusMillis = Instant.ofEpochMilli(statusDto.getValidFromMillis());
        Boolean doNotContact = user.hasProfile() ? user.getProfile().getDoNotContact() : null;
        return Arrays.asList(
                user.getGuid(), user.getHruid(),
                StringUtils.defaultString(user.getLegacyAltPid(), ""),
                StringUtils.defaultString(user.getLegacyShortId(), ""),
                user.hasProfile() ? user.getProfile().getFirstName() : "",
                user.hasProfile() ? user.getProfile().getLastName() : "",
                StringUtils.defaultString(user.getEmail(), ""),
                doNotContact == null ? "false" : doNotContact.toString(),
                createdAtMillis == null ? null : DataExporter.TIMESTAMP_FMT.format(createdAtMillis),
                statusDto.getEnrollmentStatus().name(),
                statusMillis == null ? null : DataExporter.TIMESTAMP_FMT.format(statusMillis));
    }
}
