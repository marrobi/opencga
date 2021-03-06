package org.opencb.opencga.storage.core.variant.adaptors.sample;

import org.opencb.biodata.models.feature.Genotype;
import org.opencb.biodata.models.variant.StudyEntry;
import org.opencb.biodata.models.variant.Variant;
import org.opencb.biodata.models.variant.avro.FileEntry;
import org.opencb.biodata.models.variant.stats.VariantStats;
import org.opencb.commons.datastore.core.DataResult;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.opencga.core.results.VariantQueryResult;
import org.opencb.opencga.storage.core.metadata.VariantStorageMetadataManager;
import org.opencb.opencga.storage.core.metadata.models.StudyMetadata;
import org.opencb.opencga.storage.core.variant.VariantStorageOptions;
import org.opencb.opencga.storage.core.variant.adaptors.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Created by jacobo on 27/03/19.
 */
public class VariantSampleDataManager {

    public static final String SAMPLE_BATCH_SIZE = "sampleBatchSize";
    public static final int SAMPLE_BATCH_SIZE_DEFAULT = 10000;
    public static final String MERGE = "merge";

    private final VariantDBAdaptor dbAdaptor;
    private final VariantStorageMetadataManager metadataManager;
    private final Map<String, String> normalizeGt = new HashMap<>();
    private final Logger logger = LoggerFactory.getLogger(VariantSampleDataManager.class);

    public VariantSampleDataManager(VariantDBAdaptor dbAdaptor) {
        this.dbAdaptor = dbAdaptor;
        this.metadataManager = dbAdaptor.getMetadataManager();

    }

    public final DataResult<VariantSampleData> getSampleData(String variant, String study, QueryOptions options) {
        options = options == null ? new QueryOptions() : options;
        int sampleLimit = options.getInt(SAMPLE_BATCH_SIZE, SAMPLE_BATCH_SIZE_DEFAULT);
        return getSampleData(variant, study, options, sampleLimit);
    }

    public final DataResult<VariantSampleData> getSampleData(String variant, String study, QueryOptions options, int sampleLimit) {
        options = options == null ? new QueryOptions() : options;

        Set<String> genotypes = new HashSet<>(options.getAsStringList(VariantQueryParam.GENOTYPE.key()));
        if (genotypes.isEmpty()) {
            genotypes.add("0/1");
            genotypes.add("1/1");
        }

        StudyMetadata studyMetadata = metadataManager.getStudyMetadata(study);
        boolean studyWithGts = !studyMetadata.getAttributes().getBoolean(VariantStorageOptions.EXCLUDE_GENOTYPES.key(),
                VariantStorageOptions.EXCLUDE_GENOTYPES.defaultValue());

        if (!studyWithGts) {
            genotypes = Collections.singleton(GenotypeClass.NA_GT_VALUE);
        }
        List<String> loadedGenotypes = studyMetadata.getAttributes().getAsStringList(VariantStorageOptions.LOADED_GENOTYPES.key());
        if (loadedGenotypes.size() == 1) {
            if (loadedGenotypes.contains(GenotypeClass.NA_GT_VALUE) || loadedGenotypes.contains("-1")) {
                genotypes = Collections.singleton(GenotypeClass.NA_GT_VALUE);
            }
        } else if (loadedGenotypes.contains(GenotypeClass.NA_GT_VALUE)) {
            genotypes.add(GenotypeClass.NA_GT_VALUE);
        }

        List<String> includeSamples = options.getAsStringList(VariantQueryParam.INCLUDE_SAMPLE.key());
        boolean merge = options.getBoolean(MERGE, false);

        return getSampleData(variant, study, options, includeSamples, studyWithGts, genotypes, merge, sampleLimit);
    }

