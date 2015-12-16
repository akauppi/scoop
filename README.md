# Scoop
Simple library based on Akka Cluster to partition work between multiple instances (belonging to same cluster).

## Prerequisites
- Java >= 1.8
- Maven >= 3.x

## Semantics
`Scoop` was implemented to partition work among all cluster members. It does so by assigning each cluster member a partition id. If one the members wants to perform an operation where other cluster members might be involved, it hashes the item of interest with [MurmurHash3](https://en.wikipedia.org/wiki/MurmurHash) and performs a modulo operation over all available partitions in order to determine the partition id. If the member sees that the item of interest belongs to its partition, it processes it and ignores it otherwise.

One possible use case is an application subscribing with all of its instances to a pub sub system. In this scenario, the application`s instances run a Scoop cluster in order to avoid multiple consumptions by instances of the same application.

## Tutorial
```java
        Scoop scoop = new Scoop();
        scoop = scoop.withAwsConfig() // STUPS sepcific logic gathering seed nodes, current IP etc. for Scoop setup
                     .withBindHostName("hecate") // host name to bind to (this is usually the docker host name (see '-h'))
                     .withClusterPort(25551) // port of the Scoop cluster -> all cluster nodes must be accessible via this port
                     .withPort(25551); // local node port
                     
        ActorSystem scoopSystem = scoop.build();
        ScoopClient scoopClient = scoop.defaultClient()
        // ...
        boolean shouldIprocessIt scoopClient.isHandledByMe(id);
```

## AWS Support 
*NOTE:* This logic is [Zalando STUPS](https://github.com/zalando-stups) specific and might not fit other kind of deployments.

Scoop reads AWS meta data in order to perform its AWS specific configuration. The logic is as follows:
- determine current instance IP via http://169.254.169.254/latest/meta-data/
- determine Auto Scaling group
- determine all Auto Scaling group instances to obtain cluster seed

*Important:* Make sure the role of your instances have following policy assigned:
```json
 {
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "Stmt1450176974000",
            "Effect": "Allow",
            "Action": [
                "ec2:DescribeInstances"
            ],
            "Resource": [
                "*"
            ]
        },
        {
            "Sid": "Stmt1450177255000",
            "Effect": "Allow",
            "Action": [
                "autoscaling:DescribeAutoScalingGroups",
                "autoscaling:DescribeAutoScalingInstances"
            ],
            "Resource": [
                "*"
            ]
        }
    ]
}
```

## License
http://opensource.org/licenses/MIT