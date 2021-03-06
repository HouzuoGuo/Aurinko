Networking and concurrency support in Aurinko
=============================================

*[Back to Index][]*

Server and request/response formats:

-   Aurinko is optimized for concurrent connections, but it does not yet have an official client application. To get full processing throughput, please make sure that your Aurinko client handles connection pooling properly (my suggestion is at least 50 threads).
-   One Aurinko server instance serves one database.
-   Client makes request to server in [Extensible Data Notation][].
-   Server produces responses in [Extensible Data Notation][].

Concurrency, safety and fairness:

-   One new thread is created for each incoming connection.
-   Concurrent safety and fairness in processing incoming requests is guaranteed by Clojure's [locking][] function.
-   [locking][] is also used to guarantee fairness of request processing.
-   Processing of requests are serialized at database level (No finer grained locking support yet).
-   All read operations are serialized.
-   All write operations are serialized together with read operations.

*[Back to Index][]*

[Back to Index]: https://github.com/HouzuoGuo/Aurinko/wiki
[Extensible Data Notation]: https://github.com/edn-format/edn
[locking]: http://clojuredocs.org/clojure_core/clojure.core/locking
