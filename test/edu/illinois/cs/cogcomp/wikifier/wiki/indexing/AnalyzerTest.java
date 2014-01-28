package edu.illinois.cs.cogcomp.wikifier.wiki.indexing;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.util.Version;
import org.junit.Test;

import edu.illinois.cs.cogcomp.wikifier.utils.lucene.ASCIIEnglishAnalyzer;
import edu.illinois.cs.cogcomp.wikifier.utils.lucene.WikiURLAnalyzer;


public class AnalyzerTest {
    
    static Analyzer asciiAnalyzer = new ASCIIEnglishAnalyzer(Version.LUCENE_43);
    static Analyzer URLAnalyzer = new WikiURLAnalyzer();

    @Test
    public void testASCIIHyphen() throws IOException {
        assertEquals(Arrays.asList("cac","40"),tokenize("CAC-40", asciiAnalyzer));
    }
    
    @Test
    public void testASCIIPossessive() throws IOException {
        assertEquals(Arrays.asList("christi"),tokenize("Christie's", asciiAnalyzer));
    }
    
    @Test
    public void testASCIIStem() throws IOException {
        assertEquals(Arrays.asList("ldl","cholesterol"),tokenize("LDL-Cholesterol", asciiAnalyzer));
        assertEquals(Arrays.asList("nation"),tokenize("Nationalization", asciiAnalyzer));
    }
    
    @Test
    public void testASCIIEncoding() throws IOException {
        assertEquals(Arrays.asList("slobodan","milosev"),tokenize("Slobodan Milošević", asciiAnalyzer));
        assertEquals(Arrays.asList("slobodan_milosevic"),tokenize("Slobodan Milošević", URLAnalyzer));
    }
    
    @Test
    public void testURL() throws IOException {
        assertEquals(Arrays.asList("ldl_cholesterol"),tokenize("LDL-Cholesterol", URLAnalyzer));

        assertEquals(Arrays.asList("cac_40"),tokenize("CAC 40", URLAnalyzer));
        assertEquals(Arrays.asList("cac_40"),tokenize("CAC-40", URLAnalyzer));
        assertEquals(Arrays.asList("al_ush_asd"),tokenize("al-Ush_asd", URLAnalyzer));
    }
    
    @Test
    public void testShingle() throws Exception {
        List<String> tokens = WikiTitleSpellChecker.shingle("Amhe");
        assertTrue(tokens.contains("Amh"));
        assertTrue(tokens.contains("mhe"));
        
    }
       
    
    private static List<String> tokenize(String text, Analyzer analyzer) throws IOException{
        List<String> tokens = new ArrayList<String>();
        TokenStream stream = analyzer.tokenStream(null, new StringReader(text));
        CharTermAttribute cattr = stream.addAttribute(CharTermAttribute.class);
        stream.reset();
        while (stream.incrementToken()) {
            tokens.add(cattr.toString());
        }
        stream.end();
        stream.close();
        return tokens;
    }

}
