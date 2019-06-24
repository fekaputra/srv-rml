# srv-rml: exposing API data as read-only SPARQL endpoint

_**Version 1.4.0-SNAPSHOT**_

This tool allows users to expose structured data (i.e., JSON, CSV, XML) 
collected from a REST API to be exposed as SPARQL endpoint. 

The tool utilize caRML library and the supplied RML mappings to transform the data, 
and expose it using embedded Jena Fuseki with options to make it persistent/non-persistent.
When srv-rml runs, it will open three API functions in the localhost: 

* **GET** `/api/sparql/status`: to check whether the system is running and from which API the current data is coming. 
* **GET** `/api/sparql/ontology`: to get the ontology model supplied to the system. 
* **GET** `/api/sparql/query`: to run a sparql query on the data and get the query result in JSON (SELECT) or JSON-LD (CONSTRUCT). 
  * the function accepts three parameters: query (q), api-address (a), and refresh (r) 
  * query(q): A SPARQL select query (default to `SELECT * WHERE {?a ?b ?c} LIMIT 10`)
  * api-address(a):  If added, it will change the source API of the data (default)
  * refresh(r): "true" or "false", which is an option to refresh the data from API.
* **POST** `/api/sparql/query-p`: to run a sparql query on the data and get the query result in JSON (SELECT) or JSON-LD (CONSTRUCT). 
  * the function accepts three parameters in JSON: query (q), api-address (a), and refresh (r) 
  * query(q): A SPARQL select query (default to `SELECT * WHERE {?a ?b ?c} LIMIT 10`)
  * api-address(a):  If added, it will change the source API of the data (default)
  * refresh(r): "true" or "false", which is an option to refresh the data from API.
  * example valid post parameters: 
    ```
    {
        "r": "true" ,
        "q": "select * where {?s a ?o}" ,
        "a": "https://vownyourdata.zamg.ac.at:9500/api/data?duration=7"
    }
    ``` 
    ```
    {
        "r": "false" ,
        "q": "construct where {?s a ?o}"
    }
    ```
    ```
    {
    }
    ```
  
To run it, you need to compile it using maven (`mvn clean install`), 
and afterwards execute the resulted "fat-jar" with the following options: 

* required options: m, a, t, o
* usage: utility-name
  *  **-m, --mapping <arg>**,   RML mapping file in TURTLE format
  *  **-a, --api <arg>**,       Source (e.g., Semantic Container) API address
  *  **-t, --type <arg>**,      Input file type (XML, JSON or CSV
  *  **-o, --ontology <arg>**,  Ontology model of the transformed RDF data
  *  **-c, --constraint <arg>**, SHACL constraints file in TURTLE format
  *  **-s,**                    (Optional) If activated, the transformed data will be persisted in
                                a TDB storage; otherwise it will be stored in memory

## A Running example for Seismic data from Semantic Container!

A sample working configuration using a semantic container that provides data from [ZAMG](http://zamg.ac.at/) 
as a backend service would be the following: 
```
java -jar target/srv-rml-1.3.0-SNAPSHOT-jar-with-dependencies.jar \
	-m "sample-input/rml/seismic-json.ttl" \
	-a "https://vownyourdata.zamg.ac.at:9500/api/data?duration=1" \
	-t "json" \
	-o "sample-input/ontologies/seismic.ttl" \
	-c "sample-input/shacl/seismic-shacl.ttl" \
```
The stated API address `https://vownyourdata.zamg.ac.at:9500/api/data?duration=1` will provide us with seismic data 
from the last day all over the world. 


After the tool started, you can execute all three API functions (the default port would be 2806).

### GET `/api/sparql/status`
CURL statement: `curl -X GET  http://localhost:2806/api/sparql/status`   

The service will provides you a message on the service status, similar to the following:     
  `the SPARQL service is running on data from API address: https://vownyourdata.zamg.ac.at:9500/api/data?duration=1` 

### GET `/api/sparql/ontology` 
CURL statement: `curl -X GET  http://localhost:2806/api/sparql/ontology`

The service will return you the ontology file provided in the initialization (-o): [seismic.ttl](https://github.com/fekaputra/srv-rml/blob/semcon/sample-input/ontologies/seismic.ttl)

### GET `/api/sparql/query`

#### Default query & parameters
If you run the query without any parameter, by default the parameter would be the following: 
* **query(q)**: `SELECT * WHERE {?a ?b ?c} LIMIT 10`
* **api-address(a)**: the existing API address (or the initial API address if you never changed it before)
* **refresh(r)**: false 

CURL statement: `curl -X GET  http://localhost:2806/api/sparql/query`

#### Other query with parameters

Let's take an example query to get all instances of the seismic activity 
```
PREFIX scs: <http://w3id.org/semcon/ns/seismic#>
SELECT * 
WHERE {
  ?activity a scs:SeismicActivity
}
```

* If you wants the seimic data from last 7 days instead of just the default 1    
`https://vownyourdata.zamg.ac.at:9500/api/data?duration=7`

* To run the query you need to url-encode the query and put it together with other parameters in a CURL call 
to get query results in JSON as the following:    
`
curl -X GET \
  'http://localhost:2806/api/sparql/query?r=true&a=https://vownyourdata.zamg.ac.at:9500/api/data?duration=7&q=PREFIX%20scs%3A%20%3Chttp%3A%2F%2Fw3id.org%2Fsemcon%2Fns%2Fseismic%23%3E%0ASELECT%20%2A%20%0AWHERE%20%7B%0A%20%20%3Factivity%20a%20scs%3ASeismicActivity%0A%7D'
`
* If afterwards you check the status, you'll see the following message showing the current API address:     
`the SPARQL service is running on data from API address: https://vownyourdata.zamg.ac.at:9500/api/data?duration=7`

### POST `/api/sparql/query-p`

#### Default query & parameters
If you run the query without any parameter, by default the parameter would be the following: 
* **query(q)**: `SELECT * WHERE {?a ?b ?c} LIMIT 10`
* **api-address(a)**: the existing API address (or the initial API address if you never changed it before)
* **refresh(r)**: false 

CURL statement: 
``` 
curl -X POST \
  http://localhost:2806/api/sparql/query-p \
  -H 'Content-Type: application/json' \
  -d '{}'
```

#### Other query with parameters

Let's take an example query to get all instances of the seismic activity 
```
PREFIX scs: <http://w3id.org/semcon/ns/seismic#>
SELECT * 
WHERE {
  ?activity a scs:SeismicActivity
}
```
* If you wants the seimic data from last 7 days instead of just the default 1    
`https://vownyourdata.zamg.ac.at:9500/api/data?duration=7`

* To run the query you need to url-encode the query and put it together with other parameters in a CURL call 
to get query results in JSON as the following:    
```
curl -X POST \
  http://localhost:2806/api/sparql/query-p \
  -H 'Content-Type: application/json' \
  -d '{
    "r": "true" ,
    "q": "PREFIX scs: <http://w3id.org/semcon/ns/seismic#> SELECT * WHERE { ?activity a scs:SeismicActivity }" ,
    "a": "https://vownyourdata.zamg.ac.at:9500/api/data?duration=7"
}'
```
* If afterwards you check the status, you'll see the following message showing the current API address:     
`the SPARQL service is running on data from API address: https://vownyourdata.zamg.ac.at:9500/api/data?duration=7`
