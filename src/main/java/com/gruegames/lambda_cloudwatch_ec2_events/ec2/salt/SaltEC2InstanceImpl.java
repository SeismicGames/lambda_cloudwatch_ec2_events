package com.gruegames.lambda_cloudwatch_ec2_events.ec2.salt;

import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.util.StringUtils;
import com.google.gson.Gson;
import com.gruegames.lambda_cloudwatch_ec2_events.ec2.EC2InstanceEvent;
import com.gruegames.lambda_cloudwatch_ec2_events.alerts.AlertHandler;
import com.gruegames.lambda_cloudwatch_ec2_events.helper.AWSHelper;
import com.gruegames.lambda_cloudwatch_ec2_events.pojo.*;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.connection.channel.direct.Session;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

public class SaltEC2InstanceImpl implements EC2InstanceEvent {
    private static final Logger logger = Logger.getLogger(SaltEC2InstanceImpl.class);

    private Properties properties = new Properties();
    private Client client = ClientBuilder.newClient();
    private String baseUrl;
    private String saltMasterMinion;
    private String ec2Username;
    private String ec2Password;

    public SaltEC2InstanceImpl() throws IOException {
        try {
            properties.load(SaltEC2InstanceImpl.class.getResourceAsStream("/salt.properties"));
        } catch (IOException e) {
            logger.error("Can't load properties file: ", e);
            throw e;
        }

        baseUrl = properties.getProperty("salt.url");
        saltMasterMinion = properties.getProperty("salt.master.minion");
        ec2Username = properties.getProperty("ec2.username");
        ec2Password = properties.getProperty("ec2.password");
    }

    // Initial EC2 instance setup with Salt
    @Override
    public void running(String instanceId, String region) {
        EC2Details details = AWSHelper.getInstanceDetails(instanceId, region);
        if(details.instanceName == null || details.instanceIp == null) {
            throw new RuntimeException(String.format("Instance id %s has no name!", instanceId));
        }

        AWSHelper.waitForUserData(details.instanceIp, instanceId, ec2Username, ec2Password);

        // login to salt
        String token = saltLogin(instanceId);
        SaltPayload payload = new SaltPayload("event.send", "local", saltMasterMinion,
                new SaltPayload.Job("arg", String.format("tag=salt/minion/ec2/%s/auth", instanceId)));
        makeSaltCall(instanceId, token, null, payload);
    }

    // Remove EC2 instance from Salt
    @Override
    public void terminated(String instanceId, String region) {
        EC2Details details = AWSHelper.getInstanceDetails(instanceId, region);
        Map<String, String > tags = details.tags.stream()
                .collect(Collectors.toMap(Tag::getKey, Tag::getValue));

        String tag = String.format("tag=salt/minion/ec2/%s/terminated", instanceId);
        String data = String.format("data=%s", new Gson().toJson(tags));

        String token = saltLogin(instanceId);

        SaltPayload payload = new SaltPayload("event.send", "local", saltMasterMinion, new SaltPayload.Job("arg", tag),
                new SaltPayload.Job("arg", data));
        makeSaltCall(instanceId, token, null, payload);

        payload = new SaltPayload("key.delete", "wheel", "'*'", new SaltPayload.Job("match", instanceId));
        makeSaltCall(instanceId, token, null, payload);
    }

    @Override
    public void pending(String instanceId, String region) {
        // Salt doesn't care about pending state
    }

    @Override
    public void stopping(String instanceId, String region) {
        // Salt doesn't care about stopping state
    }

    @Override
    public void stopped(String instanceId, String region) {
        // Salt doesn't care about stopped state
    }

    @Override
    public void shuttingDown(String instanceId, String region) {
        // Salt doesn't care about shuttingDown state
    }

    // Salt login helper
    private String saltLogin(String instanceId) {
        logger.info("Starting Salt login flow");

        Form form = new Form();
        form.param("username", properties.getProperty("ec2.username"));
        form.param("password", properties.getProperty("ec2.password"));
        form.param("eauth", "pam");

        Response response = client.target(baseUrl)
                .path("login")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE));

        if(response.getStatus() != 200) {
            String message = String.format("Can't authenicate with Salt: code %s", response.getStatus());
            AlertHandler.alert(Level.ERROR, instanceId, message);
            throw new RuntimeException(message);
        }

        String body = response.readEntity(String.class);
        SaltLogin saltLogin = new Gson().fromJson(body, SaltLogin.class);

        if(saltLogin.returns.size() < 1) {
            String message = String.format("No token returned from Salt! Body: %s", body);
            AlertHandler.alert(Level.ERROR, instanceId, message);
            throw new UnsupportedOperationException(message);
        }

        String token = saltLogin.returns.get(0).token;
        logger.info("Logged into salt successfully");

        return token;
    }

    // Salt API helper
    private String makeSaltCall(String instanceId, String token, String path, SaltPayload payload) {
        if(StringUtils.isNullOrEmpty(path)) {
            path = "/";
        }

        Response response = client.target(baseUrl)
                .path(path)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .header("X-Auth-Token", token)
                .post(Entity.entity(payload.getFormFromPayload(), MediaType.APPLICATION_FORM_URLENCODED_TYPE));

        if(response.getStatus() != 200) {
            AlertHandler.alert(Level.ERROR, instanceId, String.format("Failed to update Salt! Code: %s", response.getStatus()));
            throw new RuntimeException();
        } else {
            String result = response.readEntity(String.class);
            logger.debug(String.format("Event fired to Salt. Response: %s", result));
            return result;
        }
    }
}
