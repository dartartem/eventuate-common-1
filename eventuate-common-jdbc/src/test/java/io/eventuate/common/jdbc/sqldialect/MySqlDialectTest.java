package io.eventuate.common.jdbc.sqldialect;

import java.util.Optional;

public class MySqlDialectTest extends AbstractDialectTest {

  public MySqlDialectTest() {
    super("mysql",
            "com.mysql.cj.jdbc.Driver",
            MySqlDialect.class,
            "ROUND(UNIX_TIMESTAMP(CURTIME(4)) * 1000)",
            Optional.empty());
  }
}
