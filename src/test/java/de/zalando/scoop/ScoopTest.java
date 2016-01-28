package de.zalando.scoop;


import akka.cluster.Cluster;
import akka.cluster.Member;
import com.amazonaws.regions.Regions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.typesafe.config.Config;
import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static de.zalando.scoop.config.AwsConfigurationBuilder.DEFAULT_HTTP_META_DATA_INSTANCE_ID_URL;
import static org.junit.Assert.*;

public final class ScoopTest {

    private Scoop scoop;

    @Before
    public void setup() throws Exception {
        scoop = new Scoop();
    }

    @Test
    public void testDefaults() throws Exception {
        assertFalse("Scoop must not have AWS enabled by default",
                scoop.hasAwsConfig());

        assertEquals("default AWS instance meta data url is not correct",
                DEFAULT_HTTP_META_DATA_INSTANCE_ID_URL,
                scoop.getAwsMetaDataInstanceIdUrl());

        assertEquals("default instance port is not correct",
                Scoop.DEFAULT_INSTANCE_PORT,
                scoop.getPort());

        assertEquals("default cluster port is not correct",
                 Scoop.DEFAULT_CLUSTER_PORT,
                 scoop.getClusterPort());

        assertEquals("default AWS region is not correct",
                Regions.EU_WEST_1,
                scoop.getRegion());

        assertTrue("default list of Scoop seeds must be empty",
                    scoop.getSeeds().isEmpty());

        final Set<ScoopListener> defaultListeners = scoop.getListeners();
        assertTrue("list of default listeners must contain the default listener (only)",
                    defaultListeners.size() == 1);

        final ScoopListener listener = defaultListeners.iterator().next();
        assertTrue("the default scoop client is not registered as default listener",
                listener instanceof ScoopClientImpl);

        assertTrue("listener instance is not the one which is provided as default client",
                listener == scoop.defaultClient());
    }

