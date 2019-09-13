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

package org.opencb.opencga.catalog.managers;

import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.commons.datastore.core.QueryResult;
import org.opencb.opencga.catalog.audit.AuditManager;
import org.opencb.opencga.catalog.auth.authorization.AuthorizationManager;
import org.opencb.opencga.catalog.db.DBAdaptorFactory;
import org.opencb.opencga.catalog.db.api.*;
import org.opencb.opencga.catalog.exceptions.CatalogDBException;
import org.opencb.opencga.catalog.exceptions.CatalogException;
import org.opencb.opencga.catalog.io.CatalogIOManagerFactory;
import org.opencb.opencga.catalog.models.InternalGetQueryResult;
import org.opencb.opencga.core.config.AuthenticationOrigin;
import org.opencb.opencga.core.config.Configuration;
import org.opencb.opencga.core.models.Group;
import org.opencb.opencga.core.models.IPrivateStudyUid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by hpccoll1 on 12/05/15.
 */
public abstract class AbstractManager {

    protected static Logger logger;
    protected final AuthorizationManager authorizationManager;
    protected final AuditManager auditManager;
    protected final CatalogIOManagerFactory catalogIOManagerFactory;
    protected final CatalogManager catalogManager;

    protected Configuration configuration;

    protected final DBAdaptorFactory catalogDBAdaptorFactory;
    protected final UserDBAdaptor userDBAdaptor;
    protected final ProjectDBAdaptor projectDBAdaptor;
    protected final StudyDBAdaptor studyDBAdaptor;
    protected final FileDBAdaptor fileDBAdaptor;
    protected final IndividualDBAdaptor individualDBAdaptor;
    protected final SampleDBAdaptor sampleDBAdaptor;
    protected final CohortDBAdaptor cohortDBAdaptor;
    protected final FamilyDBAdaptor familyDBAdaptor;
    protected final DatasetDBAdaptor datasetDBAdaptor;
    protected final JobDBAdaptor jobDBAdaptor;
    protected final PanelDBAdaptor panelDBAdaptor;
    protected final ClinicalAnalysisDBAdaptor clinicalDBAdaptor;
    protected final InterpretationDBAdaptor interpretationDBAdaptor;

    protected static final String ROOT = "admin";
    protected static final String ANONYMOUS = "*";

    protected static final int BATCH_OPERATION_SIZE = 100;

    protected static final String INTERNAL_DELIMITER = "__";

    AbstractManager(AuthorizationManager authorizationManager, AuditManager auditManager, CatalogManager catalogManager,
                           DBAdaptorFactory catalogDBAdaptorFactory, CatalogIOManagerFactory ioManagerFactory,
                           Configuration configuration) {
        this.authorizationManager = authorizationManager;
        this.auditManager = auditManager;
        this.configuration = configuration;
        this.userDBAdaptor = catalogDBAdaptorFactory.getCatalogUserDBAdaptor();
        this.studyDBAdaptor = catalogDBAdaptorFactory.getCatalogStudyDBAdaptor();
        this.fileDBAdaptor = catalogDBAdaptorFactory.getCatalogFileDBAdaptor();
        this.individualDBAdaptor = catalogDBAdaptorFactory.getCatalogIndividualDBAdaptor();
        this.sampleDBAdaptor = catalogDBAdaptorFactory.getCatalogSampleDBAdaptor();
        this.jobDBAdaptor = catalogDBAdaptorFactory.getCatalogJobDBAdaptor();
        this.cohortDBAdaptor = catalogDBAdaptorFactory.getCatalogCohortDBAdaptor();
        this.familyDBAdaptor = catalogDBAdaptorFactory.getCatalogFamilyDBAdaptor();
        this.datasetDBAdaptor = catalogDBAdaptorFactory.getCatalogDatasetDBAdaptor();
        this.panelDBAdaptor = catalogDBAdaptorFactory.getCatalogPanelDBAdaptor();
        this.clinicalDBAdaptor = catalogDBAdaptorFactory.getClinicalAnalysisDBAdaptor();
        this.interpretationDBAdaptor = catalogDBAdaptorFactory.getInterpretationDBAdaptor();
        this.catalogIOManagerFactory = ioManagerFactory;
        this.catalogDBAdaptorFactory = catalogDBAdaptorFactory;
        this.catalogManager = catalogManager;

        projectDBAdaptor = catalogDBAdaptorFactory.getCatalogProjectDbAdaptor();

        logger = LoggerFactory.getLogger(this.getClass());
    }

