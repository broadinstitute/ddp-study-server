package org.broadinstitute.ddp.client;

import java.io.IOException;

import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.errors.ApiException;
import com.google.maps.errors.InvalidRequestException;
import com.google.maps.errors.OverDailyLimitException;
import com.google.maps.errors.OverQueryLimitException;
import com.google.maps.errors.RequestDeniedException;
import com.google.maps.model.GeocodingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A client wrapper for various Google Maps services.
 *
 * <p>Google Maps has a large API surface, but since one API key can access the various Maps services, this single
 * client is intended to consolidate those API usages into one place.
 */
public class GoogleMapsClient {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleMapsClient.class);

    private final GeoApiContext geoCtx;

    public GoogleMapsClient(String apiKey) {
        // Geo context is meant to be a singleton, so build it once per api key.
        this.geoCtx = new GeoApiContext.Builder()
                .apiKey(apiKey)
                .build();
    }

    /**
     * Convert an address into geographic coordinates using the Geocoding API.
     *
     * <p>API error payload only has a status string and no HTTP status code, so this status is used to map to the
     * appropriate HTTP status code.
     *
     * <p>See https://developers.google.com/maps/documentation/geocoding/intro#StatusCodes
     *
     * @param address the full address, where components are comma-separated
     * @return geocoding results
     */
    public ApiResult<GeocodingResult[], ApiException> lookupGeocode(String address) {
        try {
            GeocodingResult[] results = GeocodingApi.geocode(geoCtx, address).await();
            return ApiResult.ok(200, results);
        } catch (RequestDeniedException | InvalidRequestException e) {
            return ApiResult.err(400, e);
        } catch (OverDailyLimitException | OverQueryLimitException e) {
            return ApiResult.err(429, e);
        } catch (ApiException e) {
            return ApiResult.err(500, e);
        } catch (InterruptedException | IOException e) {
            return ApiResult.thrown(e);
        }
    }
}
