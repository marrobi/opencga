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

package org.opencb.opencga.catalog.utils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opencb.commons.datastore.core.DataResult;
import org.opencb.commons.datastore.core.DataStoreServerAddress;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.commons.datastore.mongodb.MongoDBConfiguration;
import org.opencb.commons.utils.StringUtils;
import org.opencb.opencga.catalog.db.api.FileDBAdaptor;
import org.opencb.opencga.catalog.exceptions.CatalogException;
import org.opencb.opencga.catalog.io.CatalogIOManager;
import org.opencb.opencga.catalog.managers.CatalogManager;
import org.opencb.opencga.catalog.managers.CatalogManagerExternalResource;
import org.opencb.opencga.catalog.managers.FileUtils;
import org.opencb.opencga.core.common.TimeUtils;
import org.opencb.opencga.core.config.Configuration;
import org.opencb.opencga.core.models.Account;
import org.opencb.opencga.core.models.File;
import org.opencb.opencga.core.models.Status;
import org.opencb.opencga.core.models.Study;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Created by jacobo on 28/01/15.
 */
public class CatalogFileUtilsTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();
    FileUtils catalogFileUtils;
    private long studyUid;
    private String studyFqn;
    private String userSessionId;
//    private String adminSessionId;
    private CatalogManager catalogManager;

    @Before
    public void before() throws CatalogException, IOException, URISyntaxException {
        Configuration configuration = Configuration.load(getClass().getResource("/configuration-test.yml")
                .openStream());
        configuration.getAdmin().setAlgorithm("HS256");
        configuration.getAdmin().setSecretKey("dummy");
        MongoDBConfiguration mongoDBConfiguration = MongoDBConfiguration.builder()
                .add("username", configuration.getCatalog().getDatabase().getUser())
                .add("password", configuration.getCatalog().getDatabase().getPassword())
                .add("authenticationDatabase", configuration.getCatalog().getDatabase().getOptions().get("authenticationDatabase"))
                .build();

        String[] split = configuration.getCatalog().getDatabase().getHosts().get(0).split(":");
        DataStoreServerAddress dataStoreServerAddress = new DataStoreServerAddress(split[0], Integer.parseInt(split[1]));

        CatalogManagerExternalResource.clearCatalog(configuration);
        catalogManager = new CatalogManager(configuration);
        catalogManager.installCatalogDB("dummy", "admin");

        //Create USER
        catalogManager.getUserManager().create("user", "name", "mi@mail.com", "asdf", "", null, Account.Type.FULL, null);
        userSessionId = catalogManager.getUserManager().login("user", "asdf");
//        adminSessionId = catalogManager.login("admin", "admin", "--").getResults().get(0).getString("sessionId");
        String projectId = catalogManager.getProjectManager().create("proj", "proj", "", "", "Homo sapiens",
                null, null, "GRCh38", new QueryOptions(), userSessionId).getResults().get(0).getId();
        Study study = catalogManager.getStudyManager().create(projectId, "std", "std", "std", Study.Type.CONTROL_SET, null, "", null, null,
                null, null, null, null, null, null, userSessionId).getResults().get(0);
        studyUid = study.getUid();
        studyFqn = study.getFqn();

        catalogFileUtils = new FileUtils(catalogManager);
    }

    @Test
    public void unlinkNonExternalFile() throws CatalogException, IOException {
        File file = catalogManager.getFileManager().create(studyFqn, File.Type.FILE, File.Format.PLAIN,
                File.Bioformat.NONE, "item." + TimeUtils.getTimeMillis() + ".txt", "file at root", null, 0, null, (long) -1, null,
                null, true, null, null, userSessionId).first();

        // Now we try to unlink it
        thrown.expect(CatalogException.class);
        thrown.expectMessage("use delete instead");
        catalogManager.getFileManager().unlink(studyFqn, file.getPath(), userSessionId);
    }

    @Test
    public void deleteFilesTest2() throws CatalogException, IOException {
        DataResult<File> queryResult = catalogManager.getFileManager().create(studyFqn, File.Type.FILE, File.Format.PLAIN,
                File.Bioformat.NONE, "my.txt", "", new File.FileStatus(File.FileStatus.STAGE), 0, null, -1, null, null, false,
                StringUtils.randomString(200), null, userSessionId);
        File file = catalogManager.getFileManager().get(studyFqn, queryResult.first().getPath(), null, userSessionId).first();
        CatalogIOManager ioManager = catalogManager.getCatalogIOManagerFactory().get(file.getUri());
        assertTrue(ioManager.exists(file.getUri()));

        catalogManager.getFileManager().delete(studyFqn, new Query(FileDBAdaptor.QueryParams.UID.key(), file.getUid()),
                null, userSessionId);
        assertTrue(ioManager.exists(file.getUri()));
    }

    @Test
    public void deleteFoldersTest() throws CatalogException, IOException {
        List<File> folderFiles = new LinkedList<>();
        File folder = prepareFiles(folderFiles);
        CatalogIOManager ioManager = catalogManager.getCatalogIOManagerFactory().get(catalogManager.getFileManager().getUri(folder));
        for (File file : folderFiles) {
            assertTrue(ioManager.exists(catalogManager.getFileManager().getUri(file)));
        }

        catalogManager.getFileManager().delete(studyFqn, new Query(FileDBAdaptor.QueryParams.UID.key(), folder.getUid()), null,
                userSessionId);
        Query query = new Query()
                .append(FileDBAdaptor.QueryParams.UID.key(), folder.getUid())
                .append(FileDBAdaptor.QueryParams.STATUS_NAME.key(), File.FileStatus.TRASHED);
        DataResult<File> fileDataResult = catalogManager.getFileManager().search(studyFqn, query, QueryOptions.empty(), userSessionId);

        assertTrue(ioManager.exists(fileDataResult.first().getUri()));
        for (File file : folderFiles) {
            assertTrue("File uri: " + file.getUri() + " should exist", ioManager.exists(file.getUri()));
        }

    }

    @Test
    public void deleteFoldersTest2() throws CatalogException, IOException {
        List<File> folderFiles = new LinkedList<>();
        File folder = prepareFiles(folderFiles);
        CatalogIOManager ioManager = catalogManager.getCatalogIOManagerFactory().get(catalogManager.getFileManager().getUri(folder));
        for (File file : folderFiles) {
            assertTrue(ioManager.exists(catalogManager.getFileManager().getUri(file)));
        }

        //Create deleted files inside the folder
        DataResult<File> queryResult1 = catalogManager.getFileManager().create(studyFqn, File.Type.FILE, File.Format.PLAIN, File.Bioformat.NONE, "folder/subfolder/toDelete.txt", "", new File.FileStatus(File.FileStatus.STAGE), 0, null, -1, null, null, true, null, null, userSessionId);
        new FileUtils(catalogManager).upload(new ByteArrayInputStream(StringUtils.randomString(200).getBytes()), queryResult1.first(), userSessionId, false, false, true);

        File toDelete = catalogManager.getFileManager().get(studyFqn, queryResult1.first().getPath(), null, userSessionId).first();
        catalogManager.getFileManager().delete(studyFqn, new Query(FileDBAdaptor.QueryParams.UID.key(), toDelete.getUid()),
                null, userSessionId);
//        catalogFileUtils.delete(toDelete.getId(), userSessionId);

        DataResult<File> queryResult = catalogManager.getFileManager().create(studyFqn, File.Type.FILE, File.Format.PLAIN, File.Bioformat.NONE, "folder/subfolder/toTrash.txt", "", new File.FileStatus(File.FileStatus.STAGE), 0, null, -1, null, null, true, null, null, userSessionId);
        new FileUtils(catalogManager).upload(new ByteArrayInputStream(StringUtils.randomString(200).getBytes()), queryResult.first(), userSessionId, false, false, true);
        File toTrash = catalogManager.getFileManager().get(studyFqn, queryResult.first().getPath(), null, userSessionId).first();
        catalogManager.getFileManager().delete(studyFqn, new Query(FileDBAdaptor.QueryParams.UID.key(), toTrash.getUid()),
                null, userSessionId);

        catalogManager.getFileManager().delete(studyFqn, new Query(FileDBAdaptor.QueryParams.UID.key(), folder.getUid()),
                null, userSessionId);
        Query query = new Query()
                .append(FileDBAdaptor.QueryParams.PATH.key(), folder.getPath())
                .append(FileDBAdaptor.QueryParams.STATUS_NAME.key(), File.FileStatus.TRASHED);
        DataResult<File> fileDataResult = catalogManager.getFileManager().search(studyFqn, query, QueryOptions.empty(), userSessionId);

        assertTrue(ioManager.exists(fileDataResult.first().getUri()));
        for (File file : folderFiles) {
            assertTrue("File uri: " + file.getUri() + " should exist", ioManager.exists(file.getUri()));
        }

//        catalogFileUtils.delete(folder.getId(), userSessionId);
//        assertTrue(!ioManager.exists(catalogManager.getFileUri(catalogManager.getFile(folder.getId(), userSessionId).first())));
//        for (File file : folderFiles) {
//            URI fileUri = catalogManager.getFileUri(catalogManager.getFile(file.getId(), userSessionId).first());
//            assertTrue("File uri: " + fileUri + " should NOT exist", !ioManager.exists(fileUri));
//        }
    }

    @Test
    public void checkFileTest() throws CatalogException, IOException {
        File file;
        File returnedFile;

        file = catalogManager.getFileManager().create(studyFqn, File.Type.FILE, File.Format.PLAIN, File.Bioformat.NONE,
                "item." + TimeUtils.getTimeMillis() + ".txt", "file at root", null, 0, null, (long) -1, null, null, true,
                StringUtils.randomString(100), null, userSessionId).first();
        returnedFile = catalogFileUtils.checkFile(studyFqn, file, true, userSessionId);

        assertSame("Should not modify the status, so should return the same file.", file, returnedFile);
        assertEquals(Status.READY, file.getStatus().getName());

//        /** Check READY and existing file **/
//        catalogFileUtils.upload(sourceUri, file, null, userSessionId, false, false, false, true);
//        fileUri = catalogManager.getFileManager().getUri(file);
//        file = catalogManager.getFileManager().get(studyFqn, file.getPath(), null, userSessionId).first();
//        returnedFile = catalogFileUtils.checkFile(studyFqn, file, true, userSessionId);
//
//        assertSame("Should not modify the READY and existing file, so should return the same file.", file, returnedFile);


        /** Check READY and missing file **/
        assertTrue(new java.io.File(file.getUri()).delete());
        returnedFile = catalogFileUtils.checkFile(studyFqn, file, true, userSessionId);

        assertNotSame(file, returnedFile);
        assertEquals(File.FileStatus.MISSING, returnedFile.getStatus().getName());

        /** Check MISSING file still missing **/
        file = catalogManager.getFileManager().get(studyFqn, file.getPath(), null, userSessionId).first();
        returnedFile = catalogFileUtils.checkFile(studyFqn, file, true, userSessionId);

        assertEquals("Should not modify the still MISSING file, so should return the same file.", file.getStatus().getName(),
                returnedFile.getStatus().getName());
        //assertSame("Should not modify the still MISSING file, so should return the same file.", file, returnedFile);

        /** Check MISSING file with found file **/
        FileOutputStream os = new FileOutputStream(file.getUri().getPath());
        os.write(StringUtils.randomString(1000).getBytes());
        os.write('\n');
        os.close();
        returnedFile = catalogFileUtils.checkFile(studyFqn, file, true, userSessionId);

        assertNotSame(file, returnedFile);
        assertEquals(File.FileStatus.READY, returnedFile.getStatus().getName());

        /** Check TRASHED file with found file **/
        catalogManager.getFileManager().delete(studyFqn, new Query(FileDBAdaptor.QueryParams.UID.key(), file.getUid()), null,
                userSessionId);

        Query query = new Query()
                .append(FileDBAdaptor.QueryParams.UID.key(), file.getUid())
                .append(FileDBAdaptor.QueryParams.STATUS_NAME.key(), "!=EMPTY");
        DataResult<File> fileDataResult = catalogManager.getFileManager().search(studyFqn, query, QueryOptions.empty(), userSessionId);

        file = fileDataResult.first();
        returnedFile = catalogFileUtils.checkFile(studyFqn, file, true, userSessionId);

        assertSame(file, returnedFile);
        assertEquals(File.FileStatus.TRASHED, returnedFile.getStatus().getName());


        /** Check TRASHED file with missing file **/
//        catalogManager.getFileManager().delete(Long.toString(file.getId()), null, userSessionId);
        assertTrue(Paths.get(file.getUri()).toFile().delete());

        returnedFile = catalogFileUtils.checkFile(studyFqn, file, true, userSessionId);

//        assertNotSame(file, returnedFile);
        assertEquals(File.FileStatus.TRASHED, returnedFile.getStatus().getName());
    }


    private File prepareFiles(List<File> folderFiles) throws CatalogException {
        File folder = catalogManager.getFileManager().createFolder(studyFqn, Paths.get("folder").toString(), null, false,
                null, QueryOptions.empty(), userSessionId).first();
        folderFiles.add(
                catalogManager.getFileManager().create(studyFqn, new File().setPath("folder/my.txt"), false, StringUtils.randomString(200),
                        null, userSessionId).first()
        );
        folderFiles.add(
                catalogManager.getFileManager().create(studyFqn, new File().setPath("folder/my2.txt"), false, StringUtils.randomString(200),
                        null, userSessionId).first()
        );
        folderFiles.add(
                catalogManager.getFileManager().create(studyFqn, new File().setPath("folder/my3.txt"), false, StringUtils.randomString(200),
                        null, userSessionId).first()
        );
        folderFiles.add(
                catalogManager.getFileManager().create(studyFqn, new File().setPath("folder/subfolder/my4.txt"), true,
                        StringUtils.randomString(200), null, userSessionId).first()
        );
        folderFiles.add(
                catalogManager.getFileManager().create(studyFqn, new File().setPath("folder/subfolder/my5.txt"), false,
                        StringUtils.randomString(200), null, userSessionId).first()
        );
        folderFiles.add(
                catalogManager.getFileManager().create(studyFqn, new File().setPath("folder/subfolder/subsubfolder/my6.txt"), true,
                        StringUtils.randomString(200), null, userSessionId).first()
        );
        return folder;
    }


}
