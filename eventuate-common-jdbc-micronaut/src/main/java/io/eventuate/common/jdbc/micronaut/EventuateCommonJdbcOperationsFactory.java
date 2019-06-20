package io.eventuate.common.jdbc.micronaut;

import io.eventuate.common.jdbc.EventuateCommonJdbcOperations;
import io.eventuate.common.jdbc.EventuateJdbcStatementExecutor;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;

import javax.inject.Singleton;
import javax.sql.DataSource;

@Factory
@Requires(property = "micronaut.eventuate.common.jdbc.operations.factory", value = "true")
public class EventuateCommonJdbcOperationsFactory {

  @Singleton
  public EventuateJdbcStatementExecutor eventuateJdbcStatementExecutor(DataSource dataSource) {
    return new EventuateMicronautJdbcStatementExecutor(dataSource);
  }

  @Singleton
  public EventuateCommonJdbcOperations eventuateCommonJdbcOperations(EventuateJdbcStatementExecutor eventuateJdbcStatementExecutor) {
    return new EventuateCommonJdbcOperations(eventuateJdbcStatementExecutor);
  }
}