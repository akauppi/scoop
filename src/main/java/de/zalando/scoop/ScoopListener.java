package de.zalando.scoop;


import akka.cluster.Cluster;
import akka.cluster.ClusterReadView;
import akka.cluster.Member;

public interface ScoopListener {

    void init(Cluster cluster);

    void onRebalanced(final int partitionId, final int numberOfPartitions);

    void onMemberUp(final Member member);

    void onMemberRemoved(final Member member);

    void onMemberUnreachable(final Member member);
}
