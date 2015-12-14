package de.zalando.scoop;


import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Range;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

import java.nio.charset.Charset;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;

public final class ScoopClientImpl implements ScoopClient, ScoopListener{

    private int partitionId;
    private int numberOfPartitions;

    private static final Charset CHARSET = Charset.forName("UTF-8");
    private static final HashFunction HASH_FUNCTION = Hashing.murmur3_32();


    @Override
    public void onRebalanced(final int partitionId, final int numberOfPartitions) {
        checkArgument(partitionId < numberOfPartitions, "[partitionId=%s] is higher than [numberOfPartitions=%s]", partitionId, numberOfPartitions);
        checkArgument(partitionId >= -1, "[partitionId=%s] is negative", partitionId);

        this.partitionId = partitionId;
        this.numberOfPartitions = numberOfPartitions;
    }

    @Override
    public boolean isHandledByMe(final String id) {
        final HashCode hashCode = HASH_FUNCTION.hashString(id, CHARSET);
        return hashCode.asInt() % numberOfPartitions == partitionId;
    }


    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("partitionId", partitionId)
                .add("numberOfPartitions", numberOfPartitions)
                .toString();
    }
}
