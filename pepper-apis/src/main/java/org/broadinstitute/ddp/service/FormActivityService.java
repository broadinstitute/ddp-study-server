package org.broadinstitute.ddp.service;

import java.util.ArrayList;
import java.util.List;

import org.broadinstitute.ddp.db.dto.UserActivityInstanceSummary;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.form.BlockVisibility;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.pex.PexException;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FormActivityService {

    private static final Logger LOG = LoggerFactory.getLogger(FormActivityService.class);

    private PexInterpreter interpreter;

    public FormActivityService(PexInterpreter interpreter) {
        this.interpreter = interpreter;
    }

    /**
     * Get and evaluate visibility only for blocks that has an associated conditional expression.
     *
     * @param handle       the jdbi handle
     * @param userGuid     the user guid
     * @param instanceGuid the form instance guid
     * @return list of block visibilities
     * @throws DDPException if pex evaluation error
     */
    public List<BlockVisibility> getBlockVisibilities(Handle handle, UserActivityInstanceSummary instanceSummary, FormActivityDef def,
                                                      String userGuid, String instanceGuid) {
        List<BlockVisibility> visibilities = new ArrayList<>();
        for (var block : def.getAllToggleableBlocks()) {
            BlockVisibility vis = evaluateVisibility(handle, block, instanceSummary, def, userGuid, instanceGuid);
            if (vis != null) {
                visibilities.add(vis);
            }
        }
        return visibilities;
    }

    private BlockVisibility evaluateVisibility(Handle handle, FormBlockDef block, UserActivityInstanceSummary instanceSummary,
                                               FormActivityDef formActivityDef, String userGuid,
                                               String instanceGuid) {
        BlockVisibility vis = null;
        String expr = block.getShownExpr();
        if (expr != null) {
            try {
                boolean shown = interpreter.eval(expr, handle, userGuid, instanceGuid, formActivityDef, instanceSummary);
                vis = new BlockVisibility(block.getBlockGuid(), shown);
            } catch (PexException e) {
                String msg = String.format("Error evaluating pex expression for form activity instance %s and block %s: `%s`",
                        instanceGuid, block.getBlockGuid(), expr);
                throw new DDPException(msg, e);
            }
        }
        return vis;
    }
}
