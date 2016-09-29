package com.gruegames.lambda_cloudwatch_ec2_events.helper;

import com.gruegames.lambda_cloudwatch_ec2_events.alerts.AlertHandler;
import com.gruegames.lambda_cloudwatch_ec2_events.alerts.slack.SlackAlertImpl;
import com.gruegames.lambda_cloudwatch_ec2_events.ec2.EC2InstanceHandler;
import com.gruegames.lambda_cloudwatch_ec2_events.ec2.salt.SaltEC2InstanceImpl;

public class ContainerHelper {
    private static ContainerHelper INSTANCE;

    private ContainerHelper() {
        registerAlerts();
        registerEC2Handlers();
    }

    public static ContainerHelper getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ContainerHelper();
        }

        return INSTANCE;
    }

    private void registerAlerts()
    {
        AlertHandler.register(SlackAlertImpl.class);
    }

    private void registerEC2Handlers()
    {
        EC2InstanceHandler.register(SaltEC2InstanceImpl.class);
    }
}
