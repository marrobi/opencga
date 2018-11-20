/*
 * Copyright 2015-2017 OpenCB
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

package org.opencb.opencga.storage.benchmark.variant;

import com.beust.jcommander.MissingCommandException;
import org.apache.commons.lang.StringUtils;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.opencga.storage.benchmark.BenchmarkRunner;
import org.opencb.opencga.storage.benchmark.variant.generators.FixedQueryGenerator;
import org.opencb.opencga.storage.benchmark.variant.generators.MultiQueryGenerator;
import org.opencb.opencga.storage.benchmark.variant.generators.QueryGenerator;
import org.opencb.opencga.storage.benchmark.variant.samplers.VariantStorageEngineDirectSampler;
import org.opencb.opencga.storage.benchmark.variant.samplers.VariantStorageEngineRestSampler;
import org.opencb.opencga.storage.benchmark.variant.samplers.VariantStorageEngineSampler;
import org.opencb.opencga.storage.core.config.StorageConfiguration;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Created on 06/04/17.
 *
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public class VariantBenchmarkRunner extends BenchmarkRunner {

    public VariantBenchmarkRunner(StorageConfiguration storageConfiguration, Path jmeterHome, Path outdir) throws IOException {
        super(storageConfiguration, jmeterHome, outdir);
    }

    public void addThreadGroup(ConnectionType type, ExecutionMode mode, Path dataDir, String queryFile,
                               String queries, QueryOptions queryOptions) {

        if (StringUtils.isEmpty(queries) && mode.equals(ExecutionMode.FIXED)) {
            queries = "all";
        } else if (StringUtils.isEmpty(queries) && mode.equals(ExecutionMode.RANDOM)) {
            throw new MissingCommandException("Please provide execution queries for dynamic mode.");
        }

        // gene,ct;region,phylop

        List<VariantStorageEngineSampler> samplers = new ArrayList<>();
        for (String query : queries.split(";")) {
            VariantStorageEngineSampler variantStorageSampler = newVariantStorageEngineSampler(type);

            variantStorageSampler.setStorageEngine(storageEngine);
            variantStorageSampler.setDBName(dbName);
            variantStorageSampler.setLimit(queryOptions.getInt(QueryOptions.LIMIT, -1));
            variantStorageSampler.setCount(queryOptions.getBoolean(QueryOptions.COUNT, false));
            variantStorageSampler.setQueryGeneratorConfig(FixedQueryGenerator.FILE, queryFile);

            if (mode.equals(ExecutionMode.FIXED)) {
                variantStorageSampler.setQueryGenerator(FixedQueryGenerator.class);
                variantStorageSampler.setQueryGeneratorConfig(FixedQueryGenerator.DATA_DIR, dataDir.toString());
                variantStorageSampler.setQueryGeneratorConfig(FixedQueryGenerator.FIXED_QUERY, query);
            } else if (mode.equals(ExecutionMode.RANDOM)) {
                variantStorageSampler.setQueryGenerator(MultiQueryGenerator.class);
                variantStorageSampler.setQueryGeneratorConfig(MultiQueryGenerator.DATA_DIR, dataDir.toString());
                variantStorageSampler.setQueryGeneratorConfig(MultiQueryGenerator.MULTI_QUERY, query);
            }

            samplers.add(variantStorageSampler);
        }

        addThreadGroup(samplers);
    }

    public void addThreadGroup(ConnectionType type, Path dataDir, List<Class<? extends QueryGenerator>> queryGenerators,
                               QueryOptions queryOptions) {
        List<VariantStorageEngineSampler> samplers = new ArrayList<>(queryGenerators.size());
        for (Class<? extends QueryGenerator> clazz : queryGenerators) {
            VariantStorageEngineSampler variantStorageSampler = newVariantStorageEngineSampler(type);

            variantStorageSampler.setStorageEngine(storageEngine);
            variantStorageSampler.setDBName(dbName);
            variantStorageSampler.setLimit(queryOptions.getInt(QueryOptions.LIMIT, -1));
            variantStorageSampler.setCount(queryOptions.getBoolean(QueryOptions.COUNT, false));
            variantStorageSampler.setQueryGenerator(clazz);
            variantStorageSampler.setQueryGeneratorConfig(QueryGenerator.DATA_DIR, dataDir.toString());

            samplers.add(variantStorageSampler);
        }

        addThreadGroup(samplers);

    }

    public VariantStorageEngineSampler newVariantStorageEngineSampler(ConnectionType type) {
        switch (type) {
            case REST:
                URI rest = storageConfiguration.getBenchmark().getRest();
                if (rest != null) {
                    return new VariantStorageEngineRestSampler(rest.getHost(), rest.getPath(), rest.getPort());
                } else {
                    return new VariantStorageEngineRestSampler("localhost", storageConfiguration.getServer().getRest());
                }
            case DIRECT:
                return new VariantStorageEngineDirectSampler();
            case GRPC:
                throw new UnsupportedOperationException("Unsupported type " + ConnectionType.GRPC);
            default:
                throw new IllegalArgumentException("Unknown type " + type);
        }
    }
}
