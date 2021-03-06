package tk.kahsolt.sqlbuilder.sql;

import java.util.ArrayList;

public class Table {

    public static class Column {

        private Table table;

        private String name;
        private String type;
        private boolean referencesTo;
        private Object defaultValue;

        private boolean isAutoincrement = false;
        private boolean isPrimaryKey = false;
        private boolean isNotNull = false;
        private boolean isUnique = false;
        private boolean isInitSetCurrent = false;
        private boolean isUpdateSetCurrent = false;

        public Column(String name) { this.name = name; }
        public Column(String name, Table table) { this.name = name; this.table = table; }
        public Column type(String type) { this.type = type.toUpperCase(); return this; }
        public Column type(long length) { this.type = String.format("VARCHAR(%d)", length); return this; }
        public Column type(String type, long length) { this.type = String.format("%s(%d)", type.toUpperCase(), length); return this; }
        public Column type(String type, long length, int precision) { this.type = String.format("%s(%d, %d)", type.toUpperCase(), length, precision); return this; }
        public Column defaultValue(Object defaultValue) { this.defaultValue = defaultValue; return this; }
        public Column referencesTo(String table) { return referencesTo(table, "id", false, false); }
        public Column referencesTo(String table, boolean isUpdateCascade, boolean isDeleteCascade) { return referencesTo(table, "id", isUpdateCascade, isDeleteCascade); }
        public Column referencesTo(String table, String column, boolean isUpdateCascade, boolean isDeleteCascade) {   // 默认false:RESTRICT
            referencesTo = true;
            String actUp = "";
            String actDel = "";
            if(isUpdateCascade) actUp = " ON UPDATE CASCADE";
            if(isDeleteCascade) actDel = " ON DELETE CASCADE";
            if(this.table.foreignkeys ==null) this.table.foreignkeys = new ArrayList<String>();
            this.table.foreignkeys.add(String.format("FOREIGN KEY(%s) REFERENCES %s(%s)%s%s",
                    this.name, table, column, actUp, actDel));
            return this;
        }

        public Column autoIncrement() { return autoIncrement(true); }
        public Column autoIncrement(boolean enable) { if(enable) isAutoincrement = isPrimaryKey = true; return this; }
        public Column primaryKey() { return primaryKey(true); }
        public Column primaryKey(boolean enable) { isPrimaryKey = enable; return this; }
        public Column notNull() { return notNull(true); }
        public Column notNull(boolean enable) { isNotNull = enable; return this; }
        public Column unique() { return unique(true); }
        public Column unique(boolean enable) { isUnique = enable; return this; }
        public Column initSetCurrent() { return initSetCurrent(true); }
        public Column initSetCurrent(boolean enable) { isInitSetCurrent = enable; return this; }
        public Column updateSetCurrent() { return updateSetCurrent(true); }
        public Column updateSetCurrent(boolean enable) { if(enable) isUpdateSetCurrent = isInitSetCurrent = true; return this; }

        public Table end() { return table; }

    }

    private Dialect dialect = Dialect.SQLITE;
    private Keyword keyword = Keyword.CREATE;

    private String table;
    private boolean overwrite = false;
    private ArrayList<Column> columns;
    private ArrayList<String> foreignkeys;
    private String engine;
    private String charset;
    private String comment;

    public Table(String table) { this.table = table; }
    public Table(String table, Keyword keyword) { this.table = table; this.keyword = keyword; }

    public Table dialect(Dialect dialect) { this.dialect = dialect; return this; }
    public Table overwrite(boolean overwrite) { this.overwrite = overwrite; return this; }
    public Table engine(String engine) { this.engine = engine; return this; }
    public Table charset(String charset) { this.charset = charset; return this; }
    public Table comment(String comment) { this.comment = comment; return this; }

    public Column column(String name) {
        if(columns==null) columns = new ArrayList<>();
        Column column = new Column(name, this);
        columns.add(column);
        return column;
    }
    public Table column(Column column) {
        if(columns==null) columns = new ArrayList<>();
        column.table = this;
        columns.add(column);
        return this;
    }
    public Column add(String name) {
        if(columns==null) columns = new ArrayList<>();
        Column column = new Column(name, this);
        columns.add(column);
        return column;
    }

