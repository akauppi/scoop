package de.zalando.scoop;


import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ScoopClientImplTest {

    private ScoopClientImpl client;

    @Before
    public void setup() throws Exception {
        client = new ScoopClientImpl();
    }

    @Test
    public void testOnRebalance() throws Exception {
        client.onRebalanced(0, 1);

        assertEquals("wrong partition id", 0, client.getPartitionId());
        assertEquals("wrong number of partitions", 1, client.getNumberOfPartitions());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOnRebalanceWithIllegalPartitionId() throws Exception{
        client.onRebalanced(-1, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOnRebalanceWithIllegalNumberOfPartitions() throws Exception{
        client.onRebalanced(0, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOnRebalanceWithImpossiblePartitionId() throws Exception{
        client.onRebalanced(2, 1);
    }

    @Test
    public void testIsHandledByMe() throws Exception {
        client.onRebalanced(0, 1);
        assertTrue("could not hash to only partition", client.isHandledByMe("some_id"));

        client.onRebalanced(0, 2);
        assertTrue("did not recognise my slot", client.isHandledByMe("3"));

        client.onRebalanced(1, 2);
        assertTrue("did not recognize my slot", client.isHandledByMe("2"));

    }
}
