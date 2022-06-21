package org.broadinstitute.ddp.model.user;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZoneId;

import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.SuperBuilder;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
@SuperBuilder(toBuilder = true)
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class UserProfile implements Serializable {
    @ColumnName("user_id")
    Long userId;

    @ColumnName("first_name")
    String firstName;

    @ColumnName("last_name")
    String lastName;

    @ColumnName("sex")
    SexType sexType;

    @ColumnName("birth_date")
    LocalDate birthDate;

    @ColumnName("preferred_language_id")
    Long preferredLangId;

    @ColumnName("iso_language_code")
    String preferredLangCode;

    @ColumnName("time_zone")
    ZoneId timeZone;

    @ColumnName("do_not_contact")
    Boolean doNotContact;

    @ColumnName("is_deceased")
    Boolean isDeceased;

    @ColumnName("skip_language_popup")
    Boolean skipLanguagePopup;

    public enum SexType {
        FEMALE, MALE, INTERSEX, PREFER_NOT_TO_ANSWER
    }

    public UserProfile(final UserProfile o) {
        this(o.userId, o.firstName, o.lastName, o.sexType, o.birthDate, o.preferredLangId, o.preferredLangCode,
                o.timeZone, o.doNotContact, o.isDeceased, o.skipLanguagePopup);
    }
}
