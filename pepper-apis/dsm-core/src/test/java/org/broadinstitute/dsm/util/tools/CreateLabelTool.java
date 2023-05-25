package org.broadinstitute.dsm.util.tools;

import com.easypost.exception.EasyPostException;
import com.easypost.model.Address;
import com.easypost.model.Parcel;
import com.easypost.model.Shipment;
import org.broadinstitute.dsm.model.ddp.DDPParticipant;
import org.broadinstitute.dsm.util.EasyPostUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateLabelTool {

    private static final Logger logger = LoggerFactory.getLogger(CreateLabelTool.class);

    public static void main(String[] args) {
        //select the method you want to run!
                createEasyPostLabel();
        //        getAddressFromID();
        //        verifyAddress();
    }

    public static void createEasyPostLabel() {
        String apiKey = "MSEFjvz6YEdV3cpemY3gGg"; //in vault file!
        String billingRef = null; // or set to null if you don't want a billing reference
        String carrier = "FedEx"; //FedEx
        String carrierID = "ca_15d3f957f78747e0b14bc21e4543d08e"; //in dsm db!
        String service = "FEDEX_2_DAY"; //FEDEX_2_DAY

        DDPParticipant participant = new DDPParticipant();
        participant.setFirstName("P");
        participant.setLastName("T");
        participant.setStreet1("1178 Stone Gate turn");
        participant.setStreet2("");
        participant.setPostalCode("19060-1840");
        participant.setCity("Garnet Valley");
        participant.setState("PA");
        participant.setCountry("US");

        try {
            EasyPostUtil easyPostUtil = new EasyPostUtil(null, apiKey);
            Address toAddress = easyPostUtil.createAddress(participant, "413-404-9083");// pegah's email
            Address returnAddress =
                    easyPostUtil.createBroadAddress("Broad Institute", "320 Charles St - Lab 181", "Attn. Broad Genomics", "Cambridge",
                            "02141", "MA", "US", "617-714-8952");
            // inches and oz
            Parcel parcel = easyPostUtil.createParcel("8", "2", "7.3", "9.5"); //stool kit sizes
            logger.info("Going to buy label");
            Shipment shipment2Participant =
                    easyPostUtil.buyShipment(carrier, carrierID, service, toAddress, returnAddress, parcel, billingRef, null);
            logger.info(shipment2Participant.getLabelUrl());
        } catch (EasyPostException e) {
            logger.error(e.toString());
        }
    }

    public static void getAddressFromID() {
        String apiKey = "API_KEY_EASYPOST";
        String easyPostAddressId = "ADDRESS_ID";
        try {
            EasyPostUtil easyPostUtil = new EasyPostUtil(null, apiKey);
            Address address = easyPostUtil.getAddress(easyPostAddressId);
            logger.info(address.getName());
        } catch (EasyPostException e) {
            logger.error(e.toString());
        }
    }

    public static void verifyAddress() {
        String apiKey = "API_KEY_EASYPOST";

        DDPParticipant participant = new DDPParticipant();
        participant.setFirstName("S");
        participant.setLastName("M");
        participant.setStreet1("415 Main St");
        participant.setStreet2("Floor 7");
        participant.setPostalCode("02142");
        participant.setCity("Cambridge");
        participant.setState("MA");
        participant.setCountry("USA");
        try {
            EasyPostUtil easyPostUtil = new EasyPostUtil(null, apiKey);
            Address address = easyPostUtil.createAddress(participant, "617-714-8952");
            Address veri = address.verify(apiKey);
            logger.info(veri.getName());
        } catch (EasyPostException e) {
            logger.error(e.toString());
        }
    }
}
