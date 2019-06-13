package id.semantics.sc;

import org.apache.commons.cli.*;
import org.apache.commons.io.IOUtils;
import org.apache.http.entity.ContentType;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.system.Txn;
import org.apache.jena.tdb2.TDB2Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Response;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Set;

import static spark.Spark.get;
import static spark.Spark.port;

public class Service {

    private static final Logger log = LoggerFactory.getLogger(Service.class);

    private final FusekiServer fusekiServer;
    private final String provGraph = "http://w3id.org/semcon/ns/ontology#Provenance";
    private final String defaultQuery = "SELECT * WHERE {?a ?b ?c} LIMIT 10";

    private final Dataset dataset;
    private final String mappingFile;
    private final String ontologyJsonLD;
    private final String sourceType;
    private String sourceAPI; // in default, the API is not initialized

    public Service(String mappingFile, String ontologyFile, String sourceType, String sourceAPI,
            boolean usePersistence) throws Exception {

        this.sourceType = sourceType;
        this.mappingFile = mappingFile;
        this.sourceAPI = sourceAPI;

        log.info("sourceType: " + sourceType);
        log.info("mappingFile: " + mappingFile);
        log.info("sourceAPI: " + sourceAPI);
        log.info("ontologyFile: " + ontologyFile);

        Model ontology = ModelFactory.createDefaultModel();
        ontology.read(new FileInputStream(ontologyFile), null, Lang.TURTLE.getName());
        StringWriter ontologyWriter = new StringWriter();
        RDFDataMgr.write(ontologyWriter, ontology, Lang.JSONLD);
        ontologyJsonLD = ontologyWriter.toString();

        ontologyWriter.close();
        ontology.close();

        if (usePersistence) {
            dataset = TDB2Factory.assembleDataset("./config/fuseki.ttl");
        } else {
            dataset = DatasetFactory.createTxnMem();
        }

        fusekiServer = FusekiServer.create().add("/rdf", dataset).port(3030).build();
        fusekiServer.start();
    }

    public static void main(String[] args) throws Exception {

        Options options = new Options();

        options.addRequiredOption("m", "mapping", true, "RML mapping file");
        options.addRequiredOption("a", "api", true, "Original Source (e.g., Semantic Container) API address");
        options.addRequiredOption("t", "type", true, "Input file type (XML, JSON or CSV)");
        options.addRequiredOption("o", "ontology", true, "Ontology file of the transformed RDF data");
        options.addOption("s", false,
                "If activated, the transformed data will be persisted in a TDB storage; otherwise it will be stored in memory");

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd = null;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("utility-name", options);
            System.exit(1);
        }

        String mappingFile = cmd.getOptionValue("m");
        String apiAddress = cmd.getOptionValue("a");
        String inputFileType = cmd.getOptionValue("t");
        String ontologyFile = cmd.getOptionValue("o");

