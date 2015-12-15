package de.zalando.scoop;

import akka.actor.ActorSystem;
import com.amazonaws.regions.Regions;
import de.zalando.scoop.config.AwsConfigurationBuilder;

public class App
{
    public static void main( String[] args ) {

        final AwsConfigurationBuilder builder = new AwsConfigurationBuilder(Regions.EU_WEST_1, 25551);
        System.out.println(builder.instanceFromId("i-55b32ed8"));

        System.out.println("-> autoscalingGroup: " + builder.autoScalingGroup("i-55b32ed8"));
        System.out.println("members: " + builder.groupInstanceIds(builder.autoScalingGroup("i-55b32ed8")));
        System.out.println("seed: " + builder.siblingIps("i-55b32ed8"));

        final ActorSystem system = new Scoop()
                .withPort(25551)
                .withClusterPort(25551)
                .withRegion(Regions.EU_WEST_1)
                .build();

        system.awaitTermination();
    }
}
