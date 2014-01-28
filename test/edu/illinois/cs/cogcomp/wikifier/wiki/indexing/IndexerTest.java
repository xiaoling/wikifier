package edu.illinois.cs.cogcomp.wikifier.wiki.indexing;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.junit.Test;

import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;
import edu.illinois.cs.cogcomp.wikifier.wiki.indexing.wikiRelations.WikiRelationIndexUtils;


public class IndexerTest {

    private static final String testString = "Saban leaves Dolphins for Alabama job"
            + "Coach ends speculation, takes offer of 8 years, $32 million all guaranteed" + "DAVIE, Fla. - Nick Saban is Bama bound."
            + "Ending five weeks of denials and two days of deliberation, Saban accepted the"
            + " Alabama coaching job and abandoned his bid to rebuild the Miami Dolphins after only two seasons."
            + "ESPN said the deal is for eight years and $32 million, all of it guaranteed."
            + "Miami owner Wayne Huizenga said he was informed of the decision in a meeting Wednesday "
            + "at Saban's house. Huizenga announced the departure at a news conference that Saban didn't attend.";

    private static File testFile;
    static {
        try {
            testFile = File.createTempFile("Temp", "tmp");
            testFile.deleteOnExit();
            FileUtils.writeStringToFile(testFile, testString);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

//    @Test
//    public void testMemoryIndex() throws Exception {
//        MemoryIndex index = new MemoryIndex();
//        Analyzer analyzer = new EnglishAnalyzer(Version.LUCENE_43);
//        index.addField("title", "Nick Sabans", analyzer);
//        QueryParser defaultParser = new AnalyzingQueryParser(Version.LUCENE_43, "title", analyzer);
//        Query query = defaultParser.parse("Saban");
//
//        assertTrue(index.search(query) > 0);
//
//    }

    @Test
    public void testZ() throws Exception {
        TokenStream tokenStream = WikiRelationIndexUtils.baseAnalyzer.tokenStream("", new StringReader("Pat-Weakness"));
        CharTermAttribute charTermAttribute = tokenStream.addAttribute(CharTermAttribute.class);
        tokenStream.reset();
        int count = 0;
        String[] expectation = new String[] { "pat", "weak" };
        while (tokenStream.incrementToken()) {
            String term = charTermAttribute.toString();
            assertEquals(expectation[count], term);
            count++;
        }
        tokenStream.end();
        tokenStream.close();
    }

    @Test
    public void mainIndexStressTest() {
        // Warm up
        for (int i = 0; i < 100; i++) {
            TitleNameIndexer.getTitle(i);
        }

        long start = System.currentTimeMillis();
        int testIteration = 5000;
        for (int i = 0; i < testIteration; i++) {
            TitleNameIndexer.getTitle(i);
        }
        long time = System.currentTimeMillis() - start;
        System.out.println(time + " ms");
        System.out.println(time * 1000 / testIteration + " microsec per query for index " + GlobalParameters.paths.titleStringIndex);
    }

}
