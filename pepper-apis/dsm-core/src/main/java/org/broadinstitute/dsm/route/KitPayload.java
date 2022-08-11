package org.broadinstitute.dsm.route;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;

@AllArgsConstructor
@Getter
public class KitPayload {

    List<ScanPayload> scanPayloads;
    Integer userId;
    DDPInstanceDto ddpInstanceDto;

}
