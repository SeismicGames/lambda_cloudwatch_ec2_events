package com.gruegames.lambda_cloudwatch_ec2_events.ec2;

import com.gruegames.lambda_cloudwatch_ec2_events.alerts.AlertHandler;
import com.gruegames.lambda_cloudwatch_ec2_events.enums.EC2State;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.util.ArrayList;

public class EC2InstanceHandler {
    private static final Logger logger = LogManager.getLogger(EC2InstanceHandler.class);
    private static ArrayList<EC2InstanceEvent> instanceList = new ArrayList<>();

    private EC2InstanceHandler() {}

    public static void register(Class<? extends EC2InstanceEvent> clazz) {
        try {
            instanceList.add(clazz.newInstance());
        } catch (IllegalAccessException | InstantiationException e) {
            logger.error(String.format("Could not register instance impl %s", clazz.getSimpleName()), e);
        }
    }

    public static void processEvent(String state, String instanceId, String region) {
        EC2State ec2State = EC2State.getFromString(state);

        switch (ec2State) {
            case pending:
                for(EC2InstanceEvent instance : instanceList) {
                    instance.pending(instanceId, region);
                }
                break;
            case running:
                for(EC2InstanceEvent instance : instanceList) {
                    instance.running(instanceId, region);
                }
                break;
            case stopping:
                for(EC2InstanceEvent instance : instanceList) {
                    instance.stopping(instanceId, region);
                }
                break;
            case stopped:
                for(EC2InstanceEvent instance : instanceList) {
                    instance.stopped(instanceId, region);
                }
                break;
            case shuttingdown:
                for(EC2InstanceEvent instance : instanceList) {
                    instance.shuttingDown(instanceId, region);
                }
                break;
            case terminated:
                for(EC2InstanceEvent instance : instanceList) {
                    instance.terminated(instanceId, region);
                }
                break;
            case unknown:
                AlertHandler.alert(Level.ERROR, instanceId,
                        String.format("Unknown instance event: %s id: %s region: %s", state, instanceId, region));
                break;
        }
    }
}
