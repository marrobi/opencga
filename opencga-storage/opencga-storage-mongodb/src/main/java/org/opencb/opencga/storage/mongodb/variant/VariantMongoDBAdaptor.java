/*
 * Copyright 2015 OpenCB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opencb.opencga.storage.mongodb.variant;

import com.mongodb.*;

import java.net.UnknownHostException;
import java.util.*;
import java.util.regex.Pattern;

import org.opencb.biodata.models.feature.Genotype;
import org.opencb.biodata.models.feature.Region;
import org.opencb.biodata.models.variant.Variant;
import org.opencb.biodata.models.variant.VariantSourceEntry;
import org.opencb.biodata.models.variant.annotation.VariantAnnotation;
import org.opencb.biodata.models.variant.stats.VariantStats;
import org.opencb.commons.io.DataWriter;
import org.opencb.datastore.core.QueryOptions;
import org.opencb.datastore.core.QueryResult;
import org.opencb.datastore.mongodb.MongoDBCollection;
import org.opencb.datastore.mongodb.MongoDataStore;
import org.opencb.datastore.mongodb.MongoDataStoreManager;
import org.opencb.opencga.storage.core.StudyConfiguration;
import org.opencb.opencga.storage.core.variant.StudyConfigurationManager;
import org.opencb.opencga.storage.core.variant.VariantStorageManager;
import org.opencb.opencga.storage.core.variant.adaptors.VariantDBIterator;
import org.opencb.opencga.storage.core.variant.adaptors.VariantSourceDBAdaptor;
import org.opencb.opencga.storage.core.variant.stats.VariantStatsWrapper;
import org.opencb.opencga.storage.mongodb.utils.MongoCredentials;
import org.opencb.opencga.storage.core.variant.adaptors.VariantDBAdaptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Cristina Yenyxe Gonzalez Garcia <cyenyxe@ebi.ac.uk>
 */
public class VariantMongoDBAdaptor implements VariantDBAdaptor {


    private final MongoDataStoreManager mongoManager;
    private final MongoDataStore db;
    private final String collectionName;
    private final MongoDBCollection variantsCollection;
    private final VariantSourceMongoDBAdaptor variantSourceMongoDBAdaptor;
    private StudyConfigurationManager studyConfigurationManager;

    private DataWriter dataWriter;
    protected static Logger logger = LoggerFactory.getLogger(VariantMongoDBAdaptor.class);

    @Deprecated
    public VariantMongoDBAdaptor(MongoCredentials credentials, String variantsCollectionName, String filesCollectionName)
            throws UnknownHostException {
        this(credentials, variantsCollectionName, filesCollectionName, new MongoDBStudyConfigurationManager(credentials, filesCollectionName));
    }

    public VariantMongoDBAdaptor(MongoCredentials credentials, String variantsCollectionName, String filesCollectionName, StudyConfigurationManager studyConfigurationManager)
            throws UnknownHostException {
        // Mongo configuration
        mongoManager = new MongoDataStoreManager(credentials.getDataStoreServerAddresses());
        db = mongoManager.get(credentials.getMongoDbName(), credentials.getMongoDBConfiguration());
        variantSourceMongoDBAdaptor = new VariantSourceMongoDBAdaptor(credentials, filesCollectionName);
        collectionName = variantsCollectionName;
        variantsCollection = db.getCollection(collectionName);
        this.studyConfigurationManager = studyConfigurationManager;
    }


    @Override
    public void setDataWriter(DataWriter dataWriter) {
        this.dataWriter = dataWriter;
    }

    @Override
    public QueryResult<Variant> getAllVariants(QueryOptions options) {

        QueryBuilder qb = QueryBuilder.start();
        parseQueryOptions(options, qb);
        DBObject projection = parseProjectionQueryOptions(options);
        logger.debug("Query to be executed {}", qb.get().toString());

        return variantsCollection.find(qb.get(), projection, getDbObjectToVariantConverter(options), options);
    }


    @Override
    public QueryResult<Variant> getVariantById(String id, QueryOptions options) {

//        BasicDBObject query = new BasicDBObject(DBObjectToVariantConverter.ID_FIELD, id);

        if(options == null) {
            options = new QueryOptions(ID, id);
        } else {
            options.addToListOption(ID, id);
        }

        QueryBuilder qb = QueryBuilder.start();
        parseQueryOptions(options, qb);
        DBObject projection = parseProjectionQueryOptions(options);
        logger.debug("Query to be executed {}", qb.get().toString());

//        return coll.find(query, options, variantConverter);
        QueryResult<Variant> queryResult = variantsCollection.find(qb.get(), projection, getDbObjectToVariantConverter(options), options);
        queryResult.setId(id);
        return queryResult;
    }

    @Override
    public List<QueryResult<Variant>> getAllVariantsByIdList(List<String> idList, QueryOptions options) {
        List<QueryResult<Variant>> allResults = new ArrayList<>(idList.size());
        for (String r : idList) {
            QueryResult<Variant> queryResult = getVariantById(r, options);
            allResults.add(queryResult);
        }
        return allResults;
    }


    @Override
    public QueryResult<Variant> getAllVariantsByRegion(Region region, QueryOptions options) {

        QueryBuilder qb = QueryBuilder.start();
        getRegionFilter(region, qb);
        parseQueryOptions(options, qb);
        DBObject projection = parseProjectionQueryOptions(options);

        if (options == null) {
            options = new QueryOptions();
        }
        
        QueryResult<Variant> queryResult = variantsCollection.find(qb.get(), projection, getDbObjectToVariantConverter(options), options);
        queryResult.setId(region.toString());
        return queryResult;
    }

    @Override
    public List<QueryResult<Variant>> getAllVariantsByRegionList(List<Region> regionList, QueryOptions options) {
        List<QueryResult<Variant>> allResults;
        if (options == null) {
            options = new QueryOptions();
        }
        
        // If the users asks to sort the results, do it by chromosome and start
        if (options.getBoolean(SORT, false)) {
            options.put(SORT, new BasicDBObject("chr", 1).append("start", 1));
        }
        
        // If the user asks to merge the results, run only one query,
        // otherwise delegate in the method to query regions one by one
        if (options.getBoolean(MERGE, false)) {
            options.add(REGION, regionList);
            allResults = Collections.singletonList(getAllVariants(options));
        } else {
            allResults = new ArrayList<>(regionList.size());
            for (Region r : regionList) {
                QueryResult queryResult = getAllVariantsByRegion(r, options);
                queryResult.setId(r.toString());
                allResults.add(queryResult);
            }
        }
        return allResults;
    }

    @Override
    public QueryResult getAllVariantsByRegionAndStudies(Region region, List<String> studyId, QueryOptions options) {

        // Aggregation for filtering when more than one study is present
        QueryBuilder qb = QueryBuilder.start(DBObjectToVariantConverter.STUDIES_FIELD + "." + DBObjectToVariantSourceEntryConverter.STUDYID_FIELD).in(studyId);
        getRegionFilter(region, qb);
        parseQueryOptions(options, qb);

        DBObject match = new BasicDBObject("$match", qb.get());
        DBObject unwind = new BasicDBObject("$unwind", "$" + DBObjectToVariantConverter.STUDIES_FIELD);
        DBObject match2 = new BasicDBObject("$match", 
                new BasicDBObject(DBObjectToVariantConverter.STUDIES_FIELD + "." + DBObjectToVariantSourceEntryConverter.STUDYID_FIELD,
                        new BasicDBObject("$in", studyId)));

        logger.debug("Query to be executed {}", qb.get().toString());

        return variantsCollection.aggregate(/*"$variantsRegionStudies", */Arrays.asList(match, unwind, match2), options);
    }

