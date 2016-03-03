package org.opencb.opencga.storage.hadoop.variant.index;

import com.google.common.collect.BiMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.hbase.client.Result;
import org.opencb.biodata.models.feature.Genotype;
import org.opencb.biodata.models.variant.StudyEntry;
import org.opencb.biodata.models.variant.Variant;
import org.opencb.biodata.models.variant.avro.AlternateCoordinate;
import org.opencb.biodata.models.variant.avro.FileEntry;
import org.opencb.biodata.models.variant.avro.VariantAnnotation;
import org.opencb.biodata.models.variant.avro.VariantType;
import org.opencb.biodata.models.variant.protobuf.VariantProto;
import org.opencb.biodata.tools.variant.converter.Converter;
import org.opencb.biodata.tools.variant.merge.VariantMerger;
import org.opencb.datastore.core.ObjectMap;
import org.opencb.datastore.core.QueryOptions;
import org.opencb.datastore.core.QueryResult;
import org.opencb.opencga.storage.core.StudyConfiguration;
import org.opencb.opencga.storage.core.variant.StudyConfigurationManager;
import org.opencb.opencga.storage.hadoop.variant.GenomeHelper;
import org.opencb.opencga.storage.hadoop.variant.HBaseStudyConfigurationManager;
import org.opencb.opencga.storage.hadoop.variant.index.annotation.HBaseToVariantAnnotationConverter;
import org.opencb.opencga.storage.hadoop.variant.index.phoenix.VariantPhoenixHelper;
import org.opencb.opencga.storage.hadoop.variant.models.protobuf.SampleList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.Map.Entry;

