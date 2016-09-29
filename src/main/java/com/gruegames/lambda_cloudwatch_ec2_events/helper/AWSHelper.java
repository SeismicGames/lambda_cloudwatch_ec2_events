package com.gruegames.lambda_cloudwatch_ec2_events.helper;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.*;
import com.gruegames.lambda_cloudwatch_ec2_events.alerts.AlertHandler;
import com.gruegames.lambda_cloudwatch_ec2_events.pojo.EC2Details;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.connection.ConnectionException;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.UserAuthException;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.Security;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public class AWSHelper {
    private static Logger logger = LogManager.getLogger(AWSHelper.class);
    private static BasicAWSCredentials awsCredentials;
    private static Properties properties = new Properties();

    static {
        try {
            properties.load(AWSHelper.class.getResourceAsStream("/ec2.properties"));
        } catch (IOException e) {
            logger.error("Can't load properties file: ", e);
            throw new RuntimeException();
        }

        awsCredentials = new BasicAWSCredentials(properties.getProperty("aws.access.key.id"),
                properties.getProperty("aws.secret.access.key"));
    }

    private AWSHelper() {}

    // Get EC2 instance details from AWS
    public static EC2Details getInstanceDetails(String instanceId, String region) {
        AmazonEC2Client client = new AmazonEC2Client(awsCredentials);
        client.setRegion(Region.getRegion(Regions.fromName(region)));

        DescribeInstancesRequest request = new DescribeInstancesRequest();
        request.setInstanceIds(Collections.singletonList(instanceId));
        DescribeInstancesResult result = client.describeInstances(request);

        if(result == null) {
            throw new RuntimeException(String.format("Instance id %s was not found!", instanceId));
        }

        Instance instance = result.getReservations().get(0).getInstances().get(0);
        logger.info(String.format("Found EC2 instance: %s", instance.getInstanceId()));

        String ip = instance.getPrivateIpAddress();
        String instanceName = instance.getPrivateDnsName();
        List<Tag> tags  = instance.getTags();

        logger.info(String.format("Found instance ip %s and name %s", ip, instanceName));
        return new EC2Details(ip, instanceName, tags);
    }

    // Test the SSH connection and sleep until it is ready
    public static SSHClient waitForEC2Instance(String instanceIp, String username, String password) {
        // see if machine is up yet
        logger.info(String.format("Trying to reach %s", instanceIp));
        while (!AWSHelper.isReachable(instanceIp)) {
            try {
                logger.info("Sleeping...");
                Thread.sleep(5000);
            } catch (InterruptedException e) { }
        }

        Security.addProvider(new BouncyCastleProvider());
        SSHClient ssh = null;
        while(true) {
            try {
                ssh = new SSHClient();
                ssh.addHostKeyVerifier(new PromiscuousVerifier());
                ssh.setConnectTimeout(5000);

                logger.info(String.format("Trying to SSH into %s", instanceIp));
                ssh.connect(instanceIp);
                ssh.authPassword(username, password);

                try(Session session = ssh.startSession()) {
                    logger.info("SSH Session able to start");
                    return ssh;
                }
            } catch (ConnectionException | UserAuthException e) {
                logger.info(String.format("%s ssh is not ready, sleeping", instanceIp));

                try {
                    ssh.disconnect();
                } catch (IOException ee) {}

                try {
                    Thread.sleep(10000);
                } catch (InterruptedException ee) {}
            } catch (TransportException e) {
                logger.error("SSH Transport Exception", e);
                throw new RuntimeException(e);
            } catch (IOException e) {
                logger.error("SSH IO Exception", e);
                throw new RuntimeException(e);
            }
        }
    }

    public static void waitForUserData(String instanceIp, String instanceId, String ec2Username, String ec2Password) {
        // wait until we can access SSH
        SSHClient ssh = AWSHelper.waitForEC2Instance(instanceIp, ec2Username, ec2Password);

        // start SSH session
        logger.info("Starting ssh transactions");
        Session.Command command;

        String fileCommand = String.format("if [ -f \"/var/lib/cloud/instances/%s/boot-finished\" ]; then echo \"true\"; else echo \"false\"; fi;", instanceId);
        String fileExists;

        try {
            do {
                try (Session session = ssh.startSession()) {
                    // install salt minion
                    command = session.exec(fileCommand);
                    fileExists = IOUtils.readFully(command.getInputStream()).toString().trim();

                    if (Boolean.parseBoolean(fileExists)) {
                        logger.info("Ok, cloud-init is finished, moving on");
                    } else {
                        logger.info("cloud-init is still working, sleeping");
                        try {
                            Thread.sleep(15000);
                        } catch (InterruptedException e) { }
                    }
                }
            } while (!Boolean.parseBoolean(fileExists));

            ssh.disconnect();
        } catch (IOException e) {
            AlertHandler.alert(Level.ERROR, instanceId, "Could not SSH. Error: "+e.getLocalizedMessage());
        }
    }

    // from http://stackoverflow.com/a/34228756
    private static boolean isReachable(String addr) {
        int openPort = 22;
        int timeOutMillis = 2000;

        try {
            try (Socket soc = new Socket()) {
                soc.connect(new InetSocketAddress(addr, openPort), timeOutMillis);
            }
            return true;
        } catch (IOException ex) {
            return false;
        }
    }
}
