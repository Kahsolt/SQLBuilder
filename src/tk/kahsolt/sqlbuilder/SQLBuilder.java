/*
 * Copyright (c)
 * Author : Kahsolt <kahsolt@qq.com>
 * Create Date : 2018-1-1
 * Update Date : 2018-1-5
 * Version : 0.3.2
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
    public Table createTable(String table) { return createTable(table, false); }
    public Table createTable(String table, boolean overwrite) { return new Table(table).dialect(dialect).overwrite(overwrite); }
    public Table dropTable(String table) { return new Table(table, Keyword.DROP); }
    public Table truncateTable(String table) { return new Table(table, Keyword.TRUNCATE).dialect(dialect); }

    // DML
    public Query insert(String table) { return new Query(table).setKeyword(Keyword.INSERT); }
    public Query replace(String table) { return new Query(table).setKeyword(Keyword.REPLACE); }
    public Query update(String table) { return new Query(table).setKeyword(Keyword.UPDATE); }
    public Query delete(String table) { return new Query(table).setKeyword(Keyword.DELETE); }

    // DQL
    public Query select(String... columns) { return select(false, columns); }
    public Query select(boolean distinct, String... columns) { return new Query().setColumns(columns).setDistinct(distinct); }

    // DCL
    public Transaction begin() { return new Transaction(); }

}
