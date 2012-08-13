# Aurinko
Aurinko is a networked document database engine implementation in Clojure programming language.

(Renamed from ClojureDB, sorry!)

## Features (and why choose Aurinko)
* Store, manage and retrieve documents in native Clojure data structures using CON (Clojure Object Notation)</li>
* Durable data - Your data is safe in event of write-failure/unclean shutdown</li>
* Networked - Safely handle concurrent database connections</li>
* Nice performance - Handle 3,000+ writes / 6,000+ lookups per second with confidence</li>
* Handle complex query - Use stack based syntax to easily build powerful and complex queries</li>
* Compact - Implemented in just under 700 lines of Clojure code</li>

## Manual
Tutorials and references can be found at `./doc/`

## Not reinventing FleetDB
FleetDB, a "Schema-free database optimized for agile development", is an in-memory database. Aurinko works in file system, more like Couch/MongoDB.

## License
Source Copyright 2012 Howard Guo.
Distributed under the Eclipse Public License, the same as Clojure uses.
