package org.broadinstitute.ddp.model.activity.definition.i18n;

import java.util.Optional;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.util.MiscUtil;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class Translation {

    protected transient Long id;
    protected transient Long revisionId;

    @NotBlank
    @SerializedName("language")
    protected String languageCode;

    @NotNull
    @SerializedName("text")
    protected String text;

    @JdbiConstructor
    public Translation(
            @ColumnName("substitution_id") long id,
            @ColumnName("iso_language_code") String languageCode,
            @ColumnName("substitution_value") String text,
            @ColumnName("substitution_revision_id") Long revisionId) {
        this.id = id;
        this.revisionId = revisionId;
        this.text = text;
        this.languageCode = languageCode;
    }

    public Translation(String languageCode, String text) {
        this.languageCode = MiscUtil.checkNotBlank(languageCode, "languageCode");
        this.text = MiscUtil.checkNonNull(text, "text");
    }

    public Optional<Long> getId() {
        return Optional.ofNullable(id);
    }

    public Optional<Long> getRevisionId() {
        return Optional.ofNullable(revisionId);
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public String getText() {
        return text;
    }
}
