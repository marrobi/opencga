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

package org.opencb.opencga.app.cli.main.executors.catalog.commons;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.opencb.commons.datastore.core.DataResponse;
import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.opencga.app.cli.main.options.commons.AnnotationCommandOptions;
import org.opencb.opencga.client.rest.catalog.AnnotationClient;
import org.opencb.opencga.core.models.AnnotationSet;

import java.io.File;
import java.io.IOException;

/**
 * Created by pfurio on 28/07/16.
 */
public class AnnotationCommandExecutor<T> {

    @Deprecated
    public DataResponse<AnnotationSet> createAnnotationSet(
            AnnotationCommandOptions.AnnotationSetsCreateCommandOptions createCommandOptions, AnnotationClient<T> client)
            throws IOException {

        ObjectMapper mapper = new ObjectMapper();
        ObjectMap obj = mapper.readValue(new File(createCommandOptions.annotations), ObjectMap.class);
        return client.createAnnotationSet(createCommandOptions.study, createCommandOptions.id, createCommandOptions.variableSetId,
                createCommandOptions.annotationSetId, obj);
    }

    @Deprecated
    public DataResponse<AnnotationSet> getAnnotationSet(AnnotationCommandOptions.AnnotationSetsInfoCommandOptions infoCommandOptions,
                                     AnnotationClient<T> client) throws IOException {

        ObjectMap params = new ObjectMap();
        params.putIfNotNull("study", infoCommandOptions.study);
        params.putIfNotNull("name", infoCommandOptions.annotationSetName);
        return client.getAnnotationSets(infoCommandOptions.id, params);
    }

    @Deprecated
    public DataResponse<AnnotationSet> searchAnnotationSets(
            AnnotationCommandOptions.AnnotationSetsSearchCommandOptions searchCommandOptions, AnnotationClient<T> client)
            throws IOException {
        ObjectMap params = new ObjectMap();
        params.putIfNotNull("variableSet", searchCommandOptions.variableSetId);
        params.putIfNotNull("annotation", searchCommandOptions.annotation);
        params.putIfNotNull("study", searchCommandOptions.study);
        return client.searchAnnotationSets(searchCommandOptions.id, params);
    }

    @Deprecated
    public DataResponse<AnnotationSet> deleteAnnotationSet(
            AnnotationCommandOptions.AnnotationSetsDeleteCommandOptions deleteCommandOptions, AnnotationClient<T> client)
            throws IOException {
        ObjectMap params = new ObjectMap();
        params.putIfNotNull("annotations", deleteCommandOptions.annotations);
        params.putIfNotNull("study", deleteCommandOptions.study);
        return client.deleteAnnotationSet(deleteCommandOptions.id, deleteCommandOptions.annotationSetName, params);
    }

    public DataResponse<AnnotationSet> updateAnnotationSet(
            AnnotationCommandOptions.AnnotationSetsUpdateCommandOptions updateCommandOptions, AnnotationClient<T> client)
            throws IOException {

        ObjectMapper mapper = new ObjectMapper();
        ObjectMap obj = mapper.readValue(new File(updateCommandOptions.annotations), ObjectMap.class);

        ObjectMap queryParams = new ObjectMap();
//        queryParams.putIfNotNull("action", updateCommandOptions.action);

        return client.updateAnnotationSet(updateCommandOptions.study, updateCommandOptions.id, updateCommandOptions.annotationSetId,
                queryParams, obj);
    }
}
