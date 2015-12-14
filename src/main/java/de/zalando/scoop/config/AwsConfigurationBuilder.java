package de.zalando.scoop.config;


import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsResult;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingInstancesRequest;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingInstancesResult;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.*;
import com.google.common.collect.Sets;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

/*
http://chrisloy.net/2014/05/11/akka-cluster-ec2-autoscaling.html
 */
public final class AwsConfigurationBuilder {

    private final AmazonAutoScalingClient scaling;
    private final AmazonEC2Client ec2;
    private int akkaClusterPort;

    private static final String HTTP_META_DATA_INSTANCE_ID_URL = "http://169.254.169.254/latest/meta-data/instance-id";


    public AwsConfigurationBuilder(final Regions regions, final int akkaClusterPort) {
        checkNotNull(regions, "regions must not be null");

        checkArgument(akkaClusterPort > 999,
                      "cluster port must be >= 1000. Got [akkaClusterPort=%s]",
                      akkaClusterPort);



        final AWSCredentialsProvider credentials = new DefaultAWSCredentialsProviderChain();
        final Region region = Region.getRegion(regions);

        this.scaling = new AmazonAutoScalingClient(credentials);
        this.scaling.setRegion(region);

        this.ec2 = new AmazonEC2Client(credentials);
        this.ec2.setRegion(region);

        this.akkaClusterPort = akkaClusterPort;
    }


    public String currentInstanceId() throws IOException {
        final URL url = new URL(HTTP_META_DATA_INSTANCE_ID_URL);
        final URLConnection connection = url.openConnection();
        final InputStream in = connection.getInputStream();

        try {
            return IOUtils.toString(in);
        }
        finally {
            IOUtils.closeQuietly(in);
        }
    }


    public String autoScalingGroup(final String instanceId) {
        final DescribeAutoScalingInstancesRequest request = new DescribeAutoScalingInstancesRequest();
        request.setInstanceIds(Sets.newHashSet(instanceId));

        final DescribeAutoScalingInstancesResult result = scaling.describeAutoScalingInstances(request);
        return result.getAutoScalingInstances().get(0).getAutoScalingGroupName();
    }


    public List<String> groupInstanceIds(final String groupName) {

        final DescribeAutoScalingGroupsRequest request = new DescribeAutoScalingGroupsRequest();
        request.setAutoScalingGroupNames(Sets.newHashSet(groupName));

        final DescribeAutoScalingGroupsResult result = scaling.describeAutoScalingGroups(request);
        return result.getAutoScalingGroups()
                     .get(0)
                     .getInstances()
                     .stream()
                     .map(instance -> instance.getInstanceId())
                     .collect(Collectors.toList());
    }


    public Instance instanceFromId(final String id) {
        final DescribeInstancesRequest request = new DescribeInstancesRequest();
        request.setInstanceIds(Sets.newHashSet(id));
        final DescribeInstancesResult result = ec2.describeInstances(request);
        return result.getReservations().get(0).getInstances().get(0);
    }


    String currentIp() {
        try {
            final String currentInstanceId = currentInstanceId();
            return instanceFromId(currentInstanceId).getPrivateIpAddress();
        }
        catch(final Exception e) {
            throw new ConfigException(e);
        }
    }


    public List<String> siblingIps(final String instanceId) {
        final String groupName = autoScalingGroup(instanceId);
        final List<String> instanceIds = groupInstanceIds(groupName);
        return  instanceIds
                .stream()
                .map(instId -> instanceFromId(instId))
                .filter(instance -> Objects.equals(instance.getState().getName(), InstanceStateName.Running.toString()))
                .map(instance -> instance.getPrivateIpAddress())
                .collect(Collectors.toList());
    }


    List<String> seeds() {
        try {
            return siblingIps(currentInstanceId())
                    .stream()
                    .map(ip -> format("akka.tcp://scoop-system@%s:25551", ip))
                    .collect(Collectors.toList());
        }
        catch(final IOException e) {
            throw new ConfigException(e);
        }
    }

    public Config build() {
        return ConfigFactory
                .empty()
                .withValue("akka.remote.netty.tcp.hostname", ConfigValueFactory.fromAnyRef(currentIp()))
                .withValue("akka.cluster.seed-nodes", ConfigValueFactory.fromIterable(seeds()))
                .withFallback(ConfigFactory.load());
    }


}
