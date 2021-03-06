package org.opencb.opencga.analysis.clinical.interpretation;

import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.opencga.analysis.ConfigurationUtils;
import org.opencb.opencga.analysis.clinical.ClinicalInterpretationManager;
import org.opencb.opencga.catalog.exceptions.CatalogException;
import org.opencb.opencga.catalog.managers.CatalogManager;
import org.opencb.opencga.core.config.Configuration;
import org.opencb.opencga.core.exception.AnalysisExecutorException;
import org.opencb.opencga.storage.core.StorageEngineFactory;
import org.opencb.opencga.storage.core.config.StorageConfiguration;

import java.io.IOException;
import java.nio.file.Paths;

public interface ClinicalInterpretationAnalysisExecutor {

    ObjectMap getExecutorParams();


    default ClinicalInterpretationManager getClinicalInterpretationManager() throws AnalysisExecutorException {
        String opencgaHome = getExecutorParams().getString("opencgaHome");
        try {
            Configuration configuration = ConfigurationUtils.loadConfiguration(opencgaHome);
            StorageConfiguration storageConfiguration = ConfigurationUtils.loadStorageConfiguration(opencgaHome);

            CatalogManager catalogManager = new CatalogManager(configuration);
            StorageEngineFactory engineFactory = StorageEngineFactory.get(storageConfiguration);

            return new ClinicalInterpretationManager(catalogManager, engineFactory,
                    Paths.get(opencgaHome + "/analysis/resources/roleInCancer.txt"),
                    Paths.get(opencgaHome + "/analysis/resources/"));

        } catch (CatalogException | IOException e) {
            throw new AnalysisExecutorException(e);
        }
    }
}
