# Overview
This repository contains the microservices application described, designed, and documented in the Red Hat reference architecture titled [WildFly Swarm Microservices on Red Hat OpenShift Container Platform 3](https://access.redhat.com/documentation/en-us/reference_architectures/2017/html/wildfly_swarm_microservices_on_red_hat_openshift_container_platform_3/)

Overview video quickly runs through some features of this reference architecture application:

[![WildFly Swarm Microservices](http://img.youtube.com/vi/-h3GWKnhIMQ/2.jpg)](http://www.youtube.com/watch?v=-h3GWKnhIMQ)

# Build and Deployment
First, clone this repository:

````
$ git clone https://github.com/RHsyseng/wildfly-swarm-msa-ocp.git LambdaAir
````

Change directory to the root of this project. It is assumed that from this point on, all instructions are executed from inside the *LambdaAir* directory.

````
$ cd LambdaAir
````

## Shared Storage
This reference architecture environment uses Network File System (NFS) to make storage available to all OpenShift nodes. 

Attach 3GB of storage and create a volume group for it, as well as a logical volume of 1GB for each required persistent volume. For example:

````
$ sudo pvcreate /dev/vdc
$ sudo vgcreate wildfly-swarm /dev/vdc
$ sudo lvcreate -L 1G -n cassandra-data wildfly-swarm
$ sudo lvcreate -L 1G -n cassandra-logs wildfly-swarm
$ sudo lvcreate -L 1G -n edge wildfly-swarm
````

Create a corresponding mount directory for each logical volume and mount them.

````
$ sudo mkfs.ext4 /dev/wildfly-swarm/cassandra-data
$ sudo mkdir -p /mnt/wildfly-swarm/cassandra-data
$ sudo mount /dev/wildfly-swarm/cassandra-data /mnt/wildfly-swarm/cassandra-data

$ sudo mkfs.ext4 /dev/wildfly-swarm/cassandra-logs
$ sudo mkdir -p /mnt/wildfly-swarm/cassandra-logs
$ sudo mount /dev/wildfly-swarm/cassandra-logs /mnt/wildfly-swarm/cassandra-logs

$ sudo mkfs.ext4 /dev/wildfly-swarm/edge
$ sudo mkdir -p /mnt/wildfly-swarm/edge
$ sudo mount /dev/wildfly-swarm/edge /mnt/wildfly-swarm/edge
````

Share these mounts with all nodes by configuring the */etc/exports* file on the NFS server, and make sure to restart the NFS service before proceeding.

## OpenShift Configuration
Create an OpenShift user, optionally with the same name, to use for creating the project and deploying the application. Assuming the use of [HTPasswd](https://access.redhat.com/documentation/en-us/openshift_container_platform/3.7/html/installation_and_configuration/install-config-configuring-authentication#HTPasswdPasswordIdentityProvider) as the authentication provider:

````
$ sudo htpasswd -c /etc/origin/master/htpasswd ocpAdmin
New password: PASSWORD
Re-type new password: PASSWORD
Adding password for user ocpAdmin
````

Grant OpenShift admin and cluster admin roles to this user, so it can create persistent volumes:

````
$ sudo oadm policy add-cluster-role-to-user admin ocpAdmin
$ sudo oadm policy add-cluster-role-to-user cluster-admin ocpAdmin
````

At this point, the new OpenShift user can be used to sign in to the cluster through the master server:

````
$ oc login -u ocpAdmin -p PASSWORD --server=https://ocp-master1.xxx.example.com:8443

Login successful.
````

Create a new project to deploy this reference architecture application:

````
$ oc new-project lambdaair --display-name="Lambda Air" --description="WildFly Swarm Microservices on Red Hat OpenShift Container Platform 3"
Now using project "lambdaair" on server "https://ocp-master1.xxx.example.com:8443".
````

## Jaeger Deployment
Jaeger uses the Cassandra database for storage, which in turn requires OpenShift persistent volumes to be created. Edit *Jaeger/jaeger-pv.yml* and provide a valid NFS server and path for each entry, before proceeding. Once the file has been corrected, use it to create the six persistent volumes:

````
$ oc create -f Jaeger/jaeger-pv.yml
persistentvolume "cassandra-pv-1" created
persistentvolume "cassandra-pv-2" created
persistentvolume "cassandra-pv-3" created
persistentvolume "cassandra-pv-4" created
persistentvolume "cassandra-pv-5" created
persistentvolume "cassandra-pv-6" created
````

Validate that the persistent volumes are available:

````
$ oc get pv
NAME                CAPACITY   ACCESSMODES   RECLAIMPOLICY   STATUS      	AGE
cassandra-pv-1      1Gi        RWO           Recycle         Available      11s
cassandra-pv-2      1Gi        RWO           Recycle         Available      11s
cassandra-pv-3      1Gi        RWO           Recycle         Available      11s
cassandra-pv-4      1Gi        RWO           Recycle         Available      11s
cassandra-pv-5      1Gi        RWO           Recycle         Available      11s
cassandra-pv-6      1Gi        RWO           Recycle         Available      11s
````

With the persistent volumes in place, use the provided version of the Jaeger production template to deploy both the Jaeger server and the Cassandra database services. The template also uses a volume claim template to dynamically create a data and log volume claim for each of the three pods:


````yaml
    volumeClaimTemplates:
    - metadata:
        name: cassandra-data
      spec:
        accessModes: [ "ReadWriteOnce" ]
        resources:
          requests:
            storage: 1Gi
    - metadata:
        name: cassandra-logs
      spec:
        accessModes: [ "ReadWriteOnce" ]
        resources:
          requests:
            storage: 1Gi
````


````
$ oc new-app -f Jaeger/jaeger-production-template.yml

--> Deploying template "swarm/jaeger-template" for "Jaeger/jaeger-production-template.yml" to project lambdaair

     Jaeger
     ---------
     Jaeger Distributed Tracing Server

     * With parameters:
        * Jaeger Service Name=jaeger
        * Image version=0.6
        * Jaeger Cassandra Keyspace=jaeger_v1_dc1
        * Jaeger Zipkin Service Name=zipkin

--> Creating resources ...
    service "cassandra" created
    statefulset "cassandra" created
    job "jaeger-cassandra-schema-job" created
    deployment "jaeger-collector" created
    service "jaeger-collector" created
    service "zipkin" created
    deployment "jaeger-query" created
    service "jaeger-query" created
    route "jaeger-query" created
--> Success
    Run 'oc status' to view your app.
````

You can use *oc status* to get a report, but for further details and to view the progress of the deployment, *watch* the pods as they get created and deployed:

````
$ watch oc get pods

Every 2.0s: oc get pods

NAME                                READY     STATUS	  RESTARTS   AGE
cassandra-0                         1/1       Running     0          4m
cassandra-1                         1/1       Running     2          4m
cassandra-2                         1/1       Running     3          4m
jaeger-cassandra-schema-job-7d58m   0/1       Completed   0          4m
jaeger-collector-418097188-b090z    1/1       Running     4          4m
jaeger-query-751032167-vxr3w        1/1       Running     3          4m
````

It may take a few minutes for the deployment process to complete, at which point there should be five pods in the *Running* state with a database loading job that is completed.

Next, deploy the Jaeger agent. This reference architecture deploys the agent as a single separate pod:

````
$ oc new-app Jaeger/jaeger-agent.yml

--> Deploying template "swarm/jaeger-jaeger-agent" for "Jaeger/jaeger-agent.yml" to project lambdaair

--> Creating resources ...
    deploymentconfig "jaeger-agent" created
    service "jaeger-agent" created
--> Success
    Run 'oc status' to view your app.
````

Note: The Jaeger agent may be deployed in multiple ways, or even bypassed entirely through [direct HTTP calls](https://github.com/jaegertracing/jaeger-client-java/issues/251) to the collector. Another option is bundling the agent as a sidecar to every microservice, as [documented](https://github.com/jaegertracing/jaeger-kubernetes#deploying-the-agent-as-sidecar) in the Jaeger project itself. Select an appropriate approach for your production environment


Next, to access the Jaeger console, first discover its address by querying the route:

````
$ oc get routes

NAME           HOST/PORT                                              PATH      SERVICES       PORT      TERMINATION   WILDCARD
jaeger-query   jaeger-query-lambdaair.ocp.xxx.example.com             jaeger-query   <all>     edge/Allow    None
````

Use the displayed URL to access the console from a browser and verify that it works correctly.

## Service Deployment
To deploy a WildFly Swarm service, use *Maven* to build the project, with the *fabric8:deploy* target for the *openshift* profile to deploy the built image to OpenShift. For convenience, an aggregator *pom* file has been provided at the root of the project that delegates the same Maven build to all 6 configured modules:

````
$ mvn clean fabric8:deploy -Popenshift

[INFO] Scanning for projects...
[INFO]                                                                         
[INFO] ------------------------------------------------------------------------
[INFO] Building Lambda Air 1.0-SNAPSHOT
[INFO] ------------------------------------------------------------------------
...
...
...
[INFO] --- fabric8-maven-plugin:3.5.30:deploy (default-cli) @ aggregation ---
[WARNING] F8: No such generated manifest file /Users/bmozaffa/RedHatDrive/SysEng/Microservices/WildFlySwarm/wildfly-swarm-msa-ocp/target/classes/META-INF/fabric8/openshift.yml for this project so ignoring
[INFO] ------------------------------------------------------------------------
[INFO] Reactor Summary:
[INFO] 
[INFO] Lambda Air ......................................... SUCCESS [02:26 min]
[INFO] Lambda Air ......................................... SUCCESS [04:18 min]
[INFO] Lambda Air ......................................... SUCCESS [02:07 min]
[INFO] Lambda Air ......................................... SUCCESS [02:42 min]
[INFO] Lambda Air ......................................... SUCCESS [01:17 min]
[INFO] Lambda Air ......................................... SUCCESS [01:13 min]
[INFO] Lambda Air ......................................... SUCCESS [  1.294 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time: 14:16 min
[INFO] Finished at: 2017-12-08T16:57:11-08:00
[INFO] Final Memory: 81M/402M
[INFO] ------------------------------------------------------------------------
````

Once all services have been built and deployed, there should be a total of 11 running pods, including the 5 Jaeger pods from before, and a new pod for each of the 6 services:

````
$ oc get pods
NAME                               READY     STATUS      RESTARTS   AGE
airports-1-bn1gp                   1/1       Running     0          24m
airports-s2i-1-build               0/1       Completed   0          24m
cassandra-0                        1/1       Running     0          55m
cassandra-1                        1/1       Running     2          55m
cassandra-2                        1/1       Running     3          55m
edge-1-nlb4b                       1/1       Running     0          12m
edge-s2i-1-build                   0/1       Completed   0          13m
flights-1-n0lbx                    1/1       Running     0          11m
flights-s2i-1-build                0/1       Completed   0          11m
jaeger-agent-1-g8s9t               1/1       Running     0          39m
jaeger-cassandra-schema-job-7d58m  0/1       Completed   0          55m
jaeger-collector-418097188-b090z   1/1       Running     4          55m
jaeger-query-751032167-vxr3w       1/1       Running     3          55m
presentation-1-dscwm               1/1       Running     0          1m
presentation-s2i-1-build           0/1       Completed   0          1m
sales-1-g96zm                      1/1       Running     0          4m
sales-s2i-1-build                  0/1       Completed   0          5m
sales2-1-36hww                     1/1       Running     0          3m
sales2-s2i-1-build                 0/1       Completed   0          4m
````

## Flight Search
The *presentation* service also creates a [route](https://raw.githubusercontent.com/RHsyseng/wildfly-swarm-msa-ocp/master/Presentation/src/main/fabric8/route.yml). Once again, list the routes in the OpenShift project:

````
$ oc get routes
NAME           HOST/PORT                                    PATH      SERVICES       PORT      TERMINATION   WILDCARD
jaeger-query   jaeger-query-lambdaair.ocp.xxx.example.com             jaeger-query   <all>     edge/Allow    None
presentation   presentation-lambdaair.ocp.xxx.example.com             presentation   8080                    None
````

Use the URL of the route to access the HTML application from a browser, and verify that it comes up. Search for a flight by entering values for each of the four fields. The first search may take a bit longer, so wait a few seconds for the response.

## External Configuration
The *Presentation* service configures *Hystrix* with a [thread pool size](https://github.com/RHsyseng/wildfly-swarm-msa-ocp/blob/master/Presentation/src/main/resources/project-defaults.yml#L14) of 20 in its environment properties. Confirm this by searching the logs of the presentation pod after a flight search operation and verify that the batch size is the same:

````
$ oc logs presentation-1-dscwm | grep batch
... ...presentation.service.API_GatewayController    : Will price a batch of 20 tickets
... ...presentation.service.API_GatewayController    : Will price a batch of 13 tickets
... ...presentation.service.API_GatewayController    : Will price a batch of 20 tickets
... ...presentation.service.API_GatewayController    : Will price a batch of 13 tickets
... ...presentation.service.API_GatewayController    : Will price a batch of 20 tickets
... ...presentation.service.API_GatewayController    : Will price a batch of 13 tickets
````

Create a new *project-defaults.yml* file that assumes a higher number of *Sales* service pods relative to *Presentation* pods:

````
$ vi project-defaults.yml
````

Enter the following values:

````yaml
hystrix:
  threadpool:
    SalesThreads:
      coreSize: 30
      maxQueueSize: 300
      queueSizeRejectionThreshold: 300
````

Create a *configmap* using the *oc* utility based on this file:

````
$ oc create configmap presentation --from-file=project-defaults.yml

configmap "presentation" created
````

Edit the *Presentation* deployment config and mount this *ConfigMap* as */deployments/config*, where it will automatically be part of the WildFly Swarm application classpath:

````
$ oc edit dc presentation
````

Add a new volume with an arbitrary name, such as *config-volume*, that references the previously created *configmap*. The *volumes* definition is a child of the *template spec*. Next, create a volume mount under the container to reference this volume and specify where it should be mounted. The final result is as follows, with the new lines highlighted:

````yaml
...
        resources: {}
        securityContext:
          privileged: false
        terminationMessagePath: /dev/termination-log
        volumeMounts:
        - name: config-volume
          mountPath: /deployments/project-defaults.yml
          subPath: project-defaults.yml
      volumes:
        - name: config-volume
          configMap:
            name: presentation
      dnsPolicy: ClusterFirst
      restartPolicy: Always
...
````

Once the deployment config is modified and saved, OpenShift will deploy a new version of the service that will include the overriding properties. This change is persistent and pods created in the future with this new version of the deployment config will also mount the yaml file.

List the pods and note that a new pod is being created to reflect the change in the deployment config, which is the mounted file:

````
$ oc get pods
NAME                               READY     STATUS      RESTARTS   AGE
airports-1-bn1gp                   1/1       Running     0          24m
airports-s2i-1-build               0/1       Completed   0          24m
cassandra-0                        1/1       Running     0          55m
cassandra-1                        1/1       Running     2          55m
cassandra-2                        1/1       Running     3          55m
edge-1-nlb4b                       1/1       Running     0          12m
edge-s2i-1-build                   0/1       Completed   0          13m
flights-1-n0lbx                    1/1       Running     0          11m
flights-s2i-1-build                0/1       Completed   0          11m
jaeger-agent-1-g8s9t               1/1       Running     0          39m
jaeger-cassandra-schema-job-7d58m  0/1       Completed   0          55m
jaeger-collector-418097188-b090z   1/1       Running     4          55m
jaeger-query-751032167-vxr3w       1/1       Running     3          55m
presentation-1-dscwm               1/1       Running     0          1m
presentation-2-deploy              0/1       ContainerCreating   0          3s
presentation-s2i-1-build           0/1       Completed   0          1m
sales-1-g96zm                      1/1       Running     0          4m
sales-s2i-1-build                  0/1       Completed   0          5m
sales2-1-36hww                     1/1       Running     0          3m
sales2-s2i-1-build                 0/1       Completed   0          4m
````

Wait until the second version of the pod has started in the running state. The first version will be terminated and subsequently removed:

````
$ oc get pods
NAME                       READY     STATUS      RESTARTS   AGE
...
presentation-2-36dt9       1/1       Running     0          2s
...
````

Once this has happened, use the browser to do one or several more flight searches. Then verify the updated thread pool size by searching the logs of the new presentation pod and verify the batch size:

````
$ oc logs presentation-2-36dt9 | grep batch
... ...presentation.service.API_GatewayController    : Will price a batch of 30 tickets
... ...presentation.service.API_GatewayController    : Will price a batch of 3 tickets
... ...presentation.service.API_GatewayController    : Will price a batch of 30 tickets
... ...presentation.service.API_GatewayController    : Will price a batch of 3 tickets
... ...presentation.service.API_GatewayController    : Will price a batch of 30 tickets
... ...presentation.service.API_GatewayController    : Will price a batch of 3 tickets
... ...presentation.service.API_GatewayController    : Will price a batch of 30 tickets
... ...presentation.service.API_GatewayController    : Will price a batch of 3 tickets
````

Notice that with the mounted overriding properties, pricing happens in concurrent batches of 30 instead of 20 items now.

## A/B Testing
Copy the JavaScript file provided in the *Edge* project over to the shared storage for this service:

````
$ cp Edge/misc/routing.js /mnt/wildfly-swarm/edge/
````

Create a persistent volume for the *Edge* service. External JavaScript files placed in this location can provide dynamic routing.

````
$ oc create -f Edge/misc/edge-pv.yml
persistentvolume "edge" created
````

Also create a persistent volume claim:

````
$ oc create -f Edge/misc/edge-pvc.yml
persistentvolumeclaim "edge" created
````

Verify that the claim is bound to the persistent volume:

````
$ oc get pvc
NAME 			           		STATUS    VOLUME           CAPACITY   ACCESSMODES STORAGECLASS AGE
cassandra-data-cassandra-0	Bound     cassandra-pv-1   1Gi        RWO                     39m
cassandra-data-cassandra-1	Bound     cassandra-pv-2   1Gi        RWO                     39m
cassandra-data-cassandra-2	Bound     cassandra-pv-3   1Gi        RWO                     39m
cassandra-logs-cassandra-0	Bound     cassandra-pv-4   1Gi        RWO                     39m
cassandra-logs-cassandra-1	Bound     cassandra-pv-5   1Gi        RWO                     39m
cassandra-logs-cassandra-2	Bound     cassandra-pv-6   1Gi        RWO                     39m
edge				Bound     edge             1Gi        RWO                     3s
````

Attach the persistent volume claim to the deployment config as a directory called *edge* on the root of the filesystem:

````
$ oc volume dc/edge --add --name=edge --type=persistentVolumeClaim --claim-name=edge --mount-path=/edge
deploymentconfig "edge" updated
````

Once again, the change prompts a new deployment and terminates the original *edge* pod, once the new version is started up and running.

Wait until the second version of the pod reaches the running state. Then return to the browser and perform one or more flight searches. After that, return to the OpenShift environment and look at the log for the edge pod.

If the IP address received from your browser ends in an odd number, the JavaScript filters pricing calls and sends them to version B of the *sales* service instead. This will be clear in the *edge* log:

````
$ oc logs edge-2-fzgg0
...
... INFO  [....impl.JavaScriptMapper] (default task-4) Rerouting to B instance for IP Address 10.3.116.235
... INFO  [....impl.JavaScriptMapper] (default task-7) Rerouting to B instance for IP Address 10.3.116.235
... INFO  [....impl.JavaScriptMapper] (default task-8) Rerouting to B instance for IP Address 10.3.116.235
... INFO  [....impl.JavaScriptMapper] (default task-11) Rerouting to B instance for IP Address 10.3.116.235
````

In this case, the logs from *sales2* will show tickets being priced with a modified algorithm:

````
$ oc logs sales2-1-36hww
... INFO  [...service.Controller] (default task-27) Priced ticket at 463
... INFO  [...service.Controller] (default task-27) Priced ticket at 425
... INFO  [...service.Controller] (default task-27) Priced ticket at 407
... INFO  [...service.Controller] (default task-27) Priced ticket at 549
... INFO  [...service.Controller] (default task-27) Priced ticket at 509
... INFO  [...service.Controller] (default task-27) Priced ticket at 598
... INFO  [...service.Controller] (default task-27) Priced ticket at 610
````

If that is not the case and your IP address ends in an even number, you will not see any logging at the *INFO* level by the JavaScript and need to turn up the verbosity to clearly see it be executed. In this case, you can change the filter criteria to send IP addresses with an even digit to the new version of pricing algorithm, instead of the odd ones.

````
$ cat /mnt/wildfly-swarm/edge/routing.js
````

````js
if( mapper.getServiceName( request ) == "sales" )
{
	var ipAddress = mapper.getBaggageItem( request, "forwarded-for" );
	mapper.fine( 'Got IP Address as ' + ipAddress );
	if( ipAddress )
	{
		var lastDigit = ipAddress.substring( ipAddress.length - 1 );
		mapper.fine( 'Got last digit as ' + lastDigit );
		if( lastDigit % 2 == 0 )
		{
			mapper.info( 'Rerouting to B instance for IP Address ' + ipAddress );
			//Even IP address, reroute for A/B testing:
			hostAddress = mapper.getRoutedAddress( request, "http://sales2:8080" );
		}
	}
}
````

This is a simple matter of editing the file and deploying a new version of the *edge* service to pick up the updated script:

````
$ oc rollout latest edge
deploymentconfig "edge" rolled out
````

Once the new pod is running, do a flight search again and check the logs. The calls to pricing should go to the *sales2* service now, and logs should appear as previously described.
