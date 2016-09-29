package com.gruegames.lambda_cloudwatch_ec2_events.enums;

import org.apache.log4j.Logger;

public enum EC2State {
    pending,
    running,
    stopping,
    stopped,
    shuttingdown,
    terminated,
    unknown;

    public static EC2State getFromString(String state) {
        try {
            if(state.equals("shutting-down")) {
                state = "shuttingdown";
            }

            return EC2State.valueOf(state);
        } catch (IllegalArgumentException | NullPointerException e) {
            final Logger logger = Logger.getLogger(EC2State.class);
            logger.error("Invalid EC2 State: ", e);
            return EC2State.unknown;
        }
    }
}
