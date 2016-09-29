package com.gruegames.lambda_cloudwatch_ec2_events.ec2;

public interface EC2InstanceEvent {
    void pending(String instanceId, String region);

    void running(String instanceId, String region);

    void stopping(String instanceId, String region);

    void stopped(String instanceId, String region);

    void shuttingDown(String instanceId, String region);

    void terminated(String instanceId, String region);
}
