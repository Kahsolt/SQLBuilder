package tk.kahsolt.sqlbuilder.sql;

import java.util.*;

public class Query {

    public class Condition {

        private Query query;    // save caller to chain the monad

        private String column;      // left operator
        private String sqlCondition;

        private Condition(String column) {
            this.column = column;
        }

        public Query isnull() {
            sqlCondition = String.format("%s IS NULL", column);
            return query;
        }
        public Query isnotnull() {
            sqlCondition = String.format("%s IS NOT NULL", column);
            return query;
        }
        public Query eq(Object value) {
            sqlCondition = String.format("%s = %s", column, query.convertValue(value));
            return query;
        }
        public Query ne(Object value) {
            sqlCondition = String.format("%s <> %s", column, query.convertValue(value));
            return query;
        }
        public Query gt(Object value) {
            sqlCondition = String.format("%s > %s", column, query.convertValue(value));
            return query;
        }
        public Query ge(Object value) {
            sqlCondition = String.format("%s >= %s", column, query.convertValue(value));
            return query;
        }
        public Query lt(Object value) {
            sqlCondition = String.format("%s < %s", column, query.convertValue(value));
            return query;
        }
        public Query le(Object value) {
            sqlCondition = String.format("%s <= %s", column, query.convertValue(value));
            return query;
        }
        public Query like(String value) {
            sqlCondition = String.format("%s like '%%%s%%'", column, value);
            return query;
        }
        public Query like(String value, boolean raw) {
            if(raw) sqlCondition = String.format("%s like '%s'", column, value);
            else like(value);
            return query;
        }
        public Query between(Object minValue, Object maxValue) {
            sqlCondition = String.format("%s BETWEEN %s AND %s", column, minValue, maxValue);
            return query;
        }

        public Query in(String subquery) {  // equivalent to '= ANY()'
            String subq = convertValue(subquery);
            sqlCondition = String.format("%s IN %s", column, subq);
            return query;
        }
        public Query notin(String subquery) {   // equivalent to '<> ALL()'
            String subq = convertValue(subquery);
            sqlCondition = String.format("%s NOT IN %s", column, subq);
            return query;
        }

    }
    public class Joint {

        private Query query;    // save caller to chain the monad

        private String table;               // the table to join
        private String direction = "INNER"; // the join way
        private String sqlJoint;

        public Joint(String table) { this.table = table; }
        public Joint(String table, String direction) { this(table); this.direction = direction; }

        public Query on(String localColumn) {
            return on(localColumn, "id");
        }
        public Query on(String localColumn, String referedColumn) {
            sqlJoint = String.format("%s JOIN %s ON %s = %s.%s", direction, table, localColumn, table, referedColumn);
            return query;
        }
    }

    private Keyword keyword = Keyword.SELECT;
    private boolean conditionFor = true;   // true for WHERE, false for HAVING; and()/or() use this to judge which clause it belongs to
    private StringBuilder sql;  // cache the build result, maybe useful for recall

    private String table;                                   // ALL                          ; "User"
    private String tables;                                  // SELECT (Cartesian product)   ; "Author, Book"
    private boolean distinct = false;                       // SELECT
    private String columns;                                 // SELECT, INSERT               ; "name, age"
    private ArrayList<String> values;                       // INSERT                       ; ["'kahsolt', 13, NULL"]
    private LinkedHashMap<String, String> sets;             // UPDATE                       ; ["username", "'1379'"]
    private LinkedHashMap<String, Condition> wheres;        // SELECT, UPDATE, DELETE       ; ["AND", "age <= 13"]
    private ArrayList<Joint> joins;                         // SELECT                       ; ["User.id", "Book.author"]
    private String groups;                                  // SELECT                       ; "uid, city"
    private LinkedHashMap<String, Condition> havings;       // SELECT                       ; ["OR", "AVERAGE(price) <= 13"]
    private LinkedHashMap<String, Boolean> orders;          // SELECT                       ; ["update_time", 1]
    private Long[] limits;                                  // SELECT                       ; [25] or [100, 1000]
    private Keyword refiner;                             // SELECT(sub-query)

    public Query() { }
    public Query(String table) { this.table = table; }

