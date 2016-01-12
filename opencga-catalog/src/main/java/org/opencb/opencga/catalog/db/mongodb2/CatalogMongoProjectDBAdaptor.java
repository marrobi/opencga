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

package org.opencb.opencga.catalog.db.mongodb2;

import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBObject;
import com.mongodb.WriteResult;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.commons.datastore.core.QueryResult;
import org.opencb.commons.datastore.mongodb.MongoDBCollection;
import org.opencb.opencga.catalog.db.CatalogDBAdaptorFactory;
import org.opencb.opencga.catalog.db.api2.CatalogProjectDBAdaptor;
import org.opencb.opencga.catalog.db.api2.CatalogUserDBAdaptor;
import org.opencb.opencga.catalog.exceptions.CatalogDBException;
import org.opencb.opencga.catalog.models.AclEntry;
import org.opencb.opencga.catalog.models.Project;
import org.opencb.opencga.catalog.models.Study;
import org.opencb.opencga.catalog.models.User;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;

import static org.opencb.opencga.catalog.db.mongodb2.CatalogMongoDBUtils.*;

/**
 * Created by imedina on 08/01/16.
 */
public class CatalogMongoProjectDBAdaptor extends AbstractCatalogMongoDBAdaptor implements CatalogProjectDBAdaptor {

    private final MongoDBCollection userCollection;
    private final MongoDBCollection metaCollection;
    private final CatalogDBAdaptorFactory dbAdaptorFactory;

    public CatalogMongoProjectDBAdaptor(CatalogDBAdaptorFactory dbAdaptorFactory, MongoDBCollection metaCollection, MongoDBCollection
            userCollection) {
        super(LoggerFactory.getLogger(CatalogMongoUserDBAdaptor.class));
        this.dbAdaptorFactory = dbAdaptorFactory;
        this.metaCollection = metaCollection;
        this.userCollection = userCollection;
    }

    @Override
    public boolean projectExists(int projectId) {
        QueryResult<Long> count = userCollection.count(new BasicDBObject("projects.id", projectId));
        return count.getResult().get(0) != 0;
    }

    @Override
    public QueryResult<Project> createProject(String userId, Project project, QueryOptions options) throws CatalogDBException {
        long startTime = startQuery();

        List<Study> studies = project.getStudies();
        if (studies == null) {
            studies = Collections.emptyList();
        }
        project.setStudies(Collections.<Study>emptyList());


        // Check if project.alias already exists.
//        DBObject countQuery = BasicDBObjectBuilder
//                .start("id", userId)
//                .append("projects.alias", project.getAlias())
//                .get();
        Bson countQuery = Filters.and(Filters.eq("id", userId), Filters.eq("projects.alias", project.getAlias()));
        QueryResult<Long> count = userCollection.count(countQuery);
        if (count.getResult().get(0) != 0) {
            throw new CatalogDBException("Project {alias:\"" + project.getAlias() + "\"} already exists in this user");
        }
//        if(getProjectId(userId, project.getAlias()) >= 0){
//            throw new CatalogManagerException( "Project {alias:\"" + project.getAlias() + "\"} already exists in this user");
//        }

        //Generate json
        int projectId = CatalogMongoDBUtils.getNewAutoIncrementId(metaCollection);
        project.setId(projectId);
//        DBObject query = new BasicDBObject("id", userId);
//        query.put("projects.alias", new BasicDBObject("$ne", project.getAlias()));
        Bson query = Filters.and(Filters.eq("id", userId), Filters.ne("projects.alias", project.getAlias()));

        Document projectDBObject = getMongoDBDocument(project, "Project");
//        DBObject update = new BasicDBObject("$push", new BasicDBObject("projects", projectDBObject));
        Bson update = Updates.push("projects", projectDBObject);

        //Update object
//        QueryResult<WriteResult> queryResult = userCollection.update(query, update, null);
        QueryResult<UpdateResult> queryResult = userCollection.update(query, update, null);

        if (queryResult.getResult().get(0).getModifiedCount() == 0) { // Check if the project has been inserted
            throw new CatalogDBException("Project {alias:\"" + project.getAlias() + "\"} already exists in this user");
        }

        String errorMsg = "";
        for (Study study : studies) {
            String studyErrorMsg = dbAdaptorFactory.getCatalogStudyDBAdaptor().createStudy(project.getId(), study, options).getErrorMsg();
            if (studyErrorMsg != null && !studyErrorMsg.isEmpty()) {
                errorMsg += ", " + study.getAlias() + ":" + studyErrorMsg;
            }
        }
        List<Project> result = getProject(project.getId(), null).getResult();
        return endQuery("Create Project", startTime, result, errorMsg, null);
    }

