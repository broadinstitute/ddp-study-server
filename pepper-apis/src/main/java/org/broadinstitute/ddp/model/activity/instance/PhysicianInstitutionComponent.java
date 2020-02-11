package org.broadinstitute.ddp.model.activity.instance;

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
    private transient boolean allowMultiple;

    private transient String addButtonText;

    private transient String titleText;

    private transient String subtitleText;

    private transient InstitutionType institutionType;

    private transient boolean showFields;

    private transient boolean required;

    protected PhysicianInstitutionComponent(ComponentType physicianOrInstitution,
                                            boolean allowMultiple,
                                            String addButtonText,
                                            String titleText,
                                            String subtitleText,
                                            InstitutionType institutionType,
                                            boolean showFields,
                                            boolean required,
                                            boolean hideNumber) {
        super(physicianOrInstitution);
        if (physicianOrInstitution != ComponentType.PHYSICIAN && physicianOrInstitution != ComponentType.INSTITUTION) {
            throw new IllegalArgumentException("Physician/Institution component must be either " + ComponentType
                    .PHYSICIAN + " or " + ComponentType.INSTITUTION);
        }
        this.allowMultiple = allowMultiple;
        this.addButtonText = addButtonText;
        this.titleText = titleText;
        this.subtitleText = subtitleText;
        this.institutionType = institutionType;
        this.showFields = showFields;
        this.hideDisplayNumber = hideNumber;
        this.required = required;
        initParametersMap();
    }

    private final void initParametersMap() {
        parameters.put(ALLOW_MULTIPLE, allowMultiple);
        parameters.put(ADD_BUTTON_TEXT, addButtonText);
        parameters.put(TITLE_TEXT, titleText);
        parameters.put(SUBTITLE_TEXT, subtitleText);
        parameters.put(INSTITUTION_TYPE, institutionType.name());
        parameters.put(SHOW_FIELDS, showFields);
        parameters.put(REQUIRED, required);
    }

    public InstitutionType getInstitutionType() {
        return institutionType;
    }
}
