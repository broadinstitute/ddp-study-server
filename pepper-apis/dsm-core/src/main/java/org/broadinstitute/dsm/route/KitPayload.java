package org.broadinstitute.dsm.route;

import java.util.List;

import lombok.AllArgsConstructor;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;

@AllArgsConstructor
public class KitPayload {

    List<ScanPayload> scanPayloads;
    Integer userId;
    DDPInstanceDto ddpInstanceDto;

}