    @Test
    public void testWithClusterPort() throws Exception {
        final int clusterPort = 1234;
        final Scoop scoopAgain = scoop.withClusterPort(clusterPort);
        assertNotNull("Scoop instance returned after build step must not be null", scoopAgain);
        assertEquals("cluster port setting was not applied", clusterPort, scoopAgain.getClusterPort());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWithClusterPortWithInvalidPort() throws Exception {
        scoop.withClusterPort(123);
    }

    @Test
    public void testWithPort() throws Exception {
        final int port = 1234;
        final Scoop scoopAgain = scoop.withPort(port);
        assertNotNull("Scoop instance returned after build step must not be null", scoopAgain);
        assertEquals("cluster port setting was not applied", port, scoopAgain.getPort());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWithPortWithInvalidPort() throws Exception {
        scoop.withPort(123);
    }

    @Test
    public void testWithBindHostName() throws Exception {
        final String hostName = "myHost";
        final Scoop scoopAgain = scoop.withBindHostName(hostName);
        assertNotNull("Scoop instance returned after build step must not be null", scoopAgain);
        assertEquals("bindHostName setting was not applied",
                hostName,
                scoopAgain.getBindHostName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWithBindHostNameWithNullHostName() throws Exception {
        scoop.withBindHostName(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWithBindHostNameWithEmptyHostName() throws Exception {
        scoop.withBindHostName("");
    }

    @Test
    public void testWithSeeds() throws Exception {
        final HashSet<String> mySeeds = Sets.newHashSet("seed-1", "seed-2");
        final Scoop scoopAgain = scoop.withSeeds(mySeeds);
        assertNotNull("Scoop instance returned after build step must not be null", scoopAgain);
        assertEquals("given seeds were not applied properly",
                mySeeds,
                scoopAgain.getSeeds());
    }

    @Test(expected = NullPointerException.class)
    public void testWithSeedsWithNull() throws Exception {
        scoop.withSeeds(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWithSeedsWithEmptySet() throws Exception {
        scoop.withSeeds(Sets.newHashSet());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWithSeedsWithEmptyEntry() throws Exception {
        scoop.withSeeds(Sets.newHashSet("seed-1", ""));
    }

    @Test
    public void testWithSeed() throws Exception {
        final String seed1 = "seed-1";
        final String seed2 = "seed-2";

        Scoop scoopAgain = scoop.withSeed(seed1);
        assertNotNull("Scoop instance returned after build step must not be null", scoopAgain);

        Set<String> seeds = scoopAgain.getSeeds();
        assertTrue("seed was not applied to Scoop",
                seeds.contains(seed1));

        scoopAgain = scoopAgain.withSeed(seed2);
        seeds = scoopAgain.getSeeds();

        assertTrue("seeds were not applied to Scoop or got lost",
                seeds.contains(seed1) && seeds.contains(seed2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWithSeedWithNull() throws Exception {
        scoop.withSeed(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWithSeedWithEmptySeed() throws Exception {
        scoop.withSeed("");
    }

    @Test
    public void testWithListener() throws Exception {
        final ScoopListener emptyListener = new EmptyListener();
        final Scoop scoopAgain = scoop.withListener(emptyListener);
        assertNotNull("Scoop instance returned after build step must not be null", scoopAgain);
        final Set<ScoopListener> listeners = scoopAgain.getListeners();
        assertTrue("default listener is gone", listeners.contains(scoop.defaultClient()));
        assertTrue("custom listener was not added", listeners.contains(emptyListener));
    }

    @Test(expected = NullPointerException.class)
    public void testWithNullListener() throws Exception {
        scoop.withListener(null);
    }

    @Test
    public void testWithRegion() throws Exception {
        final Scoop scoopAgain = scoop.withRegion(Regions.EU_WEST_1);
        assertNotNull("Scoop instance returned after build step must not be null", scoopAgain);
        assertEquals("Region was not applied to Scoop setting",
                Regions.EU_WEST_1,
                scoopAgain.getRegion());
    }

    @Test(expected = NullPointerException.class)
    public void testWithNullRegion() throws Exception {
        scoop.withRegion(null);
    }

    @Test
    public void testWithAwsConfig() throws Exception {
        final Scoop scoopAgain = scoop.withAwsConfig();
        assertNotNull("Scoop instance returned after build step must not be null", scoopAgain);
        assertTrue("AWS setting was not applied", scoopAgain.hasAwsConfig());
    }

    @Test
    public void testWithAwsConfigWithCustomMetaDataUrl() throws Exception {
        final String customMetaDataUrl = "http://localhost/meta-data";
        final Scoop scoopAgain = scoop.withAwsConfig(customMetaDataUrl);
        assertNotNull("Scoop instance returned after build step must not be null", scoopAgain);
        assertTrue("AWS setting was not applied", scoopAgain.hasAwsConfig());
        assertEquals("Custom meta data instance url was not applied",
                customMetaDataUrl,
                scoopAgain.getAwsMetaDataInstanceIdUrl());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWithAwsConfigWithNullCustomMetaDataUrl() throws Exception {
        scoop.withAwsConfig(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWithAwsConfigWithEmptyCustomMetaDataUrl() throws Exception {
        scoop.withAwsConfig("");
    }

    /**
     * Actually, we just test configuration preparation here as this is the part
     * where most of the logic is located.
     */
    @Test
    public void testBuildWithoutAws() throws Exception {

        final String hostName = "my-host";
        final int clusterPort = 1234;
        final int port = 4567;
        final String seed = "seed-1";

        final Scoop filledScoop = scoop.withBindHostName(hostName)
                                        .withClusterPort(clusterPort)
                                        .withPort(port)
                                        .withSeed(seed);

        final Config config = filledScoop.prepareConfig();

        assertEquals("port setting is not reflected in Akka Config",
                     port,
                     config.getInt("akka.remote.netty.tcp.port"));


        assertEquals("bind host setting is not reflected in Akka Config",
                     hostName,
                     config.getString("akka.remote.netty.tcp.bind-hostname"));

        assertEquals("port setting (for bind-port) is not reflected in Akka Config",
                     port,
                     config.getInt("akka.remote.netty.tcp.bind-port"));

        assertEquals("seed nodes setting is not reflected in Akka Config",
                     Lists.newArrayList(seed),
                     config.getStringList("akka.cluster.seed-nodes"));
    }


    @Test(expected = IllegalStateException.class)
    public void testBuildWithoutBindHostName() throws Exception {
        scoop.withSeed("seed-1")
             .withPort(12345)
             .build();
    }

    @Test(expected = IllegalStateException.class)
    public void testBuildWithConfigurationConflict() throws Exception {
           scoop.withSeed("seed-1")
                .withAwsConfig()
                .build();
    }



    private static final class EmptyListener implements ScoopListener {;
        @Override
        public void init(Cluster cluster) {
        }

        @Override
        public void onRebalanced(int partitionId, int numberOfPartitions) {
        }

        @Override
        public void onMemberUp(Member member) {
        }

        @Override
        public void onMemberRemoved(Member member) {
        }

        @Override
        public void onMemberUnreachable(Member member) {
        }
    }
}
