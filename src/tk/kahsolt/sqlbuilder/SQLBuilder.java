/*
 * Copyright (c)
 * Author : Kahsolt <kahsolt@qq.com>
 * Date : 2018-1-1
 * Version : 0.2
 * License : GPLv3
 * Description : 生成器主类，实例化它然后就可以用了
 */

package tk.kahsolt.sqlbuilder;

import tk.kahsolt.sqlbuilder.sql.*;

public class SQLBuilder {

    private Dialect dialect = Dialect.SQLITE;

    public SQLBuilder() { }
    public SQLBuilder(Dialect dialect) { this.dialect = dialect; }

    // DDL
    public Table createTable(String table) { return new Table(table).dialect(dialect); }
    public Table createTable(String table, boolean overwrite) { return new Table(table).dialect(dialect).overwrite(overwrite); }
    public Table dropTable(String table) { return new Table(table, Keyword.DROP); }
    public Table truncateTable(String table) { return new Table(table, Keyword.TRUNCATE).dialect(dialect); }

    // DML
    public Query insert(String table) { return new Query(table).setKeyword(Keyword.INSERT); }
    public Query update(String table) { return new Query(table).setKeyword(Keyword.UPDATE); }
    public Query delete(String table) { return new Query(table).setKeyword(Keyword.DELETE); }

    // DQL
    public Query select(String... columns) { return new Query().setColumns(columns); }
    public Query select(boolean distinct, String... columns) { return new Query().setColumns(columns).setDistinct(distinct); }

    // DCL
    public Transaction begin() { return new Transaction(); }

    // Raw
    public String raw(String sql) { return sql.trim().endsWith(";") ? sql.trim() : String.format("%s;", sql.trim()); }

}
