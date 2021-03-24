package org.broadinstitute.dsm.model.participant.data;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.utils.StringUtils;

@Getter
public class FamilyMemberDetails {

    private static final Logger logger = LoggerFactory.getLogger(FamilyMemberDetails.class);

    @SerializedName(value = "DATSTAT_FIRSTNAME", alternate = "firstName")
    private String firstName;

    @SerializedName(value = "DATSTAT_LASTNAME", alternate = "lastName")
    private String lastName;

    @SerializedName(value = "MEMBER_TYPE", alternate = "memberType")
    private String memberType;

    @SerializedName(value = "FAMILY_ID", alternate = "familyId")
    private String familyId;

    @SerializedName(value = "COLLABORATOR_PARTICIPANT_ID", alternate = "collaboratorParticipantId")
    private String collaboratorParticipantId;


    public FamilyMemberDetails() {}

    public FamilyMemberDetails(String firstName, String lastName, String memberType, String familyId,
                               String collaboratorParticipantId) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.memberType = memberType;
        this.familyId = familyId;
        this.collaboratorParticipantId = collaboratorParticipantId;
    }

    public Map<String, String> toMap() {
        Map<String, String> familyMemberDetailMap = new HashMap<>();
        List<Field> declaredFields = Arrays.stream(FamilyMemberDetails.class.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers()))
                .collect(Collectors.toList());
        for (Field f: declaredFields) {
            try {
                familyMemberDetailMap.put(f.getAnnotation(SerializedName.class).value(), (String) f.get(this));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return familyMemberDetailMap;
    }

    public boolean isFamilyMemberFieldsEmpty() {
        return StringUtils.isBlank(this.firstName) || StringUtils.isBlank(this.lastName) || StringUtils.isBlank(this.memberType)
                    || StringUtils.isBlank(this.familyId) || StringUtils.isBlank(this.collaboratorParticipantId);
    }
}
