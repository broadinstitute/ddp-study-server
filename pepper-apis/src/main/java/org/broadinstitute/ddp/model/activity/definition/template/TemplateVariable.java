package org.broadinstitute.ddp.model.activity.definition.template;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.util.MiscUtil;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class TemplateVariable {

    private transient Long id;

    @NotBlank
    @SerializedName("name")
    private String name;

    @NotEmpty
    @SerializedName("translations")
    private List<@Valid @NotNull Translation> translations;

    /**
     * Create a template variable with a single language translation.
     *
     * @param name           the variable name
     * @param languageCode   the language code
     * @param translatedText the translated text
     * @return the variable
     */
    public static TemplateVariable single(String name, String languageCode, String translatedText) {
        List<Translation> texts = new ArrayList<>();
        texts.add(new Translation(languageCode, translatedText));
        return new TemplateVariable(name, texts);
    }

    @JdbiConstructor
    public TemplateVariable(
            @ColumnName("template_variable_id") long id,
            @ColumnName("variable_name") String name) {
        this.id = id;
        this.name = name;
        this.translations = new ArrayList<>();
    }

    public TemplateVariable(Long id, String name, List<Translation> translations) {
        this(name, translations);
        this.id = id;
    }

    public TemplateVariable(String name, List<Translation> translations) {
        this.name = MiscUtil.checkNotBlank(name, "name");

        List<Translation> internalTranslations = new ArrayList<>();

        if (translations != null) {
            internalTranslations.addAll(translations);
        }

        this.translations = internalTranslations;
    }

    public Optional<Long> getId() {
        return Optional.ofNullable(this.id);
    }

    public String getName() {
        return name;
    }

    public void addTranslation(Translation translation) {
        translations.add(translation);
    }

    public List<Translation> getTranslations() {
        return new ArrayList<Translation>(translations);
    }

    public void setTranslation(List<Translation> translations) {
        this.translations = new ArrayList<>(translations);
    }

    public Optional<Translation> getTranslation(String languageCode) {
        return translations.stream().filter(trans -> trans.getLanguageCode().equals(languageCode)).findFirst();
    }
}
