package org.broadinstitute.dsm.kits;

import com.easypost.EasyPost;
import com.easypost.model.Address;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * One-off script to copy addresses from old easypost account to new easypost account
 * for kits that are in-process and have not yet been sent out.
 *
 * To get a list of kits to copy, run the following:
 select k.dsm_kit_id, k.easypost_return_id,
 from_unixtime(k.scan_date/1000) scanned_at, from_unixtime(kr.created_date/1000) created_at,
 i.instance_name, k.easypost_address_id_to, k.easypost_tracking_return_url, k.kit_complete
 from ddp_kit_request kr, ddp_kit k, ddp_instance i
 where
 i.ddp_instance_id = kr.ddp_instance_id
 and
 kr.dsm_kit_request_id = k.dsm_kit_request_id
 and (
 ((k.easypost_tracking_to_url is null or k.tracking_to_id is null) and not k.kit_complete and
 k.easypost_address_id_to is not null)
 )
 *
 * And then copy paste the ids into the main() below.  Copy/paste the sql update statements from stdout
 * and run them in the db to apply the update.
 */
public class CopyAddressToNewEasyPostAccountTest {

    private static final Logger logger = LoggerFactory.getLogger(CopyAddressToNewEasyPostAccountTest.class);


    public static void main(String[] args) throws Exception {
        CopyAddressToNewEasyPostAccountTest copier = new CopyAddressToNewEasyPostAccountTest();
        copier.copyAddressIdFromOldAccountToNewAccount(1312, "adr_19e9ecc4033611efa8c2ac1f6bc539ae");
        copier.copyAddressIdFromOldAccountToNewAccount(1278, "adr_19e9ecc4033611efa8c2ac1f6bc539ae");
    }

    // todo move me to a test, run with list of kit request ids
    public void copyAddressIdFromOldAccountToNewAccount(int dsmKitId, String oldToAddressId) throws Exception {
        String currentAccountKey = System.getenv("currentEasyPostApiKey");
        String oldAccountKey = System.getenv("oldEasyPostApiKey");
        EasyPost.apiKey = oldAccountKey;
        Address oldAddress = Address.retrieve(oldToAddressId);
        Address newAddress = null;

        EasyPost.apiKey = currentAccountKey;
        logger.info("Will query address using old api key {} and copy it with current api key {}", oldAccountKey, currentAccountKey);

        Map<String, Object> toAddressMap = new HashMap<>();
        toAddressMap.put("name", oldAddress.getName());
        toAddressMap.put("street1", oldAddress.getStreet1());
        toAddressMap.put("street2", oldAddress.getStreet2());
        toAddressMap.put("city", oldAddress.getCity());
        toAddressMap.put("state", oldAddress.getState());
        toAddressMap.put("zip", oldAddress.getZip());
        toAddressMap.put("country", oldAddress.getCountry());
        toAddressMap.put("company", oldAddress.getCompany());
        toAddressMap.put("phone", oldAddress.getPhone());
        toAddressMap.put("residential", true);
        newAddress = Address.create(toAddressMap);


        logger.info("Old address {} is being replaced with new address {} for kit id {}.", oldToAddressId, newAddress.getId(), dsmKitId);

        logger.info(oldAddress.getName() + "->" + newAddress.getName());
        logger.info(oldAddress.getStreet1() + "->" + newAddress.getStreet1());
        logger.info(oldAddress.getStreet2() + "->" + newAddress.getStreet2());
        logger.info(oldAddress.getCity() + "->" + newAddress.getCity());
        logger.info(oldAddress.getState() + "->" + newAddress.getState());
        logger.info(oldAddress.getZip() + "->" + newAddress.getZip());
        logger.info(oldAddress.getCountry() + "->" + newAddress.getCountry());
        logger.info(oldAddress.getCompany() + "->" + newAddress.getCompany());
        logger.info(oldAddress.getPhone() + "->" + newAddress.getPhone());
        logger.info(oldAddress.getResidential() + "->" + newAddress.getResidential());

        logger.info("update ddp_kit set easypost_address_id_to = '" + newAddress.getId() + "' where dsm_kit_id = " + dsmKitId + ";");
    }
}
