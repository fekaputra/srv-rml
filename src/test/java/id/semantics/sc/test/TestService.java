package id.semantics.sc.test;

import id.semantics.sc.Service;
import id.semantics.sc.Service.SimpleResponse;
import org.apache.commons.io.IOUtils;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static id.semantics.sc.Service.request;
import static org.junit.Assert.assertEquals;
import static spark.Spark.awaitInitialization;
import static spark.Spark.stop;

public class TestService {
    public static final String SERVICE_URL = "http://localhost:2806";
    public static final String FUSEKI_URL = "http://localhost:3030";
    private static final Logger log = LoggerFactory.getLogger(TestService.class);
    private static Service service;

    @BeforeClass public static void setUp() throws Exception {
        
        List<String> strings = new ArrayList<>();
        String rml = "sample-input/rml/music.rml";
        String type = "json";
        String ontology = "sample-input/ontologies/music.ttl";
        String data = "https://vownyourdata.zamg.ac.at:9820/api/data";
        String shacl = "sample-input/shacl/music-shacl.ttl";

        service = new Service(rml, ontology, type, data, shacl, false);
        service.establishRoutes();
        awaitInitialization();

    }

    @AfterClass public static void tearDown() throws Exception {
        stop();
        service.stop();
    }

    @Test public void testHello() {
        String testUrl = "/api/sparql/status";
        SimpleResponse res = request(SERVICE_URL, "GET", testUrl, null);
        assertEquals(200, res.status);
        log.info(res.toString());
    }

    @Test public void testQuery() {
        String testUrl = "/api/sparql/query";
        SimpleResponse res = request(SERVICE_URL, "GET", testUrl, null);
        assertEquals(200, res.status);
        log.info(res.toString());
    }

    @Test public void testQueryPost() {
        String testUrl = "/api/sparql/query-p";
        SimpleResponse res = request(SERVICE_URL, "POST", testUrl, "{}");
        assertEquals(200, res.status);
        log.info(res.toString());
    }

    @Test public void testQueryPostSelect() throws IOException {
        String testUrl = "/api/sparql/query-p";
        InputStream selectQueryIS = new FileInputStream("./sample-input/sparql/sample-select.rq");
        String selectQueryString = IOUtils.toString(selectQueryIS, "UTF-8");

        SimpleResponse res = request(SERVICE_URL, "POST", testUrl, selectQueryString);
        assertEquals(200, res.status);
        log.info(res.toString());
    }

    @Test public void testQueryPostConstruct() throws IOException {
        String testUrl = "/api/sparql/query-p";
        InputStream selectQueryIS = new FileInputStream("./sample-input/sparql/sample-construct.rq");
        String selectQueryString = IOUtils.toString(selectQueryIS, "UTF-8");

        SimpleResponse res = request(SERVICE_URL, "POST", testUrl, selectQueryString);
        assertEquals(200, res.status);
        log.info(res.toString());
    }

    @Test public void testQueryEndpoint() throws IOException {
        String testUrl = "/rdf/query";
        String selectQueryString = "select * where {?s a ?o}";

        QueryExecution qe = QueryExecutionFactory.sparqlService(FUSEKI_URL + testUrl, selectQueryString);
        ResultSet rs = qe.execSelect();

        log.info("query result: " + ResultSetFormatter.asText(rs));
    }

}
