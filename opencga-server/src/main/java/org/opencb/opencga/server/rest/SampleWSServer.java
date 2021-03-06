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

package org.opencb.opencga.server.rest;

import io.swagger.annotations.*;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.opencb.commons.datastore.core.*;
import org.opencb.opencga.catalog.db.api.SampleDBAdaptor;
import org.opencb.opencga.catalog.exceptions.CatalogException;
import org.opencb.opencga.catalog.exceptions.CatalogParameterException;
import org.opencb.opencga.catalog.managers.SampleManager;
import org.opencb.opencga.catalog.models.update.SampleUpdateParams;
import org.opencb.opencga.catalog.utils.CatalogSampleAnnotationsLoader;
import org.opencb.opencga.catalog.utils.Constants;
import org.opencb.opencga.catalog.utils.ParamUtils;
import org.opencb.opencga.core.exception.VersionException;
import org.opencb.opencga.core.models.AnnotationSet;
import org.opencb.opencga.core.models.File;
import org.opencb.opencga.core.models.Sample;
import org.opencb.opencga.core.models.acls.AclParams;
import org.opencb.opencga.server.WebServiceException;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.*;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by jacobo on 15/12/14.
 */
@Path("/{apiVersion}/samples")
@Produces(MediaType.APPLICATION_JSON)
@Api(value = "Samples", position = 7, description = "Methods for working with 'samples' endpoint")
public class SampleWSServer extends OpenCGAWSServer {

    private SampleManager sampleManager;

    public SampleWSServer(@Context UriInfo uriInfo, @Context HttpServletRequest httpServletRequest, @Context HttpHeaders httpHeaders) throws IOException, VersionException {
        super(uriInfo, httpServletRequest, httpHeaders);
        sampleManager = catalogManager.getSampleManager();
    }

