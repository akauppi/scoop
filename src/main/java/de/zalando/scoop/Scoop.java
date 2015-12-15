package de.zalando.scoop;


import akka.actor.ActorSystem;
import com.amazonaws.regions.Regions;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Sets;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import de.zalando.scoop.config.AwsConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public final class Scoop {

    private boolean hasAwsConfig;
    private int clusterPort;
    private Regions region;
    private HashSet<ScoopListener> listeners;
    private ScoopClientImpl scoopClient;
    private int port;

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

    public Scoop withRegion(final Regions region){
        this.region = checkNotNull(region, "region must not be null");
        return this;
    }

    public Scoop withListener(final ScoopListener listener) {
        checkNotNull(listener, "ScoopListener must not be null");
        listeners.add(listener);
        return this;
    }


    public ScoopClient defaultClient() {
        return this.scoopClient;
    }

    public ActorSystem build() {

        LOGGER.debug("Building ActorSystem with [scoop={}]", this);
        Config config;
        if(hasAwsConfig) {
            final AwsConfigurationBuilder builder = new AwsConfigurationBuilder(region, clusterPort);
            config = builder.build();
        }
        else {
            config = ConfigFactory.load();
        }

        config = config.withValue("akka.remote.netty.tcp.port",
                                  ConfigValueFactory.fromAnyRef(String.valueOf(port)));

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
