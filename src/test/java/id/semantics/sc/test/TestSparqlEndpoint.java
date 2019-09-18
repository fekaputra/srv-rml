package id.semantics.sc.test;

import org.apache.commons.io.IOUtils;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.IOException;

public class TestSparqlEndpoint {

//    @Test public void testMusicSparql() {
//        String musicEndpoint = "http://localhost:3030/rdf";
//        QueryExecution qExec =
//                QueryExecutionFactory.sparqlService(musicEndpoint, "PREFIX : <http://w3id.org/semcon/ns/ontology#> "
//                        + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> "
//                        + "SELECT * WHERE {?a a :Artist ; rdfs:label ?ArtistLabel}");
//        ResultSet rs = qExec.execSelect();
//        ResultSetFormatter.out(rs);
//    }

    @Test public void testWikidataSparql() {
        String wikidataEndpoint = "https://query.wikidata.org/sparql";
        QueryExecution qExec =
                QueryExecutionFactory.sparqlService(wikidataEndpoint, "SELECT * WHERE {?a ?b ?c} limit 10");
        ResultSet rs = qExec.execSelect();
        ResultSetFormatter.out(rs);
    }

//    @Test public void testFederatedSparql() throws IOException {
//        String musicEndpoint = "http://localhost:3030/rdf";
//        FileInputStream queryStream = new FileInputStream("./sample-input/sparql/sample-federated.rq");
//        String query = IOUtils.toString(queryStream, "UTF-8");
//
//        QueryExecution qExec = QueryExecutionFactory.sparqlService(musicEndpoint, query);
//        ResultSet rs = qExec.execSelect();
//        ResultSetFormatter.out(rs);
//    }

}
