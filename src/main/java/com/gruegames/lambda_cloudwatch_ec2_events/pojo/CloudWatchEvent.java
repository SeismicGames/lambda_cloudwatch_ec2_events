package com.gruegames.lambda_cloudwatch_ec2_events.pojo;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class CloudWatchEvent {
    public class Detail {
        @SerializedName("instance-id")
        public String instanceId;

        @SerializedName("state")
        public String state;
    }

    @SerializedName("version")
    public String version;

    @SerializedName("id")
    public String id;

    @SerializedName("detail-type")
    public String detailType;

    @SerializedName("source")
    public String source;

    @SerializedName("account")
    public String account;

    @SerializedName("time")
    public String time;

    @SerializedName("region")
    public String region;

    @SerializedName("resources")
    public List<String> resources;

    @SerializedName("detail")
    public Detail detail;
}