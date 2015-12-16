package de.zalando.scoop;


import akka.actor.ActorSystem;
import com.amazonaws.regions.Regions;
import com.google.common.base.MoreObjects;
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
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
    private final List<String> seeds;

    private static final int DEFAULT_CLUSTER_PORT = 2551;
    private static final int DEFAULT_INSTANCE_PORT = DEFAULT_CLUSTER_PORT;
    private static final Logger LOGGER = LoggerFactory.getLogger(Scoop.class);

    public Scoop() {
        this.clusterPort = DEFAULT_CLUSTER_PORT;
        this.hasAwsConfig = false;
        this.region = Regions.EU_WEST_1;
        this.port = DEFAULT_INSTANCE_PORT;
        this.scoopClient = new ScoopClientImpl();
        this.listeners = Sets.newHashSet(scoopClient);
        this.seeds = Lists.newArrayList();
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
        this.awsMetaDataInstanceIdUrl = DEFAULT_HTTP_META_DATA_INSTANCE_ID_URL;
        return this;
    }

    public Scoop withAwsConfig(final String awsMetaDataInstanceIdUrl) {
        this.hasAwsConfig = true;
        this.awsMetaDataInstanceIdUrl = checkNotNull(awsMetaDataInstanceIdUrl,
                                    "AWS meta data URL to retreive instance id must not be null");
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
        this.bindHostName = checkNotNull(bindHostName, "host name to bind to must not be null");
        return this;
    }

    public Scoop withSeeds(final List<String> seeds){
        checkNotNull(seeds, "list of seed nodes must not be null");
        checkArgument(!seeds.isEmpty(), "list of seeds must not be empty");
        this.seeds.addAll(seeds);
        return this;
    }

    public Scoop withSeed(final String seed){
        checkArgument(isNullOrEmpty(seed), "seed must not be null or empty");
        this.seeds.add(seed);
        return this;
    }

    public ScoopClient defaultClient() {
        return this.scoopClient;
    }

    public ActorSystem build() {
        LOGGER.debug("Building ActorSystem with [scoop={}]", this);

        checkState(bindHostName != null, "host name to bind to is null -> use withBindHostName(\"myHostName\")");

        Config config;
        if(hasAwsConfig) {
            checkState(seeds.isEmpty(), "CONFLICT! [seeds=%s] but automatic AWS configuration is activated", seeds);
            final AwsConfigurationBuilder builder = new AwsConfigurationBuilder(region,
                                                                                clusterPort,
                                                                                awsMetaDataInstanceIdUrl);
            config = builder.build();
        }
        else {
            // TODO could be done nicer e.g. suitable seeds are generated out of list of IPs
            config = ConfigFactory
                    .load()
                    .withValue("akka.cluster.seed-nodes", ConfigValueFactory.fromIterable(seeds));
        }

        config = config.withValue("akka.remote.netty.tcp.port",
                                         ConfigValueFactory.fromAnyRef(String.valueOf(port)))
                       .withValue("akka.remote.netty.tcp.bind-hostname",
                               ConfigValueFactory.fromAnyRef(bindHostName))
                       .withValue("akka.remote.netty.tcp.bind-port",
                               ConfigValueFactory.fromAnyRef(String.valueOf(port)));




        LOGGER.debug("using akka configuration [config={}]", config);

        final ActorSystem system = ActorSystem.create("scoop-system",  config);
        system.actorOf(ScoopActor.props(listeners), "scoop-actor");

        LOGGER.debug("built ActorSystem with [scoop={}]", this);

        return system;
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
                .toString();
    }
}
