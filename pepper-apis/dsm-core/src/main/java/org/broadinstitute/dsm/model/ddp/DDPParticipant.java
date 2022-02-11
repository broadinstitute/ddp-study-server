package org.broadinstitute.dsm.model.ddp;

import lombok.Data;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.util.DeliveryAddress;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.util.DDPRequestUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Data
public class DDPParticipant {

    private static final Logger logger = LoggerFactory.getLogger(DDPParticipant.class);

    private String participantId;
    private String firstName;
    private String lastName;
    private String mailToName;
    private String country;
    private String city;
    private String postalCode;
    private String street1;
    private String street2;
    private String state;
    private String shortId;
    private String legacyShortId;
    private DeliveryAddress address;

    public DDPParticipant() {}

    public DDPParticipant(String shortId, String legacyShortId, String firstName, String lastName) {
        this.shortId = shortId;
        this.legacyShortId = legacyShortId;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public DDPParticipant(String shortId, String legacyShortId, String firstName, String lastName, DeliveryAddress address) {
        this.shortId = shortId;
        this.legacyShortId = legacyShortId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.address = address;
    }

    public DDPParticipant(String participantId, String firstName, String lastName, String country, String city, String postalCode, String street1, String street2, String state, String shortId, String legacyShortId) {
        this(shortId, legacyShortId, firstName, lastName);
        this.participantId = participantId;
        this.country = country;
        this.city = city;
        this.postalCode = postalCode;
        this.street1 = street1;
        this.street2 = street2;
        this.state = state;
    }

    public String getShortId() {
        if (StringUtils.isNotBlank(shortId)) {
            return shortId;
        }
        return participantId;
    }

    public static DDPParticipant getDDPParticipant(@NonNull String baseUrl, @NonNull String name, @NonNull String ddpParticipantId, boolean auth0Token) {
        DDPParticipant ddpParticipant;
        try {
            String ddpParticipantEndpoint = baseUrl + RoutePath.DDP_PARTICIPANTS_PATH + "/" + ddpParticipantId;
            ddpParticipant = DDPRequestUtil.getResponseObject(DDPParticipant.class, ddpParticipantEndpoint, name, auth0Token);
        }
        catch (Exception e) {
            throw new RuntimeException("Error getting ddp participant w/ id " + ddpParticipantId, e);
        }
        logger.info("Got participant from " + name + " w/ id " + ddpParticipantId);
        return ddpParticipant;
    }
}
