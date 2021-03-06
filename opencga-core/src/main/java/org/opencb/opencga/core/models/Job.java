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

package org.opencb.opencga.core.models;

import org.opencb.opencga.core.analysis.result.AnalysisResult;
import org.opencb.opencga.core.models.common.Enums;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Created by jacobo on 11/09/14.
 */
public class Job extends PrivateStudyUid {

    private String id;
    private String uuid;
    private String name;
    private String description;

    private String userId;
    private String commandLine;

    private Map<String, Object> params;

    private String creationDate;
    private String modificationDate;

    private Enums.Priority priority;

    private JobStatus status;

    private File outDir;
    private List<File> input;    // input files to this job
    private List<File> output;   // output files of this job
    private List<String> tags;

    private AnalysisResult result;

    private File log;
    private File errorLog;

    private int release;
    private Map<String, Object> attributes;

    public static final String OPENCGA_COMMAND = "OPENCGA_COMMAND";
    public static final String OPENCGA_SUBCOMMAND = "OPENCGA_SUBCOMMAND";
    public static final String OPENCGA_STUDY = "OPENCGA_STUDY";
    public static final String OPENCGA_PARENTS = "OPENCGA_PARENTS";
//    public static final String OPENCGA_TMP_DIR = "OPENCGA_TMP_DIR";
//
//    /* ResourceManagerAttributes known keys */
//    public static final String JOB_SCHEDULER_NAME = "jobSchedulerName";
//    /* Errors */
//    public static final Map<String, String> ERROR_DESCRIPTIONS;
//    public static final String ERRNO_NONE = null;
//    public static final String ERRNO_NO_QUEUE = "ERRNO_NO_QUEUE";
//    public static final String ERRNO_FINISH_ERROR = "ERRNO_FINISH_ERROR";
//    public static final String ERRNO_ABORTED = "ERRNO_ABORTED";
//
//    static {
//        HashMap<String, String> map = new HashMap<>();
//        map.put(ERRNO_NONE, null);
//        map.put(ERRNO_NO_QUEUE, "Unable to queue job");
//        map.put(ERRNO_FINISH_ERROR, "Job finished with exit value != 0");
//        map.put(ERRNO_ABORTED, "Job aborted");
//        ERROR_DESCRIPTIONS = Collections.unmodifiableMap(map);
//    }

    public Job() {
    }

    public Job(String id, String uuid, String name, String description, String userId, String commandLine, Map<String, Object> params,
               String creationDate, String modificationDate, Enums.Priority priority, JobStatus status, File outDir,
               List<File> input, List<File> output, List<String> tags, AnalysisResult result, File log, File errorLog, int release,
               Map<String, Object> attributes) {
        this.id = id;
        this.uuid = uuid;
        this.name = name;
        this.description = description;
        this.userId = userId;
        this.commandLine = commandLine;
        this.params = params;
        this.creationDate = creationDate;
        this.modificationDate = modificationDate;
        this.priority = priority;
        this.status = status;
        this.outDir = outDir;
        this.input = input;
        this.output = output;
        this.tags = tags;
        this.result = result;
        this.log = log;
        this.errorLog = errorLog;
        this.release = release;
        this.attributes = attributes;
    }

    public static class JobStatus extends Status {

        /**
         * PENDING status means that the job is ready to be put into the queue.
         */
        public static final String PENDING = "PENDING";
        /**
         * QUEUED status means that the job is waiting on the queue to have an available slot for execution.
         */
        public static final String QUEUED = "QUEUED";
        /**
         * RUNNING status means that the job is running.
         */
        public static final String RUNNING = "RUNNING";
        /**
         * DONE status means that the job has finished the execution and everything finished successfully.
         */
        public static final String DONE = "DONE";
        /**
         * ERROR status means that the job finished with an error.
         */
        public static final String ERROR = "ERROR";
        /**
         * UNKNOWN status means that the job status could not be obtained.
         */
        public static final String UNKNOWN = "UNKNOWN";
        /**
         * ABORTED status means that the job was aborted and could not be executed.
         */
        public static final String ABORTED = "ABORTED";
        /**
         * REGISTERING status means that the job status could not be obtained.
         */
        public static final String REGISTERING = "REGISTERING";
        /**
         * UNREGISTERED status means that the job status could not be obtained.
         */
        public static final String UNREGISTERED = "UNREGISTERED";


