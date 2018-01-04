# SQLBuilder

---
    Hyperlight-weight sqlbuilder generating SQL of DDL(table-related), 
    DML and simple DQL; mainly aimed for SQLite3.
    
## General
    This toy tool is NOT FOR 
      - those expert in writing optimized SQL
      - who wants to take control in every details
    just meant FOR
      - who always forgets SQL syntax at a while
      - who is just lazy enough (2333)
  - Syntax Support(only differs in CREATE/TRUNCATE TABLE so far...)
    - SQLite3
    - MySQL (experimental)
  - Examples
    - Code: tk/kahsolt/sqlbuilder/example/Example.java
    - Output: run `java -jar sqlbuilder.jar`
      * 实际生成的sql只有一行，下文为了方便阅读而手工调整了缩进 :) *
  - IDE Build
    - Intellij artifact - JAR
    
## ChangeLog
  - v0.3
```java
// 1.增加defaultValues()函数，用于产生一行DEFAULT VALUES
sqlBuilder.insert("Test").into().defaultValues().end();
// 2.支持生成含参模板(?)，可用在无参数的values()，单参数的set()，无参数的运算符函数如eq()/gt()/like()/between()等等
sqlBuilder.insert("Unknown").into("leave", "me", "blank").values().end();   // 产生的问号个数与into()中列数相同
sqlBuilder.update("People").set("me").end();
sqlBuilder.select("name").from("User")
    .where("gender").eq()
        .and("gender").between().end();
// 3.选择常量/变量
sqlBuilder.select("@@IDENTITY").end();
// 3.支持REPLACE
sqlBuilder.replace("User").into("id", "username")
    .values(5, "hahah").end();
// 4.修复外键约束生成的语法错误的BUG，增加支持设置外键删改策略
```
```sql
INSERT INTO Test DEFAULT VALUES;

INSERT INTO Unknown(leave, me, blank) VALUES(?, ?, ?);
UPDATE People SET me = ?;
SELECT name FROM User WHERE gender = ? AND gender BETWEEN ? AND ?;

SELECT @@IDENTITY;

REPLACE INTO User(id, username) VALUES(5, 'hahah');

FOREIGN KEY(poster) REFERENCES User(id) ON UPDATE RESTRICT ON DELETE RESTRICT
```

  - v0.2
```java
// 1.去掉强制转子查询的sub()函数（已经可以自动判断）
// 2.分步建表：理解表的结构
Table table = sqlBuilder.createTable("Test", true);
Table.Column column = new Table.Column("key");
column.unique();
column.defaultValue(0);
table.column(column);
column = new Table.Column("val");
column.type("FLOAT");
column.defaultValue(null);
table.column(column);
sql = table.end();  // 调用end()才能输出结果
```
```sql
CREATE OR REPLACE TABLE Test (
  key INTEGER UNIQUE DEFAULT 0, 
  val FLOAT NULL
);
```

  - v0.1
    基本功能OK，参考下文初版Quick Start

## Quick Start
```java
import tk.kahsolt.sqlbuilder.*;
SQLBuilder sqlBuilder = new SQLBuilder();   // 默认语法为SQLITE，可传入Dialect.MYSQL
String sql = sqlBuilder.xxx().yyy()...zzz();// 可使用的方法参考下文
                                            // 若调用不当无法产生SQL时会抛出NullPointerException，可酌情处理
System.out.println(sql);
```
### Table
   - CREATE
```java
sqlBuilder.createTable("Message")
    .column("id").autoIncrement().end()             // autoIncrement 默认为 Integer + PK
    .column("poster").referencesTo("User").end()    // referencesTo 默认指向参考表的字段 id(小写)
    .column("content").type(100).defaultValue("这货啥也没说...").end()    // type 默认为 VARCHAR，可仅指出长度
    .column("likes").defaultValue(0).end()          // type 从defaultValue中推测(仅INT/FLOAT/VARCHAR)
    .column("create_time").initSetCurrent().end()   // type 从initSetCurrent或updateSetCurrent推测为TIMESTAMP
    .column("update_time").updateSetCurrent().end() // updateSetCurrent 默认隐含了 initSetCurrent
    .engine("MyISAM").charset("utf8mb4").comment("发帖记录表，也没什么卵用").end();
```
SQLite Syntax:
```sql
CREATE TABLE IF NOT EXISTS Message (
  id INTEGER PRIMARY KEY AUTOINCREMENT, 
  poster INTEGER NULL REFERENCES User(id), 
  content VARCHAR(100) DEFAULT '这货啥也没说...', 
  likes INTEGER DEFAULT 0, 
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, 
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
); 
CREATE TRIGGER update_update_time 
  AFTER UPDATE ON Message 
  FOR EACH ROW 
  WHEN NEW.update_time <= OLD.update_time 
BEGIN 
  UPDATE Message SET update_time = CURRENT_TIMESTAMP WHERE update_time = OLD.update_time; 
END;
```
MySQL Syntax:
```sql
CREATE TABLE IF NOT EXISTS Message (
  id INT PRIMARY KEY AUTO_INCREMENT, 
  poster INT NULL REFERENCES User(id), 
  content VARCHAR(100) DEFAULT '这货啥也没说...', 
  likes INT DEFAULT 0, 
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, 
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=MyISAM DEFAULT CHARSET=utf8mb4 COMMENT='发帖记录表，也没什么卵用';
```

  - DROP