    @Override
    public QueryResult getVariantFrequencyByRegion(Region region, QueryOptions options) {
        // db.variants.aggregate( { $match: { $and: [ {chr: "1"}, {start: {$gt: 251391, $lt: 2701391}} ] }}, 
        //                        { $group: { _id: { $subtract: [ { $divide: ["$start", 20000] }, { $divide: [{$mod: ["$start", 20000]}, 20000] } ] }, 
        //                                  totalCount: {$sum: 1}}})

        if(options == null) {
            options = new QueryOptions();
        }

        int interval = options.getInt("interval", 20000);

        BasicDBObject start = new BasicDBObject("$gt", region.getStart());
        start.append("$lt", region.getEnd());

        BasicDBList andArr = new BasicDBList();
        andArr.add(new BasicDBObject(DBObjectToVariantConverter.CHROMOSOME_FIELD, region.getChromosome()));
        andArr.add(new BasicDBObject(DBObjectToVariantConverter.START_FIELD, start));

        // Parsing the rest of options
        QueryBuilder qb = new QueryBuilder();
        DBObject optionsMatch = parseQueryOptions(options, qb).get();
        if(!optionsMatch.keySet().isEmpty()) {
            andArr.add(optionsMatch);
        }
        DBObject match = new BasicDBObject("$match", new BasicDBObject("$and", andArr));


//        qb.and("_at.chunkIds").in(chunkIds);
//        qb.and(DBObjectToVariantConverter.END_FIELD).greaterThanEquals(region.getStart());
//        qb.and(DBObjectToVariantConverter.START_FIELD).lessThanEquals(region.getEnd());
//
//        List<String> chunkIds = getChunkIds(region);
//        DBObject regionObject = new BasicDBObject("_at.chunkIds", new BasicDBObject("$in", chunkIds))
//                .append(DBObjectToVariantConverter.END_FIELD, new BasicDBObject("$gte", region.getStart()))
//                .append(DBObjectToVariantConverter.START_FIELD, new BasicDBObject("$lte", region.getEnd()));


        BasicDBList divide1 = new BasicDBList();
        divide1.add("$start");
        divide1.add(interval);

        BasicDBList divide2 = new BasicDBList();
        divide2.add(new BasicDBObject("$mod", divide1));
        divide2.add(interval);

        BasicDBList subtractList = new BasicDBList();
        subtractList.add(new BasicDBObject("$divide", divide1));
        subtractList.add(new BasicDBObject("$divide", divide2));


        BasicDBObject substract = new BasicDBObject("$subtract", subtractList);

        DBObject totalCount = new BasicDBObject("$sum", 1);

        BasicDBObject g = new BasicDBObject("_id", substract);
        g.append("features_count", totalCount);
        DBObject group = new BasicDBObject("$group", g);

        DBObject sort = new BasicDBObject("$sort", new BasicDBObject("_id", 1));

//        logger.info("getAllIntervalFrequencies - (>·_·)>");
//        System.out.println(options.toString());
//
//        System.out.println(match.toString());
//        System.out.println(group.toString());
//        System.out.println(sort.toString());

        long dbTimeStart = System.currentTimeMillis();
        QueryResult output = variantsCollection.aggregate(/*"$histogram", */Arrays.asList(match, group, sort), options);
        long dbTimeEnd = System.currentTimeMillis();

        Map<Long, DBObject> ids = new HashMap<>();
        // Create DBObject for intervals with features inside them
        for (DBObject intervalObj : (List<DBObject>) output.getResult()) {
            Long _id = Math.round((Double) intervalObj.get("_id"));//is double

            DBObject intervalVisited = ids.get(_id);
            if (intervalVisited == null) {
                intervalObj.put("_id", _id);
                intervalObj.put("start", getChunkStart(_id.intValue(), interval));
                intervalObj.put("end", getChunkEnd(_id.intValue(), interval));
                intervalObj.put("chromosome", region.getChromosome());
                intervalObj.put("features_count", Math.log((int) intervalObj.get("features_count")));
                ids.put(_id, intervalObj);
            } else {
                Double sum = (Double) intervalVisited.get("features_count") + Math.log((int) intervalObj.get("features_count"));
                intervalVisited.put("features_count", sum.intValue());
            }
        }

        // Create DBObject for intervals without features inside them
        BasicDBList resultList = new BasicDBList();
        int firstChunkId = getChunkId(region.getStart(), interval);
        int lastChunkId = getChunkId(region.getEnd(), interval);
        DBObject intervalObj;
        for (int chunkId = firstChunkId; chunkId <= lastChunkId; chunkId++) {
            intervalObj = ids.get((long) chunkId);
            if (intervalObj == null) {
                intervalObj = new BasicDBObject();
                intervalObj.put("_id", chunkId);
                intervalObj.put("start", getChunkStart(chunkId, interval));
                intervalObj.put("end", getChunkEnd(chunkId, interval));
                intervalObj.put("chromosome", region.getChromosome());
                intervalObj.put("features_count", 0);
            }
            resultList.add(intervalObj);
        }

        QueryResult queryResult = new QueryResult(region.toString(), ((Long) (dbTimeEnd - dbTimeStart)).intValue(),
                resultList.size(), resultList.size(), null, null, resultList);

        return queryResult;
    }


    @Override
    public QueryResult getAllVariantsByGene(String geneName, QueryOptions options) {

        QueryBuilder qb = QueryBuilder.start();
        if(options == null) {
            options = new QueryOptions(GENE, geneName);
        } else {
            options.addToListOption(GENE, geneName);
        }
        options.put(GENE, geneName);
        parseQueryOptions(options, qb);
        DBObject projection = parseProjectionQueryOptions(options);
        QueryResult<Variant> queryResult = variantsCollection.find(qb.get(), projection, getDbObjectToVariantConverter(options), options);
        queryResult.setId(geneName);
        return queryResult;
    }


    @Override
    public QueryResult groupBy(String field, QueryOptions options) {

        String documentPath;
        switch (field) {
            case "gene":
            default:
                documentPath = DBObjectToVariantConverter.ANNOTATION_FIELD + "." + DBObjectToVariantAnnotationConverter.CONSEQUENCE_TYPE_FIELD + "." + DBObjectToVariantAnnotationConverter.GENE_NAME_FIELD;
                break;
            case "ensemblGene":
                documentPath = DBObjectToVariantConverter.ANNOTATION_FIELD + "." + DBObjectToVariantAnnotationConverter.CONSEQUENCE_TYPE_FIELD + "." + DBObjectToVariantAnnotationConverter.ENSEMBL_GENE_ID_FIELD;
                break;
            case "ct":
            case "consequence_type":
                documentPath = DBObjectToVariantConverter.ANNOTATION_FIELD + "." + DBObjectToVariantAnnotationConverter.CONSEQUENCE_TYPE_FIELD + "." + DBObjectToVariantAnnotationConverter.SO_ACCESSION_FIELD;
                break;
        }

        QueryBuilder qb = QueryBuilder.start();
        parseQueryOptions(options, qb);

        DBObject match = new BasicDBObject("$match", qb.get());
        DBObject project = new BasicDBObject("$project", new BasicDBObject("field", "$"+documentPath));
        DBObject unwind = new BasicDBObject("$unwind", "$field");
        DBObject group = new BasicDBObject("$group", new BasicDBObject("_id", "$field")
//                .append("field", "$field")
                .append("count", new BasicDBObject("$sum", 1))); // sum, count, avg, ...?
        DBObject sort = new BasicDBObject("$sort", new BasicDBObject("count", options != null ? options.getInt("order", -1) : -1)); // 1 = ascending, -1 = descending
        DBObject limit = new BasicDBObject("$limit", options != null && options.getInt("limit", -1) > 0 ? options.getInt("limit") : 10);

        return variantsCollection.aggregate(Arrays.asList(match, project, unwind, group, sort, limit), options);
    }

