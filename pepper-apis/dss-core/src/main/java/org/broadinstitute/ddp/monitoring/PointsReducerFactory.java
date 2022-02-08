package org.broadinstitute.ddp.monitoring;

import java.util.function.BinaryOperator;

import com.google.monitoring.v3.Point;
import com.google.monitoring.v3.TimeInterval;
import com.google.monitoring.v3.TypedValue;
import com.google.protobuf.Timestamp;

public class PointsReducerFactory {

    public static final BinaryOperator<Point> buildMaxPointReducer() {
        return (Point point, Point point2) -> {
            if (point.getValue().getInt64Value() > point2.getValue().getInt64Value()) {
                return point;
            } else {
                return point2;
            }
        };
    }

    /**
     * Sums the value of the points and sets the time
     * for the point to be the average of the start times
     * @return
     */
    public static BinaryOperator<Point> buildSumReducer() {
        return (Point point, Point point2) -> {
            long avgTime = (point.getInterval().getStartTime().getSeconds() + point2.getInterval().getStartTime().getSeconds()) / 2;
            Timestamp avgTimestamp = Timestamp.newBuilder().setSeconds(avgTime).build();
            TimeInterval interval = TimeInterval.newBuilder().setStartTime(avgTimestamp).setEndTime(avgTimestamp).build();
            long sum = point.getValue().getInt64Value() + point2.getValue().getInt64Value();
            return Point.newBuilder().setInterval(interval).setValue(TypedValue.newBuilder().setInt64Value(sum).build()).build();
        };
    }
}
