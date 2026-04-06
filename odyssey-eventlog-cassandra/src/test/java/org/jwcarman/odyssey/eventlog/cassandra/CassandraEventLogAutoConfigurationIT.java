package org.jwcarman.odyssey.eventlog.cassandra;

import static org.assertj.core.api.Assertions.assertThat;

import com.datastax.oss.driver.api.core.CqlSession;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import org.jwcarman.odyssey.autoconfigure.OdysseyAutoConfiguration;
import org.jwcarman.odyssey.memory.InMemoryOdysseyEventLog;
import org.jwcarman.odyssey.spi.OdysseyEventLog;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.testcontainers.cassandra.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class CassandraEventLogAutoConfigurationIT {

  @Container static CassandraContainer cassandra = new CassandraContainer("cassandra:4.1");

  @Test
  void createsCassandraEventLogBean() {
    createContextRunner()
        .run(
            context -> {
              assertThat(context).hasSingleBean(CassandraOdysseyEventLog.class);
              assertThat(context).hasSingleBean(OdysseyEventLog.class);
            });
  }

  @Test
  void cassandraEventLogSuppressesInMemoryFallback() {
    createContextRunner()
        .withConfiguration(AutoConfigurations.of(OdysseyAutoConfiguration.class))
        .run(
            context -> {
              assertThat(context).hasSingleBean(OdysseyEventLog.class);
              assertThat(context.getBean(OdysseyEventLog.class))
                  .isInstanceOf(CassandraOdysseyEventLog.class);
              assertThat(context).doesNotHaveBean(InMemoryOdysseyEventLog.class);
            });
  }

  private ApplicationContextRunner createContextRunner() {
    return new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CassandraEventLogAutoConfiguration.class))
        .withPropertyValues(
            "odyssey.eventlog.type=cassandra", "odyssey.eventlog.cassandra.auto-create-schema=true")
        .withBean(
            CqlSession.class,
            () -> {
              CqlSession session =
                  CqlSession.builder()
                      .addContactPoint(
                          new InetSocketAddress(cassandra.getHost(), cassandra.getMappedPort(9042)))
                      .withLocalDatacenter("datacenter1")
                      .build();
              session.execute(
                  "CREATE KEYSPACE IF NOT EXISTS odyssey_test"
                      + " WITH replication = {'class': 'SimpleStrategy',"
                      + " 'replication_factor': 1}");
              session.execute("USE odyssey_test");
              return session;
            });
  }
}
