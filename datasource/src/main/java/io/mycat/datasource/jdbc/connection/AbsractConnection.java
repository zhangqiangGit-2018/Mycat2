package io.mycat.datasource.jdbc.connection;

import io.mycat.MycatException;
import io.mycat.beans.mysql.MySQLServerStatusFlags;
import io.mycat.beans.resultset.MycatUpdateResponse;
import io.mycat.beans.resultset.MycatUpdateResponseImpl;
import io.mycat.datasource.jdbc.JdbcDataSource;
import io.mycat.datasource.jdbc.JdbcRowBaseIteratorImpl;
import io.mycat.logTip.MycatLogger;
import io.mycat.logTip.MycatLoggerFactory;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public abstract class AbsractConnection {

  private static final MycatLogger LOGGER = MycatLoggerFactory
      .getLogger(AbsractConnection.class);
  protected final Connection connection;
  private JdbcDataSource jdbcDataSource;
  private volatile boolean isClosed = false;

  public AbsractConnection(Connection connection, JdbcDataSource jdbcDataSource) {
    this.connection = connection;
    this.jdbcDataSource = jdbcDataSource;
  }


  public MycatUpdateResponse executeUpdate(String sql, boolean needGeneratedKeys) {
    try (Statement statement = connection.createStatement()) {
      statement.executeUpdate(sql,
          needGeneratedKeys ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS);
      int lastInsertId = 0;
      if (needGeneratedKeys) {
        ResultSet generatedKeys = statement.getGeneratedKeys();
        lastInsertId = (int) (generatedKeys.next() ? generatedKeys.getLong(0) : 0L);
      }
      return new MycatUpdateResponseImpl(statement.getUpdateCount(), lastInsertId,
          MySQLServerStatusFlags.AUTO_COMMIT);
    } catch (Exception e) {
      throw new MycatException(e);
    }
  }


  public JdbcRowBaseIteratorImpl executeQuery(String sql) {
    try {
      Statement statement = connection.createStatement();
      return new JdbcRowBaseIteratorImpl(statement, statement.executeQuery(sql));
    } catch (Exception e) {
      throw new MycatException(e);
    }
  }


  public void onExceptionClose() {
    close();
  }


  public void close() {
    try {
      if (!isClosed) {
        isClosed = true;

        ///connection.close();
      }
    } catch (Exception e) {
      LOGGER.error("", e);
    }
  }

  public void setTransactionIsolation(int transactionIsolation) {
    try {
      if (connection.getTransactionIsolation() != transactionIsolation) {
        try {
          this.connection.setTransactionIsolation(transactionIsolation);
        } catch (SQLException e) {
          throw new MycatException(e);
        }
      }
    } catch (SQLException e) {
      throw new MycatException(e);
    }
  }

  public JdbcDataSource getDataSource() {
    return jdbcDataSource;
  }

  public boolean isClosed() {
    try {
      return connection.isClosed();
    } catch (SQLException e) {
      e.printStackTrace();
      return false;
    }
  }
}