    @Override
    public QueryResult getMostAffectedGenes(int numGenes, QueryOptions options) {
        return getGenesRanking(numGenes, -1, options);
    }

    @Override
    public QueryResult getLeastAffectedGenes(int numGenes, QueryOptions options) {
        return getGenesRanking(numGenes, 1, options);
    }

    private QueryResult getGenesRanking(int numGenes, int order, QueryOptions options) {
        if (options == null) {
            options = new QueryOptions();
        }
        options.put("limit", numGenes);
        options.put("order", order);

        return groupBy("gene", options);
    }


    @Override
    public QueryResult getTopConsequenceTypes(int numConsequenceTypes, QueryOptions options) {
        return getConsequenceTypesRanking(numConsequenceTypes, -1, options);
    }

    @Override
    public QueryResult getBottomConsequenceTypes(int numConsequenceTypes, QueryOptions options) {
        return getConsequenceTypesRanking(numConsequenceTypes, 1, options);
    }

    private QueryResult getConsequenceTypesRanking(int numConsequenceTypes, int order, QueryOptions options) {
        if (options == null) {
            options = new QueryOptions();
        }
        options.put("limit", numConsequenceTypes);
        options.put("order", order);

        return groupBy("ct", options);
    }

    @Override
    public VariantSourceDBAdaptor getVariantSourceDBAdaptor() {
        return variantSourceMongoDBAdaptor;
    }

    public StudyConfigurationManager getStudyConfigurationDBAdaptor() {
        return studyConfigurationManager;
    }

    public void setStudyConfigurationDBAdaptor(StudyConfigurationManager studyConfigurationManager) {
        this.studyConfigurationManager = studyConfigurationManager;
    }

    @Override
    public VariantDBIterator iterator() {
        return iterator(new QueryOptions());
    }


    @Override
    public VariantDBIterator iterator(QueryOptions options) {

        QueryBuilder qb = QueryBuilder.start();
        parseQueryOptions(options, qb);
        DBObject projection = parseProjectionQueryOptions(options);
        DBCursor dbCursor = variantsCollection.nativeQuery().find(qb.get(), projection, options);
        dbCursor.batchSize(options.getInt("batchSize", 100));
        return new VariantMongoDBIterator(dbCursor, getDbObjectToVariantConverter(options));
    }

    //@Override
    public QueryResult insert(List<Variant> variants, StudyConfiguration studyConfiguration, QueryOptions options) {
        int fileId = options.getInt(VariantStorageManager.Options.FILE_ID.key());
        boolean includeStats = options.getBoolean(VariantStorageManager.Options.INCLUDE_STATS.key(), VariantStorageManager.Options.INCLUDE_STATS.defaultValue());
        boolean includeSrc = options.getBoolean(VariantStorageManager.Options.INCLUDE_SRC.key(), VariantStorageManager.Options.INCLUDE_SRC.defaultValue());
        boolean includeGenotypes = options.getBoolean(VariantStorageManager.Options.INCLUDE_GENOTYPES.key(), VariantStorageManager.Options.INCLUDE_GENOTYPES.defaultValue());
//        boolean compressGenotypes = options.getBoolean(VariantStorageManager.Options.COMPRESS_GENOTYPES.key(), VariantStorageManager.Options.COMPRESS_GENOTYPES.defaultValue());
//        String defaultGenotype = options.getString(MongoDBVariantStorageManager.DEFAULT_GENOTYPE, "0|0");

        DBObjectToVariantConverter variantConverter = new DBObjectToVariantConverter(null, includeStats? new DBObjectToVariantStatsConverter() : null);
        DBObjectToVariantSourceEntryConverter sourceEntryConverter = new DBObjectToVariantSourceEntryConverter(includeSrc,
                includeGenotypes? new DBObjectToSamplesConverter(studyConfiguration) : null);
        return insert(variants, fileId, variantConverter, sourceEntryConverter, studyConfiguration, null);
    }

    /**
     * Two steps insertion:
     *      First check that the variant and study exists making an update.
     *      For those who doesn't exist, pushes a study with the file and genotype information
     *
     *      The documents that throw a "dup key" exception are those variants that exist and have the study.
     *      Then, only for those variants, make a second update.
     *
     * *An interesting idea would be to invert this actions depending on the number of already inserted variants.
     *
     * @param data  Variants to insert
     */
    /*package*/ QueryResult insert(List<Variant> data, int fileId, DBObjectToVariantConverter variantConverter,
                                   DBObjectToVariantSourceEntryConverter variantSourceEntryConverter, StudyConfiguration studyConfiguration, List<Integer> loadedSampleIds) {
        if (data.isEmpty()) {
            return new QueryResult("insertVariants");
        }
        List<DBObject> queries = new ArrayList<>(data.size());
        List<DBObject> updates = new ArrayList<>(data.size());
        Set<String> nonInsertedVariants;
        String fileIdStr = Integer.toString(fileId);
        if (true) {
            nonInsertedVariants = new HashSet<>();

            if (loadedSampleIds == null) {
                HashSet<String> fileSamples = new HashSet<>();
                data.get(0).getSampleNames(Integer.toString(studyConfiguration.getStudyId()), fileIdStr).iterator().forEachRemaining(fileSamples::add);
                loadedSampleIds = new LinkedList<>();
                Set<String> allSamples = studyConfiguration.getSampleIds().keySet();
                for (String sample : allSamples) {
                    if (!fileSamples.contains(sample)) {
                        loadedSampleIds.add(studyConfiguration.getSampleIds().get(sample));
                    }
                }
            }
            Map missingSamples = Collections.emptyMap();
            String defaultGenotype = studyConfiguration.getAttributes().getString(MongoDBVariantStorageManager.DEFAULT_GENOTYPE, "");
            if (defaultGenotype.equals(DBObjectToSamplesConverter.UNKNOWN_GENOTYPE)) {
                logger.debug("Do not need fill gaps. DefaultGenotype is UNKNOWN_GENOTYPE({}).");
            } else if (!loadedSampleIds.isEmpty()) {
                missingSamples = new BasicDBObject(DBObjectToSamplesConverter.UNKNOWN_GENOTYPE, loadedSampleIds);   // ?/?
            }
            for (Variant variant : data) {
                String id = variantConverter.buildStorageId(variant);
                for (VariantSourceEntry variantSourceEntry : variant.getSourceEntries().values()) {
                    if (!variantSourceEntry.getFileId().equals(fileIdStr)) {
                        continue;
                    }
                    int studyId = studyConfiguration.getStudyId();
                    DBObject study = variantSourceEntryConverter.convertToStorageType(variantSourceEntry);
                    DBObject genotypes = (DBObject) study.get(DBObjectToVariantSourceEntryConverter.GENOTYPES_FIELD);
                    if (genotypes != null) {        //If genotypes is null, genotypes are not suppose to be loaded
                        genotypes.putAll(missingSamples);   //Add missing samples
                    }
                    DBObject push = new BasicDBObject(DBObjectToVariantConverter.STUDIES_FIELD, study);
                    BasicDBObject update = new BasicDBObject()
                            .append("$push", push)
                            .append("$setOnInsert", variantConverter.convertToStorageType(variant));
                    if (variant.getIds() != null && !variant.getIds().isEmpty() && !variant.getIds().iterator().next().isEmpty()) {
                        update.put("$addToSet", new BasicDBObject(DBObjectToVariantConverter.IDS_FIELD, new BasicDBObject("$each", variant.getIds())));
                    }
                    // { _id: <variant_id>, "studies.sid": {$ne: <studyId> } }
                    //If the variant exists and contains the study, this find will fail, will try to do the upsert, and throw a duplicated key exception.
                    queries.add(new BasicDBObject("_id", id).append(DBObjectToVariantConverter.STUDIES_FIELD + "." + DBObjectToVariantSourceEntryConverter.STUDYID_FIELD,
                            new BasicDBObject("$ne", studyId)));
                    updates.add(update);
                }
            }
            QueryOptions options = new QueryOptions("upsert", true);
            options.put("multi", false);
            try {
                variantsCollection.update(queries, updates, options);
            } catch (BulkWriteException e) {
                for (BulkWriteError writeError : e.getWriteErrors()) {
                    if (writeError.getCode() == 11000) { //Dup Key error code
                        nonInsertedVariants.add(writeError.getMessage().split("dup key")[1].split("\"")[1]);
                    } else {
                        throw e;
                    }
                }
            }
            queries.clear();
            updates.clear();
        }

        for (Variant variant : data) {
            variant.setAnnotation(null);
            String id = variantConverter.buildStorageId(variant);

            if (nonInsertedVariants != null && !nonInsertedVariants.contains(id)) {
                continue;   //Already inserted variant
            }

            for (VariantSourceEntry variantSourceEntry : variant.getSourceEntries().values()) {
                if (!variantSourceEntry.getFileId().equals(fileIdStr)) {
                    continue;
                }

                DBObject studyObject = variantSourceEntryConverter.convertToStorageType(variantSourceEntry);
                DBObject genotypes = (DBObject) studyObject.get(DBObjectToVariantSourceEntryConverter.GENOTYPES_FIELD);
                DBObject push = new BasicDBObject();
                if (genotypes != null) { //If genotypes is null, genotypes are not suppose to be loaded
                    for (String genotype : genotypes.keySet()) {
                        push.put(DBObjectToVariantConverter.STUDIES_FIELD + ".$." + DBObjectToVariantSourceEntryConverter.GENOTYPES_FIELD + "." + genotype, new BasicDBObject("$each", genotypes.get(genotype)));
                    }
                }
                push.put(DBObjectToVariantConverter.STUDIES_FIELD + ".$." + DBObjectToVariantSourceEntryConverter.FILES_FIELD, studyObject.get(DBObjectToVariantSourceEntryConverter.FILES_FIELD));
                BasicDBObject update = new BasicDBObject(new BasicDBObject("$push", push));


                queries.add(new BasicDBObject("_id", id).append(DBObjectToVariantConverter.STUDIES_FIELD + "." + DBObjectToVariantSourceEntryConverter.STUDYID_FIELD, studyConfiguration.getStudyId()));
                updates.add(update);

            }

        }
        if (queries.isEmpty()) {
            return new QueryResult();
        } else {
            QueryOptions options = new QueryOptions("upsert", false);
            options.put("multi", false);
            return variantsCollection.update(queries, updates, options);
        }
    }

