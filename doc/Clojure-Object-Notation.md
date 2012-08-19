CON - Clojure Object Notation
=============================

CON is used everywhere in Aurinko: Document storage, server responses,
client requests, etc. CON is natively understood by Clojure programming
language.

To serialize an object into CON: `(pr-str object_here)`, to de-serialize an object: `(read-string "con-string here")`

The following table lists all the elements that may appear in Aurinko
collection documents:

<table>
  <tr>
    <th>Element</th>
    <th>Examples</th>
  </tr>
  <tr>
    <td>Number</td>
    <td><code>12.345</code></td>
  </tr>
  <tr>
    <td>String</td>
    <td><code>"abc123"</code></td>
  </tr>
  <tr>
    <td>Keyword</td>
    <td><code>:this-is-a-keyword</code><br />
(Case-sensitive, must begin with ':', may contain one or more [0-9][A-Z][a-z] and dash ’-’)</td>
  </tr>
  <tr>
    <td>Vector</td>
    <td><code>["abc" 123 "def" 45.67]</code><br />
(May contain nested vectors/maps)</td>
  </tr>
  <tr>
    <td>Map</td>
    <td><code>{:name "CON" :key2 23.45 :used-by ["Aurinko" "FleetDB"]}</code><br />
(Must start with '{' and end with '}', contains keyword-value pairs, may contain nested maps)</td>
  </tr>
</table>

A comprehensive example:

~~~~
{:name "Aurinko"
 :type "Document database engine"
 :depends-on {:Clojure   [1.3 1.4]
              :Leiningen [1.7 2.0]
              :JVM       [1.5 1.6 1.7]
              :OS        ["Unix" "Linux" "Solaris" "Windows"]}}
~~~~