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

package org.opencb.opencga.catalog.db.api;

import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.opencga.catalog.exceptions.CatalogDBException;
import org.opencb.opencga.core.models.AnnotationSet;
import org.opencb.opencga.core.models.Variable;
import org.opencb.opencga.core.models.VariableSet;
import org.opencb.opencga.core.models.summaries.VariableSummary;
import org.opencb.opencga.core.results.OpenCGAResult;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Created by pfurio on 06/07/16.
 */
public interface AnnotationSetDBAdaptor<T> extends DBAdaptor<T> {

    /**
     * Obtains all the annotation sets from id or just the one matching with the annotationSetName if provided.
     *
     * @param id id of the entity where the annotations are stored.
     * @param annotationSetName annotation set name of the annotation to be returned when provided.
     * @return a queryResult containing either all the annotation sets or just the one corresponding to the annotation set name if provided.
     * @throws CatalogDBException when the annotation set could not be retrieved due to a database error.
     */
    OpenCGAResult<AnnotationSet> getAnnotationSet(long id, @Nullable String annotationSetName)
            throws CatalogDBException;

    OpenCGAResult update(long id, ObjectMap parameters, List<VariableSet> variableSetList, QueryOptions queryOptions)
            throws CatalogDBException;

    OpenCGAResult update(Query query, ObjectMap parameters, List<VariableSet> variableSetList, QueryOptions queryOptions)
            throws CatalogDBException;

    /**
     * Add the variable to all the possible annotations from the variableSetId using the default value.
     *
     * @param variableSetId variable set id to identify the annotations that will add a new annotation.
     * @param variable new variable that will be added.
     * @return a OpenCGAResult object.
     * @throws CatalogDBException if the variable could not be added to an existing annotationSet.
     */
    OpenCGAResult addVariableToAnnotations(long variableSetId, Variable variable) throws CatalogDBException;

//    /**
//     * This method will rename the id of all the annotations corresponding to the variableSetId changing oldName per newName.
//     * This method cannot be called by any of the managers and will be only called when the user wants to rename the field of a variable
//     * from a variableSet.
//     * @param variableSetId Id of the variable to be renamed.
//     * @param oldName Name of the field to be renamed.
//     * @param newName New name that will be set.
//     * @return the number of annotations that renamed the name.
//     * @throws CatalogDBException when there is an error with database transactions.
//     */
//    OpenCGAResult<Long> renameAnnotationField(long variableSetId, String oldName, String newName) throws CatalogDBException;

    /**
     * Remove the annotation with annotationName from the annotation set.
     *
     * @param variableSetId variable set id for which the annotationSets have to delete the annotation.
     * @param annotationName Annotation name.
     * @return a OpenCGAResult object.
     * @throws CatalogDBException when there is an error in the database.
     */
    OpenCGAResult removeAnnotationField(long variableSetId, String annotationName) throws CatalogDBException;

    /**
     * Makes a groupBy to obtain the different values that every annotation has and the total number of each.
     *
     *
     * @param studyId study id.
     * @param variableSetId variable set id for which the group by will be done.
     * @return a list of Feature count with every different value.
     * @throws CatalogDBException when there is an error in the database.
     */
    OpenCGAResult<VariableSummary> getAnnotationSummary(long studyId, long variableSetId) throws CatalogDBException;
}
