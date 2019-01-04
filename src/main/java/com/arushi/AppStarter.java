package com.arushi;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.hazelcast.config.Config;
import com.hazelcast.config.EvictionPolicy;
import com.hazelcast.config.InMemoryFormat;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.MaxSizeConfig;
import com.hazelcast.config.MulticastConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.config.TcpIpConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.Member;

import io.vertx.core.VertxOptions;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.ext.web.Router;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

import static io.vertx.core.impl.Arguments.require;
import static java.lang.System.getProperty;
import static java.util.Collections.list;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

public class AppStarter {
    private static final String CACHE_MAP_NAME = "myMap";
    private static final String CLUSTER_MEMBERS = "cluster.members";
    private static List<String> clusterMembers;

    public static void main(final String... args) {
        require(isNotEmpty(getProperty(CLUSTER_MEMBERS)), "Please provides comma separated cluster members\"-Dcluster.members=members\".");
        final String members = getProperty(CLUSTER_MEMBERS);
        clusterMembers = Arrays.asList(members.split(","));
        final String host = getDefaultAddress();
        HazelcastClusterManager clusterManager = getClusterManager();
        VertxOptions options = new VertxOptions()
                .setClusterManager(clusterManager)
                .setClustered(true)
                .setClusterHost(host)
                .setClusterPort(41232);

        Vertx.rxClusteredVertx(options).subscribe(vertx -> {
            HazelcastInstance hazelcastInstance = clusterManager.getHazelcastInstance();
            require(hazelcastInstance != null, "Hazelcast did not start!");
            startHttpServer(vertx, host);
            registerVertxEventBusHandler(vertx, host);
        }, Throwable::printStackTrace);
    }

    private static void startHttpServer(final Vertx vertx, final String currentHost) {
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
        router.get("/hazelcast").handler(context -> {
            final String nodesInCluster = getHazelCastClusterMembers()
                    .stream()
                    .map(member -> member.getSocketAddress().getAddress().getHostAddress())
                    .collect(Collectors.joining(","));
            System.out.println("Active hazelcast cluster members - " + nodesInCluster);

            context
                    .response()
                    .setChunked(true)
                    .write(nodesInCluster)
                    .end();
        });

        vertx.createHttpServer()
                .requestHandler(router::accept)
                .listen(8080, "0.0.0.0");
    }

    private static void registerVertxEventBusHandler(final Vertx vertx, final String host) {
        vertx.eventBus().consumer("someHandler", message -> {
            System.out.println("Received on host:: " + host + " ---> Hello from " + message.body());
        });
    }

    private static HazelcastClusterManager getClusterManager() {
        HazelcastClusterManager clusterManager = new HazelcastClusterManager();
        Config config = new Config()
                .setNetworkConfig(new NetworkConfig()
                        .setPort(5701)
                        .setPortAutoIncrement(false)
                        .setJoin(createJoinConfig()));
        config.getMapConfigs().put(CACHE_MAP_NAME, createMapConfig(CACHE_MAP_NAME));
        clusterManager.setConfig(config);
        return clusterManager;
    }

    private static Set<Member> getHazelCastClusterMembers() {
        return getHazelcastInstance().getCluster().getMembers();
    }

    private static HazelcastInstance getHazelcastInstance() {
        // You can register with Spring instead of doing it every time.
        return Hazelcast.getAllHazelcastInstances().iterator().next();
    }

    private static MapConfig createMapConfig(final String cacheMapName) {
        return new MapConfig()
                .setName(cacheMapName)
                .setInMemoryFormat(InMemoryFormat.BINARY)
                .setBackupCount(0)
                .setAsyncBackupCount(1)
                .setReadBackupData(false)
                .setEvictionPolicy(EvictionPolicy.NONE)
                .setTimeToLiveSeconds(0)
                .setMaxIdleSeconds(0)
                .setStatisticsEnabled(false)
                .setMaxSizeConfig(new MaxSizeConfig(0, MaxSizeConfig.MaxSizePolicy.USED_HEAP_SIZE));
    }

    private static JoinConfig createJoinConfig() {
        final TcpIpConfig tcpipConfig = new TcpIpConfig()
                .setEnabled(true)
                .setConnectionTimeoutSeconds(5);
        if (!clusterMembers.isEmpty()) {
            clusterMembers.forEach(member -> {
                if (isNotEmpty(member)) {
                    System.out.println("Adding member " + member + " to hazelcast config.");
                    tcpipConfig.addMember(member.trim());
                }
            });
        }
        return new JoinConfig()
                .setTcpIpConfig(tcpipConfig)
                .setMulticastConfig(
                        new MulticastConfig()
                                .setEnabled(false));
    }

    private static String getDefaultAddress() {
        try {
            return list(NetworkInterface.getNetworkInterfaces()).stream()
                    .flatMap(ni -> list(ni.getInetAddresses()).stream())
                    .filter(address -> !address.isAnyLocalAddress())
                    .filter(address -> !address.isMulticastAddress())
                    .filter(address -> !address.isLoopbackAddress())
                    .filter(address -> !(address instanceof Inet6Address))
                    .map(InetAddress::getHostAddress)
                    .findFirst().orElse("0.0.0.0");
        } catch (SocketException e) {
            return "0.0.0.0";
        }
    }
}