    /* package */ QueryResult<WriteResult> fillFileGaps(int fileId, List<Region> regions, List<Integer> fileSampleIds, StudyConfiguration studyConfiguration) {

        // { "studies.sid" : <studyId>, "studies.files.fid" : { $ne : <fileId> } },
        // { $push : {
        //      "studies.$.gt.?/?" : {$each : [ <fileSampleIds> ] }
        // } }

        if (studyConfiguration.getAttributes().getString(MongoDBVariantStorageManager.DEFAULT_GENOTYPE, "").equals(DBObjectToSamplesConverter.UNKNOWN_GENOTYPE)) {
            logger.debug("Do not need fill gaps. DefaultGenotype is UNKNOWN_GENOTYPE({}).");
            return new QueryResult<>();
        }
        DBObject query = getRegionFilter(regions, new QueryBuilder()).get();
        query.put(DBObjectToVariantConverter.STUDIES_FIELD + "." + DBObjectToVariantSourceEntryConverter.STUDYID_FIELD,
                studyConfiguration.getStudyId());
        query.put(DBObjectToVariantConverter.STUDIES_FIELD + "." +
                        DBObjectToVariantSourceEntryConverter.FILES_FIELD + "." +
                        DBObjectToVariantSourceEntryConverter.FILEID_FIELD,
                new BasicDBObject("$ne", fileId));

        BasicDBObject update = new BasicDBObject("$push", new BasicDBObject()
                .append(DBObjectToVariantConverter.STUDIES_FIELD + ".$." +
                        DBObjectToVariantSourceEntryConverter.GENOTYPES_FIELD + "." +
                        DBObjectToSamplesConverter.UNKNOWN_GENOTYPE, new BasicDBObject("$each", fileSampleIds)));

        QueryOptions queryOptions = new QueryOptions("multi", true);
        return variantsCollection.update(query, update, queryOptions);
    }

    @Override
    public QueryResult updateAnnotations(List<VariantAnnotation> variantAnnotations, QueryOptions queryOptions) {

        DBCollection coll = db.getDb().getCollection(collectionName);
        BulkWriteOperation builder = coll.initializeUnorderedBulkOperation();

        long start = System.nanoTime();
        DBObjectToVariantConverter variantConverter = getDbObjectToVariantConverter(queryOptions);
        for (VariantAnnotation variantAnnotation : variantAnnotations) {
            String id = variantConverter.buildStorageId(variantAnnotation.getChromosome(), variantAnnotation.getStart(),
                    variantAnnotation.getReferenceAllele(), variantAnnotation.getAlternativeAllele());
            DBObject find = new BasicDBObject("_id", id);
            DBObjectToVariantAnnotationConverter converter = new DBObjectToVariantAnnotationConverter();
            DBObject convertedVariantAnnotation = converter.convertToStorageType(variantAnnotation);
//            System.out.println("convertedVariantAnnotation = " + convertedVariantAnnotation);
            DBObject update = new BasicDBObject("$set", new BasicDBObject(DBObjectToVariantConverter.ANNOTATION_FIELD,
                    convertedVariantAnnotation));
            builder.find(find).updateOne(update);
        }

        BulkWriteResult writeResult = builder.execute();

        return new QueryResult<>("", ((int) (System.nanoTime() - start)), 1, 1, "", "", Collections.singletonList(writeResult));
    }