    public Query from(String table) {
        if(keyword != Keyword.SELECT || tables!=null) return null;

        this.table = table;
        return this;
    }
    public Query from(String... tables) {
        if(keyword != Keyword.SELECT || table!=null) return null;

        this.tables = String.join(", ", tables);
        return this;
    }
    public Joint join(String table) {
        return join(table, "INNER");
    }
    public Joint join(String table, String direction) {
        if(keyword != Keyword.SELECT) return null;

        if(joins==null) joins = new ArrayList<>();
        Joint joint = new Joint(table, direction);
        joint.query = this;
        joins.add(joint);
        return joint;
    }
    public Query groupBy(String... columns) {
        if(keyword != Keyword.SELECT || groups!=null) return null;

        groups = String.join(", ", Arrays.asList(columns));
        return this;
    }
    public Condition having(String columnAggregation) {
        if(keyword != Keyword.SELECT) return null;

        this.havings = new LinkedHashMap<>();
        Condition condition = new Condition(columnAggregation);
        condition.query = this;
        conditionFor = false;
        havings.put("HAVING", condition);
        return condition;
    }
    public Query orderBy(String column, boolean reverse) {
        if(keyword != Keyword.SELECT) return null;

        if(orders==null) orders = new LinkedHashMap<>();
        orders.put(column, reverse);
        return this;
    }
    public Query orderBy(String... columns) {
        if(keyword != Keyword.SELECT) return null;

        if(orders==null) orders = new LinkedHashMap<>();
        for (String column : columns) {
            orders.put(column, false);
        }
        return this;
    }
    public Query limit(long count) {
        if(keyword != Keyword.SELECT) return null;

        limits = new Long[]{count};
        return this;
    }
    public Query limit(long base, long count) {
        if(keyword != Keyword.SELECT) return null;

        limits = new Long[]{base, count};
        return this;
    }

    public Query into(String... columns) {
        if(keyword != Keyword.INSERT || this.columns!=null) return null;

        this.columns = String.join(", ", Arrays.asList(columns));
        return this;
    }
    public Query values(Object... values) {
        if(keyword != Keyword.INSERT) return null;

        if(values.length!=0) {  // if values only contains one null, then use INSERT Table() DEFAULT VALUES;
            if(this.values==null) this.values = new ArrayList<>();

            ArrayList<String> strVals = new ArrayList<>();
            for (Object value : values) {
                strVals.add(convertValue(value));
            }
            this.values.add(String.join(", ", strVals));
        }
        return this;
    }

    public Query set(String column, Object value) {
        if(keyword != Keyword.UPDATE) return null;

        if(sets==null) sets = new LinkedHashMap<>();
        sets.put(column, convertValue(value));
        return this;
    }

    public Condition where(String column) {
        if(keyword == Keyword.INSERT) return null;

        this.wheres = new LinkedHashMap<>();
        Condition condition = new Condition(column);
        condition.query = this;
        conditionFor = true;
        wheres.put("WHERE", condition);
        return condition;
    }
    public Condition where(String... columns) { // for 'in' sub-query
        if(keyword == Keyword.INSERT) return null;

        this.wheres = new LinkedHashMap<>();
        Condition condition = new Condition(String.format("(%s)", String.join(", ", columns)));
        condition.query = this;
        conditionFor = true;
        wheres.put("WHERE", condition);
        return condition;
    }
    public Condition and(String column) {
        if(wheres==null && havings==null) return null;

        Condition condition = new Condition(column);
        condition.query = this;
        if(conditionFor) wheres.put("AND", condition);
        else havings.put("AND", condition);
        return condition;
    }
    public Condition or(String column) {
        if(wheres==null && havings==null) return null;

        Condition condition = new Condition(column);
        condition.query = this;
        if(conditionFor) wheres.put("OR", condition);
        else havings.put("OR", condition);
        return condition;
    }

    public Query all() {
        refiner = Keyword.ALL;
        return this;
    }
    public Query any() {
        refiner = Keyword.ANY;
        return this;
    }
    public Query some() {
        refiner = Keyword.SOME;
        return this;
    }

    public String end() {
        sql = new StringBuilder();
        switch (keyword) {
            case SELECT:
                if(!buildClause(Keyword.SELECT)) return null;
                buildClause(Keyword.JOIN);
                buildClause(Keyword.WHERE);
                if(buildClause(Keyword.GROUP_BY)) buildClause(Keyword.HAVING);
                buildClause(Keyword.ORDER_BY);
                buildClause(Keyword.LIMIT);
                if(!buildSubqueryRefiner()) buildClause(Keyword.DELIMITER); // subquery with ALL/ANY/SOME() need not terminal comma
                break;
            case INSERT:
                if(!buildClause(Keyword.INSERT)) return null;
                buildClause(Keyword.VALUES);
                buildClause(Keyword.DELIMITER);
                break;
            case UPDATE:
                if(!buildClause(Keyword.UPDATE)) return null;
                if(!buildClause(Keyword.SET)) return null;
                buildClause(Keyword.WHERE);
                buildClause(Keyword.ORDER_BY);
                buildClause(Keyword.LIMIT);
                buildClause(Keyword.DELIMITER);
                break;
            case DELETE:
                if(!buildClause(Keyword.DELETE)) return null;
                buildClause(Keyword.WHERE);
                buildClause(Keyword.ORDER_BY);
                buildClause(Keyword.LIMIT);
                buildClause(Keyword.DELIMITER);
                break;
        }
        return sql.toString();
    }

