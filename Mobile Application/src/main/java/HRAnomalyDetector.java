package com.example.geosafe;

import java.util.ArrayDeque;
import java.util.Deque;

public class HRAnomalyDetector {

    // =========================================================
    // SETTINGS
    // =========================================================

    // Number of recent heart-rate readings to remember
    private static final int WINDOW_SIZE = 15;

    // Basic abnormal heart-rate limits
    private static final int HIGH_THRESHOLD = 120;
    private static final int LOW_THRESHOLD = 40;

    // Sudden increase threshold
    // 0.25 = 25%
    private static final double SPIKE_PERCENT = 0.25;

    // Store recent readings
    private final Deque<Integer> recentReadings =
            new ArrayDeque<>();


    // =========================================================
    // ADD NEW HEART-RATE READING
    // =========================================================

    public AnomalyResult addReading(int heartRate) {

        // -----------------------------------------------------
        // Ignore invalid readings
        // 0 normally means no valid sensor reading
        // -----------------------------------------------------

        if (heartRate <= 0) {

            return new AnomalyResult(
                    false,
                    "Invalid heart-rate reading",
                    heartRate,
                    getAverage()
            );
        }


        // Calculate average BEFORE adding current reading
        double averageBefore = getAverage();


        // =====================================================
        // RULE 1: HEART RATE TOO HIGH
        // =====================================================

        if (heartRate >= HIGH_THRESHOLD) {

            recordReading(heartRate);

            return new AnomalyResult(
                    true,
                    "Heart rate too high: "
                            + heartRate
                            + " BPM",
                    heartRate,
                    averageBefore
            );
        }


        // =====================================================
        // RULE 2: HEART RATE TOO LOW
        // =====================================================

        if (heartRate <= LOW_THRESHOLD) {

            recordReading(heartRate);

            return new AnomalyResult(
                    true,
                    "Heart rate too low: "
                            + heartRate
                            + " BPM",
                    heartRate,
                    averageBefore
            );
        }


        // =====================================================
        // RULE 3: SUDDEN HEART-RATE SPIKE
        // =====================================================

        // Wait until at least 5 readings are available
        // before using the rolling average.

        if (recentReadings.size() >= 5
                && averageBefore > 0) {

            double increase =
                    (heartRate - averageBefore)
                            / averageBefore;

            if (increase >= SPIKE_PERCENT) {

                recordReading(heartRate);

                return new AnomalyResult(
                        true,
                        "Sudden HR spike: "
                                + heartRate
                                + " BPM vs average "
                                + String.format(
                                "%.1f",
                                averageBefore
                        )
                                + " BPM",
                        heartRate,
                        averageBefore
                );
            }
        }


        // =====================================================
        // NORMAL READING
        // =====================================================

        recordReading(heartRate);

        return new AnomalyResult(
                false,
                "Normal",
                heartRate,
                averageBefore
        );
    }


    // =========================================================
    // STORE READING
    // =========================================================

    private void recordReading(int heartRate) {

        recentReadings.addLast(heartRate);

        // Keep only the latest 15 readings
        if (recentReadings.size() > WINDOW_SIZE) {

            recentReadings.removeFirst();
        }
    }


    // =========================================================
    // CALCULATE ROLLING AVERAGE
    // =========================================================

    private double getAverage() {

        if (recentReadings.isEmpty()) {

            return 0.0;
        }

        double total = 0.0;

        for (int value : recentReadings) {

            total += value;
        }

        return total / recentReadings.size();
    }


    // =========================================================
    // RESULT CLASS
    // =========================================================

    public static class AnomalyResult {

        public final boolean isAnomaly;

        public final String reason;

        public final int currentHeartRate;
        public final double rollingAverage;

        public AnomalyResult(
                boolean isAnomaly,
                String reason,
                int currentHeartRate,
                double rollingAverage
        ) {

            this.isAnomaly = isAnomaly;

            this.reason = reason;

            this.currentHeartRate = currentHeartRate;

            this.rollingAverage = rollingAverage;
        }
    }
}