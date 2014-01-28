package edu.illinois.cs.cogcomp.wikifier.wiki.access;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

import com.google.protobuf.InvalidProtocolBufferException;

import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.BasicTitleDataInfoProto;
import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.LexicalTitleDataInfoProto;
import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.SemanticTitleDataInfoProto;
import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.SurfaceFormSummaryProto;
import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.LRUCache;
import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.StringMap;
import edu.illinois.cs.cogcomp.wikifier.utils.lucene.Lucene;

/**
 * 
 * @author cheng88
 * @experimental
 */
public class MapDBAccess extends WikiAccess{
    
    private Map<Integer,byte[]> wikiDataMap;
    private Map<String,byte[]> surfaceMap;
    private Map<String,Integer> titleIdMap = new StringMap<>();
    private static final String WIKIDATA_MAP = "WIKIDATA_MAP";
    private static final String SURFACE_MAP = "SURFACE_MAP";
    private static final String DB_NAME = "DB_NAME";
    
    // Pools wikidata object in the same cache
    private LRUCache<Integer, WikiData> dataCache = new LRUCache<Integer, WikiData>(1000){
        
        protected WikiData loadValue(Integer tid) throws Exception{
            return new WikiData(wikiDataMap.get(tid));
        }
    };
    
    protected MapDBAccess() {}
    
    public MapDBAccess(String indexPath) {
        super(indexPath);
        DB db = DBMaker.newFileDB(new File(indexPath,DB_NAME)).readOnly().cacheLRUEnable().make();
        wikiDataMap = db.getTreeMap(WIKIDATA_MAP);
        for(byte[] data:wikiDataMap.values()){
            String titleName;
            try {
                WikiData wikiData = new WikiData(data);
                titleName = wikiData.getBasicProto().getTitleSurfaceForm();
                titleIdMap.put(titleName, wikiData.getBasicProto().getTitleId());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        surfaceMap = db.getTreeMap(SURFACE_MAP);
    }


    @Override
    public int getTitleIdOf(String titleName) {
        Integer id = titleIdMap.get(titleName);
        return id == null ? -1 : id;
    }

    @Override
    public SurfaceFormSummaryProto getSurfaceFormInfo(String surfaceForm) {
        byte[] raw = surfaceMap.get(surfaceForm);
        if (raw != null) {
            try {
                return SurfaceFormSummaryProto.parseFrom(raw);
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    @Override
    public LexicalTitleDataInfoProto getLexicalInfo(int titleID) {
        try {
            return dataCache.get(titleID).getLexProto();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public SemanticTitleDataInfoProto getSemanticInfo(int titleID) {
        try {
            return dataCache.get(titleID).getSemanticProto();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public BasicTitleDataInfoProto getBasicInfo(int titleID) {
        try {
            return dataCache.get(titleID).getBasicProto();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Iterator<Integer> iterator() {
        return wikiDataMap.keySet().iterator();
    }

    @Override
    public boolean isKnownUnlinkable(String surface) {
        return false;
    }

    @Override
    protected boolean surfaceExists(String surface) {
        return surfaceMap.containsKey(surface);
    }

    public static void main(String[] args) throws Exception {
        MapDBAccess access = new MapDBAccess();
        access.index("data/MapDBIndex");
    }
    
    protected void index(String indexPath) throws IOException{
        DB db = DBMaker.newFileDB(new File(indexPath,DB_NAME)).make();
        wikiDataMap = db.getTreeMap(WIKIDATA_MAP);
        IndexReader wikiDataReader = Lucene.reader(indexPath,"WikiAccessProtoBuffers");
        int totalTitles = wikiDataReader.numDocs();
        for (int docId = 0; docId < totalTitles; docId++) {
            if (docId % 50000 == 0)
                System.out.println(docId + " titles processed out of " + totalTitles);
            Document doc = wikiDataReader.document(docId);
            WikiData data = new WikiData(doc);
            wikiDataMap.put(data.getBasicProto().getTitleId(), data.serialize());
        }
        
        db.close();
    }
    
    protected static void test(){
        File dbFile = new File("cache/mapdbtest");
        Map<Integer, byte[]> wiki;
        long point = System.currentTimeMillis();
        DB db = DBMaker.newFileDB(dbFile).readOnly().make();

        // wiki = db.getTreeMap("WikiData");
        // for(int i = 0 ;i < 4000000;i++){
        // if(i%4000==0)
        // System.out.println(i);
        // wiki.put(i, new byte[20]);
        // }
        // System.out.println("Creation took " + ( System.currentTimeMillis() - point ) + " ms" );
        // point = System.currentTimeMillis();

        wiki = db.getTreeMap("WikiData");
        System.out.println(wiki.size());
        List<Integer> keys = new ArrayList<>(4000000);
        for (int i = 0; i < 4000000; i++) {
            keys.add(i);
        }
        Collections.shuffle(keys);
        point = System.currentTimeMillis();
        for (Integer key : keys) {
            byte[] contents = wiki.get(key);
            int a = 1;
        }
        System.out.println("Random access took "
                + (System.currentTimeMillis() - point) + " ms");
        // took 9000ms
        // db.commit();
        // db.compact();
        point = System.currentTimeMillis();
        for (java.util.Map.Entry<Integer, byte[]> e : wiki.entrySet()) {
            byte[] contents = e.getValue();
        }
        System.out.println("Sequential access took "
                + (System.currentTimeMillis() - point) + " ms");
        // took 300ms
        db.close();
    }
    static class WikiDataSerializer implements Serializer<WikiData>, Serializable{

        /**
         * 
         */
        private static final long serialVersionUID = 1L;

        @Override
        public void serialize(DataOutput out, WikiData value) throws IOException {
            value.writeTo(out);
        }

        @Override
        public WikiData deserialize(DataInput in, int available) throws IOException {
            return new WikiData(in);
        }
        
    }
   
}
