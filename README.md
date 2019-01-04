# vertx_cluster_test

This application is created to demonstrate clustering using vertx.

**Build** 

Run command: `mvn clean install` 

**Run**

Run command: `java -jar -Dcluster.members=member1,member2....memberN  vertx-cluster-test.jar`

Provide comma separated ip addresses of the nodes. 
For instance, java -jar -Dcluster.members=172.16.85.177,172.16.85.175 vertx-cluster-test.jar

Default configuration: 

Application Address: 0.0.0.0

Application Port: 8080

Hazelcast Port: 5701 

Vertx Eventbus Port: 41232


**Endpoints**

_Publish endpoint:_ Demo of publishing message on vertx event-bus. 
All the cluster members will have a consumer on that address and will log a statement depicting that the message is received. 

For instance, 
Received on host:: 172.16.85.177 ---> Hello from host:: 172.16.85.177 

`http://localhost:8080/publish`

_Hazelcast endpoint:_ Retrieves all the active hazelcast cluster members.

For instance, 
Retrieving cluster members 172.16.85.175,172.16.85.177

`http://localhost:8080/hazelcast`


**Instructions to reproduce eventbus clustering issue**
1. Spin up 2 nodes in cluster. 

Lets take two nodes with address 172.16.85.175 and 172.16.85.177
Run command on both nodes, 
java -jar -Dcluster.members=172.16.85.175,172.16.85.177 vertx-cluster-test.jar

2. Validate that the cluster is formed with these nodes.

Log statement --->

Members {size:2, ver:2} [
        Member [172.16.85.177]:5701 - 172d7e4a-7851-4c4a-8cb7-38095b380dab this
        Member [172.16.85.175]:5701 - 4bff302c-a084-4176-af6f-bcb09edf9266
]
 
Now, when you hit endpoint _http://localhost:8080/hazelcast_ on any node, the response should have "172.16.85.175,172.16.85.177".
 
Log statement ---> "Active hazelcast cluster members - 172.16.85.175,172.16.85.177"

3. Validate that the published message on vertx eventbus is recieved by all nodes. 

When you hit endpoint _http://localhost:8080/publish_ on node 172.16.85.177, the response should be "Sent Publish from 172.16.85.177".

Log statement on node 172.16.85.177,
"Received on host:: 172.16.85.177 ---> Hello from host:: 172.16.85.177"

Log statement on node 172.16.85.175,
"Received on host:: 172.16.85.175 ---> Hello from host:: 172.16.85.177"

4. Now suspend network one of the nodes. 

We suspend network on node 172.16.85.175. 

Expected behavior on node 172.16.85.177:

Log statement ----> 
Members {size:1, ver:3} [
        Member [172.16.85.177]:5701 - 172d7e4a-7851-4c4a-8cb7-38095b380dab this
]

Hazelcast endpoint outcome,
Log statement ---> "Active hazelcast cluster members - 172.16.85.177"

Publish endpoint outcome, 
Log statement on node 172.16.85.177,
"Received on host:: 172.16.85.177 ---> Hello from host:: 172.16.85.177"

Nothing on node 172.16.85.175

5. Resume network on the suspended node. 

We resume network on node 172.16.85.175.

Logs on both nodes, 
Members {size:2, ver:6} [
        Member [172.16.85.175]:5701 - 8adec017-0658-446f-9c05-48eeb0131e8d
        Member [172.16.85.177]:5701 - 605415f3-5c98-4b99-8e30-297915312b6c this
]

When you hit hazelcast endpoint _http://localhost:8080/hazelcast_ on node 172.16.85.177, 
Log statement ---> "Active hazelcast cluster members - 172.16.85.175,172.16.85.177"


When you hit publish endpoint _http://localhost:8080/publish_ on node 172.16.85.177, the response should be "Sent Publish from 172.16.85.177".

Log statement on node 172.16.85.177,
"Received on host:: 172.16.85.177 ---> Hello from host:: 172.16.85.177"

Log statement on node 172.16.85.175,
"Received on host:: 172.16.85.175 ---> Hello from host:: 172.16.85.177"
