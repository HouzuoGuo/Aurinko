Before moving on, you should have
[Leiningen](https://github.com/technomancy/leiningen/) installed.
Aurinko will work with both Leiningen version 1.7 and 2.

The following procedure assumes that you are using a Unix/Linux operating system.

Below command responses are indicated by "> ".

## Run server, connect client
Get Aurinko:

    git clone git://github.com/HouzuoGuo/Aurinko.git
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
    > {:result ["os" "os2"], :ok true}

Rename collection: *[:rename "old_name" "new_name"]*

    [:rename "os2" "to_delete"]
    > {:ok true}

Delete collection: *[:drop "collection_name"]*

    [:drop "to_delete"]
    > {:ok true}

Repair collection: *[:repair "collection_name"]*

    [:repair "os"]
    > {:ok true}

Repair recovers collection from serious corrption by reproducing the whole collection and its indexes from its log file. Does not compress!

Compress collection: *[:compress "collection_name"]*
    
    [:compress "os"]
    > {:ok true}

Compress recovers space occupied by delete documents.

Shutdown: *[:stop]*

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
    > {:result [{:pos 0, :name "slackware", :releases {:initial 1993, :latest 2011}} {:pos 146, :name "solaris", :releases {:initial 1992, :latest 2011}} {:pos 292, :name "opensolaris", :releases {:initial 2008, :latest 2009}} {:pos 446, :name "RHEL", :releases {:initial 2003, :latest 2012}}], :ok true}

Each document has a unique identifier ":pos", which is the document's position in collection data file)                                                

Create hash indexes for fast lookup queries: *[:hindex "collection_name" & indexed_path]*

    [:hindex "os" :name]
    > {:ok true}
    [:hindex "os" :releases :latest]
    > {:ok true}

Display all indexes: *[:indexed "collection\_name"]*

    [:indexed "os"]
    > {:result [ [:releases :latest] [:name] ], :ok true}

Remove an index: *[:unindex "collection_name" & indexed_path]*
                                       
    [:unindex "os" :name]
    > {:ok true}

## Query

Aurinko uses a stack based syntax to process queries. The following concepts are useful:

-   Function - A Clojure keyword to represent a query operation: scan,
    lookup, filter, sort, etc. Takes a source as data input, produces a
    Clojure set (of document positions) as data output.
-   Source - Data source of function: it is either a Clojure set
    (function result) or :col (the whole collection)
-   Parameters - Other information to pass to the function
-   "limit" parameter - Many functions use a "limit" parameter to limit
    the returned result size. Number -1 always means that there is no
    limit.
-   Multiple functions - You may use more than one functions in a single
    query because a function's output may be used as input to another
    function. The only exception is sorting - sorted result may not be
    used as another function's input, thus sorting must happen after all
    other query functions.

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
    > {:result [#{146}], :ok true}

    [:select "os" :col [:releases :initial] 1992 -1 :eq]
    > {:result [ [{:_pos 146, :name "solaris", :releases {:initial 1992, :latest 2011}}] ], :ok true}

Which OSes do not have its latest release in 2012?

    [:select "os" :col [:releases :latest] 2012 -1 :ne]
    > {:result [ [{:_pos 0, :name "slackware", :releases {:initial 1993, :latest 2011}} {:_pos 292, :name  opensolaris", :releases {:initial 2008, :latest 2009}} {:_pos 146, :name "solaris", :releases {:initial 1992, :latest 2011}}] ], :ok true}

Which OSes were initially released before 21st century?

    [:select "os" :col [:releases :initial] 2000 -1 :lt]
    >{:result [ [{:_pos 0, :name "slackware", :releases {:initial 1993, :latest 2011}} {:_pos 146, :name "solaris", :releases {:initial 1992, :latest 2011}}] ], :ok true}

### Relational Algebras
Query functions:
-   :union - union
-   :diff - difference
-   :intersect - intersection

Parameters: source_set1, source_set2

Which OSes were initially release before 1993 or after 2003?

    [:select "os" :col [:releases :initial] 1993 -1 :lt :col [:releases :initial] 2003 -1 :gt :union]
    > {:result [ [{:_pos 292, :name "opensolaris", :releases {:initial 2008, :latest 2009}} {:_pos 146, :name "solaris", :releases {:initial 1992, :latest 2011}}] ], :ok true}

Which OSes were initially release before 1993 and still being maintained in 2011?

    [:select "os" :col [:releases :initial] 1993 -1 :lt :col [:releases :latest] 2011 -1 :eq :intersect]
    > {:result [ [{:_pos 146, :name "solaris", :releases {:initial 1992, :latest 2011}}] ], :ok true}

Which OSes were not initially released in between 1993 to 2005, and have their latest releases in 2011?

    [:select "os" :col [:releases :initial] 1993 -1 :ge :col [:releases :initial] 2005  -1 :le :diff :col [:releases :latest] 2011 -1 :eq :intersect]
    > {:result [ [{:_pos 146, :name "solaris", :releases {:initial 1992, :latest 2011}}] ], :ok true}

### Path existence
Function keywords:
-   :has - path exists
-   :not-have - path does not exist

Parameters: source, path:

Which documents contain path [:releases :initial]?

    [:select "os" :col [:releases :initial] :has]
    > {:result [ [{:_pos 0, :name "slackware", :releases {:initial 1993, :latest 2011}} {:_pos 292, :name "opensolaris", :releases {:initial 2008, :latest 2009}} {:_pos 146, :name "solaris", :releases {:initial 1992, :latest 2011}} {:_pos 446, :name "RHEL", :releases {:initial 2003, :latest 2012}}] ], :ok true}

Which documents do not have [:name]?

    [:select "os" :col [:name] :not-have]
    > {:result [ [] ], :ok true}

### Other query options

Function keyword `:all` yields a set of all document positions.
 
Which OSes do not have latest releases after 2010?

    [:select "os" :col [:releases :latest] 2010 -1 :gt :all :diff]
    > {:result [ [{:_pos 292, :name "opensolaris", :releases {:initial 2008, :latest 2009}}] ], :ok true}

Sort results ascending `:asc` or descending `:desc`

Sort OSes according to their latest release year, latest first:

    [:select "os" :col [:releases :latest] :desc]
    > {:result [ [{:_pos 446, :name "RHEL", :releases {:initial 2003, :latest 2012}} {:_pos 146, :name "solaris", :releases {:initial 1992, :latest 2011}} {:_pos 0, :name "slackware", :releases {:initial 1993, :latest 2011}} {:_pos 292, :name "opensolaris", :releases {:initial 2008, :latest 2009}}] ], :ok true}

## Update/delete documents

To delete documents, you will need to produce a query, documents in the query results will be deleted:

*[:delete "collection\_name" & query\_conditions]*

Delete OSes which were not initially released in 21st century:
    
    [:delete "os" :col [:releases :initial] 2000 -1 :lt]
    > {:ok true}
    [:findall "os"]
    > {:result [{:_pos 292, :name "opensolaris", :releases {:initial 2008, :latest 2009}} {:_pos 446, :name "RHEL", :releases {:initial 2003, :latest 2012}}], :ok true}

To update documents, you will need to provide a Clojure function that takes one parameter (the to-be-updated document - Clojure map) and a query.

*[:update \#(my\_func) & query\_conditions]*

Update the name of RHEL operating system, change it to "Redhat Enterprise Linux":

    [:update "os" #(assoc % :name "Redhat Enterprise Linux") :col [:name] "RHEL" -1 :eq]
    > {:ok true}
    [:findall "os"]
    > {:result [{:_pos 292, :name "opensolaris", :releases {:initial 2008, :latest 2009}} {:_pos 446, :name "Redhat Enterprise Linux", :releases {:initial 2003, :latest 2012}}], :ok true}

## Benchmark Aurinko performance

To see how well Aurinko performs in your environment, run `lein run -m Aurinko.benchmark`.