/**
 * Created on 20/11/15.
 *
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public class HBaseToVariantConverter implements Converter<Result, Variant> {

    private final StudyConfigurationManager scm;
    private final HBaseToVariantAnnotationConverter annotationConverter;
    private final GenomeHelper genomeHelper;
    private final QueryOptions scmOptions = new QueryOptions(StudyConfigurationManager.READ_ONLY, true)
            .append(StudyConfigurationManager.CACHED, true);
    private final Map<Integer, LinkedHashMap<String, Integer>> returnedSamplesPosition = new HashMap<>();
    private final Logger logger = LoggerFactory.getLogger(HBaseToVariantConverter.class);

    private List<String> returnedSamples = Collections.emptyList();

    private static boolean failOnWrongVariants = false; //FIXME

    public HBaseToVariantConverter(VariantTableHelper variantTableHelper) throws IOException {
        this(variantTableHelper, new HBaseStudyConfigurationManager(variantTableHelper.getOutputTableAsString(),
                variantTableHelper.getConf(), new ObjectMap()));
    }

    public HBaseToVariantConverter(GenomeHelper genomeHelper, StudyConfigurationManager scm) {
        this.genomeHelper = genomeHelper;
        this.scm = scm;
        this.annotationConverter = new HBaseToVariantAnnotationConverter(genomeHelper);
    }

    @Override
    public Variant convert(Result result) {
        return convert(genomeHelper.extractVariantFromVariantRowKey(result.getRow()), VariantTableStudyRow.parse(result, genomeHelper),
                annotationConverter.convert(result));
    }

    public Variant convert(ResultSet resultSet) throws SQLException {
        Variant variant = new Variant(resultSet.getString(VariantPhoenixHelper.VariantColumn.CHROMOSOME.column()),
                resultSet.getInt(VariantPhoenixHelper.VariantColumn.POSITION.column()),
                resultSet.getString(VariantPhoenixHelper.VariantColumn.REFERENCE.column()),
                resultSet.getString(VariantPhoenixHelper.VariantColumn.ALTERNATE.column())
        );
        try {
            return convert(variant, VariantTableStudyRow.parse(variant, resultSet, genomeHelper), annotationConverter.convert(resultSet));
        } catch (RuntimeException e) {
            logger.error("Fail to parse variant: " + variant);
            throw e;
        }
    }

    public Variant convert(VariantTableStudyRow row) {
        return convert(new Variant(row.getChromosome(), row.getPos(), row.getRef(), row.getAlt()),
                Collections.singletonList(row), null);

    }

    protected Variant convert(Variant variant, List<VariantTableStudyRow> rows, VariantAnnotation annotation) {
        if (annotation == null) {
            annotation = new VariantAnnotation();
        }
        if (annotation.getAdditionalAttributes() == null) {
            annotation.setAdditionalAttributes(new HashMap<String, Object>());
        }
        if (rows.isEmpty()) {
            throw new IllegalStateException("No Row columns supplied for row " + variant);
        }
        for (VariantTableStudyRow row : rows) {
            Map<String, String> annotMap = new HashMap<String, String>();
            Integer studyId = row.getStudyId();
            QueryResult<StudyConfiguration> queryResult = scm.getStudyConfiguration(studyId, scmOptions);
            if (queryResult.getResult().isEmpty()) {
                throw new IllegalStateException("No study found for study ID: " + studyId);
            }
            StudyConfiguration studyConfiguration = queryResult.first();

            LinkedHashMap<String, Integer> returnedSamplesPosition = new LinkedHashMap<>(getReturnedSamplesPosition(studyConfiguration));

//            Do not throw any exception. It may happen that the study is not loaded yet or no samples are required!
//            if (returnedSamplesPosition.isEmpty()) {
//                throw new IllegalStateException("No samples found for study!!!");
//            }

            Integer nSamples = returnedSamplesPosition.size();
            @SuppressWarnings ("unchecked")
            List<String>[] samplesDataArray = new List[nSamples];
            annotMap.put("PASS", row.getPassCount().toString());
            annotMap.put("CALL", row.getCallCount().toString());

            double passrate = row.getPassCount().doubleValue() / nSamples.doubleValue();
            double callrate = row.getCallCount().doubleValue() / nSamples.doubleValue();
            double opr = passrate * callrate;
            annotation.getAdditionalAttributes().put(studyId + "_PR", passrate);
            annotation.getAdditionalAttributes().put(studyId + "_CR", callrate);
            annotation.getAdditionalAttributes().put(studyId + "_OPR", opr); // OVERALL
                                                                             // pass
                                                                             // rate

            BiMap<Integer, String> mapSampleIds = studyConfiguration.getSampleIds().inverse();
            for (String genotype : row.getGenotypes()) {
                if (genotype.equals(VariantTableStudyRow.OTHER)) {
                    continue; // skip OTHER -> see Complex type
                }
                String returnedGenotype = genotype;
                for (Integer sampleId : row.getSampleIds(genotype)) {
                    String sampleName = mapSampleIds.get(sampleId);
                    Integer sampleIdx = returnedSamplesPosition.get(sampleName);
                    if (sampleIdx == null) {
                        continue;   //Sample may not be required. Ignore this sample.
                    }
                    List<String> lst = Arrays.asList(returnedGenotype, StringUtils.EMPTY);
                    samplesDataArray[sampleIdx] = lst;
                }
            }
            // Load Secondary Index
            List<VariantProto.AlternateCoordinate> s2cgt = row.getComplexVariant().getSecondaryAlternatesList();
            int secondaryAlternatesCount = row.getComplexVariant().getSecondaryAlternatesCount();
            List<AlternateCoordinate> secAltArr = new ArrayList<AlternateCoordinate>(secondaryAlternatesCount);
            if (secondaryAlternatesCount > 0) {
                for (VariantProto.AlternateCoordinate altcoord : s2cgt) {
                    VariantType vart = VariantType.valueOf(altcoord.getType().name());
                    String chr = StringUtils.isEmpty(altcoord.getChromosome()) ? variant.getChromosome() : altcoord.getChromosome();
                    Integer start = altcoord.getStart() == 0 ? variant.getStart() : altcoord.getStart();
                    Integer end = altcoord.getEnd() == 0 ? variant.getEnd() : altcoord.getEnd();
                    String reference = StringUtils.isEmpty(altcoord.getReference()) ? "" : altcoord.getReference();
                    String alternate = StringUtils.isEmpty(altcoord.getAlternate()) ? "" : altcoord.getAlternate();
                    AlternateCoordinate alt = new AlternateCoordinate(chr, start, end, reference, alternate, vart);
                    secAltArr.add(alt);
                }
            }
            // Load complex genotypes
            for (Entry<Integer, VariantProto.Genotype> entry : row.getComplexVariant().getSampleToGenotype().entrySet()) {
                Integer samplePosition = getSamplePosition(returnedSamplesPosition, mapSampleIds, entry.getKey());
                if (samplePosition == null) {
                    continue;   //Sample may not be required. Ignore this sample.
                }
                VariantProto.Genotype xgt = entry.getValue();
                String returnedGenotype = new Genotype(xgt).toGenotypeString();
                samplesDataArray[samplePosition] = Arrays.asList(returnedGenotype, StringUtils.EMPTY);
            }
            // Fill gaps (with HOM_REF)
            int homRef = 0;
            for (int i = 0; i < samplesDataArray.length; i++) {
                if (samplesDataArray[i] == null) {
                    ArrayList<String> data = new ArrayList<>(2);
                    data.add(VariantTableStudyRow.HOM_REF);
                    data.add(StringUtils.EMPTY);
                    samplesDataArray[i] = data;
                    homRef++;
                }
            }
            for (Entry<String, SampleList> entry : row.getComplexFilter().getFilterNonPass().entrySet()) {
                String filterString = entry.getKey();
                for (Integer id : entry.getValue().getSampleIdsList()) {
                    Integer samplePosition = getSamplePosition(returnedSamplesPosition, mapSampleIds, id);
                    if (samplePosition == null) {
                        continue; // Sample may not be required. Ignore this
                                  // sample.
                    }
                    samplesDataArray[samplePosition].set(1, filterString);
                }
                String sampleName = mapSampleIds.get(entry.getKey());
                Integer samplePosition = returnedSamplesPosition.get(sampleName);
                if (samplePosition == null) {
                    continue; // Sample may not be required. Ignore this sample.
                }
            }
            // Fill gaps (with PASS)
            int fillCnt = 0;
            for (int i = 0; i < samplesDataArray.length; i++) {
                if (StringUtils.isBlank(samplesDataArray[i].get(1))) {
                    samplesDataArray[i].set(1, "PASS");
                    ++fillCnt;
                }
            }
            if (fillCnt != row.getPassCount().intValue()) {
                throw new RuntimeException(
                        String.format("Pass count %s does not match filter fill count: %s", row.getPassCount(), fillCnt));
            }
            if (homRef != row.getHomRefCount()) {
                String message = "Wrong number of HomRef samples for variant " + variant + ". Got " + homRef + ", expect "
                        + row.getHomRefCount() + ". Samples number: " + samplesDataArray.length + " , ";
                for (String studyColumn : VariantTableStudyRow.STUDY_COLUMNS) {
                    message += "'" + studyColumn + "':" + row.getSampleIds(studyColumn) + " , ";
                }

                if (failOnWrongVariants) {
                    throw new RuntimeException(message);
                } else {
                    logger.warn(message);
                }
            }
            List<List<String>> samplesData = Arrays.asList(samplesDataArray);

            StudyEntry studyEntry = new StudyEntry(Integer.toString(studyConfiguration.getStudyId()));
            studyEntry.setSamplesPosition(returnedSamplesPosition);
            studyEntry.setSamplesData(samplesData);
            studyEntry.setFormat(Arrays.asList(VariantMerger.GT_KEY, VariantMerger.VCF_FILTER));
            studyEntry.setFiles(Collections.singletonList(new FileEntry("", "", annotMap)));
            studyEntry.setSecondaryAlternates(secAltArr);
            variant.addStudyEntry(studyEntry);
        }
        variant.setAnnotation(annotation);
        if (variant.getStudiesMap().isEmpty()) {
            throw new IllegalStateException("No Studies registered for variant!!! " + variant);
        }
        return variant;
    }

    private Integer getSamplePosition(LinkedHashMap<String, Integer> returnedSamplesPosition, BiMap<Integer, String> mapSampleIds,
            Integer id) {
        String sampleName = mapSampleIds.get(id);
        Integer samplePosition = returnedSamplesPosition.get(sampleName);
        return samplePosition;
    }

    /**
     * Creates a SORTED MAP with the required samples position.
     *
     * @param studyConfiguration Study Configuration
     * @return Sorted linked hash map
     */
    private LinkedHashMap<String, Integer> getReturnedSamplesPosition(StudyConfiguration studyConfiguration) {
        if (!returnedSamplesPosition.containsKey(studyConfiguration.getStudyId())) {
            LinkedHashMap<String, Integer> samplesPosition;
            if (returnedSamples.isEmpty()) {
                BiMap<Integer, String> unorderedSamplesPosition =
                        StudyConfiguration.getIndexedSamplesPosition(studyConfiguration).inverse();
                samplesPosition = new LinkedHashMap<>(unorderedSamplesPosition.size());
                for (int i = 0; i < unorderedSamplesPosition.size(); i++) {
                    samplesPosition.put(unorderedSamplesPosition.get(i), i);
                }
            } else {
                samplesPosition = new LinkedHashMap<>(returnedSamples.size());
                int index = 0;
                BiMap<String, Integer> indexedSamplesId = StudyConfiguration.getIndexedSamples(studyConfiguration);
                for (String returnedSample : returnedSamples) {
                    if (indexedSamplesId.containsKey(returnedSample)) {
                        samplesPosition.put(returnedSample, index++);
                    }
                }
            }
            returnedSamplesPosition.put(studyConfiguration.getStudyId(), samplesPosition);
        }
        return returnedSamplesPosition.get(studyConfiguration.getStudyId());
    }


    public static boolean isFailOnWrongVariants() {
        return failOnWrongVariants;
    }

    public static void setFailOnWrongVariants(boolean b) {
        failOnWrongVariants = b;
    }
}
