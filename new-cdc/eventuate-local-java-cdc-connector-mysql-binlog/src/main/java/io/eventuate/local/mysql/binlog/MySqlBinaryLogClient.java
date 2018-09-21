package io.eventuate.local.mysql.binlog;


import com.github.shyiko.mysql.binlog.BinaryLogClient;
import com.github.shyiko.mysql.binlog.event.*;
import com.github.shyiko.mysql.binlog.event.deserialization.EventDeserializer;
import com.github.shyiko.mysql.binlog.event.deserialization.NullEventDataDeserializer;
import com.github.shyiko.mysql.binlog.event.deserialization.WriteRowsEventDataDeserializer;
import com.sun.scenario.effect.Offset;
import io.eventuate.local.common.BinlogFileOffset;
import io.eventuate.local.common.EventuateLeaderSelectorListener;
import io.eventuate.local.common.JdbcUrl;
import io.eventuate.local.common.JdbcUrlParser;
import io.eventuate.local.db.log.common.DbLogClient;
import io.eventuate.local.db.log.common.OffsetStore;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class MySqlBinaryLogClient implements DbLogClient {

  private String name;

  private BinaryLogClient client;
  private long binlogClientUniqueId;

  private final String dbUserName;
  private final String dbPassword;
  private final String host;
  private final int port;
  private String defaultDatabase;
  private DataSource dataSource;

  private final Map<Long, TableMapEventData> tableMapEventByTableId = new HashMap<>();
  private String binlogFilename;
  private long offset;

  private int connectionTimeoutInMilliseconds;
  private int maxAttemptsForBinlogConnection;

  private Logger logger = LoggerFactory.getLogger(this.getClass());

  private List<MySqlBinlogEntryHandler> binlogEntryHandlers = new CopyOnWriteArrayList<>();

  private AtomicBoolean running = new AtomicBoolean(false);

  private LeaderSelector leaderSelector;

  private CuratorFramework curatorFramework;
  private String leadershipLockPath;

  private OffsetStore offsetStore;
  private DebeziumBinlogOffsetKafkaStore debeziumBinlogOffsetKafkaStore;

  public MySqlBinaryLogClient(String dbUserName,
                              String dbPassword,
                              String dataSourceUrl,
                              DataSource dataSource,
                              long binlogClientUniqueId,
                              String clientName,
                              int connectionTimeoutInMilliseconds,
                              int maxAttemptsForBinlogConnection,
                              CuratorFramework curatorFramework,
                              String leadershipLockPath,
                              OffsetStore offsetStore,
                              DebeziumBinlogOffsetKafkaStore debeziumBinlogOffsetKafkaStore) {

    this.binlogClientUniqueId = binlogClientUniqueId;
    this.dbUserName = dbUserName;
    this.dbPassword = dbPassword;

    JdbcUrl jdbcUrl = JdbcUrlParser.parse(dataSourceUrl);
    host = jdbcUrl.getHost();
    port = jdbcUrl.getPort();
    defaultDatabase = jdbcUrl.getDatabase();


    this.dataSource = dataSource;
    this.name = clientName;
    this.connectionTimeoutInMilliseconds = connectionTimeoutInMilliseconds;
    this.maxAttemptsForBinlogConnection = maxAttemptsForBinlogConnection;

    this.curatorFramework = curatorFramework;
    this.leadershipLockPath = leadershipLockPath;

    this.offsetStore = offsetStore;
    this.debeziumBinlogOffsetKafkaStore = debeziumBinlogOffsetKafkaStore;
  }

  public OffsetStore getOffsetStore() {
    return offsetStore;
  }

  public DataSource getDataSource() {
    return dataSource;
  }

  public void addBinlogEntryHandler(MySqlBinlogEntryHandler binlogEntryHandler) {
    binlogEntryHandlers.add(binlogEntryHandler);
  }

  @Override
  public void start() {
    leaderSelector = new LeaderSelector(curatorFramework, leadershipLockPath,
            new EventuateLeaderSelectorListener(this::leaderStart, this::leaderStop));

    leaderSelector.start();
  }

  private void leaderStart() {
    running.set(true);

    client = new BinaryLogClient(host, port, dbUserName, dbPassword);
    client.setServerId(binlogClientUniqueId);
    client.setKeepAliveInterval(5 * 1000);

    Optional<BinlogFileOffset> binlogFileOffset = getStartingBinlogFileOffset();

    BinlogFileOffset bfo = binlogFileOffset.orElse(new BinlogFileOffset("", 4L));

    logger.debug("Starting with {}", bfo);
    client.setBinlogFilename(bfo.getBinlogFilename());
    client.setBinlogPosition(bfo.getOffset());

    client.setEventDeserializer(getEventDeserializer());
    client.registerEventListener(event -> {
      switch (event.getHeader().getEventType()) {
        case TABLE_MAP: {
          TableMapEventData tableMapEvent = event.getData();
          tableMapEventByTableId.put(tableMapEvent.getTableId(), tableMapEvent);
          break;
        }
        case EXT_WRITE_ROWS: {
          handleWriteRowsEvent(event, binlogFileOffset);
          break;
        }
        case WRITE_ROWS: {
          handleWriteRowsEvent(event, binlogFileOffset);
          break;
        }
        case ROTATE: {
          RotateEventData eventData = event.getData();
          if (eventData != null) {
            binlogFilename = eventData.getBinlogFilename();
          }
          break;
        }
      }
    });

    connectWithRetriesOnFail();
  }

  private Optional<BinlogFileOffset> getStartingBinlogFileOffset() {
    Optional<BinlogFileOffset> binlogFileOffset = offsetStore.getLastBinlogFileOffset();

    if (!binlogFileOffset.isPresent()) {
      binlogFileOffset = debeziumBinlogOffsetKafkaStore.getLastBinlogFileOffset();
    }

    return binlogFileOffset;
  }

  private void handleWriteRowsEvent(Event event, Optional<BinlogFileOffset> startingBinlogFileOffset) {
    logger.debug("Got binlog event {}", event);
    offset = ((EventHeaderV4) event.getHeader()).getPosition();
    WriteRowsEventData eventData = event.getData();
    if (tableMapEventByTableId.containsKey(eventData.getTableId())) {

      TableMapEventData tableMapEventData = tableMapEventByTableId.get(eventData.getTableId());

      String database = tableMapEventData.getDatabase();
      String table = tableMapEventData.getTable();

      binlogEntryHandlers
              .stream()
              .filter(bh -> bh.isFor(database, table, defaultDatabase))
              .forEach(bh -> bh.accept(eventData, getCurrentBinlogFilename(), offset, startingBinlogFileOffset));
    }
  }

  private void connectWithRetriesOnFail() {
    for (int i = 1;; i++) {
      try {
        logger.info("trying to connect to mysql binlog");
        client.connect(connectionTimeoutInMilliseconds);
        logger.info("connection to mysql binlog succeed");
        break;
      } catch (TimeoutException | IOException e) {
        logger.error("connection to mysql binlog failed");
        if (i == maxAttemptsForBinlogConnection) {
          logger.error("connection attempts exceeded");
          throw new RuntimeException(e);
        }
        try {
          Thread.sleep(connectionTimeoutInMilliseconds);
        } catch (InterruptedException ex) {
          throw new RuntimeException(ex);
        }
      }
    }
  }

  private EventDeserializer getEventDeserializer() {
    EventDeserializer eventDeserializer = new EventDeserializer();

    // do not deserialize binlog events except the EXT_WRITE_ROWS, WRITE_ROWS, and TABLE_MAP
    Arrays.stream(EventType.values()).forEach(eventType -> {
      if (eventType != EventType.EXT_WRITE_ROWS &&
              eventType != EventType.TABLE_MAP &&
              eventType != EventType.WRITE_ROWS &&
              eventType != EventType.ROTATE) {
        eventDeserializer.setEventDataDeserializer(eventType,
                new NullEventDataDeserializer());
      }
    });

    eventDeserializer.setEventDataDeserializer(EventType.EXT_WRITE_ROWS,
            new WriteRowsEventDataDeserializer(
                    tableMapEventByTableId).setMayContainExtraInformation(true));

    eventDeserializer.setEventDataDeserializer(EventType.WRITE_ROWS,
            new WriteRowsEventDataDeserializer(
                    tableMapEventByTableId));

    return eventDeserializer;
  }

  @Override
  public void stop() {
    leaderSelector.close();
    leaderStop();

    binlogEntryHandlers.clear();
  }

  private void leaderStop() {
    if (!running.compareAndSet(true, false)) {
      return;
    }

    try {
      client.disconnect();
    } catch (IOException e) {
      logger.error("Cannot stop the MySqlBinaryLogClient", e);
    }
  }

  public String getCurrentBinlogFilename() {
    return this.binlogFilename;
  }

  public long getCurrentOffset() {
    return this.offset;
  }

  public String getName() {
    return name;
  }
}