    @Override
    public QueryResult<Project> getProject(int projectId, QueryOptions options) throws CatalogDBException {
        long startTime = startQuery();

        /*
        DBObject query = new BasicDBObject("projects.id", projectId);
        DBObject projection = new BasicDBObject(
                "projects",
                new BasicDBObject(
                        "$elemMatch",
                        new BasicDBObject("id", projectId)
                )
        );
        QueryResult<DBObject> result = userCollection.find(query, projection, options);
        User user = parseUser(result);
        */
        Bson query = new BsonDocument("projects.id", new BsonInt32(projectId));
        Bson projection = Projections.elemMatch("projects", Filters.eq("id", projectId));
        QueryResult<Document> result = userCollection.find(query, projection, options);

        User user = parseUser(result);

        if (user == null || user.getProjects().isEmpty()) {
            throw CatalogDBException.idNotFound("Project", projectId);
        }
        // Fixme: Check the code below
        List<Project> projects = user.getProjects();
        joinFields(projects.get(0), options);

        return endQuery("Get project", startTime, projects);
    }

    /**
     * At the moment it does not clean external references to itself.
     */
//    @Override
//    public QueryResult<Integer> deleteProject(int projectId) throws CatalogDBException {
//        long startTime = startQuery();
//        DBObject query = new BasicDBObject("projects.id", projectId);
//        DBObject pull = new BasicDBObject("$pull",
//                new BasicDBObject("projects",
//                        new BasicDBObject("id", projectId)));
//
//        QueryResult<WriteResult> update = userCollection.update(query, pull, null);
//        List<Integer> deletes = new LinkedList<>();
//        if (update.getResult().get(0).getN() == 0) {
//            throw CatalogDBException.idNotFound("Project", projectId);
//        } else {
//            deletes.add(update.getResult().get(0).getN());
//            return endQuery("delete project", startTime, deletes);
//        }
//    }

    @Override
    public QueryResult<Project> getAllProjects(String userId, QueryOptions options) throws CatalogDBException {
        long startTime = startQuery();

/*        DBObject query = new BasicDBObject("id", userId);
        DBObject projection = new BasicDBObject("projects", true);
        projection.put("_id", false);
        QueryResult<DBObject> result = userCollection.find(query, projection, options);
*/
        Bson query = new BsonDocument(CatalogMongoDBAdaptor._ID, new BsonString(userId));
        Bson projection = Projections.fields(Projections.include("projects"), Projections.excludeId());
        QueryResult<Document> result = userCollection.find(query, projection, options);

        User user = parseUser(result);
        List<Project> projects = user.getProjects();
        for (Project project : projects) {
            joinFields(project, options);
        }
        return endQuery(
                "User projects list", startTime,
                projects);
    }


