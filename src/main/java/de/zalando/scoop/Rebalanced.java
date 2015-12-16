package de.zalando.scoop;


import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

import java.io.Serializable;

import static com.google.common.base.Preconditions.checkArgument;

public final class Rebalanced implements Serializable{

    private final int partitionId;
    private final int numberOfPartitions;

    public Rebalanced(final int partitionId, final int numberOfPartitions) {
        checkArgument(partitionId > -1, "partition id must be > -1. Got [partitionId=%s]", partitionId);
        checkArgument(numberOfPartitions > partitionId, "partitionId must NOT be >= number of partitions. " +
                                                        "Got [partitionId=%s, numberOfPartitions=%s]",
                                                        partitionId, numberOfPartitions);
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
