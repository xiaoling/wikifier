package edu.illinois.cs.cogcomp.wikifier.wiki.access;

import java.io.File;
import java.io.FileInputStream;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.apache.commons.collections.Transformer;
import org.apache.commons.collections.iterators.TransformIterator;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.lucene.document.Document;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.WriteConcern;

import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;
import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.BasicTitleDataInfoProto;
import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.LexicalTitleDataInfoProto;
import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.SemanticTitleDataInfoProto;
import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.SurfaceFormSummaryProto;
import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.WikiDataSummaryProto;
import edu.illinois.cs.cogcomp.wikifier.utils.lucene.Lucene;

public class MongoDBWikiAccess extends ProtobufferBasedWikipediaAccess{
    
    public static final int DOCUMENT_CACHE_SIZE = 10000;
    public static final String dbName = "WIKIFIER";
    public static final String surfaceFormCollectionName = "SurfaceFormCollection";
    public static final String wikiDataCollectionName = "WikiDataCollection";
    public static final String redirectCollectionName = "RedirectCollection";

    private LoadingCache<String, DBObject> documentByNameCache = CacheBuilder.newBuilder().maximumSize(DOCUMENT_CACHE_SIZE)
            .build(new CacheLoader<String, DBObject>() {
                @Override
                public DBObject load(String titleName) throws Exception {
                    DBObject doc = wikiDataCollection.findOne(new BasicDBObject(WikiDataFields.TitleName.name(), titleName));
                    // Cross cache
                    documentByIdCache.put((Integer) doc.get(WikiDataFields.TitleID.name()), doc);
                    return doc;
                }

            });

    private LoadingCache<Integer, DBObject> documentByIdCache = CacheBuilder.newBuilder().maximumSize(DOCUMENT_CACHE_SIZE)
            .build(new CacheLoader<Integer, DBObject>() {
                @Override
                public DBObject load(Integer titleID) throws Exception {
                    DBObject doc = wikiDataCollection.findOne(new BasicDBObject(WikiDataFields.TitleID.name(), titleID));
                    // Cross cache
                    documentByNameCache.put((String) doc.get(WikiDataFields.TitleName.name()), doc);
                    return doc;
                }

            });

    private LoadingCache<String, DBObject> surfaceByNameCache = CacheBuilder.newBuilder().maximumSize(DOCUMENT_CACHE_SIZE)
            .build(new CacheLoader<String, DBObject>() {
                @Override
                public DBObject load(String surfaceForm) throws Exception {
                    DBObject doc = surfaceFormCollection.findOne(new BasicDBObject(SurfaceFormFields.SurfaceForm.name(), surfaceForm));
                    //TODO: Aggressively cache top k titles in this surface forms?
                    return doc;
                }
            });
    
    
    MongoClient client = null;
    DB db;
    DBCollection surfaceFormCollection;
    DBCollection wikiDataCollection;
    DBCollection redirectCollection;
    
    public MongoDBWikiAccess() throws UnknownHostException{
        client = new MongoClient();
        db = client.getDB(dbName);
        surfaceFormCollection = db.getCollection(surfaceFormCollectionName);
        wikiDataCollection = db.getCollection(wikiDataCollectionName);
        redirectCollection = db.getCollection(redirectCollectionName);
//        titleIdEssentialData = new TIntObjectHashMap<EssentialTitleIdData>();
    }
    
    public MongoDBWikiAccess(String pathToWikiSummaryDataProtobufferSavedFile) throws Exception{
        this();
        if(pathToWikiSummaryDataProtobufferSavedFile!=null)
            wikiDataSummary = new WikipediaSummaryData(WikiDataSummaryProto.parseFrom(new FileInputStream(pathToWikiSummaryDataProtobufferSavedFile)));
    }
    
    public static DB getDefaultDB() throws UnknownHostException{
        return new MongoClient().getDB(dbName);
    }

