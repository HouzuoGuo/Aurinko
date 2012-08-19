Database storage and file formats
=================================

*[Back to Index][]*

Database
--------

A Aurinko database is a directory in file system. A Aurinko server
instance operates on one database which is specified in command line
options.

Collection
----------

Aurinko database is made of collections. Each collection is a sub-directory in the database’s directory. Every collection directory has the following files:

-   “data” - Collection data, it has all of collection documents.
-   “log” - Every insert/update/delete done to the collection, used to recover collection from data corruption.
-   Index files named by index path.

### Collection data file

The data file is purely made of documents and document padding, one after another. When a new document is inserted, the data file is grown by twice the size of the document and the document is put into the grown
room. Position of document in its data file uniquely identifies the document.

When updating a document, if the new document cannot fit into the room previously allocated to the initial version of the document, it will be inserted to the end of data file.

Each document consists of:
<table>
  <tr>
    <th>Content</th>
    <th>Size</th>
    <th>Data type</th>
    <th>Notes</th>
  </tr>
  <tr>
    <td>Document header - validity</td>
    <td>4 bytes</td>
    <td>Integer</td>
    <td>Initially, document has validity of 1. It turns into 0 after it is deleted.</td>
  </tr>
  <tr>
    <td>Document header - allocated room</td>
    <td>4 bytes</td>
    <td>Integer</td>
    <td>Initially, a document is allocated twice as much space as it takes. This value is also used to calculate whether an updated document needs to placed at end of the file.</td>
  </tr>
  <tr>
    <td>Document content</td>
    <td>Size of document body</td>
    <td>String</td>
    <td></td>
  </tr>
  <tr>
    <td>Document padding</td>
    <td>Allocated room - size of document body</td>
    <td>String</td>
    <td>Made of value 0s</td>
  </tr>
</table>
### Collection log file

Every insert/update/delete is recorded in this append-only log file. When issuing a repair collection command, Aurinko will attempt to reproduce the collection data file and indexes using this log file.

Every entry comes with a trailing new line character “\\n”. There are three types of entries:
<table>
  <tr>
    <th>Operation</th>
    <th>Format</th>
  </tr>
  <tr>
    <td>Insert</td>
    <td><code>[:i {inserted_document}]</code></td>
  </tr>
  <tr>
    <td>Update</td>
    <td><code>[:u {updated_document_with_pos}]</code></td>
  </tr>
  <tr>
    <td>Delete</td>
    <td><code>[:d deleted_document_position]</code></td>
  </tr>
</table>

### Index file

As of version 0.1, Aurinko only supports hash index. The hash index implementation is a static hash table using chained buckets to handle overgrowth. Last N (user-defined) bits of each entry hash decides which bucket the entry goes to, the entry goes to the next invalidated/empty entry slot in the bucket. If such slot cannot be found, the next bucket in chain is scanned to find such an empty slot. If such slot still is
not found, a new bucket is grown at end of the hash index file and chained to the bucket.

Index files are named by the vector of indexed path (made of keywords)
replaced “:” with “!”. For example, if indexed path is
`[:os :release :notes]`, the index file name will be
`[!os !release !notes]`.

Hash index file is made of one index file header and many index entries. Index file header consists of:
<table>
  <tr>
    <th>Name</th>
    <th>Size</th>
    <th>Data type</th>
    <th>Notes</th>
  </tr>
  <tr>
    <td>Number of key bits</td>
    <td>4 bytes</td>
    <td>Integer</td>
    <td>The last N bits of key will be extracted and used to lookup in hash table.</td>
  </tr>
  <tr>
    <td>Entries per bucket</td>
    <td>4 bytes</td>
    <td>Integer</td>
    <td>All buckets have exactly the same number of entries.</td>
  </tr>
</table>

After index file header, comes all buckets and entries. Bucket has a bucket header which consists of:
<table>
  <tr>
    <th>Name</th>
    <th>Size</th>
    <th>Data type</th>
    <th>Notes</th>
  </tr>
  <tr>
    <td>Next chained bucket number</td>
    <td>4 bytes</td>
    <td>Integer</td>
    <td>If it is 0, the bucket is the last in its bucket chain.</td>
  </tr>
</table>

Each entry in bucket consists of:

<table>
  <tr>
    <th>Name</th>
    <th>Size</th>
    <th>Data type</th>
    <th>Notes</th>
  </tr>
  <tr>
    <td>Validity</td>
    <td>4 bytes</td>
    <td>Integer</td>
    <td>Initially, entry has validity of 1. It turns into 0 after it is invalidated.</td>
  </tr>
  <tr>
    <td>Entry key hash</td>
    <td>4 bytes</td>
    <td>Integer</td>
    <td></td>
  </tr>
  <tr>
    <td>Entry value</td>
    <td>4 bytes</td>
    <td>Integer</td>
    <td>This is the indexed document's position in collection data file.</td>
  </tr>
</table>
*[Back to Index][]*

[Back to Index]: https://github.com/HouzuoGuo/Aurinko/wiki