    /**
     * Prior to the conversion to a numerical featureId, there is a need to know in which user/project/study look for the string.
     * This method calculates those parameters to know how to obtain the numerical id.
     *
     * @param userId User id of the user asking for the id. If no user is found in featureStr, we will assume that it is asking for its
     *               projects/studies...
     * @param featureStr Feature id in string format. Could be one of [user@aliasProject:aliasStudy:XXXXX
     *                | user@aliasStudy:XXXXX | aliasStudy:XXXXX | XXXXX].
     * @return an objectMap with the following possible keys: "user", "project", "study", "featureName"
     */
    protected ObjectMap parseFeatureId(String userId, String featureStr) {
        ObjectMap result = new ObjectMap("user", userId);

        String[] split = featureStr.split("@");
        if (split.length == 2) { // user@project:study
            result.put("user", split[0]);
            featureStr = split[1];
        }

        split = featureStr.split(":", 3);
        if (split.length == 2) {
            result.put("study", split[0]);
            result.put("featureName", split[1]);
        } else if (split.length == 3) {
            result.put("project", split[0]);
            result.put("study", split[1]);
            result.put("featureName", split[2]);
        } else {
            result.put("featureName", featureStr);
        }
        return result;
    }

    AuthenticationOrigin getAuthenticationOrigin(String authOrigin) {
        if (configuration.getAuthentication().getAuthenticationOrigins() != null) {
            for (AuthenticationOrigin authenticationOrigin : configuration.getAuthentication().getAuthenticationOrigins()) {
                if (authOrigin.equals(authenticationOrigin.getId())) {
                    return authenticationOrigin;
                }
            }
        }
        return null;
    }


    /**
     * Return the results in the QueryResult object in the same order they were queried by the list of entries.
     * For entities with version where all versions have been requested, call to InternalGetQueryResult.getVersionedResults() to get
     * a list of lists of T.
     *
     * @param entries Original list used to perform the query.
     * @param getId   Generic function that will fetch the id that will be used to compare with the list of entries.
     * @param queryResult QueryResult object.
     * @param silent  Boolean indicating whether we will fail in case of an inconsistency or not.
     * @param keepAllVersions Boolean indicating whether to keep all versions of fail in case of id duplicities.
     * @param <T>     Generic entry (Sample, File, Cohort...)
     * @return the QueryResult with the proper order of results.
     * @throws CatalogException In case of inconsistencies found.
     */
    <T extends IPrivateStudyUid> InternalGetQueryResult<T> keepOriginalOrder(List<String> entries, Function<T, String> getId,
                                                                             QueryResult<T> queryResult, boolean silent,
                                                                             boolean keepAllVersions) throws CatalogException {
        InternalGetQueryResult<T> internalGetQueryResult = new InternalGetQueryResult<>(queryResult);

        Map<String, List<T>> resultMap = new HashMap<>();

        for (T entry : internalGetQueryResult.getResult()) {
            String id = getId.apply(entry);
            if (!resultMap.containsKey(id)) {
                resultMap.put(id, new ArrayList<>());
            } else if (!keepAllVersions) {
                throw new CatalogException("Duplicated entry " + id + " found");
            }
            resultMap.get(id).add(entry);
        }

        List<T> orderedEntryList = new ArrayList<>(internalGetQueryResult.getNumResults());
        List<Integer> groups = new ArrayList<>(entries.size());
        for (String entry : entries) {
            if (resultMap.containsKey(entry)) {
                orderedEntryList.addAll(resultMap.get(entry));
                groups.add(resultMap.get(entry).size());
            } else {
                if (!silent) {
                    throw new CatalogException("Entry " + entry + " not found in QueryResult");
                }
                groups.add(0);
                internalGetQueryResult.addMissing(entry, "Not found or user does not have permissions.");
            }
        }

        internalGetQueryResult.setResult(orderedEntryList);
        internalGetQueryResult.setGroups(groups);
        return internalGetQueryResult;
    }

    /**
     * This method will make sure that 'field' is included in case there is a INCLUDE or never excluded in case there is a EXCLUDE list.
     *
     * @param options QueryOptions object.
     * @param field field that needs to remain.
     * @return a new QueryOptions with the necessary modifications.
     */
    QueryOptions keepFieldInQueryOptions(QueryOptions options, String field) {
        if (options.isEmpty()) {
            // Everything will be included, so we don't need to do anything
            return options;
        }

        QueryOptions queryOptions = new QueryOptions(options);

        List<String> includeList = queryOptions.getAsStringList(QueryOptions.INCLUDE);
        if (!includeList.isEmpty() && !includeList.contains(field)) {
            // We need to add the field
            List<String> includeListCopy = new ArrayList<>(includeList);
            includeListCopy.add(field);
            queryOptions.put(QueryOptions.INCLUDE, includeListCopy);
        }

        List<String> excludeList = queryOptions.getAsStringList(QueryOptions.EXCLUDE);
        if (!excludeList.isEmpty() && excludeList.contains(field)) {
            // We need to remove the field from the exclusion list
            List<String> excludeListCopy = excludeList.stream().filter(x -> !x.equals(field)).collect(Collectors.toList());
            queryOptions.put(QueryOptions.EXCLUDE, excludeListCopy);
        }

        return queryOptions;
    }

