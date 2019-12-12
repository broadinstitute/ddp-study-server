package org.broadinstitute.ddp.model.activity.definition.i18n;

import java.util.Optional;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;

import org.broadinstitute.ddp.util.MiscUtil;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class Translation {

    private transient Long id;
    private transient Long revisionId;

    @NotBlank
    @SerializedName("language")
    private String languageCode;

    @NotNull
    @SerializedName("text")
    private String text;

    @JdbiConstructor
    public Translation(long id, String languageCode, String text, long revisionId) {
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
