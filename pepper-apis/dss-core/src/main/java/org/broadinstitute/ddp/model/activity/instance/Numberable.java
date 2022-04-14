package org.broadinstitute.ddp.model.activity.instance;

/**
 * Used to mark components and questions as things
 * that can be numbered in a UI
 */
public interface Numberable {

    /**
     * Sets the display number for this thing
     */
    void setDisplayNumber(Integer displayNumber);

    /**
     * Should be used for json fields
     */
    String DISPLAY_NUMBER = "displayNumber";

    Integer getDisplayNumber();

    /**
     * Returns true if the number should be hidden
     */
    boolean shouldHideNumber();

}