    @Override
    public QueryResult updateStats(List<VariantStatsWrapper> variantStatsWrappers, int studyId, QueryOptions queryOptions) {
        DBCollection coll = db.getDb().getCollection(collectionName);
        BulkWriteOperation builder = coll.initializeUnorderedBulkOperation();

        long start = System.nanoTime();
        DBObjectToVariantStatsConverter statsConverter = new DBObjectToVariantStatsConverter();
//        VariantSource variantSource = queryOptions.get(VariantStorageManager.VARIANT_SOURCE, VariantSource.class);
        int fileId = queryOptions.getInt(VariantStorageManager.Options.FILE_ID.key());
        DBObjectToVariantConverter variantConverter = getDbObjectToVariantConverter(queryOptions);
        //TODO: Use the StudyConfiguration to change names to ids

        // TODO make unset of 'st' if already present?
        for (VariantStatsWrapper wrapper : variantStatsWrappers) {
            Map<String, VariantStats> cohortStats = wrapper.getCohortStats();
            Iterator<VariantStats> iterator = cohortStats.values().iterator();
            VariantStats variantStats = iterator.hasNext()? iterator.next() : null;
            List<DBObject> cohorts = statsConverter.convertCohortsToStorageType(cohortStats, studyId, fileId);   // TODO remove when we remove fileId
//            List cohorts = statsConverter.convertCohortsToStorageType(cohortStats, variantSource.getStudyId());   // TODO use when we remove fileId

            // add cohorts, overwriting old values if that cid, fid and sid already exists: remove and then add
            // db.variants.update(
            //      {_id:<id>},
            //      {$pull:{st:{cid:{$in:["Cohort 1","cohort 2"]}, fid:{$in:["file 1", "file 2"]}, sid:{$in:["study 1", "study 2"]}}}}
            // )
            // db.variants.update(
            //      {_id:<id>},
            //      {$push:{st:{$each: [{cid:"Cohort 1", fid:"file 1", ... , defaultValue:3},{cid:"Cohort 2", ... , defaultValue:3}] }}}
            // )

            if (!cohorts.isEmpty()) {
                String id = variantConverter.buildStorageId(wrapper.getChromosome(), wrapper.getPosition(),
                        variantStats.getRefAllele(), variantStats.getAltAllele());

                List<String> cohortIds = new ArrayList<>(cohorts.size());
                List<Integer> fileIds = new ArrayList<>(cohorts.size());
                List<Integer> studyIds = new ArrayList<>(cohorts.size());
                for (DBObject cohort : cohorts) {
                    cohortIds.add((String) cohort.get(DBObjectToVariantStatsConverter.COHORT_ID));
                    fileIds.add((Integer) cohort.get(DBObjectToVariantStatsConverter.FILE_ID));
                    studyIds.add((Integer) cohort.get(DBObjectToVariantStatsConverter.STUDY_ID));
                }

                DBObject find = new BasicDBObject("_id", id);

                DBObject update = new BasicDBObject("$pull",
                        new BasicDBObject(DBObjectToVariantConverter.STATS_FIELD,
                                new BasicDBObject()
                                        .append(
                                                DBObjectToVariantStatsConverter.STUDY_ID,
                                                new BasicDBObject("$in", studyIds))
                                        .append(
                                                DBObjectToVariantStatsConverter.FILE_ID,
                                                new BasicDBObject("$in", fileIds))
                                        .append(
                                                DBObjectToVariantStatsConverter.COHORT_ID,
                                                new BasicDBObject("$in", cohortIds))));

                builder.find(find).updateOne(update);

                DBObject push = new BasicDBObject("$push",
                        new BasicDBObject(DBObjectToVariantConverter.STATS_FIELD,
                                new BasicDBObject("$each", cohorts)));

                builder.find(find).update(push);
            }
        }

        // TODO handle if the variant didn't had that studyId in the files array
        // TODO check the substitution is done right if the stats are already present
        BulkWriteResult writeResult = builder.execute();
        int writes = writeResult.getModifiedCount();

        return new QueryResult<>("", ((int) (System.nanoTime() - start)), writes, writes, "", "", Collections.singletonList(writeResult));
    }

    //@Override
    public QueryResult deleteStudy(int studyId, QueryOptions queryOptions) {

        if (queryOptions == null) {
            queryOptions = new QueryOptions();
        }
        queryOptions.put(STUDIES, studyId);
        DBObject query = parseQueryOptions(queryOptions, new QueryBuilder()).get();

        // { $pull : { files : {  sid : <studyId> } } }
        BasicDBObject update = new BasicDBObject(
                "$pull",
                new BasicDBObject(
                        DBObjectToVariantConverter.STUDIES_FIELD,
                        new BasicDBObject(
                                DBObjectToVariantSourceEntryConverter.STUDYID_FIELD, studyId
                        )
                )
        );
        QueryResult<WriteResult> result = variantsCollection.update(query, update, new QueryOptions("multi", true));

        logger.debug("deleteStudy: query = {}", query);
        logger.debug("deleteStudy: update = {}", update);

        if (queryOptions.getBoolean("purge", false)) {
            BasicDBObject purgeQuery = new BasicDBObject(DBObjectToVariantConverter.STUDIES_FIELD, new BasicDBObject("$size", 0));
            variantsCollection.remove(purgeQuery, new QueryOptions("multi", true));
        }

        return result;
    }

    //@Override
    QueryResult deleteStats(int studyId, String cohortId) {

        // { st : { $elemMatch : {  sid : <studyId>, cid : <cohortId> } } }
        DBObject query = new BasicDBObject(DBObjectToVariantConverter.STATS_FIELD,
                new BasicDBObject("$elemMatch",
                        new BasicDBObject(DBObjectToVariantStatsConverter.STUDY_ID, studyId)
                                .append(DBObjectToVariantStatsConverter.COHORT_ID, cohortId)));

        // { $pull : { st : {  sid : <studyId>, cid : <cohortId> } } }
        BasicDBObject update = new BasicDBObject(
                "$pull",
                new BasicDBObject(DBObjectToVariantConverter.STATS_FIELD,
                        new BasicDBObject(DBObjectToVariantStatsConverter.STUDY_ID, studyId)
                                .append(DBObjectToVariantStatsConverter.COHORT_ID, cohortId)
                )
        );
        logger.debug("deleteStats: query = {}", query);
        logger.debug("deleteStats: update = {}", update);

        return variantsCollection.update(query, update, new QueryOptions("multi", true));
    }

    //@Override
    QueryResult deleteAnnotation(int annotationId, int studyId, QueryOptions queryOptions) {

        if (queryOptions == null) {
            queryOptions = new QueryOptions();
        }
        queryOptions.put(STUDIES, studyId);
        DBObject query = parseQueryOptions(queryOptions, new QueryBuilder()).get();

        DBObject update = new BasicDBObject("$unset", new BasicDBObject(DBObjectToVariantConverter.ANNOTATION_FIELD, ""));

        logger.debug("deleteAnnotation: query = {}", query);
        logger.debug("deleteAnnotation: update = {}", update);

        return variantsCollection.update(query, update, new QueryOptions("multi", true));
    }

    void createIndexes(QueryOptions options) {
        logger.debug("Start creating indexes");
        DBObject onBackground = new BasicDBObject("background", true);
        variantsCollection.createIndex(new BasicDBObject("_at.chunkIds", 1), onBackground);
        variantsCollection.createIndex(new BasicDBObject(DBObjectToVariantConverter.ANNOTATION_FIELD
                + "." + DBObjectToVariantAnnotationConverter.XREFS_FIELD
                + "." + DBObjectToVariantAnnotationConverter.XREF_ID_FIELD, 1), onBackground);
        variantsCollection.createIndex(new BasicDBObject(DBObjectToVariantConverter.ANNOTATION_FIELD
                + "." + DBObjectToVariantAnnotationConverter.CONSEQUENCE_TYPE_FIELD
                + "." + DBObjectToVariantAnnotationConverter.SO_ACCESSION_FIELD, 1), onBackground);
        variantsCollection.createIndex(new BasicDBObject(DBObjectToVariantConverter.IDS_FIELD, 1), onBackground);
        variantsCollection.createIndex(new BasicDBObject(DBObjectToVariantConverter.CHROMOSOME_FIELD, 1), onBackground);
        variantsCollection.createIndex(
                new BasicDBObject(DBObjectToVariantConverter.STUDIES_FIELD + "." + DBObjectToVariantSourceEntryConverter.STUDYID_FIELD, 1)
//                        .append(DBObjectToVariantConverter.STUDIES_FIELD +
//                                "." + DBObjectToVariantSourceEntryConverter.FILES_FIELD +
//                                "." + DBObjectToVariantSourceEntryConverter.FILEID_FIELD, 1)
                , onBackground);
        variantsCollection.createIndex(new BasicDBObject(DBObjectToVariantConverter.STATS_FIELD
                + "." + DBObjectToVariantStatsConverter.MAF_FIELD, 1), onBackground);
        variantsCollection.createIndex(new BasicDBObject(DBObjectToVariantConverter.STATS_FIELD
                + "." + DBObjectToVariantStatsConverter.MGF_FIELD, 1), onBackground);
        variantsCollection.createIndex(
                new BasicDBObject(DBObjectToVariantConverter.CHROMOSOME_FIELD, 1)
                        .append(DBObjectToVariantConverter.START_FIELD, 1)
                        .append(DBObjectToVariantConverter.END_FIELD, 1), onBackground);
        logger.debug("sent order to create indices");
    }

    @Override
    public boolean close() {
        mongoManager.close(db.getDatabaseName());
        return true;
    }

