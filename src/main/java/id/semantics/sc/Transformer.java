package id.semantics.sc;

import com.taxonic.carml.engine.RmlMapper;
import com.taxonic.carml.logical_source_resolver.CsvResolver;
import com.taxonic.carml.logical_source_resolver.JsonPathResolver;
import com.taxonic.carml.logical_source_resolver.LogicalSourceResolver;
import com.taxonic.carml.logical_source_resolver.XPathResolver;
import com.taxonic.carml.model.TriplesMap;
import com.taxonic.carml.util.RmlMappingLoader;
import com.taxonic.carml.vocab.Rdf;
import org.apache.commons.cli.*;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.OWL;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Transformer {

    private static final Logger log = LoggerFactory.getLogger(Transformer.class);

    public static void main(String[] args) throws ParseException, IOException {

        Options options = new Options();
        options.addOption("m", true, "RML mapping file");
        options.addOption("i", true, "Input file");
        options.addOption("t", true, "File type (XML, JSON, or CSV)");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);
        String mappingFile = cmd.getOptionValue("m");
        String inputFile = cmd.getOptionValue("i");
        String fileType = cmd.getOptionValue("t");

        String result = transform(inputFile, mappingFile, fileType);
        String prov = extractProvenance(inputFile);
        String usagePolicy = extractUsagePolicy(inputFile);

        log.info("resulted file: " + result);
        log.info("resulted prov: " + readFile(prov, Charset.defaultCharset()));
        log.info("resulted usage: " + readFile(usagePolicy, Charset.defaultCharset()));
    }

    /**
     * The function transform an inputFile of type fileType using a mappingFile into RDF.
     * Currently only accepts JSON, CSV or XML
     *
     * @param inputFile
     * @param mappingFile
     * @param fileType
     * @return RDF file location; serialized in TURTLE syntax or NULL if error happened (check log)
     */
    public static String transform(String inputFile, String mappingFile, String fileType) {

        String rdfFile = null;
        IRI iri;
        LogicalSourceResolver resolver;
        RmlMapper mapper;

        if (fileType.equalsIgnoreCase(SourceType.JSON.name())) {
            iri = Rdf.Ql.JsonPath;
            resolver = new JsonPathResolver();
        } else if (fileType.equalsIgnoreCase(SourceType.XML.name())) {
            iri = Rdf.Ql.XPath;
            resolver = new XPathResolver();
        } else if (fileType.equalsIgnoreCase(SourceType.CSV.name())) {
            iri = Rdf.Ql.Csv;
            resolver = new CsvResolver();
        } else {
            log.error("unknown source type");
            return rdfFile;
        }

        try {
            mapper = RmlMapper.newBuilder().setLogicalSourceResolver(iri, resolver).addFunctions(new CarmlFunctions())
                    .build();
            File file = parse(inputFile, mappingFile, mapper);
            rdfFile = file.getAbsolutePath();

        } catch (IOException e) {
            log.error("error in parsing source/mapping file", e);
        } catch (Exception e) {
            log.error("error in creating RmlMapper", e);
        }

        System.gc();
        return rdfFile;
    }

    /**
     * return turtle file name generated with caRML and RML mappings.
     *
     * @param inputFile
     * @param mappingFile
     * @param rmlMapper
     * @return
     * @throws IOException
     */
    public static File parse(String inputFile, String mappingFile, RmlMapper rmlMapper) throws IOException {

        // load RML file and all supporting functions
        InputStream is = new FileInputStream(mappingFile);
        Set<TriplesMap> mapping = RmlMappingLoader.build().load(RDFFormat.TURTLE, is);

        // load input file and convert it to RDF
        InputStream instances = new FileInputStream(inputFile);
        rmlMapper.bindInputStream(instances);

        // write it out to an turtle file
        Model sesameModel = rmlMapper.map(mapping);
        is.close();
        instances.close();

        // add provenance model

        // create a temp file and return jena model
        File file = File.createTempFile("temp", ".ttl");
        OutputStream tempOutput = new FileOutputStream(file);
        Rio.write(sesameModel, tempOutput, RDFFormat.TURTLE); // write mapping
        sesameModel.clear();
        tempOutput.close();

        return file;
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

    public static String extractProvenance(String inputFile) throws IOException {

        JSONObject object = new JSONObject(readFile(inputFile, Charset.forName("UTF-8")));
        String provenance = object.getJSONObject("provision").getString("provenance");

        File file = File.createTempFile("temp", ".ttl");
        file.deleteOnExit();
        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
        writer.write(provenance);
        writer.close();

        return file.getAbsolutePath();
    }

    public static String extractUsagePolicy(String inputFile) throws IOException {
        JSONObject object = new JSONObject(readFile(inputFile, Charset.forName("UTF-8")));
        String provenance = object.getJSONObject("provision").getString("usage-policy");

        File file = File.createTempFile("temp", ".trig");
        file.deleteOnExit();
        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
        writer.write(provenance);
        writer.close();

        return file.getAbsolutePath();
    }

    private static Map<String, String> getPrefixes() {
        Map<String, String> prefixes = new HashMap<>();

        // General
        prefixes.put("rdf", RDF.NAMESPACE);
        prefixes.put("rdfs", RDFS.NAMESPACE);
        prefixes.put("owl", OWL.NS);
        prefixes.put("dct", DCTerms.NS);

        return prefixes;
    }

    public enum SourceType {
        JSON, XML, CSV
    }

}
