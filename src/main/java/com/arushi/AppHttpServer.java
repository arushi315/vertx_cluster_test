package com.arushi;

import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.Member;

import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.core.http.HttpServer;
import io.vertx.rxjava.ext.web.Router;
import io.vertxbeans.rxjava.ContextRunner;

import rx.Observable;
import rx.Subscriber;

@Component
public class AppHttpServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(AppHttpServer.class);
    private static HttpServer httpServer;
    @Autowired
    private Vertx vertx;
    @Autowired
    private VertxOptions vertxOptions;
    @Autowired
    private HazelcastInstance hazelcastInstance;
    @Autowired
    private ContextRunner contextRunner;

    @PostConstruct
    public void init() {
        final String host = Utils.getDefaultAddress();
        // WITH CONTEXT RUNNER: 
        try {
            contextRunner.executeBlocking(vertxOptions.getEventLoopPoolSize(),
                    () -> startHttpServer(host)
                            .doOnError(e -> LOGGER.info("Error while launching HTTP Server."))
                            .buffer(2), 1, TimeUnit.MINUTES);
        } catch (final InterruptedException | ExecutionException | TimeoutException e) {
            LOGGER.info("Error while starting up the application." + e);
        }

        // WITHOUT CONTEXT RUNNER: 
//        startHttpServer(host);

        registerVertxEventBusHandler(host);
    }

    //        private void startHttpServer(final String currentHost) {
    private Observable<HttpServer> startHttpServer(final String currentHost) {
        final Router router = Router.router(vertx);
        // Failure handler.
        router.route().failureHandler(context -> {
            context.getDelegate().failure().printStackTrace();
            context.response()
                    .setChunked(true)
                    .write("Failure::" + context.getDelegate().failure().getMessage())
                    .end();
        });

        // Demo of publishing messages on event-bus.
        router.get("/publish").handler(context -> {
            vertx.eventBus().publish("someHandler", "host:: " + currentHost);
            context
                    .response()
                    .setChunked(true)
                    .write("Sent Publish from " + currentHost)
                    .end();
        });

        // Gets all the active cluster members.
        router.get("/nodeInfo").handler(context -> {
            final String nodesInCluster = getHazelCastClusterMembers()
                    .stream()
                    .map(member -> member.getSocketAddress().getAddress().getHostAddress())
                    .collect(Collectors.joining(","));

            final String vertxNodes = vertxOptions.getClusterManager().getNodes()
                    .stream()
                    .collect(Collectors.joining(","));

            final String nodeIdResponse = new StringBuilder()
                    .append("Host: ").append(hazelcastInstance.getLocalEndpoint().getSocketAddress().toString())
                    .append("\nVertx Node Id: ").append(vertxOptions.getClusterManager().getNodeID())
                    .append("\nHazelCast Node Id: ").append(hazelcastInstance.getLocalEndpoint().getUuid())
                    .append("\nActive hazelCast cluster Nodes: ").append(nodesInCluster)
                    .append("\nActive Vertx cluster Nodes: ").append(vertxNodes).toString();
            LOGGER.info("Node Response - \n" + nodeIdResponse);

            context
                    .response()
                    .setChunked(true)
                    .write(nodeIdResponse)
                    .end();
        });

        // WITHOUT CONTEXT RUNNER
//        vertx.createHttpServer()
//                .requestHandler(router::accept)
//                .listen(8080, "0.0.0.0");

        final HttpServerOptions options = new HttpServerOptions()
                .setHost("localhost")
                .setPort(8080);
        final Observable<HttpServer> serverObservable = listen(options, router);
        return Observable.create((Subscriber<? super HttpServer> subscriber) -> {
            serverObservable.subscribe(remoteHttpServer -> {
                httpServer = remoteHttpServer;
                subscriber.onNext(remoteHttpServer);
                subscriber.onCompleted();
            }, subscriber::onError);
        });
    }

    private void registerVertxEventBusHandler(final String host) {
        vertx.eventBus().consumer("someHandler", message -> {
            LOGGER.info("Received on host:: " + host + " ---> Hello from " + message.body());
        });

        vertx.eventBus().consumer("partitionLost", message -> {
            LOGGER.info("Received Partition lost on host:: " + host + " from host::  " + message.body());
        });
    }

    private Set<Member> getHazelCastClusterMembers() {
        return hazelcastInstance.getCluster().getMembers();
    }

    private Observable<HttpServer> listen(final HttpServerOptions options, final Router router) {
        return vertx.createHttpServer(options)
                .requestHandler(request -> router.accept(request))
                .listenObservable()
                .doOnCompleted(() -> LOGGER.info("Listening on localhost:8080"))
                .doOnError(e -> LOGGER.info("Unable to listen on localhost:8080"));
    }
}