    public String end() {
        StringBuilder sb = new StringBuilder();
        ArrayList<String> triggers = new ArrayList<>();    // create trigger for SQLite TIMESTAMP fields
        ArrayList<String> cols;
        switch (keyword) {
            case CREATE:
                if(overwrite) sb.append(String.format("CREATE OR REPLACE TABLE %s (", table));
                else sb.append(String.format("CREATE TABLE IF NOT EXISTS %s (", table));
                cols = new ArrayList<>();
                // column def
                for (Column col : columns) cols.add(buildColumn(col, triggers));
                // table FK constraint
                if(foreignkeys!=null) cols.addAll(foreignkeys);
                sb.append(String.join(", ", cols));
                sb.append(")");
                if(dialect == Dialect.MYSQL) {
                    if(engine!=null) sb.append(String.format(" ENGINE=%s", engine));
                    if(charset!=null) sb.append(String.format(" DEFAULT CHARSET=%s", charset));
                    if(comment!=null) sb.append(String.format(" COMMENT='%s'", comment));
                }
                sb.append(";");
                for (String col : triggers) {
                    sb.append(String.format(" CREATE TRIGGER update_%s_%s AFTER UPDATE ON %s" +
                            " FOR EACH ROW WHEN NEW.%s <= OLD.%s BEGIN" +
                            " UPDATE %s SET %s = CURRENT_TIMESTAMP WHERE %s = OLD.%s;" +
                            " END;", table, col, table, col, col, table, col, col, col));
                }
                break;
            case ALTER:
                sb.append(String.format("ALTER TABLE %s ADD ", table));
                cols = new ArrayList<>();
                for (Column col : columns) cols.add(buildColumn(col, triggers));
                sb.append(String.join(", ", cols));
                sb.append(";");
                for (String col : triggers) {
                    sb.append(String.format(" CREATE TRIGGER update_%s_%s AFTER UPDATE ON %s" +
                            " FOR EACH ROW WHEN NEW.%s <= OLD.%s BEGIN" +
                            " UPDATE %s SET %s = CURRENT_TIMESTAMP WHERE %s = OLD.%s;" +
                            " END;", table, col, table, col, col, table, col, col, col));
                }
                break;
            case DROP:
                sb.append(String.format("DROP TABLE IF EXISTS %s;", table));
                break;
            case TRUNCATE:
                switch (dialect) {
                    case MYSQL:
                        sb.append(String.format("TRUNCATE TABLE %s;", table));
                        break;
                    case SQLITE:
                        sb.append(String.format("BEGIN;" +
                                " DELETE FROM %s;" +
                                " UPDATE sqlite_sequence SET seq = 0 WHERE name = '%s';" +
                                " COMMIT;" +
                                " VACUUM %s; ", table, table, table));
                }
                break;
        }
        return sb.toString();
    }

    private String buildColumn(Column col, ArrayList<String> triggers) {
        ArrayList<String> segs = new ArrayList<>();
        // Name
        segs.add(col.name);
        // Type
        if(col.isInitSetCurrent || col.isUpdateSetCurrent) {
            segs.add("TIMESTAMP");
        } else if(col.isAutoincrement || (col.type==null && col.referencesTo)) {
            if(dialect == Dialect.MYSQL) segs.add("INT");
            else segs.add("INTEGER");
        } else if(col.type==null && col.defaultValue!=null) {
            if(col.defaultValue instanceof Integer || col.defaultValue instanceof Short || col.defaultValue instanceof Long) {
                if(dialect == Dialect.MYSQL) segs.add("INT");
                else segs.add("INTEGER");
            } else if(col.defaultValue instanceof Double || col.defaultValue instanceof Float) {
                segs.add("FLOAT");
            } else segs.add("VARCHAR");
        } else if(col.type!=null) {
            String t = col.type.trim().toUpperCase();
            if(dialect==Dialect.SQLITE && t.equalsIgnoreCase("INT")) segs.add("INTEGER");
            else segs.add(t);
        } else segs.add("VARCHAR");
        // PK + AI
        if(col.isPrimaryKey) segs.add("PRIMARY KEY");
        // AI
        if(col.isAutoincrement) {
            if(dialect == Dialect.MYSQL) segs.add("AUTO_INCREMENT");
            else segs.add("AUTOINCREMENT");
        }
        // UQ
        if(col.isUnique) segs.add("UNIQUE");
        // NN
        if(col.isNotNull) segs.add("NOT NULL");
        else if(!col.isPrimaryKey && !col.isInitSetCurrent && col.defaultValue==null) segs.add("NULL");
        // CT/OUCT for Timestamp
        switch (dialect) {
            case SQLITE:
                if(col.isInitSetCurrent) segs.add("DEFAULT CURRENT_TIMESTAMP");
                if(col.isUpdateSetCurrent) triggers.add(col.name);
                break;
            case MYSQL:
                if(col.isInitSetCurrent) segs.add("DEFAULT CURRENT_TIMESTAMP");
                if(col.isUpdateSetCurrent) segs.add("ON UPDATE CURRENT_TIMESTAMP");
                break;
        }
        // DEFAULT
        if(col.defaultValue!=null) {
            if(col.defaultValue instanceof Number) segs.add(String.format("DEFAULT %s", col.defaultValue));
            else segs.add(String.format("DEFAULT '%s'", col.defaultValue));
        }
        return String.join(" ", segs);
    }

}
