package org.broadinstitute.ddp.model.activity.instance;

import org.broadinstitute.ddp.db.dto.InstitutionPhysicianComponentDto;
import org.broadinstitute.ddp.model.activity.types.ComponentType;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;

/**
 * Because physician and institution are so close to each other, we'll re-use
 * code in this superclass.  At some point the physician list and institution
 * list will diverge and we will drop this shared superclass.
 */
public abstract class PhysicianInstitutionComponent extends FormComponent {

    public static final String ALLOW_MULTIPLE = "allowMultiple";
    public static final String ADD_BUTTON_TEXT = "addButtonText";
    public static final String TITLE_TEXT = "titleText";
    public static final String SUBTITLE_TEXT = "subtitleText";
    public static final String INSTITUTION_TYPE = "institutionType";
    public static final String SHOW_FIELDS = "showFieldsInitially";
    public static final String REQUIRED = "required";

    // fields are marked transient so that gson does not deserialize them.  instead,
    // they are added to the parameters list, which is serialized.
    private transient InstitutionPhysicianComponentDto instDto;
    private transient boolean shouldHideNumber;

    private transient String buttonText;
    private transient String titleText;
    private transient String subtitleText;

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
        initParametersMap();
    }

    private final void initParametersMap() {
        renderLabels();
        parameters.put(ALLOW_MULTIPLE, instDto.getAllowMultiple());
        parameters.put(ADD_BUTTON_TEXT, buttonText);
        parameters.put(TITLE_TEXT, titleText);
        parameters.put(SUBTITLE_TEXT, subtitleText);
        parameters.put(INSTITUTION_TYPE, instDto.getInstitutionType().name());
        parameters.put(SHOW_FIELDS, instDto.showFields());
        parameters.put(REQUIRED, instDto.isRequired());
    }

    private final void renderLabels() {
        //
    }

    public InstitutionType getInstitutionType() {
        return instDto.getInstitutionType();
    }
}
