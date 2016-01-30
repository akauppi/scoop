package de.zalando.scoop;


import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import com.amazonaws.regions.Regions;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import de.zalando.scoop.config.AwsConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.*;
import static com.google.common.base.Strings.isNullOrEmpty;
import static de.zalando.scoop.config.AwsConfigurationBuilder.DEFAULT_HTTP_META_DATA_INSTANCE_ID_URL;

public final class Scoop {

    private boolean hasAwsConfig;
    private int clusterPort;
    private Regions region;
    private HashSet<ScoopListener> listeners;
    private ScoopClientImpl scoopClient;
    private int port;
    private String bindHostName;
    private String awsMetaDataInstanceIdUrl;
    private final Set<String> seeds;
    private ActorRef scoopActor;

    public static final int DEFAULT_CLUSTER_PORT = 25551;
    public static final int DEFAULT_INSTANCE_PORT = DEFAULT_CLUSTER_PORT;

    private static final String AKKA_CONFIG_FILE = "scoop.conf";


    private static final Logger LOGGER = LoggerFactory.getLogger(Scoop.class);

    public Scoop() {
        this.clusterPort = DEFAULT_CLUSTER_PORT;
        this.hasAwsConfig = false;
        this.region = Regions.EU_WEST_1;
        this.port = DEFAULT_INSTANCE_PORT;
        this.awsMetaDataInstanceIdUrl = DEFAULT_HTTP_META_DATA_INSTANCE_ID_URL;
        this.scoopClient = new ScoopClientImpl();
        this.listeners = Sets.newHashSet(scoopClient);
        this.seeds = Sets.newHashSet();
    }

    boolean hasAwsConfig() {
        return hasAwsConfig;
    }

    int getClusterPort() {
        return clusterPort;
    }

    Regions getRegion() {
        return region;
    }

    Set<ScoopListener> getListeners() {
        return ImmutableSet.copyOf(listeners);
    }

    int getPort() {
        return port;
    }

    String getBindHostName() {
        return bindHostName;
    }

    String getAwsMetaDataInstanceIdUrl() {
        return awsMetaDataInstanceIdUrl;
    }

    Set<String> getSeeds() {
        return ImmutableSet.copyOf(seeds);
    }

    public Scoop withClusterPort(final int clusterPort) {
        checkArgument(clusterPort > 999,
                      "cluster port must be >= 1000. Got [clusterPort=%s]", clusterPort);
        this.clusterPort = clusterPort;
        return this;
    }

    public Scoop withPort(final int port) {
        checkArgument(port > 999, "port must be >= 1000. Got [port=%s]", port);
        this.port = port;
        return this;
    }

    public Scoop withAwsConfig() {
        this.hasAwsConfig = true;
        return this;
    }

    public Scoop withAwsConfig(final String awsMetaDataInstanceIdUrl) {
        checkArgument(!isNullOrEmpty(awsMetaDataInstanceIdUrl),
                      "AWS meta data URL to retrieve instance id must not be null or empty");

        this.hasAwsConfig = true;
        this.awsMetaDataInstanceIdUrl = awsMetaDataInstanceIdUrl;
        return this;
    }

    public Scoop withRegion(final Regions region){
        this.region = checkNotNull(region, "region must not be null");
        return this;
    }

    public Scoop withListener(final ScoopListener listener) {
        checkNotNull(listener, "ScoopListener must not be null");
        listeners.add(listener);
        return this;
    }

    public Scoop withBindHostName(final String bindHostName){
        checkArgument(!isNullOrEmpty(bindHostName), "host name to bind to must not be null");
        this.bindHostName = bindHostName;
        return this;
    }

    public Scoop withSeeds(final Set<String> seeds){
        checkNotNull(seeds, "set of seed nodes must not be null");
        checkArgument(!seeds.isEmpty(), "set of seeds must not be empty");
        checkArgument(!seeds.contains(""), "set of seed nodes contains empty entries");

        this.seeds.addAll(seeds);
        return this;
    }

    public Scoop withSeed(final String seed){
        checkArgument(! isNullOrEmpty(seed), "seed must not be null or empty");
        this.seeds.add(seed);
        return this;
    }

    public ScoopClient defaultClient() {
        return this.scoopClient;
    }


    Config prepareConfig(){
        checkState(bindHostName != null, "host name to bind to is null -> use withBindHostName(\"myHostName\")");

        Config config;
        if(hasAwsConfig) {
            checkState(seeds.isEmpty(), "CONFLICT! [seeds=%s] but automatic AWS configuration is activated", seeds);
            LOGGER.info("fetching AWS related configuration");
            final AwsConfigurationBuilder builder = new AwsConfigurationBuilder(region,
                                                                                clusterPort,
                                                                                awsMetaDataInstanceIdUrl);
            config = builder.build();
        }
        else {
            // TODO could be done nicer e.g. suitable seeds are generated out of list of IPs
            LOGGER.info("fetching configuration from current Scoop settings and {}", AKKA_CONFIG_FILE);
            config = ConfigFactory.load(AKKA_CONFIG_FILE)
                                  .withValue("akka.cluster.seed-nodes", ConfigValueFactory.fromIterable(seeds));
        }

        return config.withValue("akka.remote.netty.tcp.port",
                            ConfigValueFactory.fromAnyRef(String.valueOf(port)))
                     .withValue("akka.remote.netty.tcp.bind-hostname",
                             ConfigValueFactory.fromAnyRef(bindHostName))
                     .withValue("akka.remote.netty.tcp.bind-port",
                             ConfigValueFactory.fromAnyRef(String.valueOf(port)));

    }


    public Config buildConfiguration() {
        return prepareConfig();
    }

    public ActorRef startScoopActor(final ActorSystem system) {
        checkNotNull(system, "actor system must not be null");

        if(scoopActor == null){
            scoopActor = system.actorOf(ScoopActor.props(listeners), "scoop-actor");
        }
        else {
            LOGGER.warn("a scoop actor is already running -> returning reference to running actor");
        }

        return scoopActor;
    }


    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("hasAwsConfig", hasAwsConfig)
                .add("clusterPort", clusterPort)
                .add("region", region)
                .add("listeners", listeners)
                .add("scoopClient", scoopClient)
                .add("port", port)
                .add("bindHostName", bindHostName)
                .add("awsMetaDataInstanceIdUrl", awsMetaDataInstanceIdUrl)
                .add("seeds", seeds)
                .toString();
    }
}
