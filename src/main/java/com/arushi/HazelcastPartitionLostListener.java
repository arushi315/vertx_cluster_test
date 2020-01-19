package com.arushi;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.hazelcast.partition.PartitionLostEvent;
import com.hazelcast.partition.PartitionLostListener;

import io.vertx.rxjava.core.Vertx;

@Component
public class HazelcastPartitionLostListener implements PartitionLostListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(HazelcastPartitionLostListener.class);
    private final AtomicBoolean partitionLost = new AtomicBoolean(false);
    @Autowired
    private ApplicationContext applicationContext;
    @Value("${partition.lost.flag.reset.interval.millis:3000}")
    private Long partitionLostFlagResetIntervalMillis;

    @Override
    public void partitionLost(final PartitionLostEvent partitionLostEvent) {
        if (partitionLostEvent.getLostBackupCount() > 0) {
            LOGGER.info("Partition Lost");
            if (partitionLost.compareAndSet(false, true)) {
                final Vertx vertx = applicationContext.getBean(Vertx.class);
                vertx.eventBus().publish("partitionLost", Utils.getDefaultAddress());
                // When a partition is lost, Hazelcast invokes this method multiple times. We don't
                // want to overwhelm the policy-update process, so reset the boolean flag after some time.
                vertx.setTimer(partitionLostFlagResetIntervalMillis, event -> {
                    partitionLost.set(false);
                });
            }
        }
    }
}