    /**
     * db.user.update(
     * {
     * "projects.id" : projectId,
     * "projects.alias" : {
     * $ne : newAlias
     * }
     * },
     * {
     * $set:{
     * "projects.$.alias":newAlias
     * }
     * })
     */
    @Override
    public QueryResult renameProjectAlias(int projectId, String newProjectAlias) throws CatalogDBException {
        long startTime = startQuery();
//        String projectOwner = getProjectOwner(projectId);
//
//        int collisionProjectId = getProjectId(projectOwner, newProjectAlias);
//        if (collisionProjectId != -1) {
//            throw new CatalogManagerException("Couldn't rename project alias, alias already used in the same user");
//        }

        QueryResult<Project> projectResult = getProject(projectId, null); // if projectId doesn't exist, an exception is raised
        Project project = projectResult.getResult().get(0);

        //String oldAlias = project.getAlias();
        project.setAlias(newProjectAlias);

        /*
        DBObject query = BasicDBObjectBuilder
                .start("projects.id", projectId)
                .append("projects.alias", new BasicDBObject("$ne", newProjectAlias))    // check that any other project in the user has
                // the new name
                .get();
        DBObject update = new BasicDBObject("$set",
                new BasicDBObject("projects.$.alias", newProjectAlias));
*/
        Bson query = Filters.and(Filters.eq("projects.id", projectId),
                Filters.ne("projects.alias", newProjectAlias));
        Bson update = Updates.set("projects.$.alias", newProjectAlias);

        QueryResult<UpdateResult> result = userCollection.update(query, update, null);
        if (result.getResult().get(0).getModifiedCount() == 0) {    //Check if the the study has been inserted
            throw new CatalogDBException("Project {alias:\"" + newProjectAlias + "\"} already exists");
        }
        return endQuery("rename project alias", startTime, result);
    }

    @Override
    public QueryResult<Project> modifyProject(int projectId, ObjectMap parameters) throws CatalogDBException {
        long startTime = startQuery();

        if (!projectExists(projectId)) {
            throw CatalogDBException.idNotFound("Project", projectId);
        }
        //BasicDBObject projectParameters = new BasicDBObject();
        Bson projectParameters = new Document();

        String[] acceptedParams = {"name", "creationDate", "description", "organization", "status", "lastActivity"};
        for (String s : acceptedParams) {
            if (parameters.containsKey(s)) {
                ((Document) projectParameters).put("projects.$." + s, parameters.getString(s));
            }
        }
        String[] acceptedIntParams = {"diskQuota", "diskUsage"};
        for (String s : acceptedIntParams) {
            if (parameters.containsKey(s)) {
                int anInt = parameters.getInt(s, Integer.MIN_VALUE);
                if (anInt != Integer.MIN_VALUE) {
                    ((Document) projectParameters).put(s, anInt);
                }
            }
        }
        Map<String, Object> attributes = parameters.getMap("attributes");
        if (attributes != null) {
            for (Map.Entry<String, Object> entry : attributes.entrySet()) {
                ((Document) projectParameters).put("projects.$.attributes." + entry.getKey(), entry.getValue());
            }
//            projectParameters.put("projects.$.attributes", attributes);
        }

        if (!((Document) projectParameters).isEmpty()) {
            Bson query = new BsonDocument("projects.id", new BsonInt32(projectId));
            // Fixme: Updates
                    /*
            BasicDBObject query = new BasicDBObject("projects.id", projectId);
            BasicDBObject updates = new BasicDBObject("$set", projectParameters);
            */
            QueryResult<WriteResult> updateResult = userCollection.update(query, updates, null);
            if (updateResult.getResult().get(0).getN() == 0) {
                throw CatalogDBException.idNotFound("Project", projectId);
            }
        }
        /*
        if (!projectParameters.isEmpty()) {
            BasicDBObject query = new BasicDBObject("projects.id", projectId);
            BasicDBObject updates = new BasicDBObject("$set", projectParameters);
            QueryResult<WriteResult> updateResult = userCollection.update(query, updates, null);
            if (updateResult.getResult().get(0).getN() == 0) {
                throw CatalogDBException.idNotFound("Project", projectId);
            }
        }
        */
        return endQuery("Modify project", startTime, getProject(projectId, null));
    }

