package org.broadinstitute.dsm.route.kit;

import java.util.List;

import lombok.Getter;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;

@Getter
public class KitPayload {

    List<? extends ScanPayload> scanPayloads;
    Integer userId;
    DDPInstanceDto ddpInstanceDto;

    public KitPayload(List<? extends ScanPayload> scanPayloads, Integer userId, DDPInstanceDto ddpInstanceDto) {
        this.scanPayloads = scanPayloads;
        this.userId = userId;
        this.ddpInstanceDto = ddpInstanceDto;
    }
}
