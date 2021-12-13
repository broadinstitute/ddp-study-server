package org.broadinstitute.dsm.db.dto.ddp.participant;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

@Setter
public class ParticipantDataDto {

    private static final Gson gson = new Gson();

    private int participantDataId;
    private String ddpParticipantId;
    private int ddpInstanceId;
    private String fieldTypeId;
    private String data;
    private long lastChanged;
    private String changedBy;

    // We cache the json data map to avoid deserializing it multiple times.
    private Map<String, String> cachedDataMap;

    private ParticipantDataDto(Builder builder) {
        this.participantDataId = builder.participantDataId;
        this.ddpParticipantId = builder.ddpParticipantId;
        this.ddpInstanceId = builder.ddpInstanceId;
        this.fieldTypeId = builder.fieldTypeId;
        this.data = builder.data;
        this.lastChanged = builder.lastChanged;
        this.changedBy = builder.changedBy;
    }

    public int getParticipantDataId() {
        return participantDataId;
    }

    public Optional<String> getDdpParticipantId() {
        return Optional.ofNullable(ddpParticipantId);
    }

    public int getDdpInstanceId() {
        return ddpInstanceId;
    }

    public Optional<String> getFieldTypeId() {
        return Optional.ofNullable(fieldTypeId);
    }

    public Optional<String> getData() {
        return Optional.ofNullable(data);
    }

    public void setData(String data) {
        this.data = data;
        this.cachedDataMap = null;
    }

    public Map<String, String> getDataMap() {
        if (cachedDataMap != null) {
            return cachedDataMap;
        }
        if (StringUtils.isBlank(data)) {
            return null;
        }
        Type type = new TypeToken<HashMap<String, String>>() {
        }.getType();
        cachedDataMap = gson.fromJson(data, type);
        return cachedDataMap;
    }

    public long getLastChanged() {
        return lastChanged;
    }

    public Optional<String> getChangedBy() {
        return Optional.ofNullable(changedBy);
    }

    public static class Builder {
        private int participantDataId;
        private String ddpParticipantId;
        private int ddpInstanceId;
        private String fieldTypeId;
        private String data;
        private long lastChanged;
        private String changedBy;

        public Builder withParticipantDataId(int participantDataId) {
            this.participantDataId = participantDataId;
            return this;
        }

        public Builder withDdpParticipantId(String ddpParticipantId) {
            this.ddpParticipantId = ddpParticipantId;
            return this;
        }

        public Builder withDdpInstanceId(int ddpInstanceId) {
            this.ddpInstanceId = ddpInstanceId;
            return this;
        }

        public Builder withFieldTypeId(String fieldTypeId) {
            this.fieldTypeId = fieldTypeId;
            return this;
        }

        public Builder withData(String data) {
            this.data = data;
            return this;
        }

        public Builder withLastChanged(long lastChanged) {
            this.lastChanged = lastChanged;
            return this;
        }

        public Builder withChangedBy(String changedBy) {
            this.changedBy = changedBy;
            return this;
        }

        public ParticipantDataDto build() {
            return new ParticipantDataDto(this);
        }


    }
}

