# lambda_cloudwatch_ec2_events
Java application to handle Cloudwatch EC2 events through Lambda

## What it does
 
This code is a simple Java application to help you handle EC2 Cloudwatch events. The current examples in the code use Salt and Slack, but can be customized for almost anything ([See below](#customize_event))

## How to build, setup and use

### Set up the application

1. Clone the repo.

 ```
 git@github.com:gruegames/lambda_cloudwatch_ec2_events.git
 ```

2. Open `src/main/resources/ec2.properties` and enter your AWS access information.

 ```
 aws.access.key.id=<aws_access_id>
 aws.secret.access.key=<aws_secret_key>
 ```

3. Open `src/main/resources/salt.properties` and enter your Salt/EC2 user access information.

 ```
 ec2.username=<valid_ec2_username>
 ec2.password=<valid_ec2_password>
 
 salt.master.minion=<salt_minion_to_fire_event>
 salt.url=<salt_api_url>
 ```

4. Open `src/main/resources/slack.properties` and enter your Slack access information.
 
 ```
 slack.api_key=<slack_bot_token>
 slack.url=https://slack.com/api/chat.postMessage
 slack.channel=<slack_channel>
 slack.username=<slack_bot_username>
 ```

5. Run `mvn clean package` and copy `target\lambda_cloudwatch_ec2_events-1.0-SNAPSHOT.jar` to S3 and grab the URL.
  * **Note:** If you have the environment variables AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY and AWS_DEFAULT_REGION set up you can directly upload to S3 from Maven.
  
  ```
  mvn -Ds3.bucketName=<S3_bucket_name> clean install
  ```

### Set up AWS Lambda to use

1. Under "AWS -> Lambda", click "Create a Lambda Function" and kkip the 'Select blueprint' step.
2. Do not select any Trigger and click 'Next'.
![lambda_select_sns](https://d37jsnyf6dadr5.cloudfront.net/configure_triggers_cloudwatch.png)
3. Set up the 'Configure function' section.
  * **Name:** cloudwatch-ec2-event 
  * **Description:** Cloudwatch EC2 Event 
  * **Runtime:** Java 8
  
  ![configure_function_cloudwatch](https://d37jsnyf6dadr5.cloudfront.net/configure_function_cloudwatch.png)
  
4. Set up the 'Lambda function code' section.
  * **Code entry type:** Upload a file from Amazon S3
  * **S3 link URL:** The S3 URL from the jar uploaded above.
  
  ![lambda_function_code_cloudwatch](https://d37jsnyf6dadr5.cloudfront.net/lambda_function_code_cloudwatch.png)
  
3. Set up the 'Lambda function handler and role' section.
  * **Handler:** com.gruegames.lambda_cloudwatch_ec2_events.LambdaHandler.handleRequest
  * **Role:** Create new role from template(s)
  * **Role name:** cloudwatch-ec2-event-role
  * **Policy templates:** S3 object read-only permissions - Feel free to add more if you desire.
  
  ![lambda_function_handler_cloudwatch](https://d37jsnyf6dadr5.cloudfront.net/lambda_function_handler_cloudwatch.png)
  
4. Click 'Advanced settings' and set the memory and timeout to the settings below. Waiting for an EC2 instance to spin up can take some time.

 ![memory](https://d37jsnyf6dadr5.cloudfront.net/memory.png)
 
5. Select your default VPC, subnets, and security group. Make sure that your security group can talk to the default VPC security group.
6. Click next and then 'Create Function' on the next page.

### Set up Cloudwatch EC2 Events

1. Go to "AWS -> Cloudwatch -> Events" and click "Create Rule".
2. Set up "Event selector" as follows:
  * **Any state**
  * **Any instance**
  
  ![event_selector](https://d37jsnyf6dadr5.cloudfront.net/event_selector.png)
  
3. Set up "Targets" as follows:
  * **Lambda Function**
  * **Function:** cloudwatch-ec2-event
  
  ![targets](https://d37jsnyf6dadr5.cloudfront.net/targets.png)
  
4. Click 'Configure Details'
  * **Name:** ec2-lambda-events
  * **Description:** EC2 Lambda Events
  * **Enabled:** Yes
  
  ![rule_definition](https://d37jsnyf6dadr5.cloudfront.net/rule_definition.png)
  
5. Click 'Create Rule'

### Set up an AWS EC2 Auto Scaling Group

1. Under 'AWS -> EC2 -> Launch Configurations', click 'Create Auto Scaling Group'.
2. Click 'Create Launch Configuration'.
3. Select your AMI (I prefer Ubuntu Server 14.04).
4. For instance type use 't2.micro' and click 'Next'.
5. Name your launch configuration and click 'Next'.
  * **Note:** you will probably want to also assign an IAM role as well. At least for your EC2 instances to access S3.
  
  ![launch_configuration](https://d37jsnyf6dadr5.cloudfront.net/launch_configuration.png)
  
6. Click next unless you want to add storage.
7. Select the default VPC security group and click 'Review'.
8. Click 'Create launch configuration'.
9. Select your default key pair. If you do not have one create one and download it.
10. Give your ASG a name, and select your default VPC and subnets to match the ones above. Click 'Next'.
11. Leave 'Keep this group at its initial size' selected and click 'Next: Configure Notifications'.
  * **Note:** you can always change this later.
12. Add the following tags you want and click 'Review'. (I'll go into tags in a later post.)
  * **Name** 
  * **Role**
  * **Environment**
  * **Project**
13. Launch the Auto Scaling Group.

To see the output, go to 'Lambda -> ec2-sns-scaling-event -> Monitoring' and click on 'View logs in CloudWatch'.

## Customize the event handler<a name="customize_event"></a>

I've included the class SaltEC2InstanceImpl as an example event handler, one that sends events to the Salt Master once an EC2 instance starts or stops. But you can add your own event handler.

1. Implement the class `EC2Instance` in your event class.

  ```java
  package com.gruegames.lambda_cloudwatch_ec2_events.ec2.example;
  
  import com.gruegames.lambda_cloudwatch_ec2_events.ec2.EC2InstanceEvent;
  
  public class EventExampleImpl implements EC2InstanceEvent {
      @Override
      public void pending(String instanceId, String region) {
          /* code to handle a pending event */
      }
      
      @Override
      public void running(String instanceId, String region) {
          /* code to handle a running event */
      }
      
      @Override
      public void stopping(String instanceId, String region) {
          /* code to handle a stopping event */
      }
      
      @Override
      public void stopped(String instanceId, String region) {
          /* code to handle a stopped event */
      }
      
      @Override
      public void shuttingDown(String instanceId, String region) {
          /* code to handle a shutting-down event */
      }
      
      @Override
      public void terminated(String instanceId, String region) {
          /* code to terminated a pending event */
      }
  }
  ```

2. Register the new class in `ContainerHelper.registerEC2Handlers`

  ```java
  private void registerEC2Handlers() {
      EC2InstanceHandler.register(SaltEC2InstanceImpl.class);
      /* ... */
      EC2InstanceHandler.register(EventExampleImpl.class);
  }
  ```

## Customize the alarm handler<a name="customize_alarm"></a>

The alert handler work just like the event handler.

1. Implement the class `Alert` in your event class.

  ```java
  package com.gruegames.lambda_cloudwatch_ec2_events.alerts.example;
  
  import com.gruegames.lambda_cloudwatch_ec2_events.alerts.Alert;
  import org.apache.log4j.Level;
  
  public class AlertExampleImpl implements Alert {
      @Override
      public void sendAlert(Level level, String instanceId, String error) {
          /* code to handle an alert, like send an email */
      }
  }
  
  ```

2. Register the new class in `ContainerHelper.registerAlerts`

  ```java
  private void registerAlerts()
       {
           AlertHandler.register(SlackAlertImpl.class);
           /* ... */
           AlertHandler.register(AlertExampleImpl.class);
       }
  ```