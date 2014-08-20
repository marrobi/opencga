package org.opencb.opencga.storage.alignment.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opencb.biodata.formats.alignment.io.AlignmentDataReader;
import org.opencb.biodata.models.alignment.Alignment;
import org.opencb.biodata.models.alignment.AlignmentHeader;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Created with IntelliJ IDEA.
 * User: jacobo
 * Date: 18/06/14
 * Time: 13:03
 * To change this template use File | Settings | File Templates.
 */
public class AlignmentJsonDataReader implements AlignmentDataReader<Alignment>{

    private final String alignmentFilename;
    private final String headerFilename;
    private final boolean gzip;
    private final JsonFactory factory;
    private final ObjectMapper jsonObjectMapper;
    private JsonParser alignmentsParser;
    private JsonParser headerParser;

    private InputStream alignmentsStream;
    private InputStream headerStream;

    private AlignmentHeader alignmentHeader;

    
    public AlignmentJsonDataReader(String baseFilename , boolean gzip) {
        this(baseFilename+(gzip?".alignments.json.gz":".alignments.json"), baseFilename+(gzip?".header.json.gz":".header.json"));
    }
    

    public AlignmentJsonDataReader(String alignmentFilename, String headerFilename) {
        this.alignmentFilename = alignmentFilename;
        this.headerFilename = headerFilename;
        this.gzip = alignmentFilename.endsWith(".gz");
        this.factory = new JsonFactory();
        this.jsonObjectMapper = new ObjectMapper(this.factory);
    }

    @Override
    public AlignmentHeader getHeader() {
        return alignmentHeader;
    }

    @Override
    public boolean open() {

        try {
            
            alignmentsStream = new FileInputStream(alignmentFilename);
            headerStream = new FileInputStream(headerFilename);

            if (gzip) {
                alignmentsStream = new GZIPInputStream(alignmentsStream);
                headerStream = new GZIPInputStream(headerStream);
            }

        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            return false;
        }

        return true;
    }

    @Override
    public boolean pre() {
        jsonObjectMapper.addMixInAnnotations(Alignment.AlignmentDifference.class, AlignmentDifferenceJsonMixin.class);
        jsonObjectMapper.addMixInAnnotations(Alignment.class, AlignmentJsonMixin.class);
        try {
            alignmentsParser = factory.createParser(alignmentsStream);
            headerParser = factory.createParser(headerStream);
            
            alignmentHeader = headerParser.readValueAs(AlignmentHeader.class);
        } catch (IOException e) {
            e.printStackTrace();
            close();
            return false;
        }

        return true;
    }

    @Override
    public List<Alignment> read() {
        Alignment elem = readElem();
        return elem != null? Arrays.asList(elem) : null;
    }

    public Alignment readElem() {
        try {
            if (alignmentsParser.nextToken() != null) {
                Alignment alignment = alignmentsParser.readValueAs(Alignment.class);
                return alignment;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public List<Alignment> read(int batchSize) {
        //List<Alignment> listRecords = new ArrayList<>(batchSize);
        List<Alignment> listRecords = new LinkedList<>();
        Alignment elem;
        for (int i = 0; i < batchSize ; i++) {
            elem = readElem();
            if(elem == null){
                break;
            }
            listRecords.add(elem);
        }
        return listRecords;
    }
    @Override
    public boolean post() {
        return true;
    }

    @Override
    public boolean close() {
        try {
            alignmentsParser.close();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

        return true;
    }
}