    /**
     * Obtains a list containing the entries that are in {@code originalEntries} that are not in {@code finalEntries}.
     *
     * @param originalEntries Original list that will be used to compare against.
     * @param finalEntries    List of {@code T} that will be compared against the {@code originalEntries}.
     * @param getId           Generic function to get the string used to make the comparison.
     * @param <T>             Generic entry (Sample, File, Cohort...)
     * @return a list containing the entries that are in {@code originalEntries} that are not in {@code finalEntries}.
     */
    <T extends IPrivateStudyUid> List<String>  getMissingFields(List<String> originalEntries, List<T> finalEntries,
                                                                Function<T, String> getId) {
        Set<String> entrySet = new HashSet<>();
        for (T finalEntry : finalEntries) {
            entrySet.add(getId.apply(finalEntry));
        }

        List<String> differences = new ArrayList<>();
        for (String originalEntry : originalEntries) {
            if (!entrySet.contains(originalEntry)) {
                differences.add(originalEntry);
            }
        }

        return differences;
    }

        /**
         * Checks if the list of members are all valid.
         *
         * The "members" can be:
         *  - '*' referring to all the users.
         *  - 'anonymous' referring to the anonymous user.
         *  - '@{groupId}' referring to a {@link Group}.
         *  - '{userId}' referring to a specific user.
         * @param studyId studyId
         * @param members List of members
         * @throws CatalogDBException CatalogDBException
         */
    protected void checkMembers(long studyId, List<String> members) throws CatalogDBException {
        for (String member : members) {
            checkMember(studyId, member);
        }
    }

    /**
     * Checks if the member is valid.
     *
     * The "member" can be:
     *  - '*' referring to all the users.
     *  - '@{groupId}' referring to a {@link Group}.
     *  - '{userId}' referring to a specific user.
     * @param studyId studyId
     * @param member member
     * @throws CatalogDBException CatalogDBException
     */
    protected void checkMember(long studyId, String member) throws CatalogDBException {
        if (member.equals("*")) {
            return;
        } else if (member.startsWith("@")) {
            QueryResult<Group> queryResult = studyDBAdaptor.getGroup(studyId, member, Collections.emptyList());
            if (queryResult.getNumResults() == 0) {
                throw CatalogDBException.idNotFound("Group", member);
            }
        } else {
            userDBAdaptor.checkId(member);
        }
    }

    @Deprecated
    public static class MyResourceId {
        private String user;
        private long studyId;
        private long resourceId;

        public MyResourceId() {
        }

        public MyResourceId(String user, long studyId, long resourceId) {
            this.user = user;
            this.studyId = studyId;
            this.resourceId = resourceId;
        }

        public String getUser() {
            return user;
        }

        public MyResourceId setUser(String user) {
            this.user = user;
            return this;
        }

        public long getStudyId() {
            return studyId;
        }

        public MyResourceId setStudyId(long studyId) {
            this.studyId = studyId;
            return this;
        }

        public long getResourceId() {
            return resourceId;
        }

        public MyResourceId setResourceId(long resourceId) {
            this.resourceId = resourceId;
            return this;
        }
    }

    @Deprecated
    public static class MyResourceIds {
        private String user;
        private long studyId;
        private List<Long> resourceIds;

        public MyResourceIds() {
        }

        public MyResourceIds(String user, long studyId, List<Long> resourceIds) {
            this.user = user;
            this.studyId = studyId;
            this.resourceIds = resourceIds;
        }

        public String getUser() {
            return user;
        }

        public MyResourceIds setUser(String user) {
            this.user = user;
            return this;
        }

        public long getStudyId() {
            return studyId;
        }

        public MyResourceIds setStudyId(long studyId) {
            this.studyId = studyId;
            return this;
        }

        public List<Long> getResourceIds() {
            return resourceIds;
        }

        public MyResourceIds setResourceIds(List<Long> resourceIds) {
            this.resourceIds = resourceIds;
            return this;
        }
    }
}