    private DBObjectToVariantConverter getDbObjectToVariantConverter(QueryOptions options) {
        List<Integer> studyIds = options.getAsIntegerList(STUDIES);

        DBObjectToSamplesConverter samplesConverter;
        if(studyIds.isEmpty()) {
            samplesConverter = new DBObjectToSamplesConverter(studyConfigurationManager, null);
        } else {
            List<StudyConfiguration> studyConfigurations = new LinkedList<>();
            for (Integer studyId : studyIds) {
                QueryResult<StudyConfiguration> queryResult = studyConfigurationManager.getStudyConfiguration(studyId, options);
                if(queryResult.getResult().isEmpty()) {
                    throw new IllegalStateException("iterator(): couldn't find studyConfiguration for StudyId {} " + studyId);
                } else {
                    studyConfigurations.add(queryResult.first());
                }
            }
            samplesConverter = new DBObjectToSamplesConverter(studyConfigurations);
        }
        DBObjectToVariantSourceEntryConverter sourceEntryConverter = new DBObjectToVariantSourceEntryConverter(
                true,
                options.containsKey(FILE_ID)? options.getInt(FILE_ID) : null,
                samplesConverter
        );
        return new DBObjectToVariantConverter(sourceEntryConverter, new DBObjectToVariantStatsConverter());
    }

