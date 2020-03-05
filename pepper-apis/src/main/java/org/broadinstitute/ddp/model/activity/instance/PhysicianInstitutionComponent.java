package org.broadinstitute.ddp.model.activity.instance;

import java.util.Optional;
import java.util.function.Consumer;
import javax.validation.constraints.NotNull;

import com.google.gson.annotations.SerializedName;

import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.db.dto.InstitutionPhysicianComponentDto;
import org.broadinstitute.ddp.model.activity.types.ComponentType;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;

/**
 * Because physician and institution are so close to each other, we'll re-use
 * code in this superclass.  At some point the physician list and institution
 * list will diverge and we will drop this shared superclass.
 */
public abstract class PhysicianInstitutionComponent extends FormComponent {

    // fields are marked transient so that gson does not deserialize them.  instead,
    // they are added to the parameters list, which is serialized.
    private transient InstitutionPhysicianComponentDto instDto;
    private transient String buttonText;
    private transient String titleText;
    private transient String subtitleText;

    @NotNull
    @SerializedName("parameters")
    // That's what is actually serialized
    private SerializedFields serializedFields = new SerializedFields();

    protected PhysicianInstitutionComponent(
            ComponentType physicianOrInstitution,
            InstitutionPhysicianComponentDto instDto,
            boolean shouldHideNumber
    ) {
        super(physicianOrInstitution);
        if (physicianOrInstitution != ComponentType.PHYSICIAN && physicianOrInstitution != ComponentType.INSTITUTION) {
            throw new IllegalArgumentException("Physician/Institution component must be either " + ComponentType
                    .PHYSICIAN + " or " + ComponentType.INSTITUTION);
        }
        this.instDto = instDto;
        this.hideDisplayNumber = shouldHideNumber;
    }

    private final void setSerializedFields() {
        serializedFields.setAllowMultiple(instDto.getAllowMultiple());
        serializedFields.setButtonText(buttonText);
        serializedFields.setTitleText(titleText);
        serializedFields.setSubtitleText(subtitleText);
        serializedFields.setInstitutionType(instDto.getInstitutionType().name());
        serializedFields.setShowFields(instDto.showFields());
        serializedFields.setIsRequired(instDto.isRequired());
    }

    @Override
    public void registerTemplateIds(Consumer<Long> registry) {
        Optional.ofNullable(instDto.getButtonTemplateId()).ifPresentOrElse(registry::accept, () -> { });
        Optional.ofNullable(instDto.getTitleTemplateId()).ifPresentOrElse(registry::accept, () -> { });
        Optional.ofNullable(instDto.getSubtitleTemplateId()).ifPresentOrElse(registry::accept, () -> { });
    }

    @Override
    public void applyRenderedTemplates(Provider<String> rendered, ContentStyle style) {
        buttonText = rendered.get(instDto.getButtonTemplateId());
        titleText = rendered.get(instDto.getTitleTemplateId());
        subtitleText = rendered.get(instDto.getSubtitleTemplateId());

        setSerializedFields();
    }

    public InstitutionType getInstitutionType() {
        return instDto.getInstitutionType();
    }

    public static class SerializedFields {
        @SerializedName("allowMultiple")
        private boolean allowMultiple;
        @SerializedName("addButtonText")
        private String buttonText;
        @SerializedName("titleText")
        private String titleText;
        @SerializedName("subtitleText")
        private String subtitleText;
        @SerializedName("institutionType")
        private String institutionType;
        @SerializedName("showFieldsInitially")
        private boolean showFields;
        @SerializedName("required")
        private boolean isRequired;

        public void setAllowMultiple(boolean allowMultiple) {
            this.allowMultiple = allowMultiple;
        }

        public void setButtonText(String buttonText) {
            this.buttonText = buttonText;
        }

        public void setTitleText(String titleText) {
            this.titleText = titleText;
        }

        public void setSubtitleText(String subtitleText) {
            this.subtitleText = subtitleText = subtitleText;
        }

        public void setInstitutionType(String institutionType) {
            this.institutionType = institutionType;
        }

        public void setShowFields(boolean showFields) {
            this.showFields = showFields;
        }

        public void setIsRequired(boolean isRequired) {
            this.isRequired = isRequired;
        }
    }
}
