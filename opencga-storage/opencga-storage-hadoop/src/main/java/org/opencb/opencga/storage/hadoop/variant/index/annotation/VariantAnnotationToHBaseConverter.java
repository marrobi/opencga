/*
 * Copyright 2015-2016 OpenCB
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

package org.opencb.opencga.storage.hadoop.variant.index.annotation;

import org.apache.hadoop.hbase.client.Put;
import org.apache.phoenix.schema.types.PArrayDataType;
import org.opencb.biodata.models.variant.avro.*;
import org.opencb.biodata.tools.variant.converter.Converter;
import org.opencb.opencga.storage.hadoop.variant.GenomeHelper;
import org.opencb.opencga.storage.hadoop.variant.index.phoenix.AbstractPhoenixConverter;
import org.opencb.opencga.storage.hadoop.variant.index.phoenix.PhoenixHelper;
import org.opencb.opencga.storage.hadoop.variant.index.phoenix.VariantPhoenixHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.opencb.opencga.storage.hadoop.variant.index.phoenix.VariantPhoenixHelper.VariantColumn.*;

/**
 * Created on 01/12/15.
 *
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public class VariantAnnotationToHBaseConverter extends AbstractPhoenixConverter implements Converter<VariantAnnotation, Put> {


    private final GenomeHelper genomeHelper;
    private boolean addFullAnnotation = true;

    public VariantAnnotationToHBaseConverter(GenomeHelper genomeHelper) {
        this.genomeHelper = genomeHelper;
    }
    private final Logger logger = LoggerFactory.getLogger(VariantAnnotationToHBaseConverter.class);

    @Override
    public Put convert(VariantAnnotation variantAnnotation) {
        byte[] bytesRowKey = genomeHelper.generateVariantRowKey(variantAnnotation.getChromosome(), variantAnnotation.getStart(),
                variantAnnotation.getReference(), variantAnnotation.getAlternate());

        Put put = new Put(bytesRowKey);

        if (addFullAnnotation) {
            add(put, FULL_ANNOTATION, variantAnnotation.toString());
        }

        Set<String> genes = new HashSet<>();
        Set<String> transcripts = new HashSet<>();
        Set<String> flags = new HashSet<>();
        Set<Integer> so = new HashSet<>();
        Set<String> biotype = new HashSet<>();
        Set<Double> polyphen = new HashSet<>();
        Set<Double> sift = new HashSet<>();
        Set<String> polyphenDesc = new HashSet<>();
        Set<String> siftDesc = new HashSet<>();
        Set<String> geneTraitName = new HashSet<>();
        Set<String> geneTraitId = new HashSet<>();
        Set<String> hpo = new HashSet<>();
        Set<String> drugs = new HashSet<>();
        Set<String> proteinKeywords = new HashSet<>();
        // Contains all the xrefs, and the id, the geneNames and transcripts
        Set<String> xrefs = new HashSet<>();

        addNotNull(xrefs, variantAnnotation.getId());

        for (ConsequenceType consequenceType : variantAnnotation.getConsequenceTypes()) {
            addNotNull(genes, consequenceType.getGeneName());
            addNotNull(genes, consequenceType.getEnsemblGeneId());
            addNotNull(transcripts, consequenceType.getEnsemblTranscriptId());
            addNotNull(biotype, consequenceType.getBiotype());
            addAllNotNull(flags, consequenceType.getTranscriptAnnotationFlags());
            for (SequenceOntologyTerm sequenceOntologyTerm : consequenceType.getSequenceOntologyTerms()) {
                String accession = sequenceOntologyTerm.getAccession();
                addNotNull(so, Integer.parseInt(accession.substring(3)));
            }
            if (consequenceType.getProteinVariantAnnotation() != null) {
                if (consequenceType.getProteinVariantAnnotation().getSubstitutionScores() != null) {
                    for (Score score : consequenceType.getProteinVariantAnnotation().getSubstitutionScores()) {
                        if (score.getSource().equalsIgnoreCase("sift")) {
                            addNotNull(sift, score.getScore());
                            addNotNull(siftDesc, score.getDescription());
                        } else if (score.getSource().equalsIgnoreCase("polyphen")) {
                            addNotNull(polyphen, score.getScore());
                            addNotNull(polyphenDesc, score.getDescription());
                        }
                    }
                }
                if (consequenceType.getProteinVariantAnnotation().getKeywords() != null) {
                    proteinKeywords.addAll(consequenceType.getProteinVariantAnnotation().getKeywords());
                }
            }
        }

        xrefs.addAll(genes);
        xrefs.addAll(transcripts);
        if (variantAnnotation.getXrefs() != null) {
            for (Xref xref : variantAnnotation.getXrefs()) {
                addNotNull(xrefs, xref.getId());
            }
        }

        if (variantAnnotation.getGeneTraitAssociation() != null) {
            for (GeneTraitAssociation geneTrait : variantAnnotation.getGeneTraitAssociation()) {
                addNotNull(geneTraitName, geneTrait.getName());
                addNotNull(geneTraitId, geneTrait.getId());
                addNotNull(hpo, geneTrait.getHpo());
            }
        }

        if (variantAnnotation.getGeneDrugInteraction() != null) {
            for (GeneDrugInteraction drug : variantAnnotation.getGeneDrugInteraction()) {
                addNotNull(drugs, drug.getDrugName());
            }
        }

        addVarcharArray(put, GENES.bytes(), genes);
        addVarcharArray(put, TRANSCRIPTS.bytes(), transcripts);
        addVarcharArray(put, BIOTYPE.bytes(), biotype);
        addIntegerArray(put, SO.bytes(), so);
        addArray(put, POLYPHEN.bytes(), sortProteinSubstitutionScores(polyphen), (PArrayDataType) POLYPHEN.getPDataType());
        addArray(put, POLYPHEN_DESC.bytes(), polyphenDesc, (PArrayDataType) POLYPHEN_DESC.getPDataType());
        addArray(put, SIFT.bytes(), sortProteinSubstitutionScores(sift), (PArrayDataType) SIFT.getPDataType());
        addArray(put, SIFT_DESC.bytes(), siftDesc, (PArrayDataType) SIFT_DESC.getPDataType());
        addVarcharArray(put, TRANSCRIPTION_FLAGS.bytes(), flags);
        addVarcharArray(put, GENE_TRAITS_ID.bytes(), geneTraitId);
        addVarcharArray(put, PROTEIN_KEYWORDS.bytes(), proteinKeywords);
        addVarcharArray(put, GENE_TRAITS_NAME.bytes(), geneTraitName);
        addVarcharArray(put, HPO.bytes(), hpo);
        addVarcharArray(put, DRUG.bytes(), drugs);
        addVarcharArray(put, XREFS.bytes(), xrefs);

        if (variantAnnotation.getConservation() != null) {
            for (Score score : variantAnnotation.getConservation()) {
                PhoenixHelper.Column column = VariantPhoenixHelper.getConservationScoreColumn(score.getSource());
                add(put, column, score.getScore());
            }
        }

        if (variantAnnotation.getPopulationFrequencies() != null) {
            for (PopulationFrequency pf : variantAnnotation.getPopulationFrequencies()) {
                PhoenixHelper.Column column = VariantPhoenixHelper.getPopulationFrequencyColumn(pf.getStudy(), pf.getPopulation());
                addArray(put, column.bytes(), Arrays.asList(pf.getRefAlleleFreq(), pf.getAltAlleleFreq()),
                        ((PArrayDataType) column.getPDataType()));
            }
        }

        if (variantAnnotation.getFunctionalScore() != null) {
            for (Score score : variantAnnotation.getFunctionalScore()) {
                PhoenixHelper.Column column = VariantPhoenixHelper.getFunctionalScoreColumn(score.getSource());
                add(put, column, score.getScore());
            }
        }

        return put;
    }

    private List<Double> sortProteinSubstitutionScores(Set<Double> scores) {
        List<Double> sorted = new ArrayList<>(scores.size());
        Double min = scores.stream().min(Double::compareTo).orElse(-1.0);
        Double max = scores.stream().max(Double::compareTo).orElse(-1.0);
        if (min >= 0) {
            sorted.add(min);
            sorted.add(max);
            scores.remove(min);
            scores.remove(max);
            sorted.addAll(scores);
        }
        return sorted;
    }

    @Override
    protected GenomeHelper getGenomeHelper() {
        return genomeHelper;
    }

}
