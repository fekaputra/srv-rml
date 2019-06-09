package id.semantics.sc;

import org.apache.commons.cli.*;
import org.apache.commons.io.IOUtils;
import org.apache.http.entity.ContentType;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.system.Txn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

import static spark.Spark.*;

public class Service {

    private static final Logger log = LoggerFactory.getLogger(Service.class);

    private final FusekiServer fusekiServer;

    private final Dataset dataset;
    private final String mappingFile;
    private final String ontologyJsonLD;
    private final String sourceType;
    private final String containerAPI;

    public Service(String mappingFile, String ontologyFile, String inputFileType, String containerURI)
            throws Exception {

        sourceType = inputFileType;
        containerAPI = containerURI;
        this.mappingFile = mappingFile;

        dataset = DatasetFactory.createTxnMem();
        fusekiServer = FusekiServer.create().add("/rdf", dataset).port(3030).build();
        fusekiServer.start();

        Model ontology = RDFDataMgr.loadModel(ontologyFile, Lang.TURTLE);
        StringWriter ontologyWriter = new StringWriter();
        RDFDataMgr.write(ontologyWriter, ontology, Lang.JSONLD);
        ontologyJsonLD = ontologyWriter.toString();

        ontologyWriter.close();
        ontology.close();
    }

    public static void main(String[] args) throws Exception {

        Options options = new Options();

        options.addOption("m", true, "RML mapping file");
        options.addOption("s", true, "Semantic Container API address");
        options.addOption("t", true, "Input file type");
        options.addOption("o", true, "Ontology model");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        String mappingFile = cmd.getOptionValue("m");
        String containerURI = cmd.getOptionValue("s");
        String inputFileType = cmd.getOptionValue("t");
        String ontologyFile = cmd.getOptionValue("o");

        log.info("starting semantic services");
        Service service = new Service(mappingFile, ontologyFile, inputFileType, containerURI);
        service.establishRoutes();
        log.info("semantic services started!");
    }

    private static String readFile(String path, Charset encoding) {
        byte[] encoded = new byte[0];
        try {
            encoded = Files.readAllBytes(Paths.get(path));
        } catch (IOException e) {
            log.error("error reading file", e);
        }
        return new String(encoded, encoding);
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
            return "the SPARQL service is running!";
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
        post("/api/sparql/query", (request, response) -> {
            log.info("api sparql/query triggered");

            response.status(200);
            response.type(ContentType.APPLICATION_JSON.toString());
            String query = request.queryParams("q");
            Boolean refresh = Boolean.parseBoolean(request.queryParams("r")); // option

            Txn.execute(dataset, () -> {
                if ((dataset.getDefaultModel().isEmpty() || refresh) && dataset.promote()) {

                    try {
                        // get data from containerAPI
                        SimpleResponse simpleResponse = request(containerAPI, "GET", "", "");
                        File tempFile = File.createTempFile("temp", ".tmp");
                        FileWriter writer = new FileWriter(tempFile);
                        writer.write(simpleResponse.body);
                        writer.flush();
                        writer.close();

                        // transform into RDF
                        String turtleString =
                                Transformer.transform(tempFile.getAbsolutePath(), mappingFile, sourceType);

                        // load into dataset
                        Txn.executeWrite(dataset, () -> RDFDataMgr.read(dataset, turtleString));

                    } catch (IOException e) {
                        log.error("error reading new input from sc-container");
                        response.body(e.getMessage());
                    }

                }
            });

            Txn.executeRead(dataset, () -> {
                try (QueryExecution qExec = QueryExecutionFactory.create(query, dataset)) {
                    ResultSet rs = qExec.execSelect();
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    ResultSetFormatter.outputAsJSON(outputStream, rs);
                    try {
                        String result = outputStream.toString("UTF-8");
                        response.body(result);
                    } catch (UnsupportedEncodingException e) {
                        log.error("error saving ResultSet to string", e);
                        response.body(e.getMessage());
                    }
                }
            });
            log.info("api sparql/query finished");

            return response.body();
        });
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
