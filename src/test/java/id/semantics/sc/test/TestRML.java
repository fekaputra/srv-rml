package id.semantics.sc.test;

import id.semantics.sc.Transformer;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class TestRML {

    @Test public void testRMLTransformer() throws FileNotFoundException {

        String input = "./sample-input/data/sample.json";
        String mapping = "./sample-input/rml/seismic-json.rml";

        File file = new File(Transformer.transform(input, mapping, "json"));
        Model model = ModelFactory.createDefaultModel();

        RDFDataMgr.read(model, new FileInputStream(file), Lang.TURTLE);
        RDFDataMgr.write(System.out, model, Lang.TURTLE);
    }

}