        public static final List<String> STATUS_LIST = Arrays.asList(PENDING, QUEUED, RUNNING, DONE, ERROR, UNKNOWN, REGISTERING,
                UNREGISTERED, ABORTED, DELETED);

        public JobStatus(String status, String message) {
            if (isValid(status)) {
                init(status, message);
            } else {
                throw new IllegalArgumentException("Unknown status " + status);
            }
        }

        public JobStatus(String status) {
            this(status, "");
        }

        public JobStatus() {
            this(PENDING, "");
        }


        public static boolean isValid(String status) {
            if (Status.isValid(status)) {
                return true;
            }
            if (status != null && STATUS_LIST.contains(status)) {
                return true;
            }
            return false;
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Job{");
        sb.append("id='").append(id).append('\'');
        sb.append(", uuid='").append(uuid).append('\'');
        sb.append(", name='").append(name).append('\'');
        sb.append(", description='").append(description).append('\'');
        sb.append(", userId='").append(userId).append('\'');
        sb.append(", commandLine='").append(commandLine).append('\'');
        sb.append(", params=").append(params);
        sb.append(", creationDate='").append(creationDate).append('\'');
        sb.append(", modificationDate='").append(modificationDate).append('\'');
        sb.append(", priority='").append(priority).append('\'');
        sb.append(", status=").append(status);
        sb.append(", outDir=").append(outDir);
        sb.append(", input=").append(input);
        sb.append(", output=").append(output);
        sb.append(", tags=").append(tags);
        sb.append(", result=").append(result);
        sb.append(", log=").append(log);
        sb.append(", errorLog=").append(errorLog);
        sb.append(", release=").append(release);
        sb.append(", attributes=").append(attributes);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public Job setStudyUid(long studyUid) {
        super.setStudyUid(studyUid);
        return this;
    }

    @Override
    public Job setUid(long uid) {
        super.setUid(uid);
        return this;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Job setId(String id) {
        this.id = id;
        return this;
    }

    @Override
    public String getUuid() {
        return uuid;
    }

    public Job setUuid(String uuid) {
        this.uuid = uuid;
        return this;
    }

    public String getName() {
        return name;
    }

    public Job setName(String name) {
        this.name = name;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public Job setDescription(String description) {
        this.description = description;
        return this;
    }

    public String getUserId() {
        return userId;
    }

    public Job setUserId(String userId) {
        this.userId = userId;
        return this;
    }

    public String getCommandLine() {
        return commandLine;
    }

    public Job setCommandLine(String commandLine) {
        this.commandLine = commandLine;
        return this;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public Job setParams(Map<String, Object> params) {
        this.params = params;
        return this;
    }

    public String getCreationDate() {
        return creationDate;
    }

    public Job setCreationDate(String creationDate) {
        this.creationDate = creationDate;
        return this;
    }

    public String getModificationDate() {
        return modificationDate;
    }

    public Job setModificationDate(String modificationDate) {
        this.modificationDate = modificationDate;
        return this;
    }

    public Enums.Priority getPriority() {
        return priority;
    }

    public Job setPriority(Enums.Priority priority) {
        this.priority = priority;
        return this;
    }

    public JobStatus getStatus() {
        return status;
    }

    public Job setStatus(JobStatus status) {
        this.status = status;
        return this;
    }

    public File getOutDir() {
        return outDir;
    }

    public Job setOutDir(File outDir) {
        this.outDir = outDir;
        return this;
    }

    public List<File> getInput() {
        return input;
    }

    public Job setInput(List<File> input) {
        this.input = input;
        return this;
    }

    public List<File> getOutput() {
        return output;
    }

    public Job setOutput(List<File> output) {
        this.output = output;
        return this;
    }

    public List<String> getTags() {
        return tags;
    }

    public Job setTags(List<String> tags) {
        this.tags = tags;
        return this;
    }

    public AnalysisResult getResult() {
        return result;
    }

    public Job setResult(AnalysisResult result) {
        this.result = result;
        return this;
    }

    public File getLog() {
        return log;
    }

    public Job setLog(File log) {
        this.log = log;
        return this;
    }

    public File getErrorLog() {
        return errorLog;
    }

    public Job setErrorLog(File errorLog) {
        this.errorLog = errorLog;
        return this;
    }

    public int getRelease() {
        return release;
    }

    public Job setRelease(int release) {
        this.release = release;
        return this;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public Job setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
        return this;
    }
}
