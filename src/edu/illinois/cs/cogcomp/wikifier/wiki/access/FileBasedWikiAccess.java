package edu.illinois.cs.cogcomp.wikifier.wiki.access;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.collect.Iterators;
import com.google.protobuf.InvalidProtocolBufferException;

import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.BasicTitleDataInfoProto;
import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.LexicalTitleDataInfoProto;
import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.SemanticTitleDataInfoProto;
import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.SurfaceFormSummaryProto;
import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.DiskArray;
import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.DiskArray.Mode;
import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.LRUCache;
import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.StringMap;
import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.StringMap.StringSet;
import gnu.trove.map.hash.TIntIntHashMap;

public class FileBasedWikiAccess extends WikiAccess {

    private DiskArray wikiArray;
    private DiskArray surfaceArray;
    private static final String SURFACE_SUBDIR = "Surfaces";
    private static final String WIKIDATA_SUBDIR = "WikiData";
    private TIntIntHashMap tid2indexMap = new TIntIntHashMap();
    private StringMap<Integer> surfaceIndex = new StringMap<>(4500000);
    private StringMap<Integer> titleIndex = new StringMap<>(TOTAL_WIKIPEDIA_TITLE_ESTIMATE);
    private StringSet unlinkables = new StringSet(10);
            
    static Logger logger = LoggerFactory.getLogger(FileBasedWikiAccess.class);
    
    private LRUCache<Integer, WikiData> protoCache = new LRUCache<Integer, WikiData>(MAX_NUM_ARTICLES_TO_CACHE) {
        @Override
        public WikiData loadValue(Integer titleID) throws Exception {
            WikiData doc = new WikiData(wikiArray.get(tid2indexMap.get(titleID)));
            return doc;
        }
    };

    private LRUCache<String, SurfaceFormSummaryProto> surfaceFormCache = new LRUCache<String, SurfaceFormSummaryProto>(
            MAX_NUM_SURFACE_FORMS_TO_CACHE) {
        @Override
        public SurfaceFormSummaryProto loadValue(String surface) throws Exception {
            int docid = surfaceIndex.get(surface);
            return new SurfaceData(surfaceArray.get(docid)).getSurfaceProto();
        }
    };
    
    public FileBasedWikiAccess(String wikiDataSummaryFile,String indexDir) throws FileNotFoundException, IOException {
        super(wikiDataSummaryFile);
        // Loads titles
        wikiArray = new DiskArray(new File(indexDir,WIKIDATA_SUBDIR), Mode.MMAP);
        int index = 0;
        for(byte[] data:wikiArray){
            WikiData wikiData = new WikiData(data);
            BasicTitleDataInfoProto titleInfo = wikiData.getBasicProto();
            int tid = titleInfo.getTitleId();
            titleIndex.put(titleInfo.getTitleSurfaceForm(), tid);
            tid2indexMap.put(tid, index);
            index++;
            if(index%100000==0)
                logger.info(index + "th title loaded. i.e. "+titleInfo.getTitleSurfaceForm());
        }
        
        // Loads surfaces
        surfaceArray = new DiskArray(new File(indexDir,SURFACE_SUBDIR), Mode.MMAP);
        index = 0;
        for(byte[] data:surfaceArray){
            SurfaceData surfaceData = new SurfaceData(data);
            String surface = surfaceData.getSurface();
            if(isUnlinkable(surface, surfaceData.getSurfaceProto()))
                unlinkables.add(surface);
            else
                surfaceIndex.put(surface, index);
            index++;
            if(index%100000==0)
                logger.info(index + "th surface loaded. i.e. surface");
        }
    }

    @Override
    public int getTitleIdOf(String titleName) {
        Integer tid = titleIndex.get(titleName);
        return tid == null ? -1 : tid;
    }
    
    @Override
    public SurfaceFormSummaryProto getSurfaceFormInfo(String surfaceForm) {
        try {
            return surfaceFormCache.get(surfaceForm);
        } catch (Exception e) {
        }
        return null;
    }

    @Override
    public LexicalTitleDataInfoProto getLexicalInfo(int titleID){
        try {
            return protoCache.get(titleID).getLexProto();
        } catch (Exception e) {
        }
        return null;
    }

    @Override
    public SemanticTitleDataInfoProto getSemanticInfo(int titleID){
        try {
            return protoCache.get(titleID).getSemanticProto();
        } catch (Exception e) {
        }
        return null;
    }

    @Override
    public BasicTitleDataInfoProto getBasicInfo(int titleID){
        try {
            return protoCache.get(titleID).getBasicProto();
        } catch (Exception e) {
        }
        return null;
    }

    @Override
    public Iterator<Integer> iterator() {
        Iterators.transform(wikiArray.iterator(), new Function<byte[],Integer>(){
            @Override
            public Integer apply(byte[] data){
                BasicTitleDataInfoProto proto;
                try {
                    proto = BasicTitleDataInfoProto.parseFrom(data);
                } catch (InvalidProtocolBufferException e) {
                    e.printStackTrace();
                    System.exit(0);
                    return null;
                }
                return proto.getTitleId();
            }
        });
        return null;
    }

    @Override
    public boolean isKnownUnlinkable(String surface) {
        return unlinkables.contains(surface);
    }

    @Override
    protected boolean surfaceExists(String surface) {
        return surfaceIndex.contains(surface);
    }

    public static void main(String[] args) throws FileNotFoundException, IOException{
//        FileBasedWikiAccess access = new FileBasedWikiAccess("../Data/WikiSummary.proto.save","../Data/ArrayIndex/");
    }

}