    protected DataResult<VariantSampleData> getSampleData(
            String variant, String study, QueryOptions options, List<String> includeSamples, boolean studyWithGts, Set<String> genotypes,
            boolean merge, int sampleLimit) {
        options = options == null ? new QueryOptions() : options;
        int studyId = metadataManager.getStudyId(study);
        int skip = Math.max(0, options.getInt(QueryOptions.SKIP, 0));
        int limit = Math.max(0, options.getInt(QueryOptions.LIMIT, 10));
        int dbTime = 0;

        Map<String, Integer> gtCountMap = new HashMap<>();
        Map<String, List<SampleData>> gtMap = new HashMap<>();
        Map<String, FileEntry> files = new HashMap<>();
        Map<String, VariantStats> stats = new HashMap<>();

        for (String gt : merge ? Collections.singleton(VariantQueryUtils.ALL) : genotypes) {
            gtCountMap.put(gt, 0);
            gtMap.put(gt, new ArrayList<>(limit));
        }

        int sampleSkip = 0;
        int readSamples = 0;
        int queries = 0;
        while (true) {
            Query query = new Query(VariantQueryParam.ID.key(), variant)
                    .append(VariantQueryParam.STUDY.key(), study)
                    .append(VariantQueryParam.SAMPLE_LIMIT.key(), sampleLimit)
                    .append(VariantQueryParam.SAMPLE_SKIP.key(), sampleSkip);
            if (includeSamples != null && !includeSamples.isEmpty()) {
                query.append(VariantQueryParam.INCLUDE_SAMPLE.key(), includeSamples); // if empty, will return all
            }
            sampleSkip += sampleLimit;
            QueryOptions variantQueryOptions;
            if (stats.isEmpty()) {
                variantQueryOptions = new QueryOptions(QueryOptions.EXCLUDE, VariantField.ANNOTATION);
            } else {
                variantQueryOptions = new QueryOptions(QueryOptions.EXCLUDE,
                        Arrays.asList(VariantField.ANNOTATION, VariantField.STUDIES_STATS));
            }

            VariantQueryResult<Variant> result = dbAdaptor.get(query, variantQueryOptions);
            if (result.getNumResults() == 0) {
                throw VariantQueryException.variantNotFound(variant);
            }
            dbTime += result.getTime();
            queries++;
            Variant v = result.first();

            StudyEntry studyEntry = v.getStudies().get(0);

            if (studyEntry.getStats() != null) {
                stats.putAll(studyEntry.getStats());
            }

            List<String> samples = studyEntry.getOrderedSamplesName();
            readSamples += samples.size();
            for (String sample : samples) {
                Map<String, String> sampleDataAsMap = studyEntry.getSampleDataAsMap(sample);

                String gt = normalizeGt(sampleDataAsMap.getOrDefault("GT", GenotypeClass.NA_GT_VALUE));
                if (gt.equals(".")) {
                    gt = GenotypeClass.NA_GT_VALUE;
                }

                if (genotypes.contains(gt)) {
                    // Skip other genotypes

                    if (merge) {
                        // Merge after filtering by genotype
                        gt = VariantQueryUtils.ALL;
                    }
                    List<SampleData> gtList = gtMap.get(gt);
                    if (gtCountMap.merge(gt, 1, Integer::sum) > skip) {
                        if (gtList.size() < limit) {
                            Integer sampleId = metadataManager.getSampleId(studyId, sample);
                            FileEntry fileEntry = null;
                            for (Integer fileId : metadataManager.getFileIdsFromSampleIds(studyId, Collections.singleton(sampleId))) {
                                String fileName = metadataManager.getFileName(studyId, fileId);
                                fileEntry = studyEntry.getFile(fileName);
                                break;
                            }
                            if (fileEntry == null) {
                                if (gt.equals(GenotypeClass.NA_GT_VALUE)) {
                                    continue;
                                }
                                List<String> fileNames = new LinkedList<>();
                                for (Integer fileId : metadataManager.getFileIdsFromSampleIds(studyId, Collections.singleton(sampleId))) {
                                    fileNames.add(metadataManager.getFileName(studyId, fileId));
                                }
                                throw new VariantQueryException("No file found for sample '" + sample + "', expected any of " + fileNames);
                            }
                            SampleData sampleData = new SampleData(sample, sampleDataAsMap, fileEntry.getFileId());
                            files.put(fileEntry.getFileId(), fileEntry);
                            gtList.add(sampleData);
                        }
                    }
                }
            }

            if (samples.size() < sampleLimit) {
//                logger.debug("Exit end samples");
                break;
            }


            if (gtMap.values().stream().allMatch(c -> c.size() >= limit)) {
//                logger.debug("Exit limit");
                break;
            }
        }

//        String msg = "Queries : " + queries + " , readSamples : " + readSamples;
        return new DataResult<>(dbTime, Collections.emptyList(), 1,
                Collections.singletonList(new VariantSampleData(variant, study, gtMap, files, stats)), 1);
    }

    protected final String normalizeGt(String gt) {
        if (gt.contains("|")) {
            return normalizeGt.computeIfAbsent(gt, k -> {
                Genotype genotype = new Genotype(k.replace('|', '/'));
                genotype.normalizeAllelesIdx();
                return genotype.toString();
            });
        }
        return gt;
    }

}