    @Override
    public int getProjectId(String userId, String projectAlias) throws CatalogDBException {
        QueryResult<Document> queryResult = userCollection.find(
                new BsonDocument("projects.alias", new BsonString(projectAlias))
                        .append("id", new BsonString(userId)),
                Projections.fields(Projections.include("projects.id"),
                        Projections.elemMatch("projects", Filters.eq("alias", projectAlias))),
                null);
/*
        QueryResult<DBObject> queryResult = userCollection.find(
                BasicDBObjectBuilder
                        .start("projects.alias", projectAlias)
                        .append("id", userId).get(),
                BasicDBObjectBuilder.start("projects.id", true)
                        .append("projects", new BasicDBObject("$elemMatch", new BasicDBObject("alias", projectAlias))).get(),
                null
        );*/
        User user = parseUser(queryResult);
        if (user == null || user.getProjects().isEmpty()) {
            return -1;
        } else {
            return user.getProjects().get(0).getId();
        }
    }

    @Override
    public String getProjectOwnerId(int projectId) throws CatalogDBException {
//        DBObject query = new BasicDBObject("projects.id", projectId);
        Bson query = Filters.eq("projects.id", projectId);

//        DBObject projection = new BasicDBObject("id", "true");
        Bson projection = Projections.include("id");

//        QueryResult<DBObject> result = userCollection.find(query, projection, null);
        QueryResult<Document> result = userCollection.find(query, projection, null);

        if (result.getResult().isEmpty()) {
            throw CatalogDBException.idNotFound("Project", projectId);
        } else {
            return result.getResult().get(0).get("id").toString();
        }
    }

    public AclEntry getFullProjectAcl(int projectId, String userId) throws CatalogDBException {
        QueryResult<Project> project = getProject(projectId, null);
        if (project.getNumResults() != 0) {
            List<AclEntry> acl = project.getResult().get(0).getAcl();
            for (AclEntry acl1 : acl) {
                if (userId.equals(acl1.getUserId())) {
                    return acl1;
                }
            }
        }
        return null;
    }

    /**
     * db.user.aggregate(
     * {"$match": {"projects.id": 2}},
     * {"$project": {"projects.acl":1, "projects.id":1}},
     * {"$unwind": "$projects"},
     * {"$match": {"projects.id": 2}},
     * {"$unwind": "$projects.acl"},
     * {"$match": {"projects.acl.userId": "jmmut"}}).pretty()
     */
    @Override
    public QueryResult<AclEntry> getProjectAcl(int projectId, String userId) throws CatalogDBException {
        long startTime = startQuery();
        /*
        DBObject match1 = new BasicDBObject("$match", new BasicDBObject("projects.id", projectId));
        DBObject project = new BasicDBObject("$project", BasicDBObjectBuilder
                .start("_id", false)
                .append("projects.acl", true)
                .append("projects.id", true).get());
        DBObject unwind1 = new BasicDBObject("$unwind", "$projects");
        DBObject match2 = new BasicDBObject("$match", new BasicDBObject("projects.id", projectId));
        DBObject unwind2 = new BasicDBObject("$unwind", "$projects.acl");
        DBObject match3 = new BasicDBObject("$match", new BasicDBObject("projects.acl.userId", userId));

*/
        Bson match1 = Aggregates.match(Filters.eq("projects.id", projectId));
        Bson project = Projections.fields(Projections.excludeId(), Projections.include("projects.acl", "projects.id"));
        Bson unwind1 = Aggregates.unwind("$projects");
        Bson match2 = Aggregates.match(Filters.eq("projects.id", projectId));
        Bson unwind2 = Aggregates.unwind("$projects.acl");
        Bson match3 = Aggregates.match(Filters.eq("projects.acl.userId", userId));

        List<Bson> operations = new LinkedList<>();
        operations.add(match1);
        operations.add(project);
        operations.add(unwind1);
        operations.add(match2);
        operations.add(unwind2);
        operations.add(match3);

        QueryResult aggregate = userCollection.aggregate(operations, null);

        List<AclEntry> acls = new LinkedList<>();
        if (aggregate.getNumResults() != 0) {
            DBObject aclObject = (DBObject) ((DBObject) ((DBObject) aggregate.getResult().get(0)).get("projects")).get("acl");
            AclEntry acl = parseObject(aclObject, AclEntry.class);
            acls.add(acl);
        }
        return endQuery("get project ACL", startTime, acls);
    }

