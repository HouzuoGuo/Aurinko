Query optimization and usage of index
=====================================

*[Back to Index][]*

As of version 0.3, Aurinko only supports hash index, thus only look-up queries are benefited by usage of index. Implementation of skip list index is in master branch, however it is not yet ready yet. Range queries will be much faster once it is implemented.

The implementation details of hash index can be found [here][].

Aurinko query executor will use index look-up whenever possible, thus you should index frequently-looked-up paths in your Aurinko collection.

*[Back to Index][]*

[Back to Index]: https://github.com/HouzuoGuo/Aurinko/wiki
[here]: https://github.com/HouzuoGuo/Aurinko/wiki/Database-Storage-and-File-Formats
