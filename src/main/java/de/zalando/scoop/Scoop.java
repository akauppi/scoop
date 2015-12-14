package de.zalando.scoop;


import akka.actor.ActorSystem;
import com.amazonaws.regions.Regions;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Sets;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import de.zalando.scoop.config.AwsConfigurationBuilder;
import java.util.HashSet;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public final class Scoop {

    private boolean hasAwsConfig;
    private int clusterPort;
    private Regions region;
    private HashSet<ScoopListener> listeners;
    private ScoopClientImpl scoopClient;

    private static final int DEFAULT_CLUSTER_PORT = 2551;

    public Scoop() {
        this.clusterPort = DEFAULT_CLUSTER_PORT;
        this.hasAwsConfig = false;
        this.region = Regions.EU_WEST_1;
        this.scoopClient = new ScoopClientImpl();
        this.listeners = Sets.newHashSet(scoopClient);
    }

    public Scoop withClusterPort(final int clusterPort) {
        checkArgument(clusterPort > 999,
                      "cluster port must be >= 1000. Got [clusterPort=%s]", clusterPort);
        this.clusterPort = clusterPort;
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
        Config config;
        if(hasAwsConfig) {
            final AwsConfigurationBuilder builder = new AwsConfigurationBuilder(region, clusterPort);
            config = builder.build();
        }
        else {
            config = ConfigFactory.load();
        }

        config = config.withValue("akka.remote.netty.tcp.port",
                                  ConfigValueFactory.fromAnyRef(String.valueOf(clusterPort)));

        final ActorSystem system = ActorSystem.create("scoop-system",  config);
        system.actorOf(ScoopActor.props(listeners), "scoop-actor");

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
                .toString();
    }
}
