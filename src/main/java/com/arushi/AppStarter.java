package com.arushi;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import io.vertxbeans.rxjava.VertxBeans;

import static io.vertx.core.impl.Arguments.require;
import static java.lang.System.getProperty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

@SpringBootApplication
@Import(VertxBeans.class)
public class AppStarter {
    private static final String CLUSTER_MEMBERS = "cluster.members";

    public static void main(final String... args) {
        require(isNotEmpty(getProperty(CLUSTER_MEMBERS)), "Please provides comma separated cluster members\"-Dcluster.members=members\".");
//        final String members = getProperty(CLUSTER_MEMBERS);
//        clusterMembers = Arrays.asList(members.split(","));
//        Vertx.rxClusteredVertx(vertxOptions).subscribe(vertx -> {
//            }, Throwable::printStackTrace);
        final SpringApplication application = new SpringApplication(AppStarter.class);
        application.setBannerMode(Banner.Mode.LOG);
        application.run(args);
    }
}
