package de.zalando.scoop;


interface ScoopListener {
    void onRebalanced(final int partitionId, final int numberOfPartitions);
}
