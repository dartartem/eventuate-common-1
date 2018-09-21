package io.eventuate.local.postgres.wal;

import io.eventuate.javaclient.commonimpl.EntityIdVersionAndEventIds;
import io.eventuate.javaclient.spring.jdbc.EventuateJdbcAccess;
import io.eventuate.javaclient.spring.jdbc.EventuateSchema;
import io.eventuate.local.common.*;
import io.eventuate.local.db.log.common.OffsetStore;
import io.eventuate.local.java.jdbckafkastore.EventuateLocalAggregateCrud;
import io.eventuate.local.test.util.AbstractCdcTest;
import org.apache.curator.framework.CuratorFramework;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

public abstract class AbstractPostgresWalCdcIntegrationTest extends AbstractCdcTest {

  @Value("${spring.datasource.url}")
  private String dataSourceURL;

  @Value("${spring.datasource.username}")
  private String dbUserName;

  @Value("${spring.datasource.password}")
  private String dbPassword;

  @Autowired
  private DataSource dataSource;

  @Autowired
  private EventuateJdbcAccess eventuateJdbcAccess;

  @Autowired
  private EventuateConfigurationProperties eventuateConfigurationProperties;

  @Autowired
  private SourceTableNameSupplier sourceTableNameSupplier;

  @Autowired
  private EventuateSchema eventuateSchema;

  @Autowired
  private CuratorFramework curatorFramework;

  @Autowired
  private OffsetStore offsetStore;

  @Test
  public void shouldGetEvents() throws InterruptedException{
    PostgresWalClient postgresWalClient = new PostgresWalClient(dataSourceURL,
            dbUserName,
            dbPassword,
            dataSource,
            eventuateConfigurationProperties.getMySqlBinLogClientName(),
            eventuateConfigurationProperties.getBinlogConnectionTimeoutInMilliseconds(),
            eventuateConfigurationProperties.getMaxAttemptsForBinlogConnection(),
            eventuateConfigurationProperties.getPostgresWalIntervalInMilliseconds(),
            eventuateConfigurationProperties.getPostgresReplicationStatusIntervalInMilliseconds(),
            eventuateConfigurationProperties.getPostgresReplicationSlotName(),
            curatorFramework,
            eventuateConfigurationProperties.getLeadershipLockPath(),
            offsetStore);

    EventuateLocalAggregateCrud localAggregateCrud = new EventuateLocalAggregateCrud(eventuateJdbcAccess);

    BlockingQueue<PublishedEvent> publishedEvents = new LinkedBlockingDeque<>();

    BinlogEntryToPublishedEventConverter binlogEntryToPublishedEventConverter = new BinlogEntryToPublishedEventConverter();

    PostgresWalBinlogEntryHandler binlogEntryHandler = new PostgresWalBinlogEntryHandler(eventuateSchema,
            sourceTableNameSupplier.getSourceTableName(),
            (binlogEntry, offset) -> publishedEvents.add(binlogEntryToPublishedEventConverter.convert(binlogEntry)));

    postgresWalClient.addBinlogEntryHandler(binlogEntryHandler);

    postgresWalClient.start();

    String accountCreatedEventData = generateAccountCreatedEvent();
    EntityIdVersionAndEventIds saveResult = saveEvent(localAggregateCrud, accountCreatedEventData);

    String accountDebitedEventData = generateAccountDebitedEvent();
    EntityIdVersionAndEventIds updateResult = updateEvent(saveResult.getEntityId(), saveResult.getEntityVersion(), localAggregateCrud, accountDebitedEventData);

    // Wait for 10 seconds
    LocalDateTime deadline = LocalDateTime.now().plusSeconds(10);

    waitForEvent(publishedEvents, saveResult.getEntityVersion(), deadline, accountCreatedEventData);
    waitForEvent(publishedEvents, updateResult.getEntityVersion(), deadline, accountDebitedEventData);
    postgresWalClient.stop();
  }

}
