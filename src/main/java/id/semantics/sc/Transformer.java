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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Transformer {

    private static final Logger log = LoggerFactory.getLogger(Transformer.class);

    public static void main(String[] args) throws ParseException {

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

        log.info("resulted file: " + result);
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
            mapper = RmlMapper.newBuilder().setLogicalSourceResolver(iri, resolver).build();
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

        // create a temp file and return jena model
        File file = File.createTempFile("temp", ".ttl");
        OutputStream tempOutput = new FileOutputStream(file);
        Rio.write(sesameModel, tempOutput, RDFFormat.TURTLE); // write mapping
        sesameModel.clear();
        tempOutput.flush();
        tempOutput.close();

        return file;
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
