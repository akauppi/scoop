package de.zalando.scoop;


import com.google.common.base.MoreObjects;

import java.io.Serializable;

public class Rebalanced implements Serializable{

    private final int partitionId;
    private final int numberOfPartitions;

    public Rebalanced(int partitionId, int numberOfPartitions) {
        this.partitionId = partitionId;
        this.numberOfPartitions = numberOfPartitions;
    }

    public int getPartitionId() {
        return partitionId;
    }

    public int getNumberOfPartitions() {
        return numberOfPartitions;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("partitionId", partitionId)
                .add("numberOfPartitions", numberOfPartitions)
                .toString();
    }
}
