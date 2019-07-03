/**
 * Copyright (C) <2019>  <chen junwen>
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program.  If
 * not, see <http://www.gnu.org/licenses/>.
 */
package io.mycat.router;

import io.mycat.MycatException;
import io.mycat.beans.mycat.MycatSchema;
import io.mycat.config.schema.SchemaType;
import io.mycat.router.routeResult.OneServerResultRoute;
import io.mycat.router.routeStrategy.SqlParseRouteRouteStrategy;
import io.mycat.sqlparser.util.BufferSQLContext;
import io.mycat.sqlparser.util.BufferSQLParser;

/**
 * @author jamie12221 date 2019-05-05 17:04
 **/
public class MycatRouter implements RouteStrategy<RouteContext> {

  final MycatRouterConfig config;
  final RouteContext context;
  final BufferSQLContext sqlContext;
  final BufferSQLParser parser;

  public MycatRouter(MycatRouterConfig config) {
    this.config = config;
    this.context = new RouteContext(config);
    this.sqlContext = new BufferSQLContext();
    this.parser = new BufferSQLParser();
  }

  private BufferSQLParser sqlParser() {
    return parser;
  }

  private BufferSQLContext sqlContext() {
    return sqlContext;
  }

  public ResultRoute enterRoute(MycatSchema defaultSchema, String sql) {
    BufferSQLContext bufferSQLContext = sqlContext();
    sqlParser().parse(sql.getBytes(), bufferSQLContext);
    return enterRoute(defaultSchema, bufferSQLContext, sql);
  }

  public BufferSQLContext simpleParse(String sql) {
    BufferSQLContext bufferSQLContext = sqlContext();
    sqlParser().parse(sql.getBytes(), bufferSQLContext);
    return bufferSQLContext;
  }

  public ResultRoute enterRoute(MycatSchema defaultSchema, BufferSQLContext sqlContext,
      String sql) {
    this.context.clear();
    this.context.setSqlContext(sqlContext);
    int sqlType = sqlContext.getSQLType();

    MycatProxyStaticAnnotation sa = sqlContext.getStaticAnnotation()
        .toMapAndClear(this.context.getStaticAnnotation());
    String balance = sa.getBalance();
    Boolean runOnMaster = sa.getRunOnMaster();

    ResultRoute routeResult = null;
    try {
      if (sa.getDataNode() != null) {
        OneServerResultRoute osr = new OneServerResultRoute();
        osr.setDataNode(sa.getDataNode());
        osr.setSql(sql);
        return routeResult = osr;
      } else if (sa.getSchema() != null) {
        defaultSchema = config.getSchemaBySchemaName(sa.getSchema());
        if (defaultSchema == null) {
          throw new MycatException("can not find schema:{}", sa.getSchema());
        }
      }
      if (defaultSchema.getSchemaType() == SchemaType.DB_IN_ONE_SERVER) {
        OneServerResultRoute osr = new OneServerResultRoute();
        osr.setDataNode(defaultSchema.getDefaultDataNode());
        osr.setSql(sql);
        return routeResult = osr;
      }
      int schemaCount = sqlContext.getSchemaCount();
      if (schemaCount == 0) {
        RouteStrategy routeStrategy = defaultSchema.getRouteStrategy();
        return routeResult = routeStrategy.route(defaultSchema, sql, this.context);
      }
      if (schemaCount == 1) {
        String schemaName = sqlContext.getSchemaName(0);
        MycatSchema schema = config.getSchemaBySchemaName(schemaName);
        if (schema == null) {
          throw new MycatException("can not find schema:{}", schemaName);
        }
        RouteStrategy routeStrategy = schema.getRouteStrategy();
        return routeResult = routeStrategy.route(schema, sql, this.context);
      } else {
        return routeResult = this.route(defaultSchema, sql, this.context);
      }
    } finally {
      if (routeResult != null) {
        if (balance != null) {
          routeResult.setBalance(balance);
        }
        if (runOnMaster != null) {
          routeResult.setRunOnMaster(runOnMaster);
        }
      }
    }
  }
//
//  public MySQLCommandRouteResultRoute enterRoute(String defaultSchemaName, int commandPakcet) {
//    MycatSchema defaultSchema = config.getSchemaBySchemaName(defaultSchemaName);
//    return enterRoute(defaultSchema, commandPakcet);
//  }
//
//  public MySQLCommandRouteResultRoute enterRoute(MycatSchema defaultSchema, int commandPakcet) {
//    String defaultDataNode = defaultSchema.getDefaultDataNode();
//    MySQLCommandRouteResultRoute result = new MySQLCommandRouteResultRoute();
//    result.setCmd(commandPakcet);
//    result.setDataNode(defaultDataNode);
//    return result;
//  }

  public ResultRoute enterRoute(String defaultSchemaName, String sql) {
    MycatSchema defaultSchema = config.getSchemaBySchemaName(defaultSchemaName);
    if (defaultSchema == null) {
      throw new MycatException("can not find schema:{}", defaultSchemaName);
    }
    return enterRoute(defaultSchema, sql);
  }

  @Override
  public ResultRoute route(MycatSchema schema, String sql, RouteContext routeContext) {
    SqlParseRouteRouteStrategy strategy = routeContext.getSqlParseRouteRouteStrategy();
    strategy.route(schema, sql, context);
    return null;
  }

  public MycatRouterConfig getConfig() {
    return config;
  }

  public MycatSchema getDefaultSchema() {
    return config.getDefaultSchema();
  }

  public MycatSchema getSchemaBySchemaName(String db) {
    return config.getSchemaBySchemaName(db);
  }

  public MycatSchema getSchemaOrDefaultBySchemaName(String name) {
    return config.getSchemaOrDefaultBySchemaName(name);
  }
}
