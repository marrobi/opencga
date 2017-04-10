package org.opencb.opencga.storage.benchmark.variant.generators;

import org.opencb.biodata.models.variant.annotation.ConsequenceTypeMappings;
import org.opencb.biodata.models.variant.avro.VariantType;
import org.opencb.commons.datastore.core.Query;
import org.opencb.opencga.storage.core.variant.adaptors.VariantDBAdaptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;

/**
 * Created by jtarraga on 07/04/17.
 */
public abstract class TermQueryGenerator extends QueryGenerator {
    protected ArrayList<String> terms = new ArrayList<>();
    private String termFilename;
    private String queryKey;
    private Logger logger = LoggerFactory.getLogger(getClass());

    public TermQueryGenerator(String termFilename, String queryKey) {
        super();
        this.termFilename = termFilename;
        this.queryKey = queryKey;
    }

    @Override
    public void setUp(Map<String, String> params) {
        super.setUp(params);
        loadTerms(params, Paths.get(params.get(DATA_DIR), termFilename));
        terms.trimToSize();
    }

    protected void loadTerms(Map<String, String> params, Path path) {
        readCsvFile(path, strings -> terms.add(strings.get(0)));
    }

    @Override
    public Query generateQuery(Query query) {
        query.append(queryKey, terms.get(random.nextInt(terms.size())));
        return query;
    }

    public static class XrefQueryGenerator extends TermQueryGenerator {

        public XrefQueryGenerator() {
            super("xrefs.csv", VariantDBAdaptor.VariantQueryParams.ANNOT_XREF.key());
        }
    }

    public static class BiotypeQueryGenerator extends TermQueryGenerator {
        private Logger logger = LoggerFactory.getLogger(getClass());

        public BiotypeQueryGenerator() {
            super("biotypes.csv", VariantDBAdaptor.VariantQueryParams.ANNOT_BIOTYPE.key());
        }
    }

    public static class GeneQueryGenerator extends TermQueryGenerator {

        public GeneQueryGenerator() {
            super("genes.csv", VariantDBAdaptor.VariantQueryParams.GENE.key());
        }

    }

    public static class StudyQueryGenerator extends TermQueryGenerator {

        public StudyQueryGenerator() {
            super("studies.csv", VariantDBAdaptor.VariantQueryParams.STUDIES.key());
        }
    }

    public static class TypeQueryGenerator extends TermQueryGenerator {

        public TypeQueryGenerator() {
            super("types.csv", VariantDBAdaptor.VariantQueryParams.TYPE.key());
        }

        @Override
        protected void loadTerms(Map<String, String> params, Path path) {
            if (path.toFile().exists()) {
                super.loadTerms(params, path);
            } else {
                for (VariantType variantType : VariantType.values()) {
                    terms.add(variantType.toString());
                }
            }
        }
    }

    public static class ConsequenceTypeQueryGenerator extends TermQueryGenerator {
        private Logger logger = LoggerFactory.getLogger(getClass());

        public ConsequenceTypeQueryGenerator() {
            super("consequence_types.csv", VariantDBAdaptor.VariantQueryParams.ANNOT_CONSEQUENCE_TYPE.key());
        }

        @Override
        protected void loadTerms(Map<String, String> params, Path path) {
            if (path.toFile().exists()) {
                super.loadTerms(params, path);
            } else {
                for (String term : ConsequenceTypeMappings.termToAccession.keySet()) {
                    terms.add(term);
                    terms.add(ConsequenceTypeMappings.getSoAccessionString(term));
                }
            }
        }

    }
}
