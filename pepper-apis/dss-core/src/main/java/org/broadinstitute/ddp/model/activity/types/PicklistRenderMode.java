package org.broadinstitute.ddp.model.activity.types;

public enum PicklistRenderMode {
    /** Hint to render as a list of options, using checkboxes as applicable. */
    CHECKBOX_LIST,

    /** Hint to render as a dropdown. */
    DROPDOWN,

    /** Hint to render as a list of options. */
    LIST,

    /** Hint to render as a list of options with autocompletion function */
    AUTOCOMPLETE,

    /** Hint to render as a list of options with server side / remote autocompletion function */
    REMOTE_AUTOCOMPLETE
}
