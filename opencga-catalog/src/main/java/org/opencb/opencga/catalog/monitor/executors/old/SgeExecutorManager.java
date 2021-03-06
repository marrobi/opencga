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

package org.opencb.opencga.catalog.monitor.executors.old;

import org.opencb.commons.datastore.core.DataResult;
import org.opencb.opencga.catalog.managers.CatalogManager;
import org.opencb.opencga.core.models.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Created on 26/11/15
 *
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt
 */
@Deprecated
public class SgeExecutorManager implements ExecutorManager {
    protected static Logger logger = LoggerFactory.getLogger(SgeExecutorManager.class);

    private final CatalogManager catalogManager;
    private final String sessionId;

    public SgeExecutorManager(CatalogManager catalogManager, String sessionId) {
        this.catalogManager = catalogManager;
        this.sessionId = sessionId;
    }

    @Deprecated
    @Override
    public DataResult<Job> run(Job job) throws Exception {
        // TODO: Lock job before submit. Avoid double submission
//        SgeManager.queueJob(job.getToolName(), job.getResourceManagerAttributes().get(Job.JOB_SCHEDULER_NAME).toString(),
//                -1, job.getTmpOutDirUri().getPath(), job.getCommandLine(), null, "job." + job.getId());
//        return catalogManager.getJobManager().update(job.getUid(),
//                new ObjectMap(JobDBAdaptor.QueryParams.STATUS_NAME.key(), Job.JobStatus.QUEUED), null, sessionId);
        return null;
    }

    @Override
    public String status(Job job) throws Exception {
        return null;
//        String status = SgeManager.status(Objects.toString(job.getResourceManagerAttributes().get(Job.JOB_SCHEDULER_NAME)));
//        switch (status) {
//            case SgeManager.ERROR:
//            case SgeManager.EXECUTION_ERROR:
//                return Job.JobStatus.ERROR;
//            case SgeManager.FINISHED:
//                return Job.JobStatus.READY;
//            case SgeManager.QUEUED:
//                return Job.JobStatus.QUEUED;
//            case SgeManager.RUNNING:
//            case SgeManager.TRANSFERRED:
//                return Job.JobStatus.RUNNING;
//            case SgeManager.UNKNOWN:
//            default:
//                return job.getStatus().getName();
//        }
    }
}
