package org.opencb.opencga.catalog.models;

import org.opencb.commons.datastore.core.QueryResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InternalGetQueryResult<T> extends QueryResult<T> {

    private List<Missing> missing;

    /**
     * When all versions are fetched for several entries, the results will be sorted as usual but here we will write how many documents of
     * every entry were found. Example: User queries sample1 (has 3 versions), sample2 (has only 1 version), sample3 (has 2 versions)
     * The results array would contain a list with: sample1 v2, sample1 v1, sample1 v3, sample2 v1, sample3 v1, sample3 v2
     * And the list of groups would be: 3, 1, 2; indicating that the first 3 samples form the first group of the query, then sample2, and
     * finally two versions of sample3.
     * Entry versions might not be sorted.
     */
    private List<Integer> groups;

    public InternalGetQueryResult() {
    }

    public InternalGetQueryResult(String id) {
        super(id);
    }

    public InternalGetQueryResult(String id, int dbTime, int numResults, long numTotalResults, String warningMsg, String errorMsg,
                                  List<T> result) {
        super(id, dbTime, numResults, numTotalResults, warningMsg, errorMsg, result);
    }

    public InternalGetQueryResult(QueryResult<T> queryResult) {
        super(queryResult.getId(), queryResult.getDbTime(), queryResult.getNumResults(), queryResult.getNumTotalResults(),
                queryResult.getWarningMsg(), queryResult.getErrorMsg(), queryResult.getResult());

    }

    public List<Missing> getMissing() {
        return missing;
    }

    public InternalGetQueryResult<T> setMissing(List<Missing> missing) {
        this.missing = missing;
        return this;
    }

    public List<Integer> getGroups() {
        return groups;
    }

    public InternalGetQueryResult<T> setGroups(List<Integer> groups) {
        this.groups = groups;
        return this;
    }

    public List<List<T>> getVersionedResults() {
        List<T> result = getResult();

        if (groups == null || groups.size() == 1) {
            return Collections.singletonList(result);
        }

        List<List<T>> myResults = new ArrayList<>();
        int counter = 0;
        for (int total : groups) {
            List<T> auxList = new ArrayList<>(total);

            for (int i = 0; i < total; i++) {
                auxList.add(result.get(counter + i));
            }
            myResults.add(auxList);
            counter += total;
        }

        return myResults;
    }

    public void addMissing(String id, String errorMsg) {
        if (this.missing == null) {
            this.missing = new ArrayList<>();
        }

        this.missing.add(new Missing(id, errorMsg));
    }

    public class Missing {
        private String id;
        private String errorMsg;

        public Missing(String id, String errorMsg) {
            this.id = id;
            this.errorMsg = errorMsg;
        }

        public String getId() {
            return id;
        }

        public String getErrorMsg() {
            return errorMsg;
        }
    }

}
