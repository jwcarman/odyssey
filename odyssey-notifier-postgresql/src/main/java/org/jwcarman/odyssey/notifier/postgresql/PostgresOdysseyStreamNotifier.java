package org.jwcarman.odyssey.notifier.postgresql;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.sql.DataSource;
import org.jwcarman.odyssey.spi.NotificationHandler;
import org.jwcarman.odyssey.spi.OdysseyStreamNotifier;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.jdbc.core.JdbcTemplate;

public class PostgresOdysseyStreamNotifier implements OdysseyStreamNotifier, SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(PostgresOdysseyStreamNotifier.class);

  private static final String PAYLOAD_DELIMITER = "|";

  private final JdbcTemplate jdbcTemplate;
  private final DataSource dataSource;
  private final String channel;
  private final int pollTimeoutMillis;
  private final List<NotificationHandler> handlers = new CopyOnWriteArrayList<>();

  private volatile boolean running;
  private volatile Thread listenerThread;
  private volatile Connection listenConnection;

  public PostgresOdysseyStreamNotifier(
      JdbcTemplate jdbcTemplate, DataSource dataSource, String channel, int pollTimeoutMillis) {
    this.jdbcTemplate = jdbcTemplate;
    this.dataSource = dataSource;
    this.channel = channel;
    this.pollTimeoutMillis = pollTimeoutMillis;
  }

  @Override
  public void notify(String streamKey, String eventId) {
    String payload = streamKey + PAYLOAD_DELIMITER + eventId;
    jdbcTemplate.execute(
        "SELECT pg_notify('" + channel + "', '" + payload.replace("'", "''") + "')");
  }

  @Override
  public void subscribe(NotificationHandler handler) {
    handlers.add(handler);
  }

  @Override
  public void start() {
    running = true;
    listenerThread = Thread.ofVirtual().name("odyssey-pg-listener").start(this::listenLoop);
  }

  @Override
  public void stop() {
    running = false;
    Thread thread = listenerThread;
    if (thread != null) {
      thread.interrupt();
    }
    closeListenConnection();
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  private void listenLoop() {
    while (running) {
      try {
        listenConnection = dataSource.getConnection();
        listenConnection.setAutoCommit(true);
        PGConnection pgConnection = listenConnection.unwrap(PGConnection.class);

        try (Statement stmt = listenConnection.createStatement()) {
          stmt.execute("LISTEN " + channel);
        }

        log.info("Listening on PostgreSQL channel '{}'", channel);

        while (running) {
          PGNotification[] notifications = pgConnection.getNotifications(pollTimeoutMillis);
          if (notifications != null) {
            for (PGNotification notification : notifications) {
              dispatchNotification(notification.getParameter());
            }
          }
        }
      } catch (SQLException e) {
        if (running) {
          log.warn("PostgreSQL LISTEN connection lost, reconnecting", e);
          closeListenConnection();
          sleepBeforeReconnect();
        }
      }
    }
  }

  private void dispatchNotification(String payload) {
    int delimiterIndex = payload.indexOf(PAYLOAD_DELIMITER);
    if (delimiterIndex < 0) {
      log.warn("Ignoring malformed notification payload: {}", payload);
      return;
    }
    String streamKey = payload.substring(0, delimiterIndex);
    String eventId = payload.substring(delimiterIndex + 1);
    for (NotificationHandler handler : handlers) {
      handler.onNotification(streamKey, eventId);
    }
  }

  private void closeListenConnection() {
    Connection conn = listenConnection;
    listenConnection = null;
    if (conn != null) {
      try {
        conn.close();
      } catch (SQLException e) {
        log.debug("Error closing LISTEN connection", e);
      }
    }
  }

  private void sleepBeforeReconnect() {
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
