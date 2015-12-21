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
import com.google.common.base.MoreObjects;
import com.google.common.collect.Sets;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

/**
 * Assembles Akka Cluster configuration from AWS based on the current EC2 instance where
 * this code is executed. The logic is as follows:
 *
 * <ul>
 *   <li>determine current instance IP via http://169.254.169.254/latest/meta-data/</li>
 *   <li>determine Auto Scaling group</li>
 *   <li>determine all Auto Scaling group instances to obtain cluster seed</li>
 *</ul>
 *
 * Most of the AWS related logic is taken from this article:
 * http://chrisloy.net/2014/05/11/akka-cluster-ec2-autoscaling.html
 */
public final class AwsConfigurationBuilder {

    private final AmazonAutoScalingClient scaling;
    private final AmazonEC2Client ec2;
    private final String awsMetaDataInstanceIdUrl;
    private int akkaClusterPort;


    public static final String DEFAULT_HTTP_META_DATA_INSTANCE_ID_URL =
                                                                  "http://169.254.169.254/latest/meta-data/instance-id";

    private static final String AKKA_CONFIG_FILE = "scoop.conf";

    private static final Logger LOGGER = LoggerFactory.getLogger(AwsConfigurationBuilder.class);

    public AwsConfigurationBuilder(final Regions regions,
                                   final int akkaClusterPort,
                                   final String awsMetaDataInstanceIdUrl) {

        checkNotNull(regions, "regions must not be null");

        checkArgument(akkaClusterPort > 999,
                "cluster port must be >= 1000. Got [akkaClusterPort=%s]",
                akkaClusterPort);

        this.awsMetaDataInstanceIdUrl = checkNotNull(awsMetaDataInstanceIdUrl,
                                                    "AWS meta data URL to retrieve instance id must not be null");

        final AWSCredentialsProvider credentials = new DefaultAWSCredentialsProviderChain();
        final Region region = Region.getRegion(regions);

        this.scaling = new AmazonAutoScalingClient(credentials);
        this.scaling.setRegion(region);

        this.ec2 = new AmazonEC2Client(credentials);
        this.ec2.setRegion(region);

        this.akkaClusterPort = akkaClusterPort;

        LOGGER.debug("built AwsConfigurationBuilder [builder={}]", this);
    }

    public AwsConfigurationBuilder(final Regions regions, final int akkaClusterPort) {
        this(regions, akkaClusterPort,  DEFAULT_HTTP_META_DATA_INSTANCE_ID_URL);
    }


    String currentInstanceId() throws IOException {
        LOGGER.debug("determining current instance id...");

        final URL url = new URL(awsMetaDataInstanceIdUrl);
        final URLConnection connection = url.openConnection();
        final InputStream in = connection.getInputStream();

        try {
            final String instanceId = IOUtils.toString(in);
            LOGGER.debug("current instance id is [currentInstanceId={}]", instanceId);
            return instanceId;
        }
        finally {
            IOUtils.closeQuietly(in);
        }
    }


    String autoScalingGroup(final String instanceId) {
        LOGGER.debug("determining autoscaling group for [instanceId={}]...", instanceId);

        final DescribeAutoScalingInstancesRequest request = new DescribeAutoScalingInstancesRequest();
        request.setInstanceIds(Sets.newHashSet(instanceId));

        final DescribeAutoScalingInstancesResult result = scaling.describeAutoScalingInstances(request);
        final String groupName = result.getAutoScalingInstances().get(0).getAutoScalingGroupName();

        LOGGER.debug("autoscaling group for [instanceId={}] is [groupName={}]", instanceId, groupName);
        return groupName;
    }


    List<String> groupInstanceIds(final String groupName) {
        LOGGER.debug("determining instance ids of group with [groupName={}]", groupName);

        final DescribeAutoScalingGroupsRequest request = new DescribeAutoScalingGroupsRequest();
        request.setAutoScalingGroupNames(Sets.newHashSet(groupName));

        final DescribeAutoScalingGroupsResult result = scaling.describeAutoScalingGroups(request);
        final List<String> instanceIds = result.getAutoScalingGroups()
                                                .get(0)
                                                .getInstances()
                                                .stream()
                                                .map(instance -> instance.getInstanceId())
                                                .collect(Collectors.toList());

        LOGGER.debug("group [groupName={}] has [instanceIds={}]", groupName, instanceIds);
        return instanceIds;
    }


    Instance instanceFromId(final String instanceId) {
        LOGGER.debug("fetching instance with [instanceId={}]", instanceId);
        final DescribeInstancesRequest request = new DescribeInstancesRequest();
        request.setInstanceIds(Sets.newHashSet(instanceId));
        final DescribeInstancesResult result = ec2.describeInstances(request);
        return result.getReservations().get(0).getInstances().get(0);
    }


    String currentIp() {
        try {
            LOGGER.debug("determining current IP...");
            final String currentInstanceId = currentInstanceId();
            final String currentIp = instanceFromId(currentInstanceId).getPrivateIpAddress();
            LOGGER.debug("current IP is [currentIp={}]", currentIp);
            return currentIp;
        }
        catch(final Exception e) {
            throw new ConfigException(e);
        }
    }


    List<String> siblingIps(final String instanceId) {
        LOGGER.debug("determining siblings of [instanceId={}]", instanceId);

        final String groupName = autoScalingGroup(instanceId);
        final List<String> instanceIds = groupInstanceIds(groupName);
        final List<String> siblings = instanceIds
                                        .stream()
                .map(instId -> instanceFromId(instId))
                .filter(instance -> Objects.equals(instance.getState().getName(), InstanceStateName.Running.toString()))
                .map(instance -> instance.getPrivateIpAddress())
                                        .collect(Collectors.toList());

        LOGGER.debug("found [siblings={}] of [instanceId={}]", siblings, instanceId);
        return siblings;
    }


    List<String> seeds() {
        try {
            LOGGER.debug("determining seed...");
            List<String> seeds = siblingIps(currentInstanceId())
                    .stream()
                    .map(ip -> format("akka.tcp://scoop-system@%s:%s", ip, akkaClusterPort))
                                    .collect(Collectors.toList());


            LOGGER.debug("determined [seed={}]", seeds);
            return seeds;
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
                .withFallback(ConfigFactory.load(AKKA_CONFIG_FILE));
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("scaling", scaling)
                .add("ec2", ec2)
                .add("akkaClusterPort", akkaClusterPort)
                .toString();
    }
}
