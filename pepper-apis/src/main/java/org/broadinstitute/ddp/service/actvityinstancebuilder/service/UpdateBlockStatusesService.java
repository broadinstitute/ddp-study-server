package org.broadinstitute.ddp.service.actvityinstancebuilder.service;

import java.util.ArrayList;
import java.util.List;

import org.broadinstitute.ddp.db.dto.UserActivityInstanceSummary;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.instance.ConditionalBlock;
import org.broadinstitute.ddp.model.activity.instance.FormBlock;
import org.broadinstitute.ddp.model.activity.instance.FormInstance;
import org.broadinstitute.ddp.model.activity.instance.FormSection;
import org.broadinstitute.ddp.model.activity.instance.GroupBlock;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.pex.PexException;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ActivityInstanceFromDefinitionBuilder;
import org.broadinstitute.ddp.service.actvityinstancebuilder.form.FormInstanceCreatorHelper;
import org.jdbi.v3.core.Handle;

public class UpdateBlockStatusesService {

    /**
     * Evaluate and update the form's block visibilities, assuming that those are all loaded. If the block does not have
     * a conditional expression (and thus toggle-able), no change will be made to the block.
     *
     * <p>TODO: this method is used only in {@link ActivityInstanceFromDefinitionBuilder} and better to move it to
     *     {@link FormInstanceCreatorHelper}. And it needs to be removed from unit tests -
     *     {@link ActivityInstanceFromDefinitionBuilder} will be used there instead.
     *
     * @param handle          the jdbi handle
     * @param interpreter     the pex interpreter to evaluate expressions
     * @param userGuid        the user guid
     * @param instanceGuid    the activity instance guid
     * @param instanceSummary container that holds data about user's instances
     * @throws DDPException if pex evaluation error
     */
    public void updateBlockStatuses(Handle handle, FormInstance formInstance, PexInterpreter interpreter,
                                           String userGuid, String operatorGuid,
                                           String instanceGuid, UserActivityInstanceSummary instanceSummary) {
        for (FormSection section : formInstance.getAllSections()) {
            for (FormBlock block : section.getBlocks()) {
                updateBlockStatus(handle, formInstance, interpreter, block, userGuid, operatorGuid, instanceGuid, instanceSummary);
                if (block.getBlockType().isContainerBlock()) {
                    List<FormBlock> children;
                    if (block.getBlockType() == BlockType.CONDITIONAL) {
                        children = ((ConditionalBlock) block).getNested();
                    } else if (block.getBlockType() == BlockType.GROUP) {
                        children = ((GroupBlock) block).getNested();
                    } else if (block.getBlockType() == BlockType.ACTIVITY) {
                        // Questions within the nested activity itself are not considered.
                        children = new ArrayList<>();
                    } else {
                        throw new DDPException("Unhandled container block type " + block.getBlockType());
                    }
                    for (FormBlock child : children) {
                        updateBlockStatus(handle, formInstance, interpreter, child, userGuid, operatorGuid, instanceGuid, instanceSummary);
                    }
                }
            }
        }
    }

    private void updateBlockStatus(Handle handle, FormInstance formInstance, PexInterpreter interpreter,
                                          FormBlock block, String userGuid,
                                          String operatorGuid, String instanceGuid, UserActivityInstanceSummary instanceSummary) {
        if (block.getShownExpr() != null) {
            try {
                boolean shown = interpreter.eval(block.getShownExpr(), handle, userGuid, operatorGuid, instanceGuid, instanceSummary);
                block.setShown(shown);
            } catch (PexException e) {
                String msg = String.format("Error evaluating pex expression for form activity instance %s and block %s: `%s`",
                        formInstance.getGuid(), block.getGuid(), block.getShownExpr());
                throw new DDPException(msg, e);
            }
        }
    }
}