    @Override
    public QueryResult setProjectAcl(int projectId, AclEntry newAcl) throws CatalogDBException {
        long startTime = startQuery();
        String userId = newAcl.getUserId();
        if (!dbAdaptorFactory.getCatalogUserDBAdaptor().userExists(userId)) {
            throw new CatalogDBException("Can not set ACL to non-existent user: " + userId);
        }

        Document newAclObject = getMongoDBDocument(newAcl, "ACL");
        //DBObject newAclObject = getDbObject(newAcl, "ACL");

        List<AclEntry> projectAcls = getProjectAcl(projectId, userId).getResult();
        Bson query = new BsonDocument("projects.ids", new BsonInt32(projectId));
        Bson push = Updates.push("projects.$.acl", newAclObject);
        if (!projectAcls.isEmpty()) { // ensure that there is no acl for that user in that project. pull
            Bson pull = Updates.pull("projects.$.acl", Filters.eq("userId", userId));
            userCollection.update(query, pull, null);
        }
        /*
        DBObject query = new BasicDBObject("projects.id", projectId);
        BasicDBObject push = new BasicDBObject("$push", new BasicDBObject("projects.$.acl", newAclObject));
        if (!projectAcls.isEmpty()) {  // ensure that there is no acl for that user in that project. pull
            DBObject pull = new BasicDBObject("$pull", new BasicDBObject("projects.$.acl", new BasicDBObject("userId", userId)));
            userCollection.update(query, pull, null);
        }
        */
        //Put study
        QueryResult pushResult = userCollection.update(query, push, null);
        return endQuery("Set project acl", startTime, pushResult);
    }

    //Join fields from other collections
    private void joinFields(User user, QueryOptions options) throws CatalogDBException {
        if (options == null) {
            return;
        }
        if (user.getProjects() != null) {
            for (Project project : user.getProjects()) {
                joinFields(project, options);
            }
        }
    }

    private void joinFields(Project project, QueryOptions options) throws CatalogDBException {
        if (options == null) {
            return;
        }
        if (options.getBoolean("includeStudies")) {
            project.setStudies(dbAdaptorFactory.getCatalogStudyDBAdaptor().getAllStudiesInProject(project.getId(), options).getResult());
        }
    }


    @Override
    public QueryResult<Long> count(Query query) {
        Bson bson = parseQuery(query);
        return userCollection.count(bson);
    }

    @Override
    public QueryResult distinct(Query query, String field) {
        Bson bson = parseQuery(query);
        return userCollection.distinct(field, bson);
    }

    @Override
    public QueryResult stats(Query query) {
        return null;
    }

    @Override
    public QueryResult<Project> get(Query query, QueryOptions options) {
        Bson bson = parseQuery(query);
        List<Document> queryResult = userCollection.find(bson, options).getResult();

        // FIXME: Pedro. Parse and set clazz to study class.

        return null;
    }

    @Override
    public QueryResult nativeGet(Query query, QueryOptions options) {
        Bson bson = parseQuery(query);
        return userCollection.find(bson, options);
    }

    @Override
    public QueryResult<Project> update(Query query, ObjectMap parameters) { return null; }