    private QueryBuilder parseQueryOptions(QueryOptions options, QueryBuilder builder) {
        if (options != null) {

            if (options.containsKey("sort")) {
                if (options.getBoolean("sort")) {
                    options.put("sort", new BasicDBObject("chr", 1).append("start", 1));
                } else {
                    options.remove("sort");
                }
            }

            /** GENOMIC REGION **/

            if (options.getString(ID) != null && !options.getString(ID).isEmpty()) { //) && !options.getString("id").isEmpty()) {
                List<String> ids = options.getAsStringList(ID);
                addQueryListFilter(DBObjectToVariantConverter.ANNOTATION_FIELD + "." +
                        DBObjectToVariantAnnotationConverter.XREFS_FIELD + "." +
                        DBObjectToVariantAnnotationConverter.XREF_ID_FIELD
                        , ids, builder, QueryOperation.OR);

                addQueryListFilter(DBObjectToVariantConverter.IDS_FIELD
                        , ids, builder, QueryOperation.OR);
            }

            if (options.containsKey(REGION) && !options.getString(REGION).isEmpty()) {
                List<String> stringList = options.getAsStringList(REGION);
                List<Region> regions = new ArrayList<>(stringList.size());
                for (String reg : stringList) {
                    Region region = Region.parseRegion(reg);
                    regions.add(region);
                }
                getRegionFilter(regions, builder);
            }

            if (options.containsKey(GENE)) {
                List<String> xrefs = options.getAsStringList(GENE);
                addQueryListFilter(DBObjectToVariantConverter.ANNOTATION_FIELD + "." +
                        DBObjectToVariantAnnotationConverter.XREFS_FIELD + "." +
                        DBObjectToVariantAnnotationConverter.XREF_ID_FIELD
                        , xrefs, builder, QueryOperation.OR);
            }

            if (options.containsKey(CHROMOSOME)) {
                List<String> chromosome = options.getAsStringList(CHROMOSOME);
                addQueryListFilter(DBObjectToVariantConverter.CHROMOSOME_FIELD
                        , chromosome, builder, QueryOperation.OR);
            }

            /** VARIANT **/

            if (options.containsKey(TYPE)) { // && !options.getString("type").isEmpty()) {
                addQueryStringFilter(DBObjectToVariantConverter.TYPE_FIELD, options.getString(TYPE), builder);
            }

            if (options.containsKey(REFERENCE) && options.getString(REFERENCE) != null) {
                addQueryStringFilter(DBObjectToVariantConverter.REFERENCE_FIELD, options.getString(REFERENCE), builder);
            }

            if (options.containsKey(ALTERNATE) && options.getString(ALTERNATE) != null) {
                addQueryStringFilter(DBObjectToVariantConverter.ALTERNATE_FIELD, options.getString(ALTERNATE), builder);
            }

            /** ANNOTATION **/

            if (options.containsKey(ANNOTATION_EXISTS)) {
                builder.and(DBObjectToVariantConverter.ANNOTATION_FIELD).exists(options.getBoolean(ANNOTATION_EXISTS));
            }

            if (options.containsKey(ANNOT_XREF)) {
                List<String> xrefs = options.getAsStringList(ANNOT_XREF);
                addQueryListFilter(DBObjectToVariantConverter.ANNOTATION_FIELD + "." +
                        DBObjectToVariantAnnotationConverter.XREFS_FIELD + "." +
                        DBObjectToVariantAnnotationConverter.XREF_ID_FIELD
                        , xrefs, builder, QueryOperation.AND);
            }

            if (options.containsKey(ANNOT_CONSEQUENCE_TYPE)) {
//                List<Integer> cts = getIntegersList(options.get(ANNOT_CONSEQUENCE_TYPE));
                List<String> cts = new ArrayList<>(options.getAsStringList(ANNOT_CONSEQUENCE_TYPE));
                List<Integer> ctsInteger = new ArrayList<>(cts.size());
                for (Iterator<String> iterator = cts.iterator(); iterator.hasNext(); ) {
                    String ct = iterator.next();
                    if (ct.startsWith("SO:")) {
                        ct = ct.substring(3);
                    }
                    try {
                        ctsInteger.add(Integer.parseInt(ct));
                    } catch (NumberFormatException e) {
                        logger.error("Error parsing integer ", e);
                        iterator.remove();  //Remove the malformed query params.
                    }
                }
                options.put(ANNOT_CONSEQUENCE_TYPE, cts); //Replace the QueryOption without the malformed query params
                addQueryListFilter(DBObjectToVariantConverter.ANNOTATION_FIELD + "." +
                        DBObjectToVariantAnnotationConverter.CONSEQUENCE_TYPE_FIELD + "." +
                        DBObjectToVariantAnnotationConverter.SO_ACCESSION_FIELD
                        , ctsInteger, builder, QueryOperation.AND);
            }

            if (options.containsKey(ANNOT_BIOTYPE)) {
                List<String> biotypes = options.getAsStringList(ANNOT_BIOTYPE);
                addQueryListFilter(DBObjectToVariantConverter.ANNOTATION_FIELD + "." +
                        DBObjectToVariantAnnotationConverter.CONSEQUENCE_TYPE_FIELD + "." +
                        DBObjectToVariantAnnotationConverter.BIOTYPE_FIELD
                        , biotypes, builder, QueryOperation.AND);
            }

            if (options.containsKey(POLYPHEN)) {
                addCompQueryFilter(DBObjectToVariantConverter.ANNOTATION_FIELD + "." +
                        DBObjectToVariantAnnotationConverter.CONSEQUENCE_TYPE_FIELD + "." +
                        DBObjectToVariantAnnotationConverter.POLYPHEN_FIELD + "." +
                        DBObjectToVariantAnnotationConverter.SCORE_SCORE_FIELD, options.getString(POLYPHEN), builder);
            }

            if (options.containsKey(SIFT)) {
                addCompQueryFilter(DBObjectToVariantConverter.ANNOTATION_FIELD + "." +
                        DBObjectToVariantAnnotationConverter.CONSEQUENCE_TYPE_FIELD + "." +
                        DBObjectToVariantAnnotationConverter.SIFT_FIELD + "." +
                        DBObjectToVariantAnnotationConverter.SCORE_SCORE_FIELD, options.getString(SIFT), builder);
            }

            if (options.containsKey(PROTEIN_SUBSTITUTION)) {
                List<String> list = new ArrayList<>(options.getAsStringList(PROTEIN_SUBSTITUTION));
                addScoreFilter(DBObjectToVariantConverter.ANNOTATION_FIELD + "." +
                        DBObjectToVariantAnnotationConverter.CONSEQUENCE_TYPE_FIELD + "." +
                        DBObjectToVariantAnnotationConverter.PROTEIN_SUBSTITUTION_SCORE_FIELD, list, builder);
                options.put(PROTEIN_SUBSTITUTION, list); //Replace the QueryOption without the malformed query params
            }

            if (options.containsKey(CONSERVED_REGION)) {
                List<String> list = new ArrayList<>(options.getAsStringList(CONSERVED_REGION));
                addScoreFilter(DBObjectToVariantConverter.ANNOTATION_FIELD + "." +
                        DBObjectToVariantAnnotationConverter.CONSERVED_REGION_SCORE_FIELD, list, builder);
                options.put(PROTEIN_SUBSTITUTION, list); //Replace the QueryOption without the malformed query params
            }

            if (options.containsKey(ALTERNATE_FREQUENCY)) {
                List<String> list = new ArrayList<>(options.getAsStringList(ALTERNATE_FREQUENCY));
                addFrequencyFilter(DBObjectToVariantConverter.ANNOTATION_FIELD + "." +
                        DBObjectToVariantAnnotationConverter.POPULATION_FREQUENCIES_FIELD,
                        DBObjectToVariantAnnotationConverter.POPULATION_FREQUENCY_ALTERNATE_FREQUENCY_FIELD, list, builder); // Same method addFrequencyFilter is used for reference and allele frequencies. Need to provide the field (reference/alternate) where to check the frequency
            }

            if (options.containsKey(REFERENCE_FREQUENCY)) {
                List<String> list = new ArrayList<>(options.getAsStringList(REFERENCE_FREQUENCY));
                addFrequencyFilter(DBObjectToVariantConverter.ANNOTATION_FIELD + "." +
                        DBObjectToVariantAnnotationConverter.POPULATION_FREQUENCIES_FIELD,
                        DBObjectToVariantAnnotationConverter.POPULATION_FREQUENCY_REFERENCE_FREQUENCY_FIELD, list, builder); // Same method addFrequencyFilter is used for reference and allele frequencies. Need to provide the field (reference/alternate) where to check the frequency
            }



            /** STATS **/

            if (options.get(MAF) != null && !options.getString(MAF).isEmpty()) {
                addCompQueryFilter(
                        DBObjectToVariantConverter.STATS_FIELD + "." + DBObjectToVariantStatsConverter.MAF_FIELD,
                        options.getString(MAF), builder);
            }

            if (options.get(MGF) != null && !options.getString(MGF).isEmpty()) {
                addCompQueryFilter(
                        DBObjectToVariantConverter.STATS_FIELD + "." + DBObjectToVariantStatsConverter.MGF_FIELD,
                        options.getString(MGF), builder);
            }

            if (options.get(MISSING_ALLELES) != null && !options.getString(MISSING_ALLELES).isEmpty()) {
                addCompQueryFilter(
                        DBObjectToVariantConverter.STATS_FIELD + "." + DBObjectToVariantStatsConverter.MISSALLELE_FIELD,
                        options.getString(MISSING_ALLELES), builder);
            }

            if (options.get(MISSING_GENOTYPES) != null && !options.getString(MISSING_GENOTYPES).isEmpty()) {
                addCompQueryFilter(
                        DBObjectToVariantConverter.STATS_FIELD + "." + DBObjectToVariantStatsConverter.MISSGENOTYPE_FIELD,
                        options.getString(MISSING_GENOTYPES), builder);
            }

            if (options.get("numgt") != null && !options.getString("numgt").isEmpty()) {
                for (String numgt : options.getAsStringList("numgt")) {
                    String[] split = numgt.split(":");
                    addCompQueryFilter(
                            DBObjectToVariantConverter.STATS_FIELD + "." + DBObjectToVariantStatsConverter.NUMGT_FIELD + "." + split[0],
                            split[1], builder);
                }
            }

//            if (options.get("freqgt") != null && !options.getString("freqgt").isEmpty()) {
//                for (String freqgt : getStringList(options.get("freqgt"))) {
//                    String[] split = freqgt.split(":");
//                    addCompQueryFilter(
//                            DBObjectToVariantSourceEntryConverter.STATS_FIELD + "." + DBObjectToVariantStatsConverter.FREQGT_FIELD + "." + split[0],
//                            split[1], builder);
//                }
//            }


            /** FILES **/
            QueryBuilder fileBuilder = QueryBuilder.start();

            if (options.containsKey(STUDIES)) { // && !options.getList("studies").isEmpty() && !options.getListAs("studies", String.class).get(0).isEmpty()) {
                addQueryListFilter(
                        DBObjectToVariantSourceEntryConverter.STUDYID_FIELD, options.getAsIntegerList(STUDIES),
                        fileBuilder, QueryOperation.AND);
            }

            if (options.containsKey(FILES)) { // && !options.getList("files").isEmpty() && !options.getListAs("files", String.class).get(0).isEmpty()) {
                addQueryListFilter(DBObjectToVariantSourceEntryConverter.FILES_FIELD + "." +
                                DBObjectToVariantSourceEntryConverter.FILEID_FIELD, options.getAsIntegerList(FILES),
                        fileBuilder, QueryOperation.AND);
            }

            if (options.containsKey(GENOTYPE)) {
                String sampleGenotypesCSV = options.getString(GENOTYPE);

//                String AND = ",";
//                String OR = ";";
//                String IS = ":";

//                String AND = "AND";
//                String OR = "OR";
//                String IS = ":";

                String AND = ";";
                String OR = ",";
                String IS = ":";

                String[] sampleGenotypesArray = sampleGenotypesCSV.split(AND);
                for (String sampleGenotypes : sampleGenotypesArray) {
                    String[] sampleGenotype = sampleGenotypes.split(IS);
                    if(sampleGenotype.length != 2) {
                        continue;
                    }
                    int sample = Integer.parseInt(sampleGenotype[0]);
                    String[] genotypes = sampleGenotype[1].split(OR);
                    QueryBuilder genotypesBuilder = QueryBuilder.start();
                    for (String genotype : genotypes) {
                        String s = DBObjectToVariantSourceEntryConverter.GENOTYPES_FIELD + "." +
                                DBObjectToSamplesConverter.genotypeToStorageType(genotype);
                        //or [ {"samp.0|0" : { $elemMatch : { $eq : <sampleId> } } } ]
                        genotypesBuilder.or(new BasicDBObject(s, new BasicDBObject("$elemMatch", new BasicDBObject("$eq", sample))));
                    }
                    fileBuilder.and(genotypesBuilder.get());
                }
            }

            DBObject fileQuery = fileBuilder.get();
            if (fileQuery.keySet().size() != 0) {
                builder.and(DBObjectToVariantConverter.STUDIES_FIELD).elemMatch(fileQuery);
            }
        }

        logger.debug("Find = " + builder.get());
        return builder;
    }

    /**
     * when the tags "include" or "exclude" The names are the same as the members of Variant.
     * @param options
     * @return
     */
    private DBObject parseProjectionQueryOptions(QueryOptions options) {
        DBObject projection = new BasicDBObject();

        if(options == null) {
            return projection;
        }

        List<String> includeList = options.getAsStringList("include");
        if (!includeList.isEmpty()) { //Include some
            for (String s : includeList) {
                String key = DBObjectToVariantConverter.toShortFieldName(s);
                if (key != null) {
                    projection.put(key, 1);
                } else {
                    logger.warn("Unknown include field: {}", s);
                }
            }
        } else { //Include all
            for (String values : DBObjectToVariantConverter.fieldsMap.values()) {
                projection.put(values, 1);
            }
            if (options.containsKey("exclude")) { // Exclude some
                List<String> excludeList = options.getAsStringList("exclude");
                for (String s : excludeList) {
                    String key = DBObjectToVariantConverter.toShortFieldName(s);
                    if (key != null) {
                        projection.removeField(key);
                    } else {
                        logger.warn("Unknown exclude field: {}", s);
                    }
                }
            }
        }

        if (options.containsKey(FILE_ID) && projection.containsField(DBObjectToVariantConverter.STUDIES_FIELD)) {
//            List<String> files = options.getListAs(FILES, String.class);
            int file = options.getInt(FILE_ID);
            projection.put(
                    DBObjectToVariantConverter.STUDIES_FIELD,
                    new BasicDBObject(
                            "$elemMatch",
                            new BasicDBObject(
                                    DBObjectToVariantSourceEntryConverter.FILES_FIELD + "." + DBObjectToVariantSourceEntryConverter.FILEID_FIELD,
                                    file
//                                    new BasicDBObject(
//                                            "$in",
//                                            files
//                                    )
                            )
                    )
            );
        }

        logger.debug("Projection: {}", projection);
        return projection;
    }

