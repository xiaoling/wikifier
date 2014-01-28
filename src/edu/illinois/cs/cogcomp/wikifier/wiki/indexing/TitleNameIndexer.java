package edu.illinois.cs.cogcomp.wikifier.wiki.indexing;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javatools.parsers.NounGroup;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.shingle.ShingleAnalyzerWrapper;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.classic.QueryParser.Operator;
import org.apache.lucene.queryparser.complexPhrase.ComplexPhraseQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.spell.JaroWinklerDistance;
import org.apache.lucene.util.Version;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;
import edu.illinois.cs.cogcomp.wikifier.utils.Comparators;
import edu.illinois.cs.cogcomp.wikifier.utils.lucene.ASCIIEnglishAnalyzer;
import edu.illinois.cs.cogcomp.wikifier.utils.lucene.CustomEditDistance;
import edu.illinois.cs.cogcomp.wikifier.utils.lucene.Lucene;
import edu.illinois.cs.cogcomp.wikifier.utils.lucene.LuceneDocIterator;
import edu.illinois.cs.cogcomp.wikifier.utils.lucene.WikiURLAnalyzer;
import edu.illinois.cs.cogcomp.wikifier.utils.lucene.WikiURLAnalyzer.URLField;
import edu.illinois.cs.cogcomp.wikifier.wiki.access.ProtobufferBasedWikipediaAccess;
import edu.illinois.cs.cogcomp.wikifier.wiki.access.TitleNameNormalizer;
import edu.illinois.cs.cogcomp.wikifier.wiki.importing.DisambiguationPages;
import edu.illinois.cs.cogcomp.wikifier.wiki.importing.WikipediaRedirectExtractor;

public class TitleNameIndexer {

    public static final int URL_CACHE_SIZE = 10000;
    private static final int ShingleMaxSize = 2;
    
