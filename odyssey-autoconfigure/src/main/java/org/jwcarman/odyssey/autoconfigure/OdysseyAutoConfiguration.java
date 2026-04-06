package org.jwcarman.odyssey.autoconfigure;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jwcarman.odyssey.engine.DefaultOdysseyStreamRegistry;
import org.jwcarman.odyssey.memory.InMemoryOdysseyEventLog;
import org.jwcarman.odyssey.memory.InMemoryOdysseyStreamNotifier;
import org.jwcarman.odyssey.spi.OdysseyEventLog;
import org.jwcarman.odyssey.spi.OdysseyStreamNotifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(OdysseyProperties.class)
public class OdysseyAutoConfiguration {

  private static final Log log = LogFactory.getLog(OdysseyAutoConfiguration.class);

  @Bean
  @ConditionalOnMissingBean(OdysseyEventLog.class)
  public InMemoryOdysseyEventLog odysseyEventLog(OdysseyProperties properties) {
    log.warn(
        "No OdysseyEventLog bean found; falling back to in-memory implementation. "
            + "Suitable for single-node environments and testing only. "
            + "For clustered deployments, add a backend module (e.g. odyssey-eventlog-redis).");
    return new InMemoryOdysseyEventLog((int) properties.getRedis().getMaxLen());
  }

  @Bean
  @ConditionalOnMissingBean(OdysseyStreamNotifier.class)
  public InMemoryOdysseyStreamNotifier odysseyStreamNotifier() {
    log.warn(
        "No OdysseyStreamNotifier bean found; falling back to in-memory implementation. "
            + "Suitable for single-node environments and testing only. "
            + "For clustered deployments, add a backend module (e.g. odyssey-notifier-redis).");
    return new InMemoryOdysseyStreamNotifier();
  }

  @Bean
  public DefaultOdysseyStreamRegistry odysseyStreamRegistry(
      OdysseyEventLog eventLog, OdysseyStreamNotifier notifier, OdysseyProperties properties) {
    return new DefaultOdysseyStreamRegistry(
        eventLog,
        notifier,
        properties.getRedis().getStreamPrefix(),
        properties.getKeepAliveInterval().toMillis(),
        properties.getSseTimeout().toMillis(),
        properties.getRedis().getMaxLastN());
  }
}