    /**
     * @BeCarefulThisMethodErasesTheCurrentDatabase!!
     * @throws Exception
     */
    protected static void migrateDataFromLucene() throws Exception{
        
        MongoDBWikiAccess mongo = new MongoDBWikiAccess();
        mongo.client.setWriteConcern(WriteConcern.JOURNALED);
        mongo.surfaceFormReader = Lucene.reader("../Data/SurfaceFromsInfo");
        mongo.completeIndexReader = Lucene.reader("../Data/WikiAccessProtoBuffers");
        mongo.db.dropDatabase();
        int batch = 10000;
        List<DBObject> writeBulkCache = Lists.newArrayListWithCapacity(batch);
        boolean indexSurface = false;
        
        if(indexSurface){
            DBCollection surfaceFormCollection = mongo.db.getCollection(surfaceFormCollectionName);
            surfaceFormCollection.ensureIndex(new BasicDBObject("SurfaceForm",1),null,true);
            
            int totalSurfaceForms = mongo.surfaceFormReader.numDocs();
            for (int surfaceId = 0; surfaceId < totalSurfaceForms; surfaceId++) {
                
                Document doc = mongo.surfaceFormReader.document(surfaceId);
                String surfaceString = doc.get("SurfaceForm");
                byte[] protoBytes = doc.getBinaryValue("SurfaceFormSummaryProto").bytes;
                
                writeBulkCache.add(new BasicDBObject("SurfaceForm",surfaceString)
                                    .append("SurfaceFormSummaryProto", protoBytes));
                
                if (surfaceId % batch == 0 || surfaceId == totalSurfaceForms -1){
                    System.out.println(surfaceId + " surface processed out of " + totalSurfaceForms);
                    surfaceFormCollection.insert(writeBulkCache);
                    writeBulkCache.clear();
    //                long start = System.currentTimeMillis();
    //                DBObject result = surfaceFormCollection.find(new BasicDBObject("_id",0)).next();
    //                System.out.println("Used "+ (System.currentTimeMillis()-start) + " ms");
    //                SurfaceFormSummaryProto proto = SurfaceFormSummaryProto.parseFrom((byte[])result.get("SurfaceFormSummaryProto"));
    //                System.out.println(proto.getTotalAppearanceCount());
    //                System.out.println("idClass "+result.get("_id").getClass());
    //                System.exit(0);
                }
            }
        }
        DBCollection wikiDataCollection = mongo.db.getCollection(wikiDataCollectionName);
        wikiDataCollection.ensureIndex(new BasicDBObject("TitleID",1),null,true);
        wikiDataCollection.ensureIndex(new BasicDBObject("TitleName",1),null,true);
        
        int totalTitles = mongo.completeIndexReader.numDocs();
        writeBulkCache.clear();
        
        for (int docId = 0; docId < totalTitles; docId++) {

            Document doc = mongo.completeIndexReader.document(docId);
            
            int titleId = Integer.parseInt(doc.get("TitleID"));
            byte[] basicInfoBytes = doc.getBinaryValue("BasicInfo").bytes;
            byte[] semanticInfoBytes = doc.getBinaryValue("SemanticInfo").bytes;
            byte[] lexicalInfoBytes = doc.getBinaryValue("LexicalInfo").bytes;

            BasicTitleDataInfoProto currentTitleData = BasicTitleDataInfoProto.parseFrom(basicInfoBytes);
            String titleName = currentTitleData.getTitleSurfaceForm();
            
            writeBulkCache.add(new BasicDBObject("TitleName",titleName).
                                        append("TitleID",titleId).
                                        append("BasicInfo",basicInfoBytes).
                                        append("SemanticInfo",semanticInfoBytes).
                                        append("LexicalInfo",lexicalInfoBytes)
                                        );

        }
        System.out.println(totalTitles + " titles processed");
        wikiDataCollection.insert(writeBulkCache);
        writeBulkCache.clear();
        
    }
    
    public static void main(String[] args) throws Exception{
        storeRedirects();
    }
    
