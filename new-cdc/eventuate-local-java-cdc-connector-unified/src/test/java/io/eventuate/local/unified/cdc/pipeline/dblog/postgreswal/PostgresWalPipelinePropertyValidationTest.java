package io.eventuate.local.unified.cdc.pipeline.dblog.postgreswal;

import io.eventuate.local.unified.cdc.pipeline.common.CommonPipelinePropertyValidationTest;
import io.eventuate.local.unified.cdc.pipeline.dblog.postgreswal.factory.PostgresWalCdcPipelineFactory;
import io.eventuate.local.unified.cdc.pipeline.dblog.postgreswal.properties.PostgresWalCdcPipelineProperties;
import org.junit.Test;

public class PostgresWalPipelinePropertyValidationTest extends CommonPipelinePropertyValidationTest {
  @Test
  public void testPostgresWalProperties() throws Exception {
    PropertyBuilder propertyBuilder = new PropertyBuilder();
    assertExceptionMessage(propertyBuilder.toString(), PostgresWalCdcPipelineProperties.class, "type must not be null");

    propertyBuilder.addString("type", PostgresWalCdcPipelineFactory.TYPE);
    testCommonRequiredProperties(PostgresWalCdcPipelineProperties.class, propertyBuilder);

    assertNoException(propertyBuilder.toString(), PostgresWalCdcPipelineProperties.class);
  }
}
