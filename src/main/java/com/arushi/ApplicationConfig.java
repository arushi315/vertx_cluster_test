package com.arushi;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

@Configuration
public class ApplicationConfig {
    private static final String CACHE_MAP_NAME = "myMap";
    @Value("#{'${cluster.members}'.split(',')}")
    private List<String> clusterMembers;

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

}
