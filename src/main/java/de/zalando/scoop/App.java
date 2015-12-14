package de.zalando.scoop;

import akka.actor.ActorSystem;
import com.google.common.collect.Sets;
import com.typesafe.config.ConfigFactory;

public class App
{
    public static void main( String[] args ) {
        final ActorSystem system = ActorSystem.create("scoop-system",  ConfigFactory.load());
        system.actorOf(ScoopActor.props(Sets.newHashSet(new ScoopClientImpl())), "scoop-actor");


        system.awaitTermination();
    }
}
