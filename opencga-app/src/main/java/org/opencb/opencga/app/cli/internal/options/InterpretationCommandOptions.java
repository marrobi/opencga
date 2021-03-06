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

package org.opencb.opencga.app.cli.internal.options;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.ParametersDelegate;
import org.opencb.biodata.models.clinical.interpretation.ClinicalProperty;
import org.opencb.opencga.analysis.clinical.interpretation.CancerTieringInterpretationAnalysis;
import org.opencb.opencga.analysis.clinical.interpretation.CustomInterpretationAnalysis;
import org.opencb.opencga.analysis.clinical.interpretation.TeamInterpretationAnalysis;
import org.opencb.opencga.analysis.clinical.interpretation.TieringInterpretationAnalysis;
import org.opencb.opencga.analysis.variant.VariantCatalogQueryUtils;
import org.opencb.opencga.app.cli.GeneralCliOptions;

import static org.opencb.opencga.analysis.clinical.interpretation.InterpretationAnalysis.*;

@Parameters(commandNames = {"interpretation"}, commandDescription = "Implement several interpretation analysis")
public class InterpretationCommandOptions {

    public TeamCommandOptions teamCommandOptions;
    public TieringCommandOptions tieringCommandOptions;
    public CancerTieringCommandOptions cancerTieringCommandOptions;
    public CustomCommandOptions customCommandOptions;

    public GeneralCliOptions.CommonCommandOptions analysisCommonOptions;
    public VariantCommandOptions.VariantQueryCommandOptions variantQueryOptions;
    public JCommander jCommander;

    public InterpretationCommandOptions(GeneralCliOptions.CommonCommandOptions analysisCommonCommandOptions,
                                        VariantCommandOptions.VariantQueryCommandOptions variantQueryCommandOptions,
                                        JCommander jCommander) {
        this.analysisCommonOptions = analysisCommonCommandOptions;
        this.variantQueryOptions =  variantQueryCommandOptions;
        this.jCommander = jCommander;

        this.teamCommandOptions = new TeamCommandOptions();
        this.tieringCommandOptions = new TieringCommandOptions();
        this.cancerTieringCommandOptions = new CancerTieringCommandOptions();
        this.customCommandOptions = new CustomCommandOptions();
    }

    @Parameters(commandNames = {TeamInterpretationAnalysis.ID}, commandDescription = "Team interpretation analysis")
    public class TeamCommandOptions {

        @ParametersDelegate
        public GeneralCliOptions.CommonCommandOptions commonOptions = analysisCommonOptions;

        @Parameter(names = {"-s", "--" + STUDY_PARAM_NAME}, description = "Study [[user@]project:]study.", required = true, arity = 1)
        public String studyId;

        @Parameter(names = {"--" + CLINICAL_ANALYISIS_PARAM_NAME}, description = "Clinical Analysis ID", arity = 1)
        public String clinicalAnalysisId;

        @Parameter(names = {"--" + PANELS_PARAM_NAME}, description = "Comma separated list of disease panel IDs", arity = 1)
        public String panelIds;

        @Parameter(names = {"--" + FAMILY_SEGREGATION_PARAM_NAME}, description = VariantCatalogQueryUtils.FAMILY_SEGREGATION_DESCR, arity = 1)
        public String familySegregation;

        @Parameter(names = {"--" + INCLUDE_LOW_COVERAGE_PARAM_NAME}, description = "Include low coverage regions", arity = 1)
        public boolean includeLowCoverage;

        @Parameter(names = {"--" + MAX_LOW_COVERAGE_PARAM_NAME}, description = "Maximum low coverage", arity = 1)
        public int maxLowCoverage;

        @Parameter(names = {"--" + INCLUDE_UNTIERED_VARIANTS_PARAM_NAME}, description = "Reported variants without tier", arity = 1)
        public boolean includeUntieredVariants;


        @Parameter(names = {"-o", "--outdir"}, description = "Directory where output files will be saved", required = true, arity = 1)
        public String outDir;
    }

    @Parameters(commandNames = {TieringInterpretationAnalysis.ID}, commandDescription = "Tiering interpretation analysis")
    public class TieringCommandOptions {

        @ParametersDelegate
        public GeneralCliOptions.CommonCommandOptions commonOptions = analysisCommonOptions;

        @Parameter(names = {"-s", "--" + STUDY_PARAM_NAME}, description = "Study [[user@]project:]study.", required = true, arity = 1)
        public String studyId;

