package org.broadinstitute.dsm.model.participant.data;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.utils.StringUtils;

@Data
public class FamilyMemberDetails {

    private static final Logger logger = LoggerFactory.getLogger(FamilyMemberDetails.class);

    @SerializedName(value = FamilyMemberConstants.FIRSTNAME, alternate = "firstName")
    private String firstName;

    @SerializedName(value = FamilyMemberConstants.LASTNAME, alternate = "lastName")
    private String lastName;

    @SerializedName(value = FamilyMemberConstants.MEMBER_TYPE, alternate = "memberType")
    private String memberType;

    @SerializedName(value = FamilyMemberConstants.FAMILY_ID, alternate = "familyId")
    private long familyId;

    @SerializedName(value = FamilyMemberConstants.SUBJECT_ID, alternate = "subjectId")
    private String subjectId;

    @SerializedName(value = FamilyMemberConstants.COLLABORATOR_PARTICIPANT_ID, alternate = "collaboratorParticipantId")
    private String collaboratorParticipantId;

    @SerializedName(value = FamilyMemberConstants.PHONE, alternate = "mobilePhone")
    private String mobilePhone;

    @SerializedName(value = FamilyMemberConstants.EMAIL, alternate = "email")
    private String email;

    @SerializedName(value = FamilyMemberConstants.IS_APPLICANT, alternate = "isApplicant")
    private boolean isApplicant;


    public FamilyMemberDetails() {}

    public FamilyMemberDetails(String firstName, String lastName, String memberType, long familyId,
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
                Object fieldValue = f.get(this);
                if (Objects.isNull(fieldValue)) continue;
                familyMemberDetailMap.put(f.getAnnotation(SerializedName.class).value(), String.valueOf(fieldValue));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return familyMemberDetailMap;
    }

    public boolean isFamilyMemberFieldsEmpty() {
        return StringUtils.isBlank(this.firstName) || StringUtils.isBlank(this.lastName) || StringUtils.isBlank(this.memberType)
                     || StringUtils.isBlank(this.subjectId);
    }



}