    @Override
    public QueryResult<Long> delete(Query query) throws CatalogDBException {
        long startTime = startQuery();

        Bson bson = parseQuery(query);
        Bson pull = Updates.pull("projects", new Document("id", query.get(CatalogUserDBAdaptor.QueryParams.PROJECT_ID.key())));

//        DBObject query = new BasicDBObject("projects.id", projectId);
//        DBObject pull = new BasicDBObject("$pull",
//                new BasicDBObject("projects",
//                        new BasicDBObject("id", projectId)));

        QueryResult<UpdateResult> update = userCollection.update(bson, pull, null);
        List<Long> deletes = new LinkedList<>();
        if (update.getResult().get(0).getModifiedCount() == 0) {
//            throw CatalogDBException.idNotFound("Project", projectId);
            throw CatalogDBException.newInstance("Project id '{}' not found", query.get(CatalogUserDBAdaptor.QueryParams.PROJECT_ID.key()));
        } else {
            deletes.add(update.getResult().get(0).getModifiedCount());
            return endQuery("delete project", startTime, deletes);
        }
    }

    @Override
    public Iterator<Project> iterator(Query query, QueryOptions options) {
        return null;
    }

    @Override
    public Iterator nativeIterator(Query query, QueryOptions options) {
        Bson bson = parseQuery(query);
        return userCollection.nativeQuery().find(bson, options).iterator();
    }

    @Override
    public QueryResult rank(Query query, String field, int numResults, boolean asc) {
        return null;
    }

    @Override
    public QueryResult groupBy(Query query, String field, QueryOptions options) {
        Bson bsonQuery = parseQuery(query);
        return groupBy(userCollection, bsonQuery, field, "name", options);
    }

    @Override
    public QueryResult groupBy(Query query, List<String> fields, QueryOptions options) {
        Bson bsonQuery = parseQuery(query);
        return groupBy(userCollection, bsonQuery, fields, "name", options);
    }

    @Override
    public void forEach(Query query, Consumer<? super Object> action, QueryOptions options) {

    }

    private Bson parseQuery(Query query) {
        List<Bson> andBsonList = new ArrayList<>();

        // FIXME: Pedro. Check the mongodb names as well as integer createQueries.
        // FIXME: Pedro. Check how the projects are inserted in the user collection.

        createOrQuery(query, QueryParams.ID.key(), "project.id", andBsonList);
        createOrQuery(query, QueryParams.NAME.key(), "project.name", andBsonList);
        createOrQuery(query, QueryParams.ALIAS.key(), "project.alias", andBsonList);
        createOrQuery(query, QueryParams.ORGANIZATION.key(), "project.organization", andBsonList);
        createOrQuery(query, QueryParams.STATUS.key(), "project.status", andBsonList);
        createOrQuery(query, QueryParams.LAST_ACTIVITY.key(), "project.lastActivity", andBsonList);

        createOrQuery(query, QueryParams.STUDY_ID.key(), "project.study.id", andBsonList);
        createOrQuery(query, QueryParams.STUDY_NAME.key(), "project.study.name", andBsonList);
        createOrQuery(query, QueryParams.STUDY_ALIAS.key(), "project.study.name", andBsonList);
        createOrQuery(query, QueryParams.STUDY_CREATOR_ID.key(), "project.study.creatorId", andBsonList);
        createOrQuery(query, QueryParams.STUDY_STATUS.key(), "project.study.status", andBsonList);
        createOrQuery(query, QueryParams.STUDY_LAST_ACTIVITY.key(), "project.study.lastActivity", andBsonList);

        createOrQuery(query, QueryParams.ACL_USER_ID.key(), "project.acl.userId", andBsonList);
        createOrQuery(query, QueryParams.ACL_READ.key(), "project.acl.read", andBsonList);
        createOrQuery(query, QueryParams.ACL_WRITE.key(), "project.acl.write", andBsonList);
        createOrQuery(query, QueryParams.ACL_EXECUTE.key(), "project.acl.execute", andBsonList);
        createOrQuery(query, QueryParams.ACL_DELETE.key(), "project.acl.delete", andBsonList);

        if (andBsonList.size() > 0) {
            return Filters.and(andBsonList);
        } else {
            return new Document();
        }
    }

}
