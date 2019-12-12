# Hsqldb on Command Line

We run hsqldb as a local in-memory database. It is often useful to be able to
connect to it and inspect the schema and tables. Hsqldb provides a tool called
[`SqlTool`][st] which allows us to connect to hsqldb via terminal. First,
download the tool:

1. Go [here][sf] and download the hsqldb source zip file `hsqldb-2.4.0.zip`.
2. Unzip the file. You should have a folder called `hsqldb-2.4.0`, which has a subdir `hsqldb`.
3. Move the subdir `hsqldb` to somewhere permanent.

Now that we have the tool (`hsqldb/lib/sqltool.jar`), you will just run the JAR
whenever you want to connect to a local hsqldb, like so:

```bash
$ java -jar /path/to/hsqldb/lib/sqltool.jar --inlineRc url=jdbc:hsqldb:file:/tmp/ddp-testingdb,user=sa,password=
```

The `inlineRc` flag provides the necessary connection parameters. The `url` value
should match what's specified in the `testing-inmemorydb.conf` file. The default
user is `sa` with an empty password.

And that's it!

[st]: http://hsqldb.org/doc/util-guide/sqltool-chapt.html
[sf]: https://sourceforge.net/projects/hsqldb/files/
