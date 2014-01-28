package edu.illinois.cs.cogcomp.wikifier.wiki.indexing.wikiRelations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.classic.QueryParser.Operator;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.Version;

import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;
import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.BasicTitleDataInfoProto;
import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.SemanticTitleDataInfoProto;
import edu.illinois.cs.cogcomp.wikifier.utils.TTLIterator;
import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.Triple;
import edu.illinois.cs.cogcomp.wikifier.utils.lucene.ASCIIEnglishAnalyzer;
import edu.illinois.cs.cogcomp.wikifier.utils.lucene.Lucene;
import edu.illinois.cs.cogcomp.wikifier.wiki.access.WikiAccess;
import edu.illinois.cs.cogcomp.wikifier.wiki.indexing.TitleNameIndexer;
import edu.illinois.cs.cogcomp.wikifier.wiki.indexing.wikiRelations.WikiRelationSearcher.Fields;

public class WikiRelationIndexUtils {
    
    public static final Version LUCENE_VERSION = Version.LUCENE_43;
    public static final Analyzer baseAnalyzer = new ASCIIEnglishAnalyzer(LUCENE_VERSION);
    public static final Analyzer keywordAnalyzer = new KeywordAnalyzer();
    public static final IndexWriterConfig config = new IndexWriterConfig(LUCENE_VERSION, baseAnalyzer);
    public static final String WIKI_LINK_RELATION = "WIKI_LINK_RELATION";
    
    static Document createRelationalDoc(BasicTitleDataInfoProto from, String relation, BasicTitleDataInfoProto to) {

        Document doc = new Document();

        // We retrieve the title id info from other places
        doc.add(new IntField(Fields.FROM_ID.name(), from.getTitleId(), Field.Store.YES));
        doc.add(new TextField(Fields.FROM.name(), from.getTitleSurfaceForm(), Field.Store.NO));

        doc.add(new StringField(Fields.RELATION.name(), relation, Field.Store.YES));

        doc.add(new IntField(Fields.TO_ID.name(), to.getTitleId(), Field.Store.YES));
        doc.add(new TextField(Fields.TO.name(), to.getTitleSurfaceForm(), Field.Store.NO));

        return doc;
    }
    
    /**
     * Converts Lucene document to our relation triple
     * @param scoreDoc
     * @return
     */
    static Triple readRelationalDoc(float score,Document doc){

        // 10000 queries takes 2 seconds => 0.2ms each query
        String fromTitle = getTitle(doc, Fields.FROM_ID);
        if (fromTitle == null || TitleNameIndexer.isDisambiguationPage(fromTitle))
            fromTitle = TitleNameIndexer.getTitle(doc.get(Fields.FROM_ID.name()));
        String predicate = doc.get(Fields.RELATION.name());
        String toTitle = getTitle(doc, Fields.TO_ID);
        if (toTitle == null || TitleNameIndexer.isDisambiguationPage(toTitle))
            toTitle = TitleNameIndexer.getTitle(doc.get(Fields.TO_ID.name()));
        return new Triple(fromTitle, predicate, toTitle, score);

    }
    
    /**
     * Gets a combined links info of a give title
     * 
     * @param wiki
     * @param tid
     * @return
     * @throws Exception
     */
    static List<BasicTitleDataInfoProto> getOutgoingTitleInfo(WikiAccess wiki, int tid) throws Exception {
        SemanticTitleDataInfoProto semanticData = wiki.getSemanticInfo(tid);
        List<BasicTitleDataInfoProto> links = new ArrayList<BasicTitleDataInfoProto>();

        for (int linkId : semanticData.getOutgoingLinksIdsList()) {
            links.add(wiki.getBasicInfo(linkId));
        }
        return links;
    }
    
    
    static <E extends Enum<E>> String getTitle(Document doc,E field){
        if(GlobalParameters.wikiAccess==null)
            return null;
        return GlobalParameters.wikiAccess.getTitle(doc.get(field.name()));
    }
    
    

    /**
     * Converts the current relations representation to triple representation
     * index
     * 
     * @param inputFile
     * @param dir
     * @throws IOException
     */
    static void indexWikiLink(String configPath, String dir) throws Exception {
        GlobalParameters.loadConfig(configPath);
        
        WikiAccess wiki = GlobalParameters.wikiAccess;
        IndexWriter writer = Lucene.writer(dir, config);
        int count = 0;
        int total = wiki.getTotalNumberOfWikipediaTitles();
        for (int tid : wiki) {

            BasicTitleDataInfoProto currentPage = wiki.getBasicInfo(tid);

            for (BasicTitleDataInfoProto otherPage : getOutgoingTitleInfo(wiki, tid)) {
                writer.addDocument(createRelationalDoc(currentPage, WIKI_LINK_RELATION, otherPage));
            }

            if (count++ % 100000 == 0){
                System.out.println("Indexed " + count + " titles out of " + total);
                System.out.println("Indexing " + currentPage.getTitleSurfaceForm() + "=> AnotherPage");
            }
        }
        writer.close();
    }

    static Query parse(String queryString, Fields field) {
        String escaped = QueryParser.escape(queryString);
        QueryParser parser = new QueryParser(LUCENE_VERSION, field.name(), baseAnalyzer);
        parser.setDefaultOperator(Operator.AND);
        try {
            return parser.parse("\"" + escaped + "\"");
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    static Query idQuery(int id, Fields field) {
        return NumericRangeQuery.newIntRange(field.name(), id, id, true, true);
    }
    
    /**
     * Indexes cleaned DBPedia file
     * @param configPath
     * @param TTLFile
     * @param dir
     * @throws Exception
     */
    static void indexInfoBox(String configPath, String TTLFile, String dir) throws Exception {
        if(GlobalParameters.wikiAccess==null)
            GlobalParameters.loadConfig(configPath);
        
        WikiAccess wiki = GlobalParameters.wikiAccess;
        IndexWriter writer = Lucene.writer(dir, config);
        TTLIterator triples = new TTLIterator(TTLFile);
        int count = 0;
        while (triples.hasNext()) {
            Triple triple = triples.next();
            int tid1 = wiki.getTitleIdFromExternalLink(triple.getArg1());
            int tid2 = wiki.getTitleIdFromExternalLink(triple.getArg2());
            if (tid1 >= 0 && tid2 >= 0) {
                writer.addDocument(createRelationalDoc(wiki.getBasicInfo(tid1), triple.getPred(), wiki.getBasicInfo(tid2)));
            }
            if (count % 100000 == 0){
                System.out.println("Indexed " + count + " titles out of 28 million");
                System.out.println("Indexing " + triple);
            }
            count++;
        }
        triples.close();
        writer.close();
    }
    
    
    static void createNewIndex() throws Exception {
        indexWikiLink("../Config/XiaoConfig", "../Data/WikiData/Index/WikiRelation/");
        indexInfoBox(
                "../Config/XiaoConfig",
                "../Data/DBpedia/cleanCombined.filtered.ttl",
                "../Data/WikiData/Index/WikiRelation/");
    }


}