        @Parameter(names = {"--" + CLINICAL_ANALYISIS_PARAM_NAME}, description = "Clinical Analysis ID", arity = 1)
        public String clinicalAnalysisId;

        @Parameter(names = {"--" + PANELS_PARAM_NAME}, description = "Comma separated list of disease panel IDs", arity = 1)
        public String panelIds;

        @Parameter(names = {"--" + PENETRANCE_PARAM_NAME}, description = "Penetrance. Accepted values: COMPLETE, INCOMPLETE", arity = 1)
        public ClinicalProperty.Penetrance penetrance = ClinicalProperty.Penetrance.COMPLETE;

        @Parameter(names = {"--" + INCLUDE_LOW_COVERAGE_PARAM_NAME}, description = "Include low coverage regions", arity = 1)
        public boolean includeLowCoverage;

        @Parameter(names = {"--" + MAX_LOW_COVERAGE_PARAM_NAME}, description = "Maximum low coverage", arity = 1)
        public int maxLowCoverage;

        @Parameter(names = {"--" + INCLUDE_UNTIERED_VARIANTS_PARAM_NAME}, description = "Reported variants without tier", arity = 1)
        public boolean includeUntieredVariants;


        @Parameter(names = {"-o", "--outdir"}, description = "Directory where output files will be saved", required = true, arity = 1)
        public String outDir;
    }

    @Parameters(commandNames = {CancerTieringInterpretationAnalysis.ID}, commandDescription = "Cancer tiering interpretation analysis")
    public class CancerTieringCommandOptions {

        @ParametersDelegate
        public GeneralCliOptions.CommonCommandOptions commonOptions = analysisCommonOptions;

        @Parameter(names = {"-s", "--" + STUDY_PARAM_NAME}, description = "Study [[user@]project:]study.", required = true, arity = 1)
        public String studyId;

        @Parameter(names = {"--" + CLINICAL_ANALYISIS_PARAM_NAME}, description = "Clinical Analysis ID", arity = 1)
        public String clinicalAnalysisId;

        @Parameter(names = {"--" + VARIANTS_TO_DISCARD_PARAM_NAME}, description = "Comma separated list of variant IDs to discard",
                arity = 1)
        public String variantIdsToDiscard;

        @Parameter(names = {"--" + INCLUDE_UNTIERED_VARIANTS_PARAM_NAME}, description = "Reported variants without tier", arity = 1)
        public boolean includeUntieredVariants;


        @Parameter(names = {"-o", "--outdir"}, description = "Directory where output files will be saved", required = true, arity = 1)
        public String outDir;
    }

    @Parameters(commandNames = {CustomInterpretationAnalysis.ID}, commandDescription = "Custom interpretation analysis")
    public class CustomCommandOptions {

        @ParametersDelegate
        public GeneralCliOptions.CommonCommandOptions commonOptions = analysisCommonOptions;

        @ParametersDelegate
        public VariantCommandOptions.VariantQueryCommandOptions variantQueryCommandOptions = variantQueryOptions;


        @Parameter(names = {"-s", "--" + STUDY_PARAM_NAME}, description = "Study [[user@]project:]study.", required = true, arity = 1)
        public String studyId;

        @Parameter(names = {"--" + CLINICAL_ANALYISIS_PARAM_NAME}, description = "Clinical Analysis ID", arity = 1)
        public String clinicalAnalysisId;

        @Parameter(names = {"--" + VARIANTS_TO_DISCARD_PARAM_NAME}, description = "Comma separated list of variant IDs to discard",
                arity = 1)
        public String variantIdsToDiscard;

        @Parameter(names = {"--" + INCLUDE_LOW_COVERAGE_PARAM_NAME}, description = "Include low coverage regions", arity = 1)
        public boolean includeLowCoverage;

        @Parameter(names = {"--" + MAX_LOW_COVERAGE_PARAM_NAME}, description = "Maximum low coverage", arity = 1)
        public int maxLowCoverage;

        @Parameter(names = {"--" + INCLUDE_UNTIERED_VARIANTS_PARAM_NAME}, description = "Reported variants without tier", arity = 1)
        public boolean includeUntieredVariants;


        @Parameter(names = {"-o", "--outdir"}, description = "Directory where output files will be saved", required = true, arity = 1)
        public String outDir;
    }
}