    private enum QueryOperation {
        AND, OR
    }

    private QueryBuilder addQueryStringFilter(String key, String value, QueryBuilder builder) {
        if(value != null && !value.isEmpty()) {
            if(value.indexOf(",") == -1) {
                builder.and(key).is(value);
            }else {
                String[] values = value.split(",");
                builder.and(key).in(values);
            }
        }
        return builder;
    }

    private QueryBuilder addQueryListFilter(String key, List<?> values, QueryBuilder builder, QueryOperation op) {
        if (values != null)
            if (values.size() == 1) {
                if(op == QueryOperation.AND) {
                    builder.and(key).is(values.get(0));
                } else {
                    builder.or(QueryBuilder.start(key).is(values.get(0)).get());
                }
            } else if (!values.isEmpty()) {
                if(op == QueryOperation.AND) {
                    builder.and(key).in(values);
                } else {
                    builder.or(QueryBuilder.start(key).in(values).get());
                }
            }
        return builder;
    }

    private QueryBuilder addCompQueryFilter(String key, String value, QueryBuilder builder) {
        String op = value.substring(0, 2);
        op = op.replaceFirst("[0-9]", "");
        String obj = value.replaceFirst(op, "");

        switch(op) {
            case "<":
                builder.and(key).lessThan(Float.parseFloat(obj));
                break;
            case "<=":
                builder.and(key).lessThanEquals(Float.parseFloat(obj));
                break;
            case ">":
                builder.and(key).greaterThan(Float.parseFloat(obj));
                break;
            case ">=":
                builder.and(key).greaterThanEquals(Float.parseFloat(obj));
                break;
            case "=":
            case "==":
                builder.and(key).is(Float.parseFloat(obj));
                break;
            case "!=":
                builder.and(key).notEquals(Float.parseFloat(obj));
                break;
            case "~=":
                builder.and(key).regex(Pattern.compile(obj));
                break;
        }
        return builder;
    }

    private QueryBuilder addScoreFilter(String key, List<String> list, QueryBuilder builder) {
//        ArrayList<DBObject> and = new ArrayList<>(list.size());
//        DBObject[] ands = new DBObject[list.size()];
        List<DBObject> ands = new ArrayList<>();
        for (Iterator<String> iterator = list.iterator(); iterator.hasNext(); ) {
            String elem = iterator.next();
            String[] split = elem.split(":");
            if (split.length == 2) {
                String source = split[0];
                String score = split[1];
                QueryBuilder scoreBuilder = new QueryBuilder();
                scoreBuilder.and(DBObjectToVariantAnnotationConverter.SCORE_SOURCE_FIELD).is(source);
                addCompQueryFilter(DBObjectToVariantAnnotationConverter.SCORE_SCORE_FIELD
                        , score, scoreBuilder);
//                builder.and(key).elemMatch(scoreBuilder.get());
                ands.add(new BasicDBObject(key, new BasicDBObject("$elemMatch", scoreBuilder.get())));
            } else {
                logger.error("Bad score filter: " + elem);
                iterator.remove(); //Remove the malformed query params.
            }
        }
        if (!ands.isEmpty()) {
            builder.and(ands.toArray(new DBObject[ands.size()]));
        }
        return builder;
    }

    private QueryBuilder addFrequencyFilter(String key, String alleleFrequencyField, List<String> list, QueryBuilder builder) {
//        ArrayList<DBObject> and = new ArrayList<>(list.size());
//        DBObject[] ands = new DBObject[list.size()];
        List<DBObject> ands = new ArrayList<>();
        for (Iterator<String> iterator = list.iterator(); iterator.hasNext(); ) {
            String elem = iterator.next();
            String[] split = elem.split(":");
            if (split.length == 3) {
                String study = split[0];
                String population = split[1];
                String frequency = split[2];
                QueryBuilder frequencyBuilder = new QueryBuilder();
                frequencyBuilder.and(DBObjectToVariantAnnotationConverter.POPULATION_FREQUENCY_STUDY_FIELD).is(study);
                frequencyBuilder.and(DBObjectToVariantAnnotationConverter.POPULATION_FREQUENCY_POP_FIELD).is(population);
                addCompQueryFilter(alleleFrequencyField, frequency, frequencyBuilder);
                ands.add(new BasicDBObject(key, new BasicDBObject("$elemMatch", frequencyBuilder.get())));
            } else {
                logger.error("Bad score filter: " + elem);
                iterator.remove(); //Remove the malformed query params.
            }
        }
        if (!ands.isEmpty()) {
            builder.and(ands.toArray(new DBObject[ands.size()]));
        }
        return builder;
    }

    private QueryBuilder getRegionFilter(Region region, QueryBuilder builder) {
        List<String> chunkIds = getChunkIds(region);
        builder.and("_at.chunkIds").in(chunkIds);
        builder.and(DBObjectToVariantConverter.END_FIELD).greaterThanEquals(region.getStart());
        builder.and(DBObjectToVariantConverter.START_FIELD).lessThanEquals(region.getEnd());
        return builder;
    }

    private QueryBuilder getRegionFilter(List<Region> regions, QueryBuilder builder) {
        if (regions == null || regions.isEmpty()) {
            return builder;
        }
        DBObject[] objects = new DBObject[regions.size()];

        int i = 0;
        for (Region region : regions) {
            List<String> chunkIds = getChunkIds(region);
            DBObject regionObject = new BasicDBObject("_at.chunkIds", new BasicDBObject("$in", chunkIds))
                    .append(DBObjectToVariantConverter.END_FIELD, new BasicDBObject("$gte", region.getStart()))
                    .append(DBObjectToVariantConverter.START_FIELD, new BasicDBObject("$lte", region.getEnd()));
            objects[i] = regionObject;
            i++;
        }
        builder.or(objects);
        return builder;
    }

    /* *******************
     * Auxiliary methods *
     * *******************/

    private List<String> getChunkIds(Region region) {
        List<String> chunkIds = new LinkedList<>();

        int chunkSize = (region.getEnd() - region.getStart() > VariantMongoDBWriter.CHUNK_SIZE_BIG) ?
                VariantMongoDBWriter.CHUNK_SIZE_BIG : VariantMongoDBWriter.CHUNK_SIZE_SMALL;
        int ks = chunkSize / 1000;
        int chunkStart = region.getStart() / chunkSize;
        int chunkEnd = region.getEnd() / chunkSize;

        for (int i = chunkStart; i <= chunkEnd; i++) {
            String chunkId = region.getChromosome() + "_" + i + "_" + ks + "k";
            chunkIds.add(chunkId);
        }

        return chunkIds;
    }

    private int getChunkId(int position, int chunksize) {
        return position / chunksize;
    }

    private int getChunkStart(int id, int chunksize) {
        return (id == 0) ? 1 : id * chunksize;
    }

    private int getChunkEnd(int id, int chunksize) {
        return (id * chunksize) + chunksize - 1;
    }


}