    public static enum Fields {
        TITLE, ARTICLE_ID, URL, REDIRECT
    }
    private static final Analyzer baseAnalyzer = new ASCIIEnglishAnalyzer(Version.LUCENE_43);
    private static final Analyzer ngramAnalyzer = new ShingleAnalyzerWrapper(baseAnalyzer, ShingleMaxSize);
    public static final WikiURLAnalyzer urlAnalyzer = new WikiURLAnalyzer();
    @SuppressWarnings("serial")
    public static final Analyzer wikiAnalyzer = new PerFieldAnalyzerWrapper(
            ngramAnalyzer, 
            new HashMap<String,Analyzer>(2){{
                put(Fields.URL.name(), urlAnalyzer);
            }});
    
    
    private static final IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_43, wikiAnalyzer);
    private static TitleNameIndexer instance = null;
    public static DisambiguationPages disambiguations = DisambiguationPages.load(GlobalParameters.paths.protobufferAccessDir);
    private IndexSearcher searcher;
    private WikiTitleSpellChecker checker = null;
    
    //Thread safe cache
    private static LoadingCache<String, String> normalizationCache = CacheBuilder.newBuilder()
            .maximumSize(URL_CACHE_SIZE)
            .build(new CacheLoader<String, String>(){
                @Override
                public String load(String url) throws Exception {
                    return getInstance().normalizeURL(url);
                }
            });

    public static final String[] WIKI_FUNCTION_PAGE_PREFIXES = new String[] { "Wikipedia:", "Template:", "Category:", ":Category:",
            "Help:", "Portal:", "File:", "Special:" };

    /**
     * This index uses fuzzy matching
     * 
     * @param dir
     * @throws IOException
     */
    private TitleNameIndexer(String dir) throws IOException {
        searcher = Lucene.searcher(dir);
        searcher.setSimilarity(new BM25Similarity());
//        checker = new WikiTitleSpellChecker();
    }
    
    public static String checkSpell(String query) {
        if (getInstance().checker != null)
            return getInstance().checker.spellCheck(query);
        return query;
    }

    /**
     * This function returns the same number of candidates as the the set global
     * candidates count
     */
    public ScoreDoc[] search(String surface, Operator opearator) {
        try {
            QueryParser parser = new ComplexPhraseQueryParser(Version.LUCENE_43, Fields.TITLE.name(), wikiAnalyzer);
            
            // We do not want too many results, therefore we force every query term to appear in results
            parser.setDefaultOperator(opearator);
            Query query = parser.parse(QueryParser.escape(surface));

            TopDocs docs = searcher.search(query, GlobalParameters.params.maxCandidatesToGenerateInitially);

            return docs.scoreDocs;

        } catch (Exception e) {

        }
        return new ScoreDoc[0];
    }
    

    /**
     * Useful when you know you only need 1 result
     * 
     * @param query
     * @return
     */
    private Document getTopDocument(Query query) {
        TopDocs docs;
        try {
            docs = searcher.search(query, 1);
            if (docs.totalHits > 0) {
                return searcher.doc(docs.scoreDocs[0].doc);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * public interfaces
     */


    /**
     * This gives us a way to get a real wikipedia-like behavior for search urls,
     * Minimal lower-casing and hyphen to space alterations are incorporated
     * @param url
     * @return destination link for the querying URL
     */
    public static String normalize(String url) {
        if(url==null)
            return null;
        try {
            return normalizationCache.get(url);
        } catch (Exception e) {
        }
        return null;
    }
    
    private String normalizeURL(String url) {
        url = url.replace(" ,", ",");
        url = url.replace(' ', '_');
        QueryParser parser = new QueryParser(Version.LUCENE_43,Fields.URL.name(),urlAnalyzer);
     
        Query query;
        try {
            query = parser.parse(QueryParser.escape(url));
        } catch (ParseException e1) {
            e1.printStackTrace();
            return null;
        }
        
        TopDocs docs;
        try {
            docs = searcher.search(query, 10);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        
        // Attempts to clean up mismatch caused by segmentation problems
        if(docs.scoreDocs.length == 0){
            String alteredUrl = null;
            if(url.endsWith("Inc") || url.endsWith("Corp"))
                alteredUrl = url + '.';
            else if(url.endsWith("'"))
                alteredUrl = alteredUrl + 's';
            if(alteredUrl!=null){
                return normalize(alteredUrl);
            }
        }
        
        Document doc = null;
        for(ScoreDoc scoreDoc : docs.scoreDocs){
            try {
                doc = getInstance().searcher.doc(scoreDoc.doc);
            } catch (IOException e) {
                continue;
            }
            String matchedURL = doc.get(Fields.URL.name());
            if(url.equals(matchedURL))
                break;
        }
        
        if (doc != null) {
            String redirect = doc.get(Fields.REDIRECT.name());
            if (redirect == null)
                return doc.get(Fields.URL.name());
            return redirect;
        }
        return null;
    }

    public static int getTid(String url) {
        Query query = new TermQuery(new Term(Fields.URL.name(), url.toLowerCase()));
        Document doc = getInstance().getTopDocument(query);
        if (doc != null) {
            return Integer.parseInt(doc.get(Fields.ARTICLE_ID.name()));
        }
        return -1;
    }

    public static String getTitle(int tid) {
        Query query = NumericRangeQuery.newIntRange(Fields.ARTICLE_ID.name(), tid, tid, true, true);
        Document doc = getInstance().getTopDocument(query);
        if (doc != null) {
            return doc.get(Fields.URL.name());
        }
        return null;
    }

    public static String getTitle(String tid) {
        int nid = Integer.parseInt(tid);
        return getTitle(nid);
    }
    
    static JaroWinklerDistance customeEditDistance = new JaroWinklerDistance();
    
    /**
     * Does not return unless not ambiguous
     * @param surface
     * @param matchDegree
     * @return
     */
    public static List<String> tokenLevelEditDistanceMatches(String surface,double matchDegree){
        List<String> candidates = searchTitles(surface, Operator.OR, -1f, false);
        
        List<String> retCandidates = Lists.newArrayList();
        Map<String,Double> simScore = Maps.newHashMap();
        String cleaned = surface.replace(' ', '_');
        for(String candidate:candidates){
            double similarity = customeEditDistance.getDistance(cleaned, candidate);
            simScore.put(candidate, similarity);
            // Only keeps those satisifying the matching requirement
            if(similarity>=matchDegree){
                // Among them only keep the shortest one
                retCandidates.add(candidate);
            }
        }
        Collections.sort(retCandidates,new Comparators.MapValueComparator(simScore));
        return Lists.reverse(retCandidates);
    }

    public static List<String> exactSearch(String surface){
        return searchTitles(surface, Operator.AND,-1f,true);
    }
    
    public static List<String> searchTitles(String surface,float minScore) {
        return searchTitles(surface, Operator.OR,minScore,false);
    }
    
    public static List<String> searchTitles(String surface) {
        return searchTitles(surface, Operator.OR,-1f,false);
    }
    
    /**
     * Only return results above certain lexical matching scores
     * For results with the same score we sort them by string length
     * @param surface
     * @param scoreLowerBound
     * @return
     */
    private static List<String> searchTitles(String surface,Operator operator,float minScore,boolean retainTopTierOnly) {

        List<String> titles = new ArrayList<String>();
        Set<String> results = new HashSet<String>();

        float prevScore = -1;
        for (ScoreDoc scoreDoc : getInstance().search(surface, operator)) {
            
            if(scoreDoc.score < minScore)
                continue;
            
            if(prevScore < 0)
                prevScore = scoreDoc.score;

            try {

                Document doc = getInstance().searcher.doc(scoreDoc.doc);

                // We only want normalized result
                String redirect = doc.get(Fields.REDIRECT.name());
                if (redirect == null) {
                    redirect = doc.get(Fields.URL.name());
                }
                
//              System.out.println(redirect + ':'+scoreDoc.score);
                if(results.contains(redirect))
                    continue;
                
                results.add(redirect);

                if (prevScore > scoreDoc.score && retainTopTierOnly) {
                    break;
                }
                
                titles.add(redirect);

            } catch (IOException e) {
                e.printStackTrace();
            }
            

        }

        return titles;
    }
    
    protected static class SortedQueue extends ArrayList<String>{
        
        /**
         * 
         */
        private static final long serialVersionUID = -6940933267944652263L;

        public void sortedDequeue(List<String> other){
            if(size()>1){
                Collections.sort(this, Comparators.shorterStringFirst);
            }
            other.addAll(this);
            clear();
        }
    }

    public static TitleNameIndexer getInstance() {
        if (instance == null) {
            try {
                instance = new TitleNameIndexer(GlobalParameters.paths.titleStringIndex);
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(0);
            }
        }
        return instance;
    }

    /**
     * Dumps the internal protobuffer representation into this new schema
     * 
     * @param dir
     * @throws Exception
     */
    @Deprecated
    public static void indexExistingTitles(String dir) throws Exception {
        ProtobufferBasedWikipediaAccess wiki = ProtobufferBasedWikipediaAccess.getDefaultInstance();
        Iterator<Integer> iterator = wiki.iterator();

        IndexWriter writer = Lucene.writer(dir, config);
        int count = 0;
        while (iterator.hasNext()) {
            int tid = iterator.next();
            String title = wiki.getBasicInfo(tid).getTitleSurfaceForm();

            String redirect = TitleNameNormalizer.normalize(title);
            redirect = title.equals(redirect) ? null : title;

            writer.addDocument(titleDoc(tid, title, redirect));
            count++;
            if (count % 1000 == 0)//
                System.out.println("Indexed " + count + " titles.");
        }
        writer.close();
    }

    /**
     * Adds new Wikipedia titles to the current index in a compatible fasion
     * 
     * @param dir
     * @throws IOException
     */
    private static void indexFullTitles(String titleIdFile, String outputDir, String redirectFile) throws IOException {
        IndexWriter writer = Lucene.writer(outputDir, config);
        LineIterator iterator = FileUtils.lineIterator(new File(titleIdFile));

        int count = 0;
        if (redirectFile == null) {
            System.out.println("[WARNING] indexing does not contain redirect infomation.");
        }
        Map<String, String> redirects = redirectFile != null ? loadRedirects(redirectFile) : new HashMap<String, String>();

        while (iterator.hasNext()) {

            String line = iterator.nextLine();
            if(line.length()<1)
                continue;
            count++;

            String[] fields = StringUtils.split(line, '\t');
            if (fields == null || fields.length != 2) {
                System.out.println("Bad fields: " + fields);
                continue;
            }

            String title = fields[1];
            if (!isValidArticleTitle(title)) {
                continue;
            }

            int tid = safeIntParse(fields[0]);
            if (tid < 0)
                continue;

            writer.addDocument(titleDoc(tid, title, redirects.get(title)));

            if (count % 10000 == 0)
                System.out.println("Indexed " + count + " titles.");
        }
        iterator.close();
        writer.close();
    }

    private static int safeIntParse(String s) {
        int tid = -1;
        try {
            tid = Integer.parseInt(s);
            if (tid < 0) {
                System.out.println("Bad tid: " + tid);
                return tid;
            }
        } catch (NumberFormatException e) {
            System.out.println("Bad tid: " + s);
        }
        return tid;
    }

    private static Map<String, String> loadRedirects(String file) throws IOException {
        System.out.println("Loading redirects...");
        Map<String, String> redirects = new HashMap<String, String>(10000000);
        LineIterator iterator = FileUtils.lineIterator(new File(file));
        while (iterator.hasNext()) {
            String[] line = StringUtils.split(iterator.nextLine(), '\t');
            redirects.put(line[0], line[1]);
        }
        iterator.close();
        return redirects;
    }

    protected static void cleanIndex(String indexDir, boolean checkingOnly) throws IOException {
        IndexReader reader;
        IndexSearcher searcher = null;
        IndexWriter writer = null;
        
        if(checkingOnly){
            reader  = Lucene.reader(indexDir);
            searcher = new IndexSearcher(reader);
        }else{
            writer = Lucene.writer(indexDir, config);
        }

        
        List<Query> queries = new ArrayList<Query>();
        for (String prefix : WIKI_FUNCTION_PAGE_PREFIXES) {
            Query prefixQuery = new PrefixQuery(new Term(Fields.URL.name(), prefix.toLowerCase()));
            queries.add(prefixQuery);
            if(searcher != null){
                for (ScoreDoc sc : searcher.search(prefixQuery, 10).scoreDocs) {
                    Document doc = searcher.doc(sc.doc);
                    System.out.println(doc.get(Fields.URL.name()));
                }
            }
            System.out.println(prefix + " clear");
        }
        if(!checkingOnly){
             writer.deleteDocuments(queries.toArray(new Query[0]));
             writer.commit();
             writer.forceMergeDeletes();
             writer.close();
        }

    }
    
    protected static void migrateIndex(String oldDir,String newDir) throws IOException{
        IndexReader reader = Lucene.reader(oldDir);
        IndexWriter writer = Lucene.writer(newDir, config);
        for(int i = 0;i<reader.maxDoc();i++){
            Document oldDoc = reader.document(i);
            String url = oldDoc.get(Fields.URL.name());
            if(!isValidArticleTitle(url))
                continue;
            String redirect = oldDoc.get(Fields.REDIRECT.name());
            int tid = Integer.parseInt(oldDoc.get(Fields.ARTICLE_ID.name()));
            writer.addDocument(titleDoc(tid, url, redirect));
            if(i%100000 == 0)
                System.out.println(i);
        }
        writer.close();
    }
    
    public static Iterator<Document> getDocumentIterator(){
        return new LuceneDocIterator(getInstance().searcher.getIndexReader());
    }

    /**
     * We do not need to index these titles
     * 
     * @param line
     * @return
     */
    private static boolean isValidArticleTitle(String line) {
        if (line == null)
            return false;
        for (String prefix : WIKI_FUNCTION_PAGE_PREFIXES) {
            if (line.startsWith(prefix))
                return false;
        }
        return true;
    }

    /**
     * The schema for this database
     * 
     * @param tid
     * @param url
     * @param redirect
     * @return
     */
    private static Document titleDoc(int tid, String url, String redirect) {
        Document doc = new Document();
        doc.add(new TextField(Fields.TITLE.name(), url, Field.Store.NO));
        doc.add(new URLField(Fields.URL.name(), url));
        doc.add(new IntField(Fields.ARTICLE_ID.name(), tid, Field.Store.YES));
        if (redirect != null) {
            doc.add(new StringField(Fields.REDIRECT.name(), redirect, Field.Store.YES));
        }
        return doc;
    }


    static void scoreTest() throws Exception{
        String tempDir = "/scratch/cheng88/index";
        try{
            indexFullTitles("/scratch/cheng88/title.txt", tempDir, null);
        }catch(Exception e){
            e.printStackTrace();
        }
        GlobalParameters.paths.titleStringIndex = tempDir;
    }

    /**
     * Public interface for extracting titleId and redirects and index them
     * using lucene. Use null extractionDir if you do not want to store the
     * extracted redirects and title id files
     * 
     * @param dumpFile
     * @param extractionFileDir
     * @param indexDir
     * @throws Exception
     */
    public static void indexFromDump(String dumpFile, String extractionFileDir, String indexDir) throws Exception {

        if (extractionFileDir == null)
            extractionFileDir = FileUtils.getTempDirectoryPath();

        String[] outputFiles = WikipediaRedirectExtractor.extract(dumpFile, extractionFileDir);

        System.out.println("Finished extraction from dump, starting indexing...");
        String redirectFile = outputFiles[0];
        String titleIdFile = outputFiles[1];

        long start = System.currentTimeMillis();
        indexFullTitles(titleIdFile, indexDir, redirectFile);
        System.out.println("Finished indexing in " + (System.currentTimeMillis() - start) / 60000 + " minutes");
        System.out.println("Index directory at " + indexDir);
    }
    
    

    public static void main(String[] args) throws Exception {
//        cleanIndex("../Data/WikiData/Index/TitleAndRedirects/", false);
//        migrateIndex("../Data/WikiData/Index/Titles/", "../Data/WikiData/Index/TitleAndRedirectsNew/");
//        System.out.println(TitleNameIndexer.normalize("Dan_Pfeiffer"));

//        scoreTest();
        // stressTest();
        // indexFullTitles(
        // "../Data/WikiDump/2013-05-28/extracted/enwiki-redirect.txt-title-id.txt",
        // "../Data/WikiData/Index/Titles/",
        // "../Data/WikiDump/2013-05-28/extracted/enwiki-clean-redirect.txt");

//        System.out.println(normalize("A.K. Anthony"));
//        System.out.println(normalize("A._K._Anthony"));
//        System.out.println(Index.getTokenSet("A.K. Anthony"));
//        System.out.println(TitleNameIndexer.isDisambiguationPage("John_Weir"));
//        System.out.println(TitleNameIndexer.isDisambiguationPage("City_college"));

//        System.out.println(TitleNameIndexer.searchTitles("Zimbawe Congres of Trade Unions"));
        System.out.println(TitleNameIndexer.searchTitles("Engerland"));
        System.out.println(TitleNameIndexer.searchTitles("The Sooner State"));
        System.out.println(checkSpell("Zimbawe Congres of Trade Unions"));
        System.exit(0);
        System.out.println(TitleNameIndexer.searchTitles("College of Medicine at the University of Florida"));
        System.out.println(new NounGroup("College of Medicine at the University of Florida").head());
        System.out.println(TitleNameIndexer.normalize("Oerebro"));
        System.out.println(TitleNameIndexer.normalize("Orebro"));
        System.out.println(new JaroWinklerDistance().getDistance("Abu Musab Abdulwadood", "Abu Musab Abdel Wadoud"));

//        System.out.println(GlobalParameters.NESimMetric.NESimilarity("PER#Abu Musab Abdulwadood", "PER#Abu Musab Abdel Wadoud"));
//        System.out.println(GlobalParameters.NESimMetric.NESimilarity("ORG#South African Broadcasting Corp.", "PER#South African Broadcasting Corporation"));
//        System.out.println(GlobalParameters.NESimMetric.NESimilarity("ORG#Queen Sirikit Natoinal Convention Center", "ORG#Queen Sirikit National Convention Center"));
//        System.out.println(GlobalParameters.NESimMetric.NESimilarity("ORG#Sirius Satellite Radio Inc.", "ORG#Sirius Satellite Radio"));
//        System.out.println(GlobalParameters.NESimMetric.NESimilarity("PER#Alexander Smith", "PER#Alex Smith"));
        System.out.println(searchTitles("Abu Musab Abdulwadood"));
        System.out.println(searchTitles("Birmingham School of Nursing"));
        System.out.println(searchTitles("National Petroleum Corporation"));
        System.out.println(checkSpell("Oerebro"));
        System.out.println(checkSpell("Ahmed Jassim"));
        System.out.println(checkSpell("A. K. Anthony"));
        System.out.println(normalize("A._K._Antony"));
        System.out.println(exactSearch("Portugal's Prime Minister"));
        String[] queries = new String[]{
                "Saudi Soccer Foundation",
//                "Mohammad Shariat-Madari",
//                "Souer",
//                "Ruth_Bater_Ginsburg",
//                "Joe_O'Grossman",
//                "South_African_Broadcasting_Corp.",
//                "Kamal_Kharrazi",
//                "Stereotaxic_surgery",
//                "Nitsarim",
//                "Milosevic",
//                "Irkotsik",
//                "Janus Capital",
//                "Aviation Consumer Protection Div",
                "Audelio Luis Cortes"
                
        };
//        
        for(String q:queries){
            System.out.println(checkSpell(q));
            System.out.println("======= end of query "+ q + " =======");
        }

//        System.out.println(normalize("Martian north pole"));
//        System.out.println(normalize("distress call"));
//        System.out.println(normalize("Home Depot Inc."));
//        System.out.println(normalize("Washington"));
//        System.out.println(normalize("Prime Minister of the United Kingdom"));
//        System.out.println(10240000000L >> 10);
////        System.out.println(normalize("Trondheim_Airport,_Værnes"));
////        System.out.println(normalize("LDL-Cholesterol"));
////        System.out.println(normalize("CAC-40"));
////        System.out.println(normalize("Seljuk empire"));
////        System.out.println(normalize("golden plover"));
////        System.out.println(normalize("James_Purdey_and_Sons"));
////        System.out.println(normalize("Junior Reserve Officers' Training Corps"));
////        System.out.println(normalize("Dow industrial"));
////        System.out.println(getTopUnambiguousPage("Christie's"));


        String[] qs = new String[]{
                "Foreign Intelligence Surveillance Court",
                "South African Broadcasting Corp.",
                "Queen Sirikit Natoinal Convention Center",
                "Sirius Satellite Radio Inc.",
                "Grand Marais, Minn",
                "Prime Minister Nouri",
                "Abu Musab Abdulwadood"
                ,"Abudllah","National Petroleum Corporation"
        };

        for(String q:qs){
            System.out.println(tokenLevelEditDistanceMatches(q, 0.95));
        }
        
        System.out.println(new CustomEditDistance().proximity("Abudllah", "Sirius_Satellite_Radio"));
        System.out.println(new CustomEditDistance().proximity("The Supreme Court in Florida", "Supreme Court of Florida"));
        
        
        
        System.out.println(new JaroWinklerDistance().getDistance("Grand Marais, Minn", "Grand Marais, Minnesota"));
        System.out.println(new JaroWinklerDistance().getDistance("Grand Marais", "Grand Marais, Minnesota"));
        System.out.println(new JaroWinklerDistance().getDistance("South African Broadcasting Corporation", "South African Broadcasting Corp."));
        System.out.println(new JaroWinklerDistance().getDistance("National Iraqi News Agency", "Iraqi News Agency"));
        System.out.println(new JaroWinklerDistance().getDistance("Queen Sirikit Natoinal Convention Center", "Queen Sirikit National Convention Center"));
        System.out.println(new JaroWinklerDistance().getDistance("Janus Capital", "Janus Capital Group"));
        System.out.println(new JaroWinklerDistance().getDistance("Prime Minister of China", "Prime Minister Nouri"));
        System.out.println(new JaroWinklerDistance().getDistance("Abudllah", "Abdullah"));
        System.out.println(new JaroWinklerDistance().getDistance(
                StringUtils.reverse("Prime Minister of China"), 
                StringUtils.reverse("Prime Minister Nouri")));
        System.out.println(new JaroWinklerDistance().getDistance("of China", "Nouri"));
        System.out.println(new JaroWinklerDistance().getDistance("Inc.", ""));
        System.out.println(new JaroWinklerDistance().getDistance("Minnesota", "Minn"));
        System.out.println(new JaroWinklerDistance().getDistance("Corporation", "Corp."));
        System.out.println(new JaroWinklerDistance().getDistance("Natoinal", "National"));
        
        System.out.println(
                tokenLevelEditDistanceMatches("Democrat Maria Catwell and Republican Senator Slate Gordon".replace(' ', '_')
                ,0.95));
        
//        System.out.println(normalize("Chengdu Research Base"));
        
//        System.out.println(searchTitles("LDL-Cholesterol"));
//        System.out.println(searchTitles("Cholesterol"));

        // cleanIndex("../Data/WikiData/Index/Titles/");
    }

    public static boolean isDisambiguationPage(String redirect) {
        return disambiguations.isDisambiguationPage(redirect);
    }

    public static List<String> getAlternativeNames(String url) {
        
        return Collections.emptyList();
    }

}
