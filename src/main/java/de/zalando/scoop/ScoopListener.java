package de.zalando.scoop;


import akka.cluster.ClusterReadView;
import akka.cluster.Member;

public interface ScoopListener {

    void init(ClusterReadView clusterReadView);

    void onRebalanced(final int partitionId, final int numberOfPartitions);

    void onMemberUp(final Member member);

    void onMemberRemoved(final Member member);

    void onMemberUnreachable(final Member member);
}
