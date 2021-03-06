package org.opencb.opencga.storage.hadoop.variant.index.sample;

import htsjdk.variant.vcf.VCFConstants;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.util.Bytes;
import org.opencb.biodata.models.variant.StudyEntry;
import org.opencb.biodata.models.variant.Variant;
import org.opencb.biodata.models.variant.avro.FileEntry;
import org.opencb.biodata.models.variant.avro.VariantType;

import java.util.Map;
import java.util.SortedSet;
import java.util.stream.Collectors;

import static org.opencb.opencga.storage.hadoop.variant.index.sample.SampleIndexSchema.INTRA_CHROMOSOME_VARIANT_COMPARATOR;

/**
 * Created on 31/01/19.
 *
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public class SampleIndexToHBaseConverter {

    public static final double QUAL_THRESHOLD_20 = 20;
    public static final double QUAL_THRESHOLD_40 = 40;
    public static final int DP_THRESHOLD_20 = 20;
    private final byte[] family;

    public static final byte SNV_MASK          = (byte) (1 << 0);
    public static final byte FILTER_PASS_MASK  = (byte) (1 << 1);
    public static final byte QUAL_GT_20_MASK   = (byte) (1 << 2);
    public static final byte QUAL_GT_40_MASK   = (byte) (1 << 3);
    public static final byte DP_GT_20_MASK     = (byte) (1 << 4);
    public static final byte UNUSED_5_MASK     = (byte) (1 << 5);
    public static final byte UNUSED_6_MASK     = (byte) (1 << 6);
    public static final byte UNUSED_7_MASK     = (byte) (1 << 7);
    private final SampleIndexVariantBiConverter variantConverter;

    public SampleIndexToHBaseConverter(byte[] family) {
        this.family = family;
        variantConverter = new SampleIndexVariantBiConverter();
    }

    public Put convert(byte[] rk, Map<String, SortedSet<Variant>> gtsMap, int sampleIdx) {
        Put put = new Put(rk);

        for (Map.Entry<String, SortedSet<Variant>> gtsEntry : gtsMap.entrySet()) {
            SortedSet<Variant> variants = gtsEntry.getValue();
            String gt = gtsEntry.getKey();

            byte[] variantsBytes = variantConverter.toBytes(variants);
            byte[] fileMask = new byte[variants.size()];
            int i = 0;
            for (Variant variant : variants) {
                fileMask[i] = createFileIndexValue(sampleIdx, variant);
                i++;
            }

            put.addColumn(family, SampleIndexSchema.toGenotypeColumn(gt), variantsBytes);
            put.addColumn(family, SampleIndexSchema.toGenotypeCountColumn(gt), Bytes.toBytes(variants.size()));
            put.addColumn(family, SampleIndexSchema.toFileIndexColumn(gt), fileMask);
        }


        return put;
    }

    public Put convert(byte[] rk, Map<String, SortedSet<VariantFileIndex>> gtsMap) {
        Put put = new Put(rk);

        for (Map.Entry<String, SortedSet<VariantFileIndex>> gtsEntry : gtsMap.entrySet()) {
            SortedSet<VariantFileIndex> variants = gtsEntry.getValue();
            String gt = gtsEntry.getKey();

            byte[] variantsBytes = variantConverter.toBytes(variants.stream()
                    .map(VariantFileIndex::getVariant)
                    .collect(Collectors.toList()));
            byte[] fileMask = new byte[variants.size()];
            int i = 0;
            for (VariantFileIndex variant : variants) {
                fileMask[i] = variant.getFileIndex();
                i++;
            }

            put.addColumn(family, SampleIndexSchema.toGenotypeColumn(gt), variantsBytes);
            put.addColumn(family, SampleIndexSchema.toGenotypeCountColumn(gt), Bytes.toBytes(variants.size()));
            put.addColumn(family, SampleIndexSchema.toFileIndexColumn(gt), fileMask);
        }

        return put;
    }

    public byte createFileIndexValue(int sampleIdx, Variant variant) {
        // Expecting only one study and only one file
        StudyEntry study = variant.getStudies().get(0);
        FileEntry file = study.getFiles().get(0);

        Integer dpIdx = study.getFormatPositions().get(VCFConstants.DEPTH_KEY);
        String dpStr;
        if (dpIdx != null) {
            dpStr = study.getSampleData(sampleIdx).get(dpIdx);
        } else {
            dpStr = null;
        }

        return createFileIndexValue(variant.getType(), file.getAttributes(), dpStr);
    }

    public byte createFileIndexValue(VariantType type, Map<String, String> fileAttributes, String dpStr) {
        byte b = 0;

        if (type.equals(VariantType.SNV) || type.equals(VariantType.SNP)) {
            b |= SNV_MASK;
        }

        String filter = fileAttributes.get(StudyEntry.FILTER);
        if (VCFConstants.PASSES_FILTERS_v4.equals(filter)) {
            b |= FILTER_PASS_MASK;
        }
        String qualStr = fileAttributes.get(StudyEntry.QUAL);
        double qual;
        try {
            if (qualStr == null || qualStr.isEmpty() || ".".equals(qualStr)) {
                qual = 0;
            } else {
                qual = Double.parseDouble(qualStr);
            }
        } catch (NumberFormatException e) {
            qual = 0;
        }
        if (qual > QUAL_THRESHOLD_40) {
            b |= QUAL_GT_20_MASK;
            b |= QUAL_GT_40_MASK;
        } else if (qual > QUAL_THRESHOLD_20) {
            b |= QUAL_GT_20_MASK;
        }

        if (dpStr == null) {
            dpStr = fileAttributes.get(VCFConstants.DEPTH_KEY);
        }
        if (StringUtils.isNumeric(dpStr)) {
            int dp = Integer.parseInt(dpStr);
            if (dp > DP_THRESHOLD_20) {
                b |= DP_GT_20_MASK;
            }
        }
        return b;
    }

    public static class VariantFileIndex implements Comparable<VariantFileIndex> {

        private final Variant variant;
        private final byte fileIndex;

        public VariantFileIndex(Variant variant, byte fileIndex) {
            this.variant = variant;
            this.fileIndex = fileIndex;
        }

        public Variant getVariant() {
            return variant;
        }

        public byte getFileIndex() {
            return fileIndex;
        }

        @Override
        public int compareTo(VariantFileIndex o) {
            return INTRA_CHROMOSOME_VARIANT_COMPARATOR.compare(variant, o.variant);
        }
    }
}
