package edu.illinois.cs.cogcomp.wikifier.wiki.access;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Iterator;

import org.apache.commons.lang.StringUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;

import com.google.protobuf.InvalidProtocolBufferException;

import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;
import edu.illinois.cs.cogcomp.wikifier.common.GlobalPaths;
import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.BasicTitleDataInfoProto;
import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.LexicalTitleDataInfoProto;
import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.SemanticTitleDataInfoProto;
import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.SurfaceFormSummaryProto;
import edu.illinois.cs.cogcomp.wikifier.utils.IntIterator;
import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.LRUCache;
import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.StringMap;
import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.StringMap.StringSet;
import edu.illinois.cs.cogcomp.wikifier.utils.lucene.Lucene;
import gnu.trove.map.hash.TIntIntHashMap;

public class ProtobufferBasedWikipediaAccess extends WikiAccess {

    // private static boolean debug = true;

    /*
     * Note that the two fields below will be re-built for each problem!
     */

    private LRUCache<Integer, Document> protoCache = new LRUCache<Integer, Document>(MAX_NUM_ARTICLES_TO_CACHE) {
        @Override
        public Document loadValue(Integer titleID) throws Exception {
            Document doc = completeIndexReader.document(tid2DocId.get(titleID));
            return doc;
        }
    };

    private LRUCache<String, SurfaceFormSummaryProto> surfaceFormCache = new LRUCache<String, SurfaceFormSummaryProto>(
            MAX_NUM_SURFACE_FORMS_TO_CACHE) {
        @Override
        public SurfaceFormSummaryProto loadValue(String surface) throws Exception {
            int docid = surfaceFromToLuceneIdMap.get(surface);
            return surfaceProtoFromDoc(surfaceFormReader.document(docid));
        }
    };

    /*
     * This is the stuff that makes it friendly to work with the main Wikipedia index
     */
    // private TObjectIntHashMap<String> exactNormalizedTitleNameToTitleIdMatcher = null;
    protected StringMap<Integer> wikiTitleToIdMap = null;

    protected TIntIntHashMap tid2DocId = null;

    private StringMap<Integer> surfaceFromToLuceneIdMap = null;

    protected StringSet unlinkableSurfaces = null;

    protected IndexReader completeIndexReader = null;
    protected IndexReader surfaceFormReader = null;
    
    protected ProtobufferBasedWikipediaAccess(){
        super("data/Lucene4Index/");
    }

    public ProtobufferBasedWikipediaAccess(String indexPath) throws Exception {
        super(indexPath);
        loadTitleIndex(Paths.get(indexPath,"WikiAccessProtoBuffers").toString());
        loadSurfaceForms(Paths.get(indexPath,"SurfaceFormsInfo").toString());
    }

    private void loadTitleIndex(String pathToMainWikiDataProtobuffersIndex) throws Exception {
        System.out.println("Opening the index for the complete index interface");
        tid2DocId = new TIntIntHashMap();
        completeIndexReader = Lucene.reader(pathToMainWikiDataProtobuffersIndex);
        System.out.println("Prefetching the basic information about the wikipedia articles");

        int totalTitles = completeIndexReader.numDocs();

        wikiTitleToIdMap = new StringMap<Integer>(TOTAL_WIKIPEDIA_TITLE_ESTIMATE);
        
        for (int docId = 0; docId < totalTitles; docId++) {
            if (docId % 50000 == 0)
                System.out.println(docId + " titles processed out of " + totalTitles);

            Document doc = completeIndexReader.document(docId,WikiDataFields.INITIAL_FIELD_SET);

            BasicTitleDataInfoProto currentTitleData = BasicTitleDataInfoProto
                    .parseFrom(doc.getBinaryValue(WikiDataFields.BasicInfo.name()).bytes);
            
            int titleId = currentTitleData.getTitleId();

            // Database sanity check
//            if (titleId != currentTitleData.getTitleId())
//                throw new Exception("Data base title id mismatch for " + currentTitleData + " with indexed titleID as " + titleId);
            wikiTitleToIdMap.put(currentTitleData.getTitleSurfaceForm(), titleId);

            // Redirect pages will be masked away by normalization
            tid2DocId.put(titleId, docId);
        }
        // Remapping titles to their redirected ids
        TitleNameNormalizer.normalize(wikiTitleToIdMap);

        System.out.printf("Actual capacities:\nTitleEssentialData:%d\n", tid2DocId.capacity());
        System.out.printf("Loaded %d nonNormalizedTitles\n", wikiTitleToIdMap.size());
        System.out.printf("Done prefetching the basic data about %d Wikipedia articles\n", totalTitles);
    }

