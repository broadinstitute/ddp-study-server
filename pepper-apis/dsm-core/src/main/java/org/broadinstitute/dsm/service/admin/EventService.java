//package org.broadinstitute.dsm.service.admin;
//
//import java.io.IOException;
//import java.sql.SQLException;
//
//import lombok.extern.slf4j.Slf4j;
//import org.broadinstitute.dsm.db.KitRequestShipping;
//import org.broadinstitute.dsm.db.dto.kit.BSPKitDto;
//
//@Slf4j
//public class EventService {
//
//    public void processEvent(String ddpLabel, EventType eventType, KitRequestShipping kitRequestShipping, BSPKitDto bspKitDto,
//                             boolean firstTimeReceived) {
//        try (Connection conn = getDatabaseConnection()) {
//            // DB updates
//            updateKitReceived(conn, eventType, ddpLabel, bspKitDto);
//            // Triggers without open transaction
//            if (eventType.shouldTriggerExternal()) {
//                triggerExternalService(bspKitDto, eventType);
//            }
//        } catch (SQLException e) {
//            log.error("Database operation failed", e);
//        }
//    }
//
//    private void updateKitReceived(Connection conn, EventType eventType, String ddpLabel, BSPKitDto bspKitDto) throws SQLException {
//        // Database update logic here
//        // Similar to your previous PreparedStatement logic
//    }
//
//    private void triggerExternalService(BSPKitDto bspKitDto, EventType eventType) {
//        int retries = 3;
//        boolean success = false;
//        while (!success && retries > 0) {
//            try {
//                // Your external POST request logic here
//                success = true; // Set based on response
//            } catch (IOException e) {
//                retries--;
//                logger.error("Failed to trigger external service, attempts remaining: " + retries, e);
//                if (retries == 0) {
//                    // Log final failure or handle it as necessary
//                }
//            }
//        }
//    }
//}
//
