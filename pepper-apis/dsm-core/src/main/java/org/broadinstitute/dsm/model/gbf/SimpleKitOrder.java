package org.broadinstitute.dsm.model.gbf;

public class SimpleKitOrder {

    private final Address recipientAddress;

    private final String externalKitOrderNumber;

    private final String externalKitName;

    private final String participantGuid;

    public SimpleKitOrder(Address recipientAddress, String externalKitOrderNumber, String externalKitName, String participantGuid) {
        this.recipientAddress = recipientAddress;
        this.externalKitOrderNumber = externalKitOrderNumber;
        this.externalKitName = externalKitName;
        this.participantGuid = participantGuid;
    }

    public Address getRecipientAddress() {
        return recipientAddress;
    }

    public String getExternalKitOrderNumber() {
        return externalKitOrderNumber;
    }

    public String getExternalKitName() {
        return externalKitName;
    }

    public String getParticipantGuid() {
        return participantGuid;
    }
}
