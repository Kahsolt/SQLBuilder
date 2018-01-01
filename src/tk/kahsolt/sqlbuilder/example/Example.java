/*
 *  * Copyright (c)
 *  * Author : Kahsolt <kahsolt@qq.com>
 *  * Date : 2018-1-1
 *  * Version : 0.1
 *  * License : GPLv3
 *  * Description : 让我想想这个模块有什么卯月...
 */

package tk.kahsolt.sqlbuilder.example;

import tk.kahsolt.sqlbuilder.*;
import tk.kahsolt.sqlbuilder.sql.*;

public class Example {

    public static void main(String[] args) {
        SQLBuilder sqliteBuilder = new SQLBuilder();    // 默认为 Dialect.SQLITE
        SQLBuilder mysqlBuilder = new SQLBuilder(Dialect.MYSQL);

        System.out.println("Hello SQLBuilder! XD\n--------------------");

        System.out.println("\n===== [DDL] =====");
        String sql = mysqlBuilder.createTable("User", true)
                .column("id").type("INTEGER").primaryKey().end()                  // 行定义以end()结束
                .column("username").type("VARCHAR", 32).unique().notNull().end() // 非主键/时间戳的行默认为可空 NULL
                .column("password").type("VARCHAR", 64).notNull().end()
                .column("create_time").type("TIMESTAMP").initSetCurrent().end()
                .column("update_time").type("TIMESTAMP").initSetCurrent().updateSetCurrent().end()
                .engine("InnoDB").charset("utf8").comment("用户表，没什么卵用").end();   // 表定义以end()结束
        System.out.println(sql);

        // 使用惯例简化表定义
        sql = mysqlBuilder.createTable("Message")
                .column("id").primaryKey().end()                // primaryKey 默认为 Integer + AutoIncrement
                .column("poster").referencesTo("User").end()    // referencesTo 默认指向参考表的字段 id(小写)
                .column("content").type(100).defaultValue("这货啥也没说...").end()    // type 默认为 VARCHAR，可仅指出长度
                .column("likes").defaultValue(0).end()          // type 从defaultValue中推测(仅INT/FLOAT/VARCHAR)
                .column("create_time").initSetCurrent().end()   // type 从initSetCurrent或updateSetCurrent推测为TIMESTAMP
                .column("update_time").updateSetCurrent().end() // updateSetCurrent 默认隐含了 initSetCurrent
                .engine("MyISAM").charset("utf8mb4").comment("发帖记录表，也没什么卵用").end();
        System.out.println(sql);

        // 上表的SQLITE版，时间戳自动更新改写为一个TRIGGER，并忽略不受支持的参数
        sql = sqliteBuilder.createTable("Message")
                .column("id").primaryKey().end()
                .column("poster").referencesTo("User").end()
                .column("content").type(100).defaultValue("这货啥也没说...").end()
                .column("likes").defaultValue(0).end()
                .column("create_time").initSetCurrent().end()
                .column("update_time").updateSetCurrent().end()
                .engine("MyISAM").charset("utf8mb4").comment("发帖记录表，也没什么卵用").end();
        System.out.println(sql);

        // 分步建表：理解表的结构
        Table table = sqliteBuilder.createTable("Test", true);
        Table.Column column = new Table.Column("key");
        column.unique();
        column.defaultValue(0);
        table.column(column);
        column = new Table.Column("val");
        column.type("FLOAT");
        column.defaultValue(null);
        table.column(column);
        sql = table.end();          // 调用end()才能输出结果
        System.out.println(sql);

        sql = mysqlBuilder.dropTable("Message").end();  // 与SQLITE版无语法差别
        System.out.println(sql);

        sql = mysqlBuilder.truncateTable("User").end();
        System.out.println(sql);

        sql = sqliteBuilder.truncateTable("User").end();   // sqlite不支持TRUNCATE子句，转化为 DELETE + 自增基数归零 + VACUUM
        System.out.println(sql);


        System.out.println("\n===== [DML] =====");
        sql = mysqlBuilder.insert("Test").into()     // 无参 into() 不生成插入列名表
                .values("The", 2, "parameter").end();
        System.out.println(sql);
        sql = mysqlBuilder.insert("Const").into("Name", "Value")
                .values("Pi", 3.1415).end();
        System.out.println(sql);
        sql = mysqlBuilder.insert("User").into("username", "password", "time")
                .values("kahsolt", 13759, null)           // 单句插入多行
                .values("luper", 18392, "Now").end();
        System.out.println(sql);

        sql = mysqlBuilder.update("User")
                .set("firstname", "luper").end();
        System.out.println(sql);
        sql = mysqlBuilder.update("User")
                .set("name", "kahsolt")           // 单句更新多列
                .set("age", 12).end();
        System.out.println(sql);
        sql = mysqlBuilder.update("User")
                .set("isAllowed", false).where("age").lt(18).end(); // 带简单where条件
        System.out.println(sql);

        sql = mysqlBuilder.delete("Empty").end();   // 直接清空表
        System.out.println(sql);
        sql = mysqlBuilder.delete("User")
                .where("username").eq("kahsolt")     // 生成的where句中and和or分条件不带括号，请自行组织顺序 :(
                    .and("age").between(13, 31)
                    .or("gender").like("秀吉").end(); // like(x) 默认产生 LIKE '%x%', 可带参数raw传入原生SQL片段
        System.out.println(sql);
        sql = mysqlBuilder.delete("Post")
                .where("LEN(content)").gt(10000)     // 数据库函数调用直接写出来就行(23333)
                    .or("author").isnull()
                    .and("comment").ne(".keep").end();
        System.out.println(sql);


        System.out.println("\n===== [DQL] =====");
        sql = mysqlBuilder.select("username", "time").from("Log")
                .where("username").eq("kahsolt")
                .and("date").gt("2017-5-8")
                .orderBy("date", true).orderBy("username")  // 允许多个orderBy子句，用以单独确定每个列是否逆序
                .limit(20).end();
        System.out.println(sql);

        sql = mysqlBuilder.select("*").from("Author", "Books")   // 多表查询产生笛卡儿积
                .where("price").gt(200)
                .groupBy("author", "year")
                .having("MAX(price)").gt(30)        // 聚合函数也需要直接指出(233333)
                    .and("AVERAGE(price)").between(50, 90)
                .orderBy("name", "price")                                 // orderBy() 默认升序
                .limit(10, 500).end();
        System.out.println(sql);

        sql = mysqlBuilder.select(true, "name", "COUNT(price)").from("`Order`")
                .join("Author").on("author")             // join() 默认方向为INNER, on() 默认指向参照表的id字段 => INNER JOIN Author ON author = Author.id
                .join("Book", "OUTER").on("book", "title")  // => OUTER JOIN Book ON book = Book.title
                .where("year").between(2016, 2018)
                .groupBy("author")
                .having("COUNT(title)").gt(5)
                .orderBy("year", true)
                .limit(100).end();
        System.out.println(sql);

        sql = mysqlBuilder.select("name").from("User")
                .where("age").eq(
                        mysqlBuilder.select("AVERAGE(age)").from("User")
                                .where("gender").eq("female").end()         // 算符型子查询
                ).end();
        System.out.println(sql);

        sql = mysqlBuilder.select("name").from("User")
                .where("age").gt(
                        mysqlBuilder.select("age").from("User")
                                .where("gender").eq("female").any().end()   // 量词型子查询，可调用any()/some()/all()
                ).end();
        System.out.println(sql);

        sql = mysqlBuilder.select("name").from("User")
                .where("age", "job").in(                                        // 集合型子查询，可调用in()/notin()，可传入多个列名(列构造器)
                        mysqlBuilder.select("age", "job").from("User")
                                .where("gender").eq("female").end()
                ).end();
        System.out.println(sql);


        System.out.println("\n===== [DCL] =====");
        sql = mysqlBuilder.begin()
                .block(mysqlBuilder.insert("User").into("username", "age")  // 可以用SQLBuilder嵌套造句
                        .values("kahsolt", 13)
                        .values("luper", 31).end())
                .block("SELECT COUNT(*) FROM User;")                              // 也可以传手写的SQL
                .commit();                                                        // 以commit()结尾，无需end()
        System.out.println(sql);

        System.out.println("\n--------------------\nEnd of SQLBuilder Examples ;)");

    }

}
