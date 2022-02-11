package org.broadinstitute.dsm.db.dto.ddp.participant;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.structure.ColumnName;
import org.broadinstitute.dsm.db.structure.TableName;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;

@TableName(
        name = DBConstants.DDP_PARTICIPANT_DATA,
        alias = DBConstants.DDP_PARTICIPANT_DATA_ALIAS,
        primaryKey = DBConstants.PARTICIPANT_DATA_ID,
        columnPrefix = "")
@JsonIgnoreProperties(ignoreUnknown = true)
@Setter
public class ParticipantData {

    private static final Gson gson = new Gson();

    @ColumnName(ParticipantDataDao.PARTICIPANT_DATA_ID)
    private int participantDataId;

    @ColumnName(ParticipantDataDao.DDP_PARTICIPANT_ID)
    private String ddpParticipantId;

    /*
        used only for Jackson library, jackson by default uses getter of the field to serialize its data
        since we follow Optional way of getters, jackson by default weirdly serializes Optional and not field's data
        JsonGetter is used to give a hint to Jackson to use specific method to serialize data for the field
     */
    @JsonGetter("ddpParticipantId")
    private String serializeDdpParticipantId() {
        return ddpParticipantId;
    }

    @ColumnName(ParticipantDataDao.DDP_INSTANCE_ID)
    private int ddpInstanceId;

    @ColumnName(ParticipantDataDao.FIELD_TYPE_ID)
    private String fieldTypeId;

    /*
        used only for Jackson library, jackson by default uses getter of the field to serialize its data
        since we follow Optional way of getters, jackson by default weirdly serializes Optional and not field's data
        JsonGetter is used to give a hint to Jackson to use specific method to serialize data for the field
     */
    @JsonGetter("fieldTypeId")
    private String serializeFieldTypeId() {
        return fieldTypeId;
    }

    @ColumnName(ParticipantDataDao.DATA)
    @JsonProperty("dynamicFields")
    private String data;

    @JsonProperty("dynamicFields")
    public Map<String, Object> getDynamicFields() {
        try {
            return ObjectMapperSingleton.instance().readValue(data, new TypeReference<Map<String, Object>>() {});
        } catch (IOException | NullPointerException e) {
            return Map.of();
        }
    }

    public ParticipantData() {}

    @JsonIgnore
    private long lastChanged;

    @JsonIgnore
    private String changedBy;

    // We cache the json data map to avoid deserializing it multiple times.
    @JsonIgnore
    private Map<String, String> cachedDataMap;

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

    @JsonIgnore
    public Map<String, String> getDataMap() {
        if (cachedDataMap != null) {
            return cachedDataMap;
        }
        if (StringUtils.isBlank(data)) {
            return null;
        }
        Type type = new TypeToken<HashMap<String, String>>() {}.getType();
        cachedDataMap = gson.fromJson(data, type);
        return cachedDataMap;
    }

    public long getLastChanged() {
        return lastChanged;
    }

    public Optional<String> getChangedBy() {
        return Optional.ofNullable(changedBy);
    }

    private ParticipantData(Builder builder) {
        this.participantDataId = builder.participantDataId;
        this.ddpParticipantId = builder.ddpParticipantId;
        this.ddpInstanceId = builder.ddpInstanceId;
        this.fieldTypeId = builder.fieldTypeId;
        this.data = builder.data;
        this.lastChanged = builder.lastChanged;
        this.changedBy = builder.changedBy;
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

        public ParticipantData build() {
            return new ParticipantData(this);
        }
        
        
    }
}