```java
sqlBuilder.dropTable("Message").end();
```
```sql
DROP TABLE IF EXISTS Message;
```

  - TRUNCATE
```java
sqlBuilder.truncateTable("User").end();
```
SQLite Syntax:
```sql
BEGIN; 
  DELETE FROM User; 
  UPDATE sqlite_sequence SET seq = 0 WHERE name = 'User'; 
COMMIT; 
VACUUM User;
```
MySQL Syntax:
```sql
TRUNCATE TABLE User;
```

### Query
  - INSERT
```java
sqlBuilder.insert("Test").into()     // 无参 into() 不生成插入列名表
    .values("The", 2, "parameter").end();
```
```sql
INSERT INTO Test VALUES('The', 2, 'parameter');
```
```java
sqlBuilder.insert("User").into("username", "password", "time")
    .values("kahsolt", 13759, null)           // 单句插入多行
    .values("luper", 18392, "Now").end();
```
```sql
INSERT INTO User(username, password, time)
  VALUES('kahsolt', 13759, NULL), ('luper', 18392, 'Now');
```

  - UPDATE
```java
sqlBuilder.update("User")
    .set("name", "kahsolt")     // 单句更新多列
    .set("age", 12).end();
```
```sql
UPDATE User SET name = 'kahsolt', age = 12;
```
```java
sqlBuilder.update("User")
    .set("isAllowed", false).where("age").lt(18).end(); // 带简单where条件
```
```sql
UPDATE User SET isAllowed = 0 WHERE age < 18;
```

  - DELETE
```java
sqlBuilder.delete("User")
    .where("username").eq("kahsolt")      // 生成的where句中and和or分条件不带括号，请自行组织顺序 :(
        .and("age").between(13, 31)
        .or("gender").like("秀吉").end(); // like(x) 默认产生 LIKE '%x%', 可带参数raw传入原生SQL片段
```
```sql
DELETE FROM User 
  WHERE username = 'kahsolt' 
    AND age BETWEEN 13 AND 31 
    OR gender like '%秀吉%';
```

  - SELECT
```java
sqlBuilder.select(true, "name", "COUNT(price)").from("`Order`")
    .join("Author").on("author")                // join() 默认方向为INNER, on() 默认指向参照表的id字段 => INNER JOIN Author ON author = Author.id
    .join("Book", "OUTER").on("book", "title")  // => OUTER JOIN Book ON book = Book.title
    .where("year").between(2016, 2018)
    .groupBy("author")
    .having("COUNT(title)").gt(5)
    .orderBy("year", true)
    .limit(100).end();
```
```sql
SELECT DISTINCT name, COUNT(price) FROM `Order` 
  INNER JOIN Author ON author = Author.id 
  OUTER JOIN Book ON book = Book.title 
  WHERE year BETWEEN 2016 AND 2018 
  GROUP BY author 
    HAVING COUNT(title) > 5 
  ORDER BY year DESC 
  LIMIT 100;
```
```java
sqlBuilder.select("name").from("User")
    .where("age").gt(
        mysqlBuilder.select("age").from("User")
            .where("gender").eq("female").any().end()   // 量词型子查询，可调用any()/some()/all()
    ).end();
```
```sql
SELECT name FROM User 
  WHERE age > ANY(
    SELECT age FROM User 
      WHERE gender = 'female'
  );
```
```java
sqlBuilder.select("name").from("User")
    .where("age", "job").in(                            // 集合型子查询，可调用in()/notin()，可传入多个列名(列构造器)
        mysqlBuilder.select("age", "job").from("User")
            .where("gender").eq("female").end()
    ).end();
```
```sql
SELECT name FROM User 
  WHERE (age, job) IN (
    SELECT age, job FROM User 
      WHERE gender = 'female'
  );
```

### Transaction
```java
sqlBuilder.begin()
    .block(mysqlBuilder.insert("User").into("username", "age")  // 可以用SQLBuilder嵌套造句
        .values("kahsolt", 13)
        .values("luper", 31).end())
    .block("SELECT COUNT(*) FROM User;")                        // 也可以传手写的SQL
    .commit();
```
```sql
BEGIN; 
  INSERT INTO User(username, age) 
    VALUES('kahsolt', 13), ('luper', 31); 
  SELECT COUNT(*) FROM User; 
COMMIT;
```
