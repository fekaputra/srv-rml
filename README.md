# srv-rml: exposing API data as read-only SPARQL endpoint

This tool allows users to expose structured data (i.e., JSON, CSV, XML) 
collected from a REST API to be exposed as SPARQL endpoint. 

The tool utilize caRML library and the supplied RML mappings to transform the data, 
and expose it using embedded Jena Fuseki with options to make it persistent/non-persistent.
When srv-rml runs, it will open three API functions in the localhost: 

* GET `/api/sparql/status`: to check whether the system is running
* GET `/api/sparql/ontology`: to get the ontology model supplied to the system
* GET `/api/sparql/query`: to run a sparql query on the data and get the query result in JSON
  * the function will need two parameters: query (q) and refresh (r)
  * query (q) would be a working sparql select query, and
  * refresh (r) would be true or false, which is an option to refresh the data from API.

To run it, you need to compile it using maven (`mvn clean install`), 
and afterwards execute the resulted "fat-jar" with the following options: 

* required options: m, a, t, o
* usage: utility-name
  *  -a, --api <arg>,       Source (e.g., Semantic Container) API address
  *  -m, --mapping <arg>,   RML mapping file
  *  -o, --ontology <arg>,  Ontology model of the transformed RDF data
  *  -t, --type <arg>,      Input file type (XML, JSON or CSV
  *  -s,                    If activated, the transformed data will be persisted in
                            a TDB storage; otherwise it will be stored in memory

A sample working configuration using a semantic container as a backend service would be the following: 
```
java -jar target/srv-rml-1.2.1-SNAPSHOT-jar-with-dependencies.jar \
	-m "sample-input/rml/seismic-json.ttl" \
	-a "https://vownyourdata.zamg.ac.at:9500/api/data?duration=1" \
	-t "json" \
	-o "sample-input/ontologies/seismic.ttl" 
```

After the tool started, you can execute all three API functions.

As a running example, let's take an example query to get all instances of the seismic activity from a seismic container 
(the ontology provided in the `./sample-input` folder): 
```
PREFIX scs: <http://w3id.org/semcon/ns/seismic#>
SELECT * 
WHERE {
  ?activity a scs:SeismicActivity
}
```

To run the query you need to url-encode the query and put it in a CURL call to get query results in JSON: 
`
curl -X GET 'http://localhost:2806/api/sparql/query?q=PREFIX%20scs%3A%20%3Chttp%3A%2F%2Fw3id.org%2Fsemcon%2Fns%2Fseismic%23%3E%0ASELECT%20%2A%20%0AWHERE%20%7B%0A%20%20%3Factivity%20a%20scs%3ASeismicActivity%0A%7D&r=true'
`
