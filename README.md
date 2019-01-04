# vertx_cluster_test

This application is created to demonstrate clustering using vertx.

**Build** 

Run command: `mvn clean install` 

**Run**

Run command: `java -jar -Dcluster.members=member1,member3 vertx-cluster-test-1.0-SNAPSHOT.jar`

Provide comma separated ip addresses of the nodes. 

Default configuration: 

Application Address: 0.0.0.0

Application Port: 8080

Hazelcast Port: 5701 

Vertx Eventbus Port: 41232


**Endpoints**

_Publish endpoint:_ Demo of publishing message on vertx event-bus. 
All the cluster members will have a consumer on that address with a log statement depicting that the message is received. 

For instance, 
Received on host:: 172.16.85.177 ---> Hello from host:: 172.16.85.177 

`http://localhost:8080/publish`

_Hazelcast endpoint:_ Retrieves all the active hazelcast members.

For instance, 
Retrieving cluster members 172.16.85.175,172.16.85.177

`http://localhost:8080/hazelcast`
