package io.eventuate.local.unified.cdc.pipeline.dblog.common.configuration;

import io.eventuate.local.unified.cdc.pipeline.common.configuration.CommonCdcDefaultPipelinePropertiesConfiguration;
import io.eventuate.local.unified.cdc.pipeline.dblog.common.properties.CommonDbLogCdcPipelineProperties;
import io.eventuate.local.unified.cdc.pipeline.dblog.common.properties.CommonDbLogCdcPipelineReaderProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CommonDbLogCdcDefaultPipelinePropertiesConfiguration extends CommonCdcDefaultPipelinePropertiesConfiguration {

  protected void initCommonDbLogCdcPipelineReaderProperties(CommonDbLogCdcPipelineReaderProperties commonDbLogCdcPipelineReaderProperties) {
    commonDbLogCdcPipelineReaderProperties.setBinlogConnectionTimeoutInMilliseconds(eventuateConfigurationProperties.getBinlogConnectionTimeoutInMilliseconds());
    commonDbLogCdcPipelineReaderProperties.setMaxAttemptsForBinlogConnection(eventuateConfigurationProperties.getMaxAttemptsForBinlogConnection());
    commonDbLogCdcPipelineReaderProperties.setMySqlBinLogClientName(eventuateConfigurationProperties.getMySqlBinLogClientName());
    commonDbLogCdcPipelineReaderProperties.setDbHistoryTopicName(eventuateConfigurationProperties.getDbHistoryTopicName());
  }
}
