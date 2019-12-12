package org.broadinstitute.ddp.model.pdf;

import static org.broadinstitute.ddp.model.pdf.SubstitutionType.PROFILE;

import java.util.Set;

import com.google.common.collect.Sets;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public final class ProfileSubstitution extends PdfSubstitution {

    public static final Set<String> SUPPORTED_PROFILE_FIELDS = Sets.newHashSet(
            "hruid",
            "email",
            "first_name",
            "last_name"
    );

    private String fieldName;

    @JdbiConstructor
    public ProfileSubstitution(@ColumnName("pdf_substitution_id") long id,
                               @ColumnName("pdf_template_id") long templateId,
                               @ColumnName("placeholder") String placeholder,
                               @ColumnName("profile_field_name") String fieldName) {
        super(id, templateId, PROFILE, placeholder);
        this.fieldName = fieldName;
    }

    public ProfileSubstitution(String placeholder, String fieldName) {
        super(PROFILE, placeholder);
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }
}