    private void loadSurfaceForms(String pathToSurfaceFormsDataIndex) throws CorruptIndexException, IOException {
        System.out.println("Loading information about surface form to title id mappings");
        surfaceFormReader = Lucene.reader(pathToSurfaceFormsDataIndex);
        // System.out.println(surfaceFormReader.getFieldNames(IndexReader.FieldOption.ALL));

        int addedSurfaceForms = 0;
        int totalSurfaceForms = surfaceFormReader.numDocs();
        surfaceFromToLuceneIdMap = new StringMap<Integer>(4500000);
        unlinkableSurfaces = new StringSet(10);
        
        for (int docid = 0; docid < totalSurfaceForms; docid++) {
            Document doc = surfaceFormReader.document(docid);
            String surfaceForm = doc.get(SurfaceFormFields.SurfaceForm.name());
            SurfaceFormSummaryProto surfaceFormData = surfaceProtoFromDoc(doc);

            if (isUnlinkable(surfaceForm, surfaceFormData)) {
                unlinkableSurfaces.add(surfaceForm);
            } else {
                addedSurfaceForms++;
                surfaceFromToLuceneIdMap.put(surfaceForm, docid);
                if (docid % 100000 == 0)
                    System.out.println(addedSurfaceForms + " surface forms is linkable out of " + docid + ". There are "
                            + totalSurfaceForms + "  surface forms total; last surface form read: " + surfaceForm);

            }
        }
        System.out.println("There are " + unlinkableSurfaces.size() + " unlinkable surface forms");
        System.out.printf("Actual capacities:\nSurfaceFormData:%d\n", surfaceFromToLuceneIdMap.capacity());
        System.out.println("Done loading information about surface form to title id mappings");

    }

    @Override
    public boolean isKnownUnlinkable(String surface) {
        return StringUtils.isEmpty(surface) || unlinkableSurfaces.contains(surface);
    }

    protected boolean surfaceExists(String surfaceForm) {
        return surfaceFromToLuceneIdMap.containsKey(surfaceForm);
    }

    /*
     * (non-Javadoc)
     * 
     * @see CommonSenseWikifier.WikiDataAccess.RemoteWikipediaAccess#getSurfaceFormsIterator()
     */
    public Iterator<String> getSurfaceFormsIterator() {
        return surfaceFromToLuceneIdMap.keySet().iterator();
    }

    /*
     * returns -1 if the title name is not in the index
     */
    /*
     * (non-Javadoc)
     * 
     * @see
     * CommonSenseWikifier.WikiDataAccess.RemoteWikipediaAccess#getTitleIdFromUnnormalizedTitleName
     * (java.lang.String)
     */
    @Override
    public int getTitleIdOf(String unnormalizedTitleName) {
        if (wikiTitleToIdMap.containsKey(unnormalizedTitleName))
            return wikiTitleToIdMap.get(unnormalizedTitleName);
        return -1;
    }

    @Override
    public SurfaceFormSummaryProto getSurfaceFormInfo(String surfaceForm) {
        return surfaceFormCache.get(surfaceForm);
    }

    @Override
    public LexicalTitleDataInfoProto getLexicalInfo(int titleID) {
        try {
            return LexicalTitleDataInfoProto.parseFrom(getBinaryField(protoCache, WikiDataFields.LexicalInfo.name(), titleID));
        } catch (Exception e) {
        }
        return null;
    }

    @Override
    public SemanticTitleDataInfoProto getSemanticInfo(int titleID) {
        try {
            return SemanticTitleDataInfoProto.parseFrom(getBinaryField(protoCache, WikiDataFields.SemanticInfo.name(), titleID));
        } catch (Exception e) {
        }
        return null;
    }

    @Override
    public BasicTitleDataInfoProto getBasicInfo(int titleID) {
        try {
            return BasicTitleDataInfoProto.parseFrom(getBinaryField(protoCache, WikiDataFields.BasicInfo.name(), titleID));
        } catch (Exception e) {
        }
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see CommonSenseWikifier.WikiDataAccess.RemoteWikipediaAccess#getTitleIdIterator()
     */
    public Iterator<Integer> iterator() {
        return new IntIterator(tid2DocId.keySet().iterator());
    }

    public static void defaultSetup() throws Exception {
        GlobalParameters.paths.compressedRedirects = "data/WikiData/Redirects/2013-05-28.redirect";
        GlobalParameters.wikiAccess = getDefaultInstance();
    }

    public static ProtobufferBasedWikipediaAccess getDefaultInstance() throws Exception {
        return new ProtobufferBasedWikipediaAccess(GlobalPaths.defaultInstance().protobufferAccessDir);
    }

    private static <K> byte[] getBinaryField(LRUCache<K, Document> cache, String fieldName, K key) {
        return cache.get(key).getBinaryValue(fieldName).bytes;
    }

    private static SurfaceFormSummaryProto surfaceProtoFromDoc(Document doc) {
        try {
            return SurfaceFormSummaryProto.parseFrom(doc.getBinaryValue(SurfaceFormFields.SurfaceFormSummaryProto.name()).bytes);
        } catch (InvalidProtocolBufferException e) {
        }
        return null;
    }

    public static void main(String[] args) throws Exception {
//         migrateIndex("../Data/WikiAccessProtoBuffers","../Data/SurfaceFromsInfo");
        defaultSetup();

    }

}
