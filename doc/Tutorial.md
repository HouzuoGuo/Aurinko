Before moving on, you should have
[Leiningen](https://github.com/technomancy/leiningen/) installed.
Aurinko will work with both Leiningen version 1.7 and 2.

The following procedure assumes that you are using a Unix/Linux operating system.

Below command responses are indicated by "> ".

## Run server, connect client
Get Aurinko:

    git clone git://github.com/HouzuoGuo/Aurinko.git
    git checkout 0.3
    cd Aurinko

Run Aurinko server:

    lein run 1993 /tmp/db

Command line options: [port\_number] [db_dir]
                                        
Connect to server:

    telnet localhost 1993

## Collection management

Create a collection: *[:create "collection_name"]*

    [:create "os"]
    > {:ok true}
    [:create "os2"]
    > {:ok true}

Display all collections: *[:all]*
    
    [:all]
    > ["os" "os2"]

Rename collection: *[:rename "old_name" "new_name"]*

    [:rename "os2" "to_delete"]
    > {:ok true}

Delete collection: *[:drop "collection_name"]*

    [:drop "to_delete"]
    > {:ok true}

Repair collection: *[:repair "collection_name"]*

    [:repair "os"]
    > {:ok true}

Repair recovers collection from serious corruption by reproducing the whole collection and its indexes from its log file. Does not compress!

Compress collection: *[:compress "collection_name"]*
    
    [:compress "os"]
    > {:ok true}

Compress recovers space occupied by delete documents.

Shutdown server (The safe way): *[:stop]*

    [:stop]
    > {:ok true}

## Document management

Insert document: *[:insert "collection_name" {the_document}]*

    [:insert "os" {:name "slackware" :releases {:initial 1993 :latest 2011}}]
    > {:ok true}
    [:insert "os" {:name "solaris" :releases {:initial 1992 :latest 2011}}]
    > {:ok true}
    [:insert "os" {:name "opensolaris" :releases {:initial 2008 :latest 2009}}]
    > {:ok true}
    [:insert "os" {:name "RHEL" :releases {:initial 2003 :latest 2012}}]
    > {:ok true}
                                                
Retrieve all documents: *[:findall "collection\_name"]*
    
    [:findall "os"]
    > [{:_pos 4, :name "slackware", :releases {:initial 1993, :latest 2011}} {:_pos 150, :name "solaris", :releases {:initial 1992, :latest 2011}} {:_pos 296, :name "opensolaris", :releases {:initial 2008, :latest 2009}} {:_pos 450, :name "RHEL", :releases {:initial 2003, :latest 2012}}]

Each document has a unique identifier ":pos", which is the document's position in collection data file)

Create hash indexes for fast lookup queries: *[:hindex "collection_name" & indexed_path]*

    [:hindex "os" [:name]]
    > {:ok true}
    [:hindex "os" [:releases :latest]]
    > {:ok true}

Display all indexes: *[:indexed "collection\_name"]*

    [:indexed "os"]
    > {:hash [ [:name] [:releases :latest] ]}

Remove an index: *[:unindex "collection_name" & indexed_path]*
                                       
    [:unindex "os" [:name]]
    > {:ok true}

## Query

Aurinko uses a stack based syntax to process queries. The following concepts are useful:

-   Function - A Clojure keyword to represent a query operation: scan, lookup, filter, sort, etc. Takes a source as data input, produces a Clojure set (of document positions) as data output.
-   Source - Data source of function: it is either a Clojure set (intermediate, function result) or :col (the whole collection)
-   Parameters - Other information to pass to the function.
-   "limit" parameter - Many functions use a "limit" parameter to limit the returned result size. Number -1 always means that there is no limit.
-   Multiple functions - You may use more than one functions in a single query because a function's output may be used as input to another function. The only exception is sorting - sorted result may not be used as another function's input, thus sorting must happen after all other query functions.

Query common syntax: *[:q "collection_name" & query_parameters :function...]*

The difference between using ":q" and ":select" to query is that the former only yields a set of document positions as result, the latter yields document positions plus document contents.

### Lookup and range queries
Function keyword:
-   :gt - Greater than
-   :ge - Greater or equal
-   :lt - Less than
-   :le - Less or equal
-   :ne - Not equal

Parameters: source, lookup path, lookup value, limit:

Which OSes have their initial releases in 1992?
    
    [:q "os" :col [:releases :initial] 1992 -1 :eq]
    > [#{150}]

    [:select "os" :col [:releases :initial] 1992 -1 :eq]
    > [ [{:_pos 150, :name "solaris", :releases {:initial 1992, :latest 2011}}] ]

Which OSes do not have its latest release in 2012?

    [:select "os" :col [:releases :latest] 2012 -1 :ne]
    > [ [{:_pos 4, :name "slackware", :releases {:initial 1993, :latest 2011}} {:_pos 296, :name "opensolaris", :releases {:initial 2008, :latest 2009}} {:_pos 150, :name "solaris", :releases {:initial 1992, :latest 2011}}] ]

Which OSes were initially released before 21st century?

    [:select "os" :col [:releases :initial] 2000 -1 :lt]
    > [ [{:_pos 4, :name "slackware", :releases {:initial 1993, :latest 2011}} {:_pos 150, :name "solaris", :releases {:initial 1992, :latest 2011}}] ]

### Relational Algebras
Query functions:
-   :union - union
-   :diff - difference
-   :intersect - intersection

Parameters: source_set1, source_set2

Which OSes were initially release before 1993 or after 2003?

    [:select "os" :col [:releases :initial] 1993 -1 :lt :col [:releases :initial] 2003 -1 :gt :union]
    > [ [{:_pos 296, :name "opensolaris", :releases {:initial 2008, :latest 2009}} {:_pos 150, :name "solaris", :releases {:initial 1992, :latest 2011}}] ]

Which OSes were initially release before 1993 and still being maintained in 2011?

    [:select "os" :col [:releases :initial] 1993 -1 :lt :col [:releases :latest] 2011 -1 :eq :intersect]
    > [ [{:_pos 150, :name "solaris", :releases {:initial 1992, :latest 2011}}] ]

Which OSes were not initially released in between 1993 to 2005, and have their latest releases in 2011?

    [:select "os" :col [:releases :initial] 1993 -1 :ge :col [:releases :initial] 2005  -1 :le :diff :col [:releases :latest] 2011 -1 :eq :intersect]
    > [ [{:_pos 150, :name "solaris", :releases {:initial 1992, :latest 2011}}] ]

### Path existence
Function keywords:
-   :has - path exists
-   :not-have - path does not exist

Parameters: source, path:

Which documents contain path [:releases :initial]?

    [:select "os" :col [:releases :initial] :has]
    > [ [{:_pos 450, :name "RHEL", :releases {:initial 2003, :latest 2012}} {:_pos 4, :name "slackware", :releases {:initial 1993, :latest 2011}} {:_pos 296, :name "opensolaris", :releases {:initial 2008, :latest 2009}} {:_pos 150, :name "solaris", :releases {:initial 1992, :latest 2011}}] ]

Which documents do not have [:name]?

    [:select "os" :col [:name] :not-have]
    > [ [] ]

### Other query options

Function keyword `:all` yields a set of all document positions.
 
Which OSes do not have latest releases after 2010?

    [:select "os" :col [:releases :latest] 2010 -1 :gt :all :diff]
    > [ [{:_pos 296, :name "opensolaris", :releases {:initial 2008, :latest 2009}}] ]

Sort results ascending `:asc` or descending `:desc`

Sort OSes according to their latest release year, latest first:

    [:select "os" :col [:releases :latest] :desc]
    > [ [{:_pos 450, :name "RHEL", :releases {:initial 2003, :latest 2012}} {:_pos 150, :name "solaris", :releases {:initial 1992, :latest 2011}} {:_pos 4, :name "slackware", :releases {:initial 1993, :latest 2011}} {:_pos 296, :name "opensolaris", :releases {:initial 2008, :latest 2009}}] ]

If you want to retrieve documents by their position, use `:fastselect` followed by document positions:

    [:fastselect "os" 450 296]
    > [{:_pos 450, :name "RHEL", :releases {:initial 2003, :latest 2012}} {:_pos 296, :name "opensolaris", :releases {:initial 2008, :latest 2009}}]

Count query result/collection documents by using `:count` keyword; use `:contains` to search inside a string - if the path you're looking for is not a string, it will be considered as a string for the search.

Count the number of OSes which sound like "solar" (NOTE the usage of `:q` rather than `:select`):

    [:q "os" :col [:name] "solar" :contains :count]
    > [2]

## Update/delete documents

To delete documents, you will need to produce a query, documents in the query results will be deleted:

*[:delete "collection\_name" & query\_conditions]*

Delete OSes which were not initially released in 21st century:
    
    [:delete "os" :col [:releases :initial] 2000 -1 :lt]
    > {:ok true}
    [:findall "os"]
    > [{:_pos 296, :name "opensolaris", :releases {:initial 2008, :latest 2009}} {:_pos 450, :name "RHEL", :releases {:initial 2003, :latest 2012}}]

To update documents, you will need to provide a Clojure function that takes one parameter (the to-be-updated document - Clojure map) and a query.

*[:update \#(my\_func) & query\_conditions]*

Update the name of RHEL operating system, change it to "Redhat Enterprise Linux":

    [:update "os" #(assoc % :name "Redhat Enterprise Linux") :col [:name] "RHEL" -1 :eq]
    > {:ok true}
    [:findall "os"]
    > [{:_pos 296, :name "opensolaris", :releases {:initial 2008, :latest 2009}} {:_pos 450, :name "Redhat Enterprise Linux", :releases {:initial 2003, :latest 2012}}

If document positions are known to you, and you wish to update those documents as a whole, use `:fastupdate`:

    [:fastupdate "os" {:_pos 296 :name "OpenSolaris"} {:_pos 450 :name "RHEL"}]
    > {:ok true}
    [:findall "os"]
    > [{:_pos 296, :name "OpenSolaris"} {:_pos 450, :name "RHEL"}]

Similarly, you may delete documents given their positions using `:fastdelete`:

    [:fastdelete "os" 296 450]
    > {:ok true}
    [:findall "os"]
    > []

## Benchmark Aurinko performance

To see how well Aurinko performs in your environment, run `lein run -m Aurinko.benchmark`.
