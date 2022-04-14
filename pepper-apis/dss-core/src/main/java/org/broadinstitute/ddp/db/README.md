## DAO-ish classes and db utilities


### Overview
Data Access Objects (DAOs) and database utilities belong in this package.

Classes which perform database operations (CRUD) should in general _not_ directly use `TransactionWrapper.inTransaction()`.  Classes which are higher up at the edge
of the [ECB (Entity/Control/Boundary)](http://www.cs.sjsu.edu/~pearce/modules/patterns/enterprise/ecb/ecb.htm) level (in particular spark Routes)
should be where new connections are fetched from the pool via `TransactionWrapper.inTransaction()`.  The motivation for this is twofold:
* To enable the general pattern of "One rest call, one transaction", but to do so with clarity from the code and without container magic, and allowing
custom escape hatches without running afoul of the aforementioned container magic
* To make it easy to understand what operations (internal/external calls, network IO, disk) are happening while holding a db transaction

### Should I _always_ use one connection per request?
Not necessarily.  You may want different transactions in one request in order to minimize your footprint in the database, or
to aid in fault tolerance.

### What's up with all the `SQLException` wrapping?
For spice, `SQLException` is pretty much fatal, so our DAO classes wrap `SQLException` into `RuntimeException` and rethrow the
exception, adding a bit of context in the error message.  This is tedious but less tedious than littering route code with
lots of `try ... catch(SQLException)`.