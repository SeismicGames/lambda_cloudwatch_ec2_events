package com.gruegames.lambda_cloudwatch_ec2_events;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.amazonaws.util.StringUtils;
import com.google.gson.Gson;
import com.gruegames.lambda_cloudwatch_ec2_events.alerts.AlertHandler;
import com.gruegames.lambda_cloudwatch_ec2_events.ec2.EC2InstanceHandler;
import com.gruegames.lambda_cloudwatch_ec2_events.helper.ContainerHelper;
import com.gruegames.lambda_cloudwatch_ec2_events.pojo.CloudWatchEvent;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.io.*;

public class LambdaHandler implements RequestStreamHandler {
    private static Logger logger = LogManager.getLogger(LambdaHandler.class);

    @Override
    public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {
        // initialize the lambda container
        ContainerHelper.getInstance();

        StringBuilder sb = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
        String line = br.readLine();
        while (line != null) {
            sb.append(line);
            line = br.readLine();
        }
        inputStream.close();

        CloudWatchEvent event = new Gson().fromJson(sb.toString(), CloudWatchEvent.class);
        if(StringUtils.isNullOrEmpty(event.detail.state) || StringUtils.isNullOrEmpty(event.detail.instanceId) ||
                StringUtils.isNullOrEmpty(event.region)) {
            String message = String.format("Error with event: %s", sb.toString());
            AlertHandler.alert(Level.ERROR, event.detail.instanceId, message);
            return;
        }

        EC2InstanceHandler.processEvent(event.detail.state, event.detail.instanceId, event.region);
    }
}
