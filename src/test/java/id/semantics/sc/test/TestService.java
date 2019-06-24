package id.semantics.sc.test;

import id.semantics.sc.Service;
import id.semantics.sc.Service.SimpleResponse;
import org.apache.commons.io.IOUtils;
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

    private static final Logger log = LoggerFactory.getLogger(TestService.class);

    @BeforeClass public static void setUp() throws Exception {
        List<String> strings = new ArrayList<>();
        strings.add("-m");
        strings.add("sample-input/rml/seismic-json.ttl");
        strings.add("-t");
        strings.add("json");
        strings.add("-o");
        strings.add("sample-input/ontologies/seismic.ttl");
        strings.add("-a");
        strings.add("https://vownyourdata.zamg.ac.at:9500/api/data?duration=1");
        strings.add("-c");
        strings.add("sample-input/shacl/seismic-shacl.ttl");
        Service.main(strings.toArray(new String[0]));
        awaitInitialization();

    }

    @AfterClass public static void tearDown() throws Exception {
        stop();
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

}
