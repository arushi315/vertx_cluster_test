package com.arushi;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hazelcast.config.*;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

@Configuration
public class ApplicationConfig {
    // WARNING - These constants are used as system properties for Hazelcast, don't change the value.
    public static final String HAZELCAST_SOCKET_CONNECT_TIMEOUT_SECONDS = "hazelcast.socket.connect.timeout.seconds";
    public static final String HAZELCAST_OPERATION_CALL_TIMEOUT_MILLIS = "hazelcast.operation.call.timeout.millis";
    public static final String HAZELCAST_IO_INPUT_THREAD_COUNT = "hazelcast.io.input.thread.count";
    public static final String HAZELCAST_IO_OUTPUT_THREAD_COUNT = "hazelcast.io.output.thread.count";
    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationConfig.class);
    private static final String CACHE_MAP_NAME = "myMap";
    // Zero means "infinite". If it's not zero then Hazelcast will try to join the cluster
    // within that time, else it will never join. So, be careful when you change it from Zero.
    @Value("${hazelcast.socket.connect.timeout.seconds:0}")
    private String socketConnectTimeout;
    @Value("${hazelcast.operation.call.timeout.millis:8000}")
    private String operationCallTimeoutMillis;
    @Value("${hazelcast.io.input.thread.count:3}")
    private String ioInputThreadCount;
    @Value("${hazelcast.io.output.thread.count:5}")
    private String ioOutputThreadCount;
    @Value("#{'${cluster.members}'.split(',')}")
    private List<String> clusterMembers;

    @Autowired
    private HazelcastPartitionLostListener hazelcastPartitionLostListener;

    @Bean
    public EventBusOptions createEventBusOptions() {
        final EventBusOptions eventBusOptions = new EventBusOptions();
        final String host = Utils.getDefaultAddress();
        eventBusOptions.setClustered(true)
                .setHost(host)
                .setPort(41232);
        return eventBusOptions;
    }

    @Bean(destroyMethod = "")
    public HazelcastInstance createHazelcastInstance() {
        return Hazelcast.getAllHazelcastInstances().iterator().next();
    }

    @Bean
    public HazelcastClusterManager getClusterManager() {
        HazelcastClusterManager clusterManager = new HazelcastClusterManager();
        final Config config = clusterManager.loadConfig()
                .setNetworkConfig(new NetworkConfig()
                        .setPort(5701)
                        .setPortAutoIncrement(false)
                        .setJoin(createJoinConfig()));
        config.getMapConfigs().put(CACHE_MAP_NAME, createMapConfig(CACHE_MAP_NAME));
        config.getGroupConfig().setName("TEST-3_8_4");
        config.setProperty(HAZELCAST_SOCKET_CONNECT_TIMEOUT_SECONDS, socketConnectTimeout);
        config.setProperty(HAZELCAST_OPERATION_CALL_TIMEOUT_MILLIS, operationCallTimeoutMillis);

        // Set threads count. See http://docs.hazelcast.org/docs/latest-development/manual/html/Performance/Threading_Model/I:O_Threading.html
        config.setProperty(HAZELCAST_IO_INPUT_THREAD_COUNT, ioInputThreadCount);
        config.setProperty(HAZELCAST_IO_OUTPUT_THREAD_COUNT, ioOutputThreadCount);
        config.addListenerConfig(new ListenerConfig(hazelcastPartitionLostListener));

        clusterManager.setConfig(config);
        return clusterManager;
    }

    private MapConfig createMapConfig(final String cacheMapName) {
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

    private JoinConfig createJoinConfig() {
        final TcpIpConfig tcpipConfig = new TcpIpConfig()
                .setEnabled(true)
                .setConnectionTimeoutSeconds(5);
        if (!clusterMembers.isEmpty()) {
            clusterMembers.forEach(member -> {
                if (isNotEmpty(member)) {
                    LOGGER.info("Adding member " + member + " to hazelcast config.");
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

}