    private boolean buildClause(Keyword keyword) {
        ArrayList<String> strVals = new ArrayList<>();  // local var for convertion
        switch (keyword) {
            case SELECT:
                if(table!=null || tables!=null) {
                    sql.append("SELECT");
                    if(distinct) sql.append(" DISTINCT");
                    if(tables!=null) sql.append(String.format(" %s FROM %s", columns, tables));
                    else sql.append(String.format(" %s FROM %s", columns, table));
                } else return false;
                break;
            case JOIN:
                if(joins!=null) {
                    sql.append(" ");
                    strVals.clear();
                    for (Joint join : joins) {
                        strVals.add(join.sqlJoint);
                    }
                    sql.append(String.join(" ", strVals));
                } else return false;
                break;
            case GROUP_BY:
                if(groups!=null) {
                    sql.append(String.format(" GROUP BY %s", groups));
                } else return false;
                break;
            case HAVING:
                if(havings!=null) {
                    sql.append(" ");
                    strVals.clear();
                    for (Iterator it = havings.keySet().iterator(); it.hasNext();) {
                        String logicOperator = (String)it.next();
                        Condition condition = havings.get(logicOperator);
                        strVals.add(String.format("%s %s", logicOperator, condition.sqlCondition));
                    }
                    sql.append(String.join(" ", strVals));
                } else return false;
                break;

            case INSERT:
                if(table!=null && columns!=null) {
                    if(columns.equals("")) sql.append(String.format("INSERT INTO %s", table));
                    else sql.append(String.format("INSERT INTO %s(%s)", table, columns));
                } else return false;
                break;
            case VALUES:
                if(values==null) sql.append(" DEFAULT VALUES");
                else {
                    sql.append(" VALUES");
                    strVals.clear();
                    for (String value : values) {
                        strVals.add(String.format("(%s)", value));
                    }
                    sql.append(String.join(", ", strVals));
                }
                break;

            case UPDATE:
                if(table!=null) {
                    sql.append(String.format("UPDATE %s SET", table));
                } else return false;
                break;
            case SET:
                if(sets!=null) {
                    sql.append(" ");
                    strVals.clear();
                    for (Iterator it = sets.keySet().iterator(); it.hasNext(); ) {
                        String column = (String) it.next();
                        String value = sets.get(column);
                        strVals.add(String.format("%s = %s", column, value));
                    }
                    sql.append(String.join(", ", strVals));
                } else return false;
                break;

            case DELETE:
                if(table!=null) {
                    sql.append(String.format("DELETE FROM %s", table));
                } else return false;
                break;

            case WHERE:
                if(wheres!=null) {
                    sql.append(" ");
                    strVals.clear();
                    for (Iterator it = wheres.keySet().iterator(); it.hasNext();) {
                        String logicOperator = (String)it.next();
                        Condition condition = wheres.get(logicOperator);
                        strVals.add(String.format("%s %s", logicOperator, condition.sqlCondition));
                    }
                    sql.append(String.join(" ", strVals));
                } else return false;
                break;
            case ORDER_BY:
                if(orders!=null) {
                    sql.append(" ORDER BY ");
                    strVals.clear();
                    for (Iterator it = orders.keySet().iterator(); it.hasNext();) {
                        String column = (String)it.next();
                        boolean reserve = orders.get(column);
                        if(reserve) strVals.add(String.format("%s DESC", column));
                        else strVals.add(column);
                    }
                    sql.append(String.join(", ", strVals));
                } else return false;
                break;
            case LIMIT:
                if(limits!=null) {
                    sql.append(" LIMIT ");
                    if(limits.length==1) sql.append(limits[0]);
                    else sql.append(String.format("%d, %d", limits[0], limits[1]));
                } else return false;
                break;

            case DELIMITER:
                sql.append(";");
                break;
        }
        return true;
    }

    private boolean buildSubqueryRefiner() {
        if(refiner ==null) return false;
        switch (refiner) {
            case ALL:
                sql.insert(0, "ALL(").append(")");
                break;
            case ANY:
                sql.insert(0, "ANY(").append(")");
                break;
            case SOME:
                sql.insert(0, "SOME(").append(")");
                break;
        }
        return true;
    }

    private String convertValue(Object value) {
        if(value==null)
            return "NULL";
        else if(value instanceof Boolean)
            return (Boolean)value ? "1" : "0";
        else if(value instanceof Number)
            return value.toString();
        else {
            String val = value.toString().trim();
            if(val.endsWith(";")) val = val.substring(0, val.length()-1);
            if(val.length() > 6 && val.substring(0, 6).equalsIgnoreCase("SELECT"))
                return String.format("(%s)", val);
            else if(val.matches("\\(.*\\)") || val.matches("(?i)ALL\\(.*\\)")
                    || val.matches("(?i)ANY\\(.*\\)") || val.matches("(?i)SOME\\(.*\\)"))
                return val;
            else
                return String.format("'%s'", val);
        }
    }

    public Query setDistinct(boolean distinct) {
        this.distinct = distinct;
        return this;
    }

    public Query setColumns(String... columns) {
        this.columns = String.join(", ", Arrays.asList(columns));
        return this;
    }

    public Query setKeyword(Keyword keyword) {
        this.keyword = keyword;
        return this;
    }
}
