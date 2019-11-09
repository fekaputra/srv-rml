package id.semantics.sc.test;

import id.semantics.sc.Service;
import id.semantics.sc.Service.SimpleResponse;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static id.semantics.sc.Service.request;
import static org.junit.Assert.assertEquals;
import static spark.Spark.awaitInitialization;
import static spark.Spark.stop;

public class TestServiceSeismic {
    public static final String SERVICE_URL = "http://localhost:2806";
    public static final String FUSEKI_URL = "http://localhost:3030";
    private static final Logger log = LoggerFactory.getLogger(TestServiceSeismic.class);
    private static Service service;

    @BeforeClass public static void setUp() throws Exception {
        String rml = "sample-input/rml/seismic-json.rml";
        String type = "json";
        String ontology = "sample-input/ontologies/seismic.ttl";
        String data = "https://seismic.data-container.net/api/data";
        String shacl = "sample-input/shacl/seismic-shacl.ttl";

        service = new Service(rml, ontology, type, data, shacl, false);
        service.establishRoutes();
        awaitInitialization();

    }

    @AfterClass public static void tearDown() throws Exception {
        stop();
        service.stop();
    }

    @Test public void testQueryEndpoint() throws IOException {
        String testUrl = "/rdf/query";
        String selectQueryString = "select * where {?s ?p ?o}";

        QueryExecution qe = QueryExecutionFactory.sparqlService(FUSEKI_URL + testUrl, selectQueryString);
        ResultSet rs = qe.execSelect();

        log.info("query result: " + ResultSetFormatter.asText(rs));
    }

    @Test public void testQuery() throws IOException {
        String testUrl = "/api/sparql/query";
        SimpleResponse res = request(SERVICE_URL, "GET", testUrl, null);
        assertEquals(200, res.status);
        log.info(res.toString());
    }

}
