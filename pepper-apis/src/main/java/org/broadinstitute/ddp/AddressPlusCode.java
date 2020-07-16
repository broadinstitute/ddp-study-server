package org.broadinstitute.ddp;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.service.OLCService;
import org.broadinstitute.ddp.util.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AddressPlusCode {

    private final String mailingAddress;
    private static final Logger LOG = LoggerFactory.getLogger(AddressPlusCode.class);

    public AddressPlusCode(String mailingAddress) {
        this.mailingAddress = mailingAddress;
    }

    private String getAddressPluscode() {
        Config cfg = ConfigManager.getInstance().getConfig();
        String geocodingKey = cfg.getString(ConfigFile.GEOCODING_API_KEY);
        OLCService location = new OLCService(geocodingKey);
        return location.calculatePlusCodeWithPrecision(mailingAddress, location.DEFAULT_OLC_PRECISION);
    }

    public static void main(String[] args) {
        String address = "415 Main Street, Cambridge, MA 02142";
        AddressPlusCode plusCode = new AddressPlusCode(address);
        String result = plusCode.getAddressPluscode();
        LOG.info("RESULT: {}", result);
    }
}
