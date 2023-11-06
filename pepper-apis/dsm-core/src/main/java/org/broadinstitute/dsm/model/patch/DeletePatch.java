package org.broadinstitute.dsm.model.patch;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.dao.DeletedObjectDao;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.exception.UnsafeDeleteError;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.util.ElasticSearchDeleteUtil;
import org.broadinstitute.dsm.util.NotificationUtil;

@Slf4j
public class DeletePatch extends ExistingRecordPatch {
    DeleteType deleteType;

    public DeletePatch(Patch patch, NotificationUtil notificationUtil, DeleteType deleteType) {
        super(patch, notificationUtil);
        this.dbElementBuilder = new DefaultDBElementBuilder();
        this.deleteType = deleteType;
    }

    @Override
    public Object doPatch() {
        //todo this should get changed after 1209 is merged
        if (!isSafeToDelete() || !patch.isDeleteAnyway()) {
            throw new UnsafeDeleteError("This object is used in a clinical order");
        }
        //recursively start by creating patches for "children" of the current
        DeletePatchFactory.deleteChildrenFields(this.patch, this.getNotificationUtil());
        if (isNameValuePairs()) {
            throw new DsmInternalError(String.format("Patch is invalid for NameValues {}, delete patch should have only one name value",
                    patch.getNameValues()));
        }
        return patchNameValuePair();
    }

    @Override
    public Object patchNameValuePair() {
        Optional<Object> maybeNameValue = processSingleNameValue();
        return maybeNameValue.orElse(null);
    }

    @Override
    Object handleSingleNameValue() {
        List<NameValue> nameValues = new ArrayList<>();
        if (DeletedObjectDao.deletePatch(patch.getId(), patch.getUser(), dbElement, deleteType)) {
            DDPInstanceDto ddpInstanceDto = new DDPInstanceDao().getDDPInstanceByInstanceName(patch.getRealm()).orElseThrow();
            try {
                ElasticSearchDeleteUtil.deleteFromIndexById(patch.getDdpParticipantId(), Integer.parseInt(patch.getId()), ddpInstanceDto, deleteType);
            } catch (Exception e) {
                throw new DsmInternalError("Unable to delete from ES for patch with id "+patch.getId() +" for NameValue: "+patch.getNameValue().toString(),e);
            }
            return nameValues;
        }
        return nameValues;
    }

    @Override
    Optional<Object> processSingleNameValue() {
        Optional<Object> result;
        dbElement = dbElementBuilder.fromName(patch.getNameValue().getName());
        if (dbElement != null) {
            result = Optional.of(handleSingleNameValue());
        } else {
            throw new DsmInternalError("DBElement not found in ColumnNameMap: " + patch.getNameValue().getName());
        }
        return result;
    }

    //TODO this is supposed to get handled by PEPPER-1209
    private boolean isSafeToDelete() {
        return true;
    }


}
