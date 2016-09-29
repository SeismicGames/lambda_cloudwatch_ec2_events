package com.gruegames.lambda_cloudwatch_ec2_events.alerts;

import org.apache.log4j.Level;

public interface Alert {
    void sendAlert(Level level, String instanceId, String error);
}
