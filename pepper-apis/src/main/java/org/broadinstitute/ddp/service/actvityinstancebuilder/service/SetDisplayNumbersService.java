package org.broadinstitute.ddp.service.actvityinstancebuilder.service;

import java.util.List;

import org.broadinstitute.ddp.model.activity.instance.FormBlock;
import org.broadinstitute.ddp.model.activity.instance.FormInstance;
import org.broadinstitute.ddp.model.activity.instance.FormSection;
import org.broadinstitute.ddp.model.activity.instance.Numberable;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ActivityInstanceFromDefinitionBuilder;
import org.broadinstitute.ddp.service.actvityinstancebuilder.form.FormInstanceCreatorHelper;

public class SetDisplayNumbersService {

    /**
     * Walks through the sections and blocks in order and
     * sets the {@link Numberable} fields accordingly.
     * @return the maximum display number used
     */
    public int setDisplayNumbers(FormInstance formInstance) {
        int startingNumber = 1;
        if (formInstance.getIntroduction() != null) {
            startingNumber = setNumberables(formInstance.getIntroduction().getBlocks(), startingNumber);

        }
        if (formInstance.getBodySections() != null) {
            for (FormSection bodySection : formInstance.getBodySections()) {
                startingNumber = setNumberables(bodySection.getBlocks(), startingNumber);
            }
        }
        if (formInstance.getClosing() != null) {
            startingNumber = setNumberables(formInstance.getClosing().getBlocks(), startingNumber);
        }
        return startingNumber;
    }

    /**
     * Sets the display number for the blocks in order,
     * starting at startingNumber
     *
     * <p>TODO: this method is used only in {@link ActivityInstanceFromDefinitionBuilder} and better to move it to
     *    {@link FormInstanceCreatorHelper}. And it needs to be removed from unit tests -
     *    {@link ActivityInstanceFromDefinitionBuilder} will be used there instead.
     *
     * @param blocks the blocks to number
     * @param startingNumber the number at which to start
     * @return the ending number
     *
     */
    private int setNumberables(List<FormBlock> blocks, int startingNumber) {
        for (FormBlock formBlock : blocks) {
            if (formBlock instanceof Numberable) {
                Numberable numberable = (Numberable)formBlock;
                if (numberable.shouldHideNumber()) {
                    numberable.setDisplayNumber(null);
                } else {
                    numberable.setDisplayNumber(startingNumber++);
                }
            }
        }
        return startingNumber;
    }
}
