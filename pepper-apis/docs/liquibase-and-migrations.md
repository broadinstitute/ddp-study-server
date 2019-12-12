# Liquibase and Migrations

[Liquibase][liquibase] is a tool that pepper is using to manage database
migrations. Currently, the liquibase utility runs during pepper server bootup
to execute migration of any new database changes. The changes are written in
XML files and registered in the file `changelog-master.xml`.

## ChangeSets and Transactions

Database changes are organized into [changesets][changeset]. When the
underlying database supports it, liquibase will run changesets within a
transaction. Since pepper is backed by MySQL (and hsqldb) this should always
be the case. What this means is that liquibase will take each changeset and
start a transaction to run it within. When it's done and successful, the
transaction is committed. But if a change within the changeset fails, the
transaction (and thus the whole changeset) is rolled back.

```xml
<changeSet id="example-changeset" author="...">
    <insert tableName="...">
        ...
    </insert>
</changeSet>
```

### Implicitly Committed Changes

The gotcha is that some changes cannot be rolled back as part of the
changeset transaction. Some sql statements causes an implicit commit, and
thus cannot be rolled back even when used within a transaction since it has
already been committed. In [MySQL's case][mysql-implicit], these statements
are usually DDL statements like create table, add column, etc. The
recommendation is to separate these out into their own changesets:

```xml
<changeSet id="create-the-table" author="...">
    <createTable tableName="...">...</createTable>
</changeSet>

<changeSet id="insert-the-data" author="...">
    <insert tableName="...">...</insert>
</changeSet>
```

When changeset `insert-the-data` fails for some reason, at least those
changes will be rolled back as part of a transaction. Fortunately, there's a
way to handle rolling back implicitly committed changes with liquibase, as
discussed below.

## Rolling Back Changes

Liquibase can be leveraged to [rollback changes][rollback], even
changesets that has been implicitly committed. Unlike a transaction rollback,
we need to manually ask liquibase to perform the rollback. Depending on how
far we need to rollback, liquibase will take the most recently ran changesets
and try to "undo" the changesets.

For some changes, liquibase understands what the "inverse" of the change is
and can automatically perform it for us. For instance, the inverse of create
table is to drop the table. Take a look at the "Auto Rollback" column of the
"Database Support" documentation for the change you're interested in to find
out (for example [create table][create-table] does support auto-rollback).
For changes that does not support auto-rollback (for example
[insert][insert]), we need to manually provide the rollback instructions.
When liquibase performs a rollback and it cannot find rollback instructions,
the rollback process will fail.

```xml
<changeSet id="create-the-table" author="...">
    <createTable tableName="...">...</createTable>
    <!-- this has auto-rollback -->
</changeSet>

<changeSet id="insert-the-data" author="...">
    <insert tableName="...">...</insert>
    <rollback>
        <!-- need to provide instructions -->
        delete from ...;
    </rollback>
</changeSet>
```

Currently, pepper is setup to automatically run new migrations during server
bootup, but it also tries to catch failed migrations and roll them back. The
support is best-effort, meaning if the rollback process itself fails (perhaps
due to missing rollback instructions) no other cleanup will be done. Thus,
although pepper has support for rollbacks, developers must do their part to
plan/organize/write changesets in a way that will help facilitate successful
migration and rollback.

### Tagging

How does liquibase know how far to rollback? Currently, this is facilitated
by "tagging" the database. Liquibase supports tagging the database with a
string. Internally, liquibase marks the last successfully ran changeset with
the tag. When we set a new tag, it does the same thing of marking the last
successful changeset. If it's the same changeset, the changeset's tag will be
updated with the new tag. So effectively, the tag can be used to serve as a
timestamp of the last-known-good state of the database.

The strategy that pepper currently adopts is as follows:

1. Set a new tag _before_ running migrations.
2. Run new migrations.
3. If migration failed, rollback to tag and then propagate original migration error up.

## Bonus: Using Liquibase via Maven

Liquibase may be used via maven, which should help ease local development and
testing out migrations locally. The [liquibase maven plugin][maven-plugin]
has already been configured to work with pepper's setup, but when running the
liquibase maven goals the database credentials will need to be provided:

```
-Dliquibase.driver=$DRIVER -Dliquibase.url=$DB_URL -Dliquibase.username=$USER -Dliquibase.password=$PASS
```

where you should provide the `DB_URL` and username/password. The `DRIVER` for
MySQL should be `com.mysql.jdbc.Driver` and hsql should be `org.hsqldb.jdbcDriver`.

Just to list a few goals:

```bash
# list all available liquibase goals
$ mvn liquibase:help

# list currently held locks
$ mvn liquibase:listLocks [db flags]

# list changesets that has not been applied yet
$ mvn liquibase:status [db flags]
```

One way to use the plugin is:

```bash
$ mvn liquibase:tag -Dliquibase.tag=some-tag [db flags]
$ mvn liquibase:update [db flags]
$ mvn liquibase:rollback -Dliquibase.rollbackTag=some-tag [db flags] # if things fail
```

[liquibase]: http://www.liquibase.org/index.html
[changeset]: http://www.liquibase.org/documentation/changeset.html
[create-table]: https://www.liquibase.org/documentation/changes/create_table.html
[insert]: https://www.liquibase.org/documentation/changes/insert.html
[rollback]: http://www.liquibase.org/documentation/rollback.html
[maven-plugin]: http://www.liquibase.org/documentation/maven/index.html
[mysql-implicit]: https://dev.mysql.com/doc/refman/5.7/en/implicit-commit.html
