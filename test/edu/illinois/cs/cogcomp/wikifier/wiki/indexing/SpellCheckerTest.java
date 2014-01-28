package edu.illinois.cs.cogcomp.wikifier.wiki.indexing;

import static edu.illinois.cs.cogcomp.wikifier.wiki.indexing.WikiTitleSpellChecker.*;

import java.io.IOException;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;
import org.junit.Test;

public class SpellCheckerTest {
    
    @Test
    public void test() throws IOException, ParseException{
        RAMDirectory dir = new RAMDirectory();
        IndexWriter writer = new IndexWriter(dir, config);
        String[] testTitles = new String[]{
                "Ruth_Bader_Ginsburg",
                "Joel_Grossman",
                "South_African_Broadcasting_Corporation",
                "Kamal_Kharazi",
                "Stereotactic_surgery",
                "Netzarim",
                "Take_O_A"
                
        };
        String[] queries = new String[]{
                "Ruth_Bater_Ginsburg",
                "Joe_O'Grossman",
                "South_African_Broadcasting_Corp.",
                "Kamal_Kharrazi",
                "Stereotaxic_surgery",
                "Nitsarim",
                "Take_O_B"
                
        };
        for(int i=0;i<testTitles.length;i++){
            Document d = spellDoc(testTitles[i]);
            writer.addDocument(d);
        }
        writer.close();
        IndexSearcher searcher = new IndexSearcher(DirectoryReader.open(dir));
        QueryParser parser = new QueryParser(Version.LUCENE_43, ShingleField, spellAnalyzer);
        for(int i = 0 ;i<queries.length;i++){
            String url = queries[i];
            Query query = parser.parse(QueryParser.escape(shingleString(url)));
            TopDocs docs = searcher.search(query, 5);
            for(ScoreDoc sd:docs.scoreDocs){
                if(sd.score<0.1)
                    continue;
                System.out.println(searcher.doc(sd.doc).get(URLField)+" with sim "+sd.score);
            }
            System.out.println("======= end of query "+ url + " =======");
        }
        
        
    }
}