        log.info("starting semantic services");
        Service service = new Service(mappingFile, ontologyFile, inputFileType, apiAddress, cmd.hasOption("s"));
        service.establishRoutes();
        log.info("semantic services started!");
    }

    public static SimpleResponse request(String apiURL, String method, String path, String requestBody) {
        try {
            URL url = new URL(apiURL + path);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method);
            connection.setDoOutput(true);
            if (method.equals("POST")) {
                connection.setRequestProperty("Content-Type", "application/json");
                OutputStreamWriter osWriter = new OutputStreamWriter(connection.getOutputStream());
                osWriter.write(requestBody);
                osWriter.flush();
            }
            connection.connect();
            try {
                String body = IOUtils.toString(connection.getInputStream(), "UTF-8");
                return new SimpleResponse(connection.getResponseCode(), body);
            } catch (IOException e) {
                String error = IOUtils.toString(connection.getErrorStream(), "UTF-8");
                return new SimpleResponse(connection.getResponseCode(), error);
            }

        } catch (IOException e) {
            log.error("Sending request failed: " + e.getMessage(), e);
            return new SimpleResponse();
        }
    }

    public void establishRoutes() {
        // set port
        port(2806);

        // status checks
        // -- no additional parameters required
        get("/api/sparql/status", (request, response) -> {
            log.info("api sparql/status triggered");
            return "the SPARQL service is running on data from API address: " + sourceAPI;
        });

        // get the ontology model for the data
        get("/api/sparql/ontology", (request, response) -> {

            log.info("api sparql/ontology triggered");
            response.status(200);
            response.type(ContentType.APPLICATION_JSON.toString());
            log.info("api sparql/ontology finished");

            return ontologyJsonLD;
        });

        // query data
        get("/api/sparql/query", (request, response) -> {

            long start = System.currentTimeMillis();
            log.info("api sparql/query triggered");
            response.status(200);
            response.type(ContentType.APPLICATION_JSON.toString());
            Set<String> params = request.queryParams();

            String query = request.queryParamOrDefault("q", defaultQuery);
            String apiAddress = request.queryParamOrDefault("a", sourceAPI);
            Boolean refresh = Boolean.parseBoolean(request.queryParamOrDefault("r", "false")); // option

            log.info("query: " + query);
            log.info("api: " + apiAddress);
            log.info("data-refresh: " + refresh);

            Txn.execute(dataset, () -> {
                if ((dataset.getDefaultModel().isEmpty() // empty dataset
                        || !sourceAPI.equals(apiAddress) // if the api source is different
                        || refresh) // if users asks for refresh
                        && dataset.promote()) { // if it's possible to update the data
                    sourceAPI = apiAddress;
                    updateDataset(sourceAPI, response);
                }
            });

            Txn.executeRead(dataset, () -> {
                readDataset(query, response);
            });
            log.info("api sparql/query finished in " + (System.currentTimeMillis() - start) + " ms");

            return response.body();
        });
    }

    private void updateDataset(String api, Response response) {

        log.info("refresh data from the original source is started");
        try {
            // get data from sourceAPI
            SimpleResponse simpleResponse = request(api, "GET", "", "");
            File tempFile = File.createTempFile("temp", ".tmp");
            FileWriter writer = new FileWriter(tempFile);
            writer.write(simpleResponse.body);
            writer.flush();
            writer.close();

            // transform into RDF
            String mainFile = Transformer.transform(tempFile.getAbsolutePath(), mappingFile, sourceType);
            String provenance = Transformer.extractProvenance(tempFile.getAbsolutePath());
            String usagePolicy = Transformer.extractUsagePolicy(tempFile.getAbsolutePath());

            // load into dataset
            Txn.executeWrite(dataset, () -> {
                dataset.asDatasetGraph().clear();
                RDFDataMgr.read(dataset, mainFile); // add to default graph
                RDFDataMgr.read(dataset, usagePolicy); // automatically create a named graph
                dataset.addNamedModel(provGraph, RDFDataMgr.loadModel(provenance)); // create a named graph for prov
            });
            log.info("refresh data from the original source is successful");

        } catch (Exception e) {
            log.error("error reading new input from API source");
            response.body(e.getMessage());
        }
    }

    private void readDataset(String query, Response response) {

        log.info("query data from fuseki is started");
        try (QueryExecution qExec = QueryExecutionFactory.create(query, dataset)) {
            ResultSet rs = qExec.execSelect();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ResultSetFormatter.outputAsJSON(outputStream, rs);
            try {
                String result = outputStream.toString("UTF-8");
                response.body(result);
                log.info("query data from fuseki is successful");
            } catch (UnsupportedEncodingException e) {
                log.error("error saving ResultSet to string", e);
                response.body(e.getMessage());
            }
        }
    }

    public static class SimpleResponse {

        public final String body;
        public final int status;

        public SimpleResponse() {
            body = "";
            status = 500;
        }

        public SimpleResponse(int status, String body) {
            this.status = status;
            this.body = body;
        }

        @Override public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(System.lineSeparator());
            sb.append("status: ").append(status).append(System.lineSeparator());
            sb.append("body: ").append(body);

            return sb.toString();
        }
    }
}
