package edu.illinois.cs.cogcomp.wikifier.wiki.indexing;

import java.io.IOException;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.Version;

import com.google.common.collect.Lists;

import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;
import edu.illinois.cs.cogcomp.wikifier.utils.lucene.ASCIIEnglishAnalyzer;
import edu.illinois.cs.cogcomp.wikifier.utils.lucene.CustomEditDistance;
import edu.illinois.cs.cogcomp.wikifier.utils.lucene.Lucene;
import edu.illinois.cs.cogcomp.wikifier.wiki.indexing.TitleNameIndexer.Fields;

/**
 * Not used currently, due to slow response
 * @author cheng88
 *
 */
public class WikiTitleSpellChecker {

//    SpellChecker checker;
//    IndexReader reader;
    // private static final SuggestWord[] NO_SUGGESTION = new SuggestWord[0];

    public static final int SPELL_CHECK_SUGGESTION_LIMIT = 5;
    public static final float SPELL_CHECK_SIM_LOWERBOUND = 0.7f;
    public static final String indexDir = "data/WikiData/Index/SpellCheckIndex/";//Parameters.pathTacSpellCheck;//
    static final String ShingleField = "SHINGLE";
    static final String URLField = "URL";
    static final Analyzer spellAnalyzer = new ASCIIEnglishAnalyzer(Version.LUCENE_43);
    public static final IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_43, spellAnalyzer);
    static CustomEditDistance customeEditDistance = new CustomEditDistance();
    
    IndexSearcher searcher;
    QueryParser parser = new QueryParser(Version.LUCENE_43, ShingleField, spellAnalyzer);

    // public LuceneSpellChecker(IndexReader reader){
    // // indexDir = NIOFSDirectory.open(new File(dir));
    // this.reader = reader;
    // checker = new DirectSpellChecker();
    // // checker.setDistance(new JaroWinklerDistance());
    // checker.setDistance(new LevensteinDistance());
    // }

    public WikiTitleSpellChecker() throws IOException {
        searcher = Lucene.searcher(indexDir);
        
        // checker.setStringDistance(new LevensteinDistance());
        // checker.setStringDistance(new JaroWinklerDistance());
        // checker.setComparator(new Comparator<SuggestWord>() {
        //
        // @Override
        // public int compare(SuggestWord o1, SuggestWord o2) {
        //
        // return Double.compare(o1.score, o2.score);
        // }
        // });
    }

//    static final EntityComparison ec = new EntityComparison();cd
    
    public String spellCheck(String url) {
        String answer = null;
        // Term term = new Term(Fields.URL.name(), text);
        try {
            Query query = parser.parse(QueryParser.escape(shingleString(url)));
            Query prefix = new PrefixQuery(new Term(URLField, url.substring(0,1)));
            BooleanQuery combined = new BooleanQuery();
            combined.add(query, Occur.MUST);
            combined.add(prefix, Occur.MUST);
            TopDocs docs = searcher.search(combined, 5);
            double maxSim = -1;
           
            for(ScoreDoc sd:docs.scoreDocs){
//                if(sd.score<SPELL_CHECK_SIM_LOWERBOUND)
//                    continue;
                String retrievedURL = searcher.doc(sd.doc).get(URLField);
                
                double currentSim = customeEditDistance.proximity(url.replace(' ', '_'), retrievedURL);
                
//                ec.NESimilarity(url, retrievedURL.replace(' ', '_'));//

                if (currentSim > maxSim && currentSim > SPELL_CHECK_SIM_LOWERBOUND){
                    answer = retrievedURL;
                    maxSim = currentSim;
                    System.out.println(retrievedURL+" with sim "+currentSim);
                }
            }
            
        } catch (Exception e) {
            
        }
        
        return TitleNameIndexer.normalize(answer);
    }

    public static final int shingleSize = 5;

    public static List<String> shingle(String text) {
        List<String> shingles = Lists.newArrayList();
        String s = text.replace('_', ' ');
        s = StringUtils.stripAccents(s);
        for (String token : StringUtils.split(s, ' ')) {
            if (token.length() == 0) {
                continue;
            }
            for (int len = 1; len <= shingleSize; len++) {
                for (int i = 0; i <= token.length() - len; i++) {
                    shingles.add(token.substring(i, i + len));
                }
            }
        }
        return shingles;
    }

    static String shingleString(String url) {
        return StringUtils.join(shingle(url), ' ');
    }

    public static Document spellDoc(String url) {
        Document d = new Document();
        d.add(new TextField(ShingleField, shingleString(url), Field.Store.NO));
        d.add(new StringField(URLField, url, Field.Store.YES));
        return d;
    }
    
    static void index() throws IOException{
        IndexReader reader = Lucene.reader(GlobalParameters.paths.titleStringIndex);
        IndexWriter writer = Lucene.writer(indexDir, config);
        for (int i = 0; i < reader.numDocs(); i++) {
            String url = reader.document(i).get(Fields.URL.name());
            writer.addDocument(spellDoc(url));
            if (i % 10000 == 0)
                System.out.println("Indexed " + i + " urls");
        }
        writer.close();
    }
    
    
}