    @GET
    @Path("/{samples}/info")
    @ApiOperation(value = "Get sample information", position = 1, response = Sample.class)
    @ApiImplicitParams({
            @ApiImplicitParam(name = "include", value = "Fields included in the response, whole JSON path must be provided", example = "name,attributes", dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "exclude", value = "Fields excluded in the response, whole JSON path must be provided", example = "id,status", dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "includeIndividual", value = "Include Individual object as an attribute (this replaces old lazy parameter)",
                    defaultValue = "false", dataType = "boolean", paramType = "query"),
            @ApiImplicitParam(name = Constants.FLATTENED_ANNOTATIONS, value = "Flatten the annotations?", defaultValue = "false",
                    dataType = "boolean", paramType = "query")
    })
    public Response infoSample(
            @ApiParam(value = "Comma separated list of sample IDs or names up to a maximum of 100", required = true) @PathParam("samples") String samplesStr,
            @ApiParam(value = "Study [[user@]project:]study where study and project can be either the id or alias")
            @QueryParam("study") String studyStr,
            @ApiParam(value = "Sample version") @QueryParam("version") Integer version,
            @ApiParam(value = "Boolean to retrieve deleted samples", defaultValue = "false") @QueryParam("deleted") boolean deleted) {
        try {
            query.remove("study");
            query.remove("samples");

            List<String> sampleList = getIdList(samplesStr);
            DataResult<Sample> sampleQueryResult = sampleManager.get(studyStr, sampleList, query, queryOptions, true, token);
            return createOkResponse(sampleQueryResult);
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @POST
    @Path("/create")
    @ApiOperation(value = "Create sample", response = Sample.class,
            notes = "WARNING: The Individual object in the body is deprecated and will be completely removed in a future release. From"
                    + " that moment on it will not be possible to create an individual when creating a new sample. To do that you must "
                    + "use the individual/create web service, this web service allows now to create a new individual with its samples. "
                    + "This web service now allows to create a new sample and associate it to an existing individual.")
    public Response createSamplePOST(
            @ApiParam(value = "DEPRECATED: studyId", hidden = true) @QueryParam("studyId") String studyIdStr,
            @ApiParam(value = "Study [[user@]project:]study where study and project can be either the id or alias") @QueryParam("study")
                    String studyStr,
            @ApiParam(value = "DEPRECATED: It should be passed in the body.") @QueryParam("individual") String individual,
            @ApiParam(value = "JSON containing sample information", required = true) CreateSamplePOST params) {
        try {
            params = ObjectUtils.defaultIfNull(params, new CreateSamplePOST());

            if (StringUtils.isNotEmpty(studyIdStr)) {
                studyStr = studyIdStr;
            }

            Sample sample = params.toSample();
            if (StringUtils.isNotEmpty(individual) && StringUtils.isNotEmpty(sample.getIndividualId())) {
                throw new CatalogParameterException("Found both individual and individualId as a query parameter and in the body. Please, "
                        + "only pass individualId in the body");
            }
            if (StringUtils.isNotEmpty(individual)) {
                sample.setIndividualId(individual);
            }

            return createOkResponse(sampleManager.create(studyStr, sample, queryOptions, token));
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @GET
    @Path("/load")
    @ApiOperation(value = "Load samples from a ped file [EXPERIMENTAL]", position = 3)
    public Response loadSamples(@ApiParam(value = "DEPRECATED: studyId", hidden = true) @QueryParam("studyId") String studyIdStr,
                                @ApiParam(value = "Study [[user@]project:]study where study and project can be either the id or alias")
                                @QueryParam("study") String studyStr,
                                @ApiParam(value = "DEPRECATED: use file instead", hidden = true) @QueryParam("fileId") String fileIdStr,
                                @ApiParam(value = "file", required = true) @QueryParam("file") String fileStr,
                                @ApiParam(value = "variableSet", required = false) @QueryParam("variableSet") String variableSet) {
        try {
            if (StringUtils.isNotEmpty(studyIdStr)) {
                studyStr = studyIdStr;
            }
            if (StringUtils.isNotEmpty(fileStr)) {
                fileIdStr = fileStr;
            }

            File pedigreeFile = catalogManager.getFileManager().get(studyStr, fileIdStr, null, token).first();
            CatalogSampleAnnotationsLoader loader = new CatalogSampleAnnotationsLoader(catalogManager);
            DataResult<Sample> sampleQueryResult = loader.loadSampleAnnotations(pedigreeFile, variableSet, token);
            return createOkResponse(sampleQueryResult);
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @GET
    @Path("/search")
    @ApiOperation(value = "Sample search method", position = 4, response = Sample[].class)
    @ApiImplicitParams({
            @ApiImplicitParam(name = "include", value = "Fields included in the response, whole JSON path must be provided", example = "name,attributes", dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "exclude", value = "Fields excluded in the response, whole JSON path must be provided", example = "id,status", dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "limit", value = "Number of results to be returned in the queries", dataType = "integer", paramType = "query"),
            @ApiImplicitParam(name = "skip", value = "Number of results to skip in the queries", dataType = "integer", paramType = "query"),
            @ApiImplicitParam(name = "count", value = "Total number of results", defaultValue = "false", dataType = "boolean", paramType = "query"),
            @ApiImplicitParam(name = "includeIndividual", value = "Include Individual object as an attribute (this replaces old lazy parameter)",
                    defaultValue = "false", dataType = "boolean", paramType = "query"),
            @ApiImplicitParam(name = Constants.FLATTENED_ANNOTATIONS, value = "Flatten the annotations?", defaultValue = "false",
                    dataType = "boolean", paramType = "query")
    })
    public Response search(
            @ApiParam(value = "DEPRECATED: use study instead", hidden = true) @QueryParam("studyId") String studyIdStr,
            @ApiParam(value = "Study [[user@]project:]{study1,study2|*}  where studies and project can be either the id or"
                    + " alias.", required = false) @QueryParam("study") String studyStr,
            @ApiParam(value = "DEPRECATED: use /info instead", hidden = true) @QueryParam("id") String id,
            @ApiParam(value = "DEPRECATED: name") @QueryParam("name") String name,
            @ApiParam(value = "source") @QueryParam("source") String source,
            @ApiParam(value = "type") @QueryParam("type") String type,
            @ApiParam(value = "somatic") @QueryParam("somatic") Boolean somatic,
            @ApiParam(value = "Individual id or name", hidden = true) @QueryParam("individual.id") String individualId,
            @ApiParam(value = "Individual id or name") @QueryParam("individual") String individual,
            @ApiParam(value = "Creation date (Format: yyyyMMddHHmmss. Examples: >2018, 2017-2018, <201805...)")
                @QueryParam("creationDate") String creationDate,
            @ApiParam(value = "Modification date (Format: yyyyMMddHHmmss. Examples: >2018, 2017-2018, <201805...)")
                @QueryParam("modificationDate") String modificationDate,
            @ApiParam(value = "Boolean to retrieve deleted samples", defaultValue = "false") @QueryParam("deleted") boolean deleted,
            @ApiParam(value = "Comma separated list of phenotype ids or names") @QueryParam("phenotypes") String phenotypes,
            @ApiParam(value = "DEPRECATED: Use annotation queryParam this way: annotationSet[=|==|!|!=]{annotationSetName}")
            @QueryParam("annotationsetName") String annotationsetName,
            @ApiParam(value = "DEPRECATED: Use annotation queryParam this way: variableSet[=|==|!|!=]{variableSetId}")
            @QueryParam("variableSet") String variableSet,
            @ApiParam(value = "Annotation, e.g: key1=value(;key2=value)") @QueryParam("annotation") String annotation,
            @ApiParam(value = "Text attributes (Format: sex=male,age>20 ...)", required = false) @DefaultValue("") @QueryParam("attributes") String attributes,
            @ApiParam(value = "Numerical attributes (Format: sex=male,age>20 ...)", required = false) @DefaultValue("")
            @QueryParam("nattributes") String nattributes,
            @ApiParam(value = "Skip count", defaultValue = "false") @QueryParam("skipCount") boolean skipCount,
            @ApiParam(value = "Release value (Current release from the moment the samples were first created)")
            @QueryParam("release") String release,
            @ApiParam(value = "Snapshot value (Latest version of samples in the specified release)") @QueryParam("snapshot")
                    int snapshot) {
        try {
            query.remove("study");

            queryOptions.put(QueryOptions.SKIP_COUNT, skipCount);

            if (StringUtils.isNotEmpty(studyIdStr)) {
                studyStr = studyIdStr;
            }

            List<String> annotationList = new ArrayList<>();
            if (StringUtils.isNotEmpty(annotation)) {
                annotationList.add(annotation);
            }
            if (StringUtils.isNotEmpty(variableSet)) {
                annotationList.add(Constants.VARIABLE_SET + "=" + variableSet);
            }
            if (StringUtils.isNotEmpty(annotationsetName)) {
                annotationList.add(Constants.ANNOTATION_SET_NAME + "=" + annotationsetName);
            }
            if (!annotationList.isEmpty()) {
                query.put(Constants.ANNOTATION, StringUtils.join(annotationList, ";"));
            }

            // TODO: individualId is deprecated. Remember to remove this if after next release
            if (query.containsKey(SampleDBAdaptor.QueryParams.INDIVIDUAL_UID.key())) {
                if (!query.containsKey(SampleDBAdaptor.QueryParams.INDIVIDUAL.key())) {
                    query.put(SampleDBAdaptor.QueryParams.INDIVIDUAL.key(), query.get(SampleDBAdaptor.QueryParams.INDIVIDUAL_UID.key()));
                }
                query.remove(SampleDBAdaptor.QueryParams.INDIVIDUAL_UID.key());
            }

            DataResult<Sample> queryResult;
            if (count) {
                queryResult = sampleManager.count(studyStr, query, token);
            } else {
                queryResult = sampleManager.search(studyStr, query, queryOptions, token);
            }
            return createOkResponse(queryResult);
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @POST
    @Path("/update")
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update some sample attributes")
    public Response updateByPost(
            @ApiParam(value = "Study [[user@]project:]study where study and project can be either the id or alias")
            @QueryParam("study") String studyStr,
            @ApiParam(value = "Sample id") @QueryParam("id") String id,
            @ApiParam(value = "Sample name") @QueryParam("name") String name,
            @ApiParam(value = "Sample source") @QueryParam("source") String source,
            @ApiParam(value = "Sample type") @QueryParam("type") String type,
            @ApiParam(value = "Somatic") @QueryParam("somatic") Boolean somatic,
            @ApiParam(value = "Individual id or name") @QueryParam("individual") String individual,
            @ApiParam(value = "Creation date (Format: yyyyMMddHHmmss)") @QueryParam("creationDate") String creationDate,
            @ApiParam(value = "Comma separated list of phenotype ids or names") @QueryParam("phenotypes") String phenotypes,
            @ApiParam(value = "Annotation, e.g: key1=value(;key2=value)") @QueryParam("annotation") String annotation,
            @ApiParam(value = "Text attributes (Format: sex=male,age>20 ...)") @QueryParam("attributes") String attributes,
            @ApiParam(value = "Numerical attributes (Format: sex=male,age>20 ...)") @QueryParam("nattributes") String nattributes,
            @ApiParam(value = "Release value (Current release from the moment the samples were first created)")
            @QueryParam("release") String release,

            @ApiParam(value = "Create a new version of sample", defaultValue = "false")
            @QueryParam(Constants.INCREMENT_VERSION) boolean incVersion,
            @ApiParam(value = "Action to be performed if the array of annotationSets is being updated.", defaultValue = "ADD")
            @QueryParam("annotationSetsAction") ParamUtils.UpdateAction annotationSetsAction,
            @ApiParam(value = "params") SampleUpdateParams parameters) {
        try {
            query.remove("study");
            if (annotationSetsAction == null) {
                annotationSetsAction = ParamUtils.UpdateAction.ADD;
            }
            Map<String, Object> actionMap = new HashMap<>();
            actionMap.put(SampleDBAdaptor.QueryParams.ANNOTATION_SETS.key(), annotationSetsAction);
            QueryOptions options = new QueryOptions(Constants.ACTIONS, actionMap);

            return createOkResponse(sampleManager.update(studyStr, query, parameters, true, options, token));
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @POST
    @Path("/{samples}/update")
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update some sample attributes")
    public Response updateByPost(
            @ApiParam(value = "Comma separated list of sample ids", required = true) @PathParam("samples") String sampleStr,
            @ApiParam(value = "Study [[user@]project:]study where study and project can be either the id or alias")
                @QueryParam("study") String studyStr,
            @ApiParam(value = "Create a new version of sample", defaultValue = "false")
                @QueryParam(Constants.INCREMENT_VERSION) boolean incVersion,
            @ApiParam(value = "Action to be performed if the array of annotationSets is being updated.", defaultValue = "ADD")
                @QueryParam("annotationSetsAction") ParamUtils.UpdateAction annotationSetsAction,
            @ApiParam(value = "params") SampleUpdateParams parameters) {
        try {
            if (annotationSetsAction == null) {
                annotationSetsAction = ParamUtils.UpdateAction.ADD;
            }
            Map<String, Object> actionMap = new HashMap<>();
            actionMap.put(SampleDBAdaptor.QueryParams.ANNOTATION_SETS.key(), annotationSetsAction);
            QueryOptions options = new QueryOptions(Constants.ACTIONS, actionMap);

            return createOkResponse(sampleManager.update(studyStr, getIdList(sampleStr), parameters, true, options, token));
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @POST
    @Path("/{sample}/annotationSets/{annotationSet}/annotations/update")
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Update annotations from an annotationSet")
    public Response updateAnnotations(
            @ApiParam(value = "Sample id", required = true) @PathParam("sample") String sampleStr,
            @ApiParam(value = "Study [[user@]project:]study.") @QueryParam("study") String studyStr,
            @ApiParam(value = "AnnotationSet id to be updated.") @PathParam("annotationSet") String annotationSetId,
            @ApiParam(value = "Action to be performed: ADD to add new annotations; REPLACE to replace the value of an already existing "
                    + "annotation; SET to set the new list of annotations removing any possible old annotations; REMOVE to remove some "
                    + "annotations; RESET to set some annotations to the default value configured in the corresponding variables of the "
                    + "VariableSet if any.", defaultValue = "ADD") @QueryParam("action") ParamUtils.CompleteUpdateAction action,
            @ApiParam(value = "Create a new version of sample", defaultValue = "false") @QueryParam(Constants.INCREMENT_VERSION)
                    boolean incVersion,
            @ApiParam(value = "Json containing the map of annotations when the action is ADD, SET or REPLACE, a json with only the key "
                    + "'remove' containing the comma separated variables to be removed as a value when the action is REMOVE or a json "
                    + "with only the key 'reset' containing the comma separated variables that will be set to the default value"
                    + " when the action is RESET") Map<String, Object> updateParams) {
        try {
            if (action == null) {
                action = ParamUtils.CompleteUpdateAction.ADD;
            }
            return createOkResponse(catalogManager.getSampleManager().updateAnnotations(studyStr, sampleStr, annotationSetId,
                    updateParams, action, queryOptions, token));
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @DELETE
    @Path("{samples}/delete")
    @ApiOperation(value = "Delete samples")
    @ApiImplicitParams({
            @ApiImplicitParam(name = Constants.FORCE, value = "Force the deletion of samples even if they are associated to files, "
                    + "individuals or cohorts.", dataType = "boolean", defaultValue = "false", paramType = "query"),
            @ApiImplicitParam(name = Constants.EMPTY_FILES_ACTION, value = "Action to be performed over files that were associated only to"
                    + " the sample to be deleted. Possible actions are NONE, TRASH, DELETE.", dataType = "string",
                    defaultValue = "NONE", paramType = "query"),
            @ApiImplicitParam(name = Constants.DELETE_EMPTY_COHORTS, value = "Boolean indicating if the cohorts associated only to the "
                    + "sample to be deleted should be also deleted.", dataType = "boolean", defaultValue = "false",
                    paramType = "query")
    })
    public Response delete(
            @ApiParam(value = "Study [[user@]project:]study where study and project can be either the id or alias")
                @QueryParam("study") String studyStr,
            @ApiParam(value = "Comma separated list of sample ids") @PathParam("samples") String samples) {
        try {
            queryOptions.put(Constants.EMPTY_FILES_ACTION, query.getString(Constants.EMPTY_FILES_ACTION, "NONE"));
            queryOptions.put(Constants.DELETE_EMPTY_COHORTS, query.getBoolean(Constants.DELETE_EMPTY_COHORTS, false));

            return createOkResponse(sampleManager.delete(studyStr, getIdList(samples), queryOptions, true, token));
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }


    @DELETE
    @Path("/delete")
    @ApiOperation(value = "Delete samples")
    @ApiImplicitParams({
            @ApiImplicitParam(name = Constants.FORCE, value = "Force the deletion of samples even if they are associated to files, "
                    + "individuals or cohorts.", dataType = "boolean", defaultValue = "false", paramType = "query"),
            @ApiImplicitParam(name = Constants.EMPTY_FILES_ACTION, value = "Action to be performed over files that were associated only to"
                    + " the sample to be deleted. Possible actions are NONE, TRASH, DELETE.", dataType = "string",
                    defaultValue = "NONE", paramType = "query"),
            @ApiImplicitParam(name = Constants.DELETE_EMPTY_COHORTS, value = "Boolean indicating if the cohorts associated only to the "
                    + "sample to be deleted should be also deleted.", dataType = "boolean", defaultValue = "false",
                    paramType = "query")
    })
    public Response delete(
            @ApiParam(value = "Study [[user@]project:]study where study and project can be either the id or alias")
            @QueryParam("study") String studyStr,
            @ApiParam(value = "Sample id") @QueryParam("id") String id,
            @ApiParam(value = "Sample name") @QueryParam("name") String name,
            @ApiParam(value = "Sample source") @QueryParam("source") String source,
            @ApiParam(value = "Sample type") @QueryParam("type") String type,
            @ApiParam(value = "Somatic") @QueryParam("somatic") Boolean somatic,
            @ApiParam(value = "Individual id or name") @QueryParam("individual") String individual,
            @ApiParam(value = "Creation date (Format: yyyyMMddHHmmss)") @QueryParam("creationDate") String creationDate,
            @ApiParam(value = "Comma separated list of phenotype ids or names") @QueryParam("phenotypes") String phenotypes,
            @ApiParam(value = "Annotation, e.g: key1=value(;key2=value)") @QueryParam("annotation") String annotation,
            @ApiParam(value = "Text attributes (Format: sex=male,age>20 ...)") @QueryParam("attributes") String attributes,
            @ApiParam(value = "Numerical attributes (Format: sex=male,age>20 ...)") @QueryParam("nattributes") String nattributes,
            @ApiParam(value = "Release value (Current release from the moment the samples were first created)")
            @QueryParam("release") String release) {
        try {
            query.remove("study");
            queryOptions.put(Constants.EMPTY_FILES_ACTION, query.getString(Constants.EMPTY_FILES_ACTION, "NONE"));
            queryOptions.put(Constants.DELETE_EMPTY_COHORTS, query.getBoolean(Constants.DELETE_EMPTY_COHORTS, false));

            query.remove(Constants.EMPTY_FILES_ACTION);
            query.remove(Constants.DELETE_EMPTY_COHORTS);

            return createOkResponse(sampleManager.delete(studyStr, query, queryOptions, true, token));
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @GET
    @Path("/groupBy")
    @ApiOperation(value = "Group samples by several fields", position = 10, hidden = true,
            notes = "Only group by categorical variables. Grouping by continuous variables might cause unexpected behaviour")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "count", value = "Count the number of elements matching the group", dataType = "boolean",
                    paramType = "query"),
            @ApiImplicitParam(name = "limit", value = "Maximum number of documents (groups) to be returned", dataType = "integer",
                    paramType = "query", defaultValue = "50")
    })
    public Response groupBy(
            @ApiParam(value = "Comma separated list of fields by which to group by.", required = true) @DefaultValue("")
            @QueryParam("fields") String fields,
            @ApiParam(value = "DEPRECATED: use study instead", hidden = true) @DefaultValue("") @QueryParam("studyId") String studyIdStr,
            @ApiParam(value = "Study [[user@]project:]study where study and project can be either the id or alias")
            @QueryParam("study") String studyStr,
            @ApiParam(value = "Comma separated list of ids.") @QueryParam("id") String id,
            @ApiParam(value = "DEPRECATED: Comma separated list of names.") @QueryParam("name") String name,
            @ApiParam(value = "source") @QueryParam("source") String source,
            @ApiParam(value = "Individual id or name", hidden = true) @QueryParam("individual.id") String individualId,
            @ApiParam(value = "Individual id or name") @QueryParam("individual") String individual,
            @ApiParam(value = "DEPRECATED: Use annotation queryParam this way: annotationSet[=|==|!|!=]{annotationSetName}")
            @QueryParam("annotationsetName") String annotationsetName,
            @ApiParam(value = "DEPRECATED: Use annotation queryParam this way: variableSet[=|==|!|!=]{variableSetId}")
            @QueryParam("variableSet") String variableSet,
            @ApiParam(value = "Annotation, e.g: key1=value(;key2=value)") @QueryParam("annotation") String annotation,
            @ApiParam(value = "Release value (Current release from the moment the families were first created)")
            @QueryParam("release") String release,
            @ApiParam(value = "Snapshot value (Latest version of families in the specified release)") @QueryParam("snapshot")
                    int snapshot) {
        try {
            query.remove("study");
            query.remove("fields");

            if (StringUtils.isNotEmpty(studyIdStr)) {
                studyStr = studyIdStr;
            }

            // TODO: individualId is deprecated. Remember to remove this if after next release
            if (query.containsKey(SampleDBAdaptor.QueryParams.INDIVIDUAL_UID.key())) {
                if (!query.containsKey(SampleDBAdaptor.QueryParams.INDIVIDUAL.key())) {
                    query.put(SampleDBAdaptor.QueryParams.INDIVIDUAL.key(), query.get(SampleDBAdaptor.QueryParams.INDIVIDUAL_UID.key()));
                }
                query.remove(SampleDBAdaptor.QueryParams.INDIVIDUAL_UID.key());
            }
            DataResult result = sampleManager.groupBy(studyStr, query, fields, queryOptions, token);
            return createOkResponse(result);
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @GET
    @Path("/{sample}/annotationsets/search")
    @ApiOperation(value = "Search annotation sets [DEPRECATED]", hidden = true, position = 11, notes = "Use /samples/search instead")
    public Response searchAnnotationSetGET(
            @ApiParam(value = "sampleId", required = true) @PathParam("sample") String sampleStr,
            @ApiParam(value = "Study [[user@]project:]study where study and project can be either the id or alias") @QueryParam("study") String studyStr,
            @ApiParam(value = "Variable set id") @QueryParam("variableSet") String variableSet,
            @ApiParam(value = "Annotation, e.g: key1=value(,key2=value)") @QueryParam("annotation") String annotation,
            @ApiParam(value = "Indicates whether to show the annotations as key-value", defaultValue = "false") @QueryParam("asMap") boolean asMap) {
        try {
            Sample sample = sampleManager.get(studyStr, sampleStr, SampleManager.INCLUDE_SAMPLE_IDS, token).first();
            Query query = new Query(SampleDBAdaptor.QueryParams.UID.key(), sample.getUid());

            if (StringUtils.isEmpty(annotation)) {
                if (StringUtils.isNotEmpty(variableSet)) {
                    annotation = Constants.VARIABLE_SET + "=" + variableSet;
                }
            } else {
                if (StringUtils.isNotEmpty(variableSet)) {
                    String[] annotationsSplitted = StringUtils.split(annotation, ",");
                    List<String> annotationList = new ArrayList<>(annotationsSplitted.length);
                    for (String auxAnnotation : annotationsSplitted) {
                        String[] split = StringUtils.split(auxAnnotation, ":");
                        if (split.length == 1) {
                            annotationList.add(variableSet + ":" + auxAnnotation);
                        } else {
                            annotationList.add(auxAnnotation);
                        }
                    }
                    annotation = StringUtils.join(annotationList, ";");
                }
            }
            query.putIfNotEmpty(Constants.ANNOTATION, annotation);

            DataResult<Sample> search = sampleManager.search(studyStr, query, new QueryOptions(Constants.FLATTENED_ANNOTATIONS, asMap),
                    token);
            if (search.getNumResults() == 1) {
                return createOkResponse(new DataResult<>(search.getTime(), search.getEvents(), search.first().getAnnotationSets().size(),
                        search.first().getAnnotationSets(), search.first().getAnnotationSets().size()));
            } else {
                return createOkResponse(search);
            }
        } catch (CatalogException e) {
            return createErrorResponse(e);
        }
    }

    @GET
    @Path("/{samples}/annotationsets")
    @ApiOperation(value = "Return the annotation sets of the sample [DEPRECATED]", hidden = true, position = 12, notes = "Use /samples/search instead")
    public Response getAnnotationSet(
            @ApiParam(value = "Comma separated list sample IDs or names up to a maximum of 100", required = true) @PathParam("samples") String samplesStr,
            @ApiParam(value = "Study [[user@]project:]study where study and project can be either the id or alias") @QueryParam("study") String studyStr,
            @ApiParam(value = "Indicates whether to show the annotations as key-value", defaultValue = "false") @QueryParam("asMap") boolean asMap,
            @ApiParam(value = "Annotation set name. If provided, only chosen annotation set will be shown") @QueryParam("name") String annotationsetName,
            @ApiParam(value = "Boolean to retrieve all possible entries that are queried for, false to raise an "
                    + "exception whenever one of the entries looked for cannot be shown for whichever reason", defaultValue = "false")
            @QueryParam("silent") boolean silent) throws WebServiceException {
        try {
            DataResult<Sample> queryResult = sampleManager.get(studyStr, getIdList(samplesStr), null, token);

            Query query = new Query(SampleDBAdaptor.QueryParams.UID.key(),
                    queryResult.getResults().stream().map(Sample::getUid).collect(Collectors.toList()));
            QueryOptions queryOptions = new QueryOptions(Constants.FLATTENED_ANNOTATIONS, asMap);

            if (StringUtils.isNotEmpty(annotationsetName)) {
                query.append(Constants.ANNOTATION, Constants.ANNOTATION_SET_NAME + "=" + annotationsetName);
                queryOptions.put(QueryOptions.INCLUDE, Constants.ANNOTATION_SET_NAME + "." + annotationsetName);
            }

            DataResult<Sample> search = sampleManager.search(studyStr, query, queryOptions, token);
            if (search.getNumResults() == 1) {
                return createOkResponse(new DataResult<>(search.getTime(), search.getEvents(), search.first().getAnnotationSets().size(),
                        search.first().getAnnotationSets(), search.first().getAnnotationSets().size()));
            } else {
                return createOkResponse(search);
            }
        } catch (CatalogException e) {
            return createErrorResponse(e);
        }
    }

    @POST
    @Path("/{sample}/annotationsets/create")
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create an annotation set for the sample [DEPRECATED]", hidden = true, position = 13, notes = "Use /{sample}/update instead")
    public Response annotateSamplePOST(
            @ApiParam(value = "SampleId", required = true) @PathParam("sample") String sampleStr,
            @ApiParam(value = "Study [[user@]project:]study where study and project can be either the id or alias") @QueryParam("study")
                    String studyStr,
            @ApiParam(value = "Variable set id or name", hidden = true) @QueryParam("variableSetId") String variableSetId,
            @ApiParam(value = "Variable set id or name", required = true) @QueryParam("variableSet") String variableSet,
            @ApiParam(value = "JSON containing the annotation set name and the array of annotations. The name should be unique for the "
                    + "sample", required = true) CohortWSServer.AnnotationsetParameters params) {
        try {
            if (StringUtils.isNotEmpty(variableSetId)) {
                variableSet = variableSetId;
            }

            String annotationSetId = StringUtils.isEmpty(params.id) ? params.name : params.id;
            sampleManager.update(studyStr, sampleStr, new SampleUpdateParams()
                    .setAnnotationSets(Collections.singletonList(new AnnotationSet(annotationSetId, variableSet, params.annotations))),
                    QueryOptions.empty(), token);
            DataResult<Sample> sampleQueryResult = sampleManager.get(studyStr, sampleStr, new QueryOptions(QueryOptions.INCLUDE,
                    Constants.ANNOTATION_SET_NAME + "." + annotationSetId), token);
            List<AnnotationSet> annotationSets = sampleQueryResult.first().getAnnotationSets();
            DataResult<AnnotationSet> queryResult = new DataResult<>(sampleQueryResult.getTime(), Collections.emptyList(),
                    annotationSets.size(), annotationSets, annotationSets.size());
            return createOkResponse(queryResult);
        } catch (CatalogException e) {
            return createErrorResponse(e);
        }
    }

    @GET
    @Path("/{samples}/acl")
    @ApiOperation(value = "Returns the acl of the samples. If member is provided, it will only return the acl for the member.", position = 18)
    public Response getAcls(@ApiParam(value = "Comma separated list of sample IDs or names up to a maximum of 100", required = true) @PathParam("samples")
                                    String sampleIdsStr,
                            @ApiParam(value = "Study [[user@]project:]study where study and project can be either the id or alias")
                            @QueryParam("study") String studyStr,
                            @ApiParam(value = "User or group id") @QueryParam("member") String member,
                            @ApiParam(value = "Boolean to retrieve all possible entries that are queried for, false to raise an "
                                    + "exception whenever one of the entries looked for cannot be shown for whichever reason",
                                    defaultValue = "false") @QueryParam("silent") boolean silent) {
        try {
            List<String> idList = getIdList(sampleIdsStr);
            return createOkResponse(sampleManager.getAcls(studyStr, idList, member, silent, token));
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @POST
    @Path("/{sample}/acl/{memberId}/update")
    @ApiOperation(value = "Update the set of permissions granted for the member [DEPRECATED]", position = 21, hidden = true,
            notes = "DEPRECATED: The usage of this webservice is discouraged. A different entrypoint /acl/{members}/update has been added "
                    + "to also support changing permissions using queries.")
    public Response updateAclPOST(
            @ApiParam(value = "Sample id or name", required = true) @PathParam("sample") String sampleIdStr,
            @ApiParam(value = "Study [[user@]project:]study where study and project can be either the id or alias") @QueryParam("study")
                    String studyStr,
            @ApiParam(value = "Member id", required = true) @PathParam("memberId") String memberId,
            @ApiParam(value = "JSON containing one of the keys 'add', 'set' or 'remove'", required = true) StudyWSServer.MemberAclUpdateOld params) {
        try {
            Sample.SampleAclParams sampleAclParams = getAclParams(params.add, params.remove, params.set);
            List<String> idList = StringUtils.isEmpty(sampleIdStr) ? Collections.emptyList() : getIdList(sampleIdStr);
            return createOkResponse(sampleManager.updateAcl(studyStr, idList, memberId, sampleAclParams, token));
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    // Temporal method used by deprecated methods. This will be removed at some point.
    @Override
    protected Sample.SampleAclParams getAclParams(@ApiParam(value = "Comma separated list of permissions to add", required = false)
                                                  @QueryParam("add") String addPermissions,
                                                  @ApiParam(value = "Comma separated list of permissions to remove", required = false)
                                                  @QueryParam("remove") String removePermissions,
                                                  @ApiParam(value = "Comma separated list of permissions to set", required = false)
                                                  @QueryParam("set") String setPermissions) throws CatalogException {
        int count = 0;
        count += StringUtils.isNotEmpty(setPermissions) ? 1 : 0;
        count += StringUtils.isNotEmpty(addPermissions) ? 1 : 0;
        count += StringUtils.isNotEmpty(removePermissions) ? 1 : 0;
        if (count > 1) {
            throw new CatalogException("Only one of add, remove or set parameters are allowed.");
        } else if (count == 0) {
            throw new CatalogException("One of add, remove or set parameters is expected.");
        }

        String permissions = null;
        AclParams.Action action = null;
        if (StringUtils.isNotEmpty(addPermissions)) {
            permissions = addPermissions;
            action = AclParams.Action.ADD;
        }
        if (StringUtils.isNotEmpty(setPermissions)) {
            permissions = setPermissions;
            action = AclParams.Action.SET;
        }
        if (StringUtils.isNotEmpty(removePermissions)) {
            permissions = removePermissions;
            action = AclParams.Action.REMOVE;
        }
        return new Sample.SampleAclParams(permissions, action, null, null, null);
    }

    public static class SampleAcl extends AclParams {
        public String sample;
        public String individual;
        public String file;
        public String cohort;

        public boolean propagate;
    }

    @POST
    @Path("/acl/{members}/update")
    @ApiOperation(value = "Update the set of permissions granted for the member", position = 21)
    public Response updateAcl(
            @ApiParam(value = "Study [[user@]project:]study where study and project can be either the id or alias") @QueryParam("study")
                    String studyStr,
            @ApiParam(value = "Comma separated list of user or group ids", required = true) @PathParam("members") String memberId,
            @ApiParam(value = "JSON containing the parameters to update the permissions. If propagate flag is set to true, it will "
                    + "propagate the permissions defined to the individuals that are associated to the matching samples", required = true)
                    SampleAcl params) {
        try {
            params = ObjectUtils.defaultIfNull(params, new SampleAcl());
            Sample.SampleAclParams sampleAclParams = new Sample.SampleAclParams(
                    params.getPermissions(), params.getAction(), params.individual, params.file, params.cohort, params.propagate);
            List<String> idList = StringUtils.isEmpty(params.sample) ? Collections.emptyList() : getIdList(params.sample, false);
            return createOkResponse(sampleManager.updateAcl(studyStr, idList, memberId, sampleAclParams, token));
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @GET
    @Path("/stats")
    @ApiOperation(value = "Fetch catalog sample stats", position = 15, hidden = true, response = QueryResponse.class)
    public Response getStats(
            @ApiParam(value = "Study [[user@]project:]study where study and project can be either the id or alias")
            @QueryParam("study") String studyStr,
            @ApiParam(value = "Source") @QueryParam("source") String source,
            @ApiParam(value = "Creation year") @QueryParam("creationYear") String creationYear,
            @ApiParam(value = "Creation month (JANUARY, FEBRUARY...)") @QueryParam("creationMonth") String creationMonth,
            @ApiParam(value = "Creation day") @QueryParam("creationDay") String creationDay,
            @ApiParam(value = "Creation day of week (MONDAY, TUESDAY...)") @QueryParam("creationDayOfWeek") String creationDayOfWeek,
            @ApiParam(value = "Status") @QueryParam("status") String status,
            @ApiParam(value = "Type") @QueryParam("type") String type,
            @ApiParam(value = "Phenotypes") @QueryParam("phenotypes") String phenotypes,
            @ApiParam(value = "Release") @QueryParam("release") String release,
            @ApiParam(value = "Version") @QueryParam("version") String version,
            @ApiParam(value = "Somatic") @QueryParam("somatic") Boolean somatic,
            @ApiParam(value = "Annotation, e.g: key1=value(;key2=value)") @QueryParam("annotation") String annotation,

            @ApiParam(value = "Calculate default stats", defaultValue = "false") @QueryParam("default") boolean defaultStats,

            @ApiParam(value = "List of fields separated by semicolons, e.g.: studies;type. For nested fields use >>, e.g.: studies>>biotype;type;numSamples[0..10]:1") @QueryParam("field") String facet) {
        try {
            query.remove("study");
            query.remove("field");

            queryOptions.put(QueryOptions.FACET, facet);

            DataResult<FacetField> queryResult = catalogManager.getSampleManager().facet(studyStr, query, queryOptions, defaultStats,
                    token);
            return createOkResponse(queryResult);
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @GET
    @Path("/aggregationStats")
    @ApiOperation(value = "Fetch catalog sample stats", position = 15, response = QueryResponse.class)
    public Response getAggregationStats(
            @ApiParam(value = "Study [[user@]project:]study where study and project can be either the id or alias")
            @QueryParam("study") String studyStr,
            @ApiParam(value = "Source") @QueryParam("source") String source,
            @ApiParam(value = "Creation year") @QueryParam("creationYear") String creationYear,
            @ApiParam(value = "Creation month (JANUARY, FEBRUARY...)") @QueryParam("creationMonth") String creationMonth,
            @ApiParam(value = "Creation day") @QueryParam("creationDay") String creationDay,
            @ApiParam(value = "Creation day of week (MONDAY, TUESDAY...)") @QueryParam("creationDayOfWeek") String creationDayOfWeek,
            @ApiParam(value = "Status") @QueryParam("status") String status,
            @ApiParam(value = "Type") @QueryParam("type") String type,
            @ApiParam(value = "Phenotypes") @QueryParam("phenotypes") String phenotypes,
            @ApiParam(value = "Release") @QueryParam("release") String release,
            @ApiParam(value = "Version") @QueryParam("version") String version,
            @ApiParam(value = "Somatic") @QueryParam("somatic") Boolean somatic,
            @ApiParam(value = "Annotation, e.g: key1=value(;key2=value)") @QueryParam("annotation") String annotation,

            @ApiParam(value = "Calculate default stats", defaultValue = "false") @QueryParam("default") boolean defaultStats,

            @ApiParam(value = "List of fields separated by semicolons, e.g.: studies;type. For nested fields use >>, e.g.: studies>>biotype;type;numSamples[0..10]:1") @QueryParam("field") String facet) {
        try {
            query.remove("study");
            query.remove("field");

            queryOptions.put(QueryOptions.FACET, facet);

            DataResult<FacetField> queryResult = catalogManager.getSampleManager().facet(studyStr, query, queryOptions, defaultStats,
                    token);
            return createOkResponse(queryResult);
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

//    private static class SamplePOST {
//        public String id;
//        @Deprecated
//        public String name;
//        public String description;
//        public String type;
//        public String individualId;
//        public SampleProcessing processing;
//        public SampleCollection collection;
//        public String source;
//        public boolean somatic;
//        public List<Phenotype> phenotypes;
//        public List<AnnotationSet> annotationSets;
//        @Deprecated
//        public Map<String, Object> stats;
//        public Map<String, Object> attributes;
//    }

    public static class CreateSamplePOST extends SampleUpdateParams {
        @Deprecated
        public String name;
        @Deprecated
        public Map<String, Object> stats;

        public Sample toSample() {

            String sampleId = StringUtils.isEmpty(this.getId()) ? name : this.getId();
            String sampleName = StringUtils.isEmpty(name) ? sampleId : name;
            return new Sample(sampleId, getSource(), getIndividualId(), getProcessing(), getCollection(), 1, 1, getDescription(), getType(),
                    getSomatic(), getPhenotypes(), getAnnotationSets(), getAttributes())
                    .setName(sampleName).setStats(stats);
        }
    }
}
