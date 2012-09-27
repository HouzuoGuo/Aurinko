Welcome! Aurinko is a networked document database engine implementation in Clojure programming language.

Features (and why you would choose Aurinko)
---------------------------------

-   Store, manage and retrieve documents in native Clojure data structures using EDN [Extensible Data Notation][]
-   Durable data - Your data is safe in event of write-failure/unclean shutdown
-   Networked - Safely handle concurrent database connections with fairness guarantee
-   Nice performance - Handle 6,000+ writes / 8,000+ lookups per second with confidence
-   Handle complex query - Use stack based syntax to easily build powerful and complex queries
-   Compact - Implemented in just about 700 lines of Clojure code

Tutorial
--------

This 10 minutes tutorial will walk you through all features of Aurinko: [click here][]

Implementation Details
----------------------

-   [Database storage and file formats][]
-   [Query optimization and index][]
-   [Networking and concurrency][]
-   [Technical limitations][]

Project Releases
----------------
<table>
  <tr>
    <th>Version</th>
    <th>Release date</th>
    <th>Branch</th>
    <th>Changes/Notes</th>
  </tr>
  <tr>
    <td>0.1</td>
    <td>12 August 2012</td>
    <td>0.1</td>
    <td>First release</td>
  </tr>
  <tr>
    <td>0.2</td>
    <td>23 August 2012</td>
    <td>0.2</td>
    <td>%40+ performance improvements<br />
V0.1 database is fully compatible with V0.2</td>
  </tr>
  <tr>
    <td>0.3</td>
    <td>27 September 2012</td>
    <td>0.3</td>
    <td>Fixed performance, network throughput and memory usage issues.<br />
V0.3 database is <b>NOT</b> compatible with V0.1/V0.2.</td>
  </tr>
</table>

Aurinko is actively developed, please submit feature recommendations and check out [Issues][] section for features wish list.

Contact
-------

You are very welcomed to submit your feedback/question/suggestion/feature request to the author [Howard Guo][].
Please also follow me on Twitter [@hzguo][] and my blog [Howard’s programming and OS stuff][].

License & Copyright
-------------------

Source Copyright 2012 Howard Guo.
Distributed under the [Simplified BSD License][].

  [click here]: https://github.com/HouzuoGuo/Aurinko/wiki/Tutorial
  [Database storage and file formats]: https://github.com/HouzuoGuo/Aurinko/wiki/Database-Storage-and-File-Formats
  [Query optimization and index]: https://github.com/HouzuoGuo/Aurinko/wiki/Query-Optimization-and-Index-Usage
  [Networking and concurrency]: https://github.com/HouzuoGuo/Aurinko/wiki/Network-and-Concurrency-Suppport
  [Technical limitations]: https://github.com/HouzuoGuo/Aurinko/wiki/Limitations
  [Issues]: https://github.com/HouzuoGuo/Aurinko/issues
  [my github]: https://github.com/HouzuoGuo
  [Howard Guo]: mailto:guohouzuo@gmail.com
  [@hzguo]: https://twitter.com/hzguo
  [Howard’s programming and OS stuff]: http://allstarnix.blogspot.com.au
  [Simplified BSD License]: http://www.freebsd.org/copyright/freebsd-license.html
  [Extensible Data Notation]: https://github.com/edn-format/edn
