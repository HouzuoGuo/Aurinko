Networking and concurrency support in Aurinko
=============================================

*[Back to Index][]*

Facts about Aurinko networking support:

-   One Aurinko server instance serves one database.
-   One new thread is created for each incoming connection.
-   Client makes request to server using a format natively understood by Clojure programming language.
-   Server produces responses in a format which is natively understood by Clojure programming language. (See [CON][] for more detials)

Facts about concurrency in Aurinko:

-   All requests from all connections are queued in one
    `java.util.concurrent.LinkedBlockingQueue`
-   There is one worker in each Aurinko server instance to work on the queue.
-   All read operations are serialized.
-   All write operations are serialized.

Concurrent read support is a high priority feature in the next Aurinko release.

*[Back to Index][]*

[Back to Index]: https://github.com/HouzuoGuo/Aurinko/wiki
[CON]: https://github.com/HouzuoGuo/Aurinko/wiki/Clojure-Object-Notation