    protected static void storeRedirects() throws Exception{
        MongoDBWikiAccess access = new MongoDBWikiAccess();

        LineIterator iterator = FileUtils.lineIterator(new File("../Data/WikiDump/2013-05-28/extracted/enwiki-clean-redirect.txt"));
        List<DBObject> cache = Lists.newArrayList();
        
        while(iterator.hasNext()){
            String[] parts = iterator.nextLine().split("\t");
            DBObject redirect = new BasicDBObject(RedirectFields.From.name(),parts[0])
                            .append(RedirectFields.To.name(), parts[1]);
            cache.add(redirect);
        }
        System.out.println("Bulk inserting to db");
        access.redirectCollection.ensureIndex(RedirectFields.From.name());
        access.redirectCollection.insert(cache);
    }
    
    public boolean surfaceExists(String surfaceForm){
        return getSurfaceFormInfo(surfaceForm)!=null;
    }

    public int getTitleIdOf(String unnormalizedTitleName){
        try {
            return (Integer)documentByNameCache.get(unnormalizedTitleName).get(WikiDataFields.TitleID.name());
        } catch (Exception e) {
        }
        return -1;
    }

    @Override
    public SurfaceFormSummaryProto getSurfaceFormInfo(String surfaceForm){
        try {
            return SurfaceFormSummaryProto.parseFrom(getBinary(surfaceByNameCache, SurfaceFormFields.SurfaceFormSummaryProto.name(), surfaceForm));
        } catch (Exception e) {
        }
        return null;
    }

    @Override
    public LexicalTitleDataInfoProto getLexicalInfo(int titleID){
        try {
            return LexicalTitleDataInfoProto.parseFrom(getBinary(documentByIdCache, WikiDataFields.LexicalInfo.name(), titleID));
        } catch (Exception e) {
        }
        return null;
    }

    public SemanticTitleDataInfoProto getSemanticInfo(int titleID){
        try {
            return SemanticTitleDataInfoProto.parseFrom(getBinary(documentByIdCache, WikiDataFields.SemanticInfo.name(), titleID));
        } catch (Exception e) {
        }
        return null;
    }

    public BasicTitleDataInfoProto getBasicInfo(int titleID){
        try {
            return BasicTitleDataInfoProto.parseFrom(getBinary(documentByIdCache, WikiDataFields.BasicInfo.name(), titleID));
        } catch (Exception e) {
        }
        return null;
    }

    public int getNumberIngoingLinks(int titleId){
        BasicTitleDataInfoProto titleInfo = getBasicInfo(titleId);
        if(titleInfo!=null)
            return titleInfo.getNumberOfIngoingLinks();
        return 0;
    }

    public int getNumberOutgoingLinks(int titleId){
        BasicTitleDataInfoProto titleInfo = getBasicInfo(titleId);
        if(titleInfo!=null)
            return titleInfo.getNumberOfOugoingLinks();
        return 0;
    }

    @SuppressWarnings("unchecked")
    public Iterator<Integer> iterator(){
        return new TransformIterator(wikiDataCollection.find().iterator(), new Transformer(){
            @Override
            public Object transform(Object arg0) {
                DBObject doc = (DBObject)arg0;
                return doc.get(WikiDataFields.TitleID.name());
            }
            
        });
    }

    @Override
    public boolean isKnownUnlinkable(String surface){
        SurfaceFormSummaryProto proto = getSurfaceFormInfo(surface);
        if(proto!=null){
            double linkability = ((double)proto.getLinkedAppearanceCount())/proto.getTotalAppearanceCount();
            return linkability <= GlobalParameters.params.minLinkability;
        }
        return false;
    }
    
    private static <K> byte[] getBinary(LoadingCache<K,DBObject> cache,String fieldName,K key){
        try {
            return (byte[]) cache.get(key).get(fieldName);
        } catch (ExecutionException e) {
        }
        return null;
    }

}
