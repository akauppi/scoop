package de.zalando.scoop;


import akka.actor.ActorSelection;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.cluster.*;
import akka.cluster.ClusterEvent.*;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.util.HashSet;
import java.util.Set;

import static java.util.Objects.requireNonNull;

final class ScoopActor extends UntypedActor {

    private final LoggingAdapter logger;
    private final Cluster cluster;
    private final Set<ScoopListener> listeners;
    private final HashSet<ActorSelection> memberSet;


    public ScoopActor(final Set<ScoopListener> listeners) {
        this.listeners = requireNonNull(listeners, "set of listeners must not be null");
        this.logger = Logging.getLogger(context().system(), this);
        this.cluster = Cluster.get(context().system());
        this.memberSet = Sets.newHashSet();

        if(listeners.isEmpty()) {
            logger.warning("list of ScoopActor listeners is empty");
        }
    }


    public static Props props(final Set<ScoopListener> listeners) {
        requireNonNull(listeners, "set of listeners must not be null");
        return Props.create(ScoopActor.class, listeners);
    }


    @Override
    public void preStart() {
        cluster.subscribe(self(),
                MemberEvent.class,
                MemberUp.class,
                MemberRemoved.class,
                UnreachableMember.class);

        listeners.forEach(l -> l.init(cluster));
    }


    @Override
    public void postStop() {
        cluster.unsubscribe(getSelf());
    }


    @Override
    public void onReceive(final Object message) throws Exception {

        logger.info("debug message: {}", message);

        if (message instanceof MemberUp) {
            final MemberUp mUp = (MemberUp) message;
            final Member member = mUp.member();
            register(member);
            listeners.stream().forEach(l -> l.onMemberUp(member));
        } else if (message instanceof CurrentClusterState) {
            final CurrentClusterState state = (CurrentClusterState) message;
            for (Member member : state.getMembers()) {
                if (member.status().equals(MemberStatus.up())) {
                    register(member);
                    listeners.stream().forEach(l -> l.onMemberUp(member));
                }
            }
        } else if (message instanceof MemberRemoved) {
            final MemberRemoved memberRemoved = (MemberRemoved) message;
            final Member removedMember = memberRemoved.member();
            unregister(removedMember);
            listeners.stream().forEach(l -> l.onMemberRemoved(removedMember));
        } else if (message instanceof Rebalanced) {
            final Rebalanced rebalanced = (Rebalanced) message;
            final int partitionId = rebalanced.getPartitionId();
            final int numberOfPartitions = rebalanced.getNumberOfPartitions();
            listeners.stream().forEach(l -> l.onRebalanced(partitionId, numberOfPartitions));
        }
        else if (message instanceof UnreachableMember) {
            final UnreachableMember um = (UnreachableMember) message;
            final Member unreachableMember = um.member();
            listeners.stream().forEach(l -> l.onMemberUnreachable(unreachableMember));
        }
        else {
            logger.debug("unhandled message: {}", message);
            unhandled(message);
        }
    }

    private ActorSelection selectActorByMember(final Member member){
        return context().actorSelection(member.address() + "/user/scoop-actor");
    }

    private void unregister(final Member member) {
        final ActorSelection actorSelection = selectActorByMember(member);
        memberSet.remove(actorSelection);

        rebalanceIfLeader();
    }

    private void register(final Member member) {
        final ActorSelection actorSelection = selectActorByMember(member);
        memberSet.add(actorSelection);

        rebalanceIfLeader();
    }


    private void rebalanceIfLeader() {
        if(cluster.readView().isLeader()) {
            logger.info("I am LEADER -> rebalancing");
            final int numberOfMembers = memberSet.size();

            int i = 0;
            for (ActorSelection memberSelection : memberSet) {
                memberSelection.tell(new Rebalanced(i, numberOfMembers), self());
                i++;
            }
        }
        else {
            logger.info("I am NOT a LEADER");
        }
    }

}
