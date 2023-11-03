package org.broadinstitute.dsm.model.patch;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.exception.UnsafeDeleteError;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.util.NotificationUtil;

@Slf4j
public class DeletePatch extends ExistingRecordPatch {
    public DeleteType deleteType;
    public DeletePatch(Patch patch, NotificationUtil notificationUtil, DeleteType deleteType) {
        super(patch, notificationUtil);
        this.dbElementBuilder = new DefaultDBElementBuilder();
        // todo delete type can be used later instead of DBElement, since this is a delete and we want to make sure we only delete from specific tables
        this.deleteType = deleteType;
    }

    @Override
    public Object doPatch() {
        //todo pegah not sure where I have to check the isSafeToDelete (related to PEPEPR-1209) but leave it here for now
        if (!isSafeToDelete() || !patch.isDeleteAnyway()) {
            throw new UnsafeDeleteError("This object is used in a clinical order");
        }
        //recursively start by creating patches for children and
        DeletePatchFactory.deleteChildrenFields(this.patch, this.getNotificationUtil());
        return super.doPatch();
    }

    @Override
    Object handleSingleNameValue() {
        List<NameValue> nameValues = new ArrayList<>();
        if (Patch.deletePatch(patch.getId(), patch.getUser(), patch.getNameValue(), dbElement, patch)) {
// todo pegah add remove and export to ES here
            return nameValues;
        }
        return nameValues;
    }

    //TODO this is supposed to get handled by PEPPER-1209
    private boolean isSafeToDelete() {
        return true;
    }


}
