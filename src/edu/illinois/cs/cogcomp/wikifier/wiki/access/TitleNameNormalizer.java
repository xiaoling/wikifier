package edu.illinois.cs.cogcomp.wikifier.wiki.access;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;

import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;
import edu.illinois.cs.cogcomp.wikifier.utils.TimeAndMemMonitor;
import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.LRUCache;
import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.StringMap;
import edu.illinois.cs.cogcomp.wikifier.utils.io.CompressionUtils;
import edu.illinois.cs.cogcomp.wikifier.wiki.indexing.TitleNameIndexer;


public class TitleNameNormalizer {
    
    public static boolean showInitProgress = true;

    public static final int ESTIMATED_REDIRECTS = 6000000;
	private Map<String,String> redirects;

	private BloomFilter<CharSequence> redirectFilter;
	
	private static boolean useBloomFilter = false;//GlobalParameters.systemPriority == Priority.MEMORY;
	
	private static TitleNameNormalizer evaluatorInstance = null;
	
    public static TitleNameNormalizer getInstance(){

        if (evaluatorInstance == null) {
            synchronized(TitleNameNormalizer.class){
                if (evaluatorInstance == null)
                    try {
                        evaluatorInstance = new TitleNameNormalizer(GlobalParameters.paths.compressedRedirects);
                    } catch (IOException e) {
                        e.printStackTrace();
                        System.exit(0);
                    }
            }
        }
        return evaluatorInstance;
    }
	
	private TitleNameNormalizer(){}
	
	private TitleNameNormalizer(String pathToEvaluationRedirectsData) throws IOException{
	    
        if (useBloomFilter){
            redirectFilter = BloomFilter.create(Funnels.stringFunnel(), ESTIMATED_REDIRECTS);
            redirects = new LRUCache<String, String>(5000) {
                protected String loadValue(String src) {
                    String normalized = TitleNameIndexer.normalize(src);
                    if (normalized == null)
                        return src;
                    return TitleNameIndexer.normalize(src);
                }
            };
        }
        else
            redirects = new StringMap<String>();
        if (showInitProgress)
            System.out.println("Loading the most recent redirect pages from Wikipedia to normalize the output links to the latest version");
        if (pathToEvaluationRedirectsData != null) {
            InputStream is = CompressionUtils.readSnappyCompressed(pathToEvaluationRedirectsData);
            LineIterator iterator = IOUtils.lineIterator(is, StandardCharsets.UTF_8);

            long linecount = 0;
            while (iterator.hasNext()) {
                String line = iterator.nextLine();
                if (showInitProgress && linecount++ % 100000 == 0)
                    System.out.println("loading the latest redirects; linecount=" + linecount);
                String[] parts = StringUtils.split(line, '\t');

                String src = parts[0].trim().replace(' ', '_');
                String trg = parts[1].trim().replace(' ', '_');
                if (useBloomFilter)
                    redirectFilter.put(src);
                else
                    redirects.put(src, trg);
            }
            iterator.close();
        }
		redirects = Collections.unmodifiableMap(redirects);
        if (showInitProgress)
            System.out.println("Done  - Loading the most recent redirect pages from Wikipedia to normalize the output links to the latest version");
	}
	
	private TitleNameNormalizer(Map<String,String> redirectMap){
		redirects = redirectMap;
	}
	
	public static String normalize(String wikiLink){
		try {
			return getInstance().redirectWithLatestLinks(wikiLink);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return wikiLink;
	}
	
    protected String redirect(String title) {
        if (useBloomFilter && !redirectFilter.mightContain(title))
            return title;
        String to = redirects.get(title);
        int count = 0;
        while (to != null && !to.equals(title) && count++ <= 50) {
            title = to;
            to = redirects.get(title);
        }

        if (count >= 50) {
            System.out.println("Fixed point reached with title : " + title + " ; stopping to loop in redirects at this moment");
        }
        return title;
    }
    
    /**
     * Normalizes a map with wiki titles as keys
     * @param titleMap
     */
    public static <T> void normalize(Map<String,T> titleMap){
        for (Entry<String, String> redirect : getInstance().redirects.entrySet()) {
            String from = redirect.getKey();
            String to = redirect.getValue();
            if (titleMap.containsKey(from) && titleMap.containsKey(to)) {
                titleMap.put(from, titleMap.get(to));
            }
        }
    }

	private String redirectWithLatestLinks(String wikiLink) {
		if(wikiLink==null)
			return "*null*";
		String title = wikiLink.replace(' ', '_');
		if(wikiLink.indexOf("http://en.wikipedia.org/wiki/")>-1)
			title = wikiLink.substring(wikiLink.lastIndexOf("http://en.wikipedia.org/wiki/")+
					"http://en.wikipedia.org/wiki/".length(),wikiLink.length());
				
		title = redirect(title);
		
		return StringEscapeUtils.unescapeXml(StringUtils.capitalize(title));
	}
	
	public static void main(String[] args) throws Exception{
		
		//createRandomAccessRedirects(ParametersAndGlobalVariables.pathToRedirectHashMap);
		//loads redirect file into disk backed hash map

		
		// check that the redirects were loaded correctly 
		
		long memoryBeforeLodaingTheTrie = TimeAndMemMonitor.usedMemory();
		//ParametersAndGlobalVariables.pathToEvaluationRedirectsData = "../WikiData/RedirectsForEvaluationData/RedirectsAug2010.txt";
		TitleNameNormalizer normalizer = TitleNameNormalizer.getInstance();
		if(!normalizer.redirectWithLatestLinks("List of Legend of Light episodes").equals(
				normalizer.redirectWithLatestLinks("List of Hikari no Densetsu episodes")))
			throw new Exception("Exception1:"+normalizer.redirectWithLatestLinks("List of Legend of Light episodes")+"!="+
					normalizer.redirectWithLatestLinks("List of Hikari no Densetsu episodes"));
		if(!normalizer.redirectWithLatestLinks("BabaLevRatinov").equals("BabaLevRatinov"))
			throw new Exception("Exception2");
		if(!normalizer.redirectWithLatestLinks("15 December").equals("December_15"))
			throw new Exception("Exception3");
		System.out.println("The usage of the redirect trie is = "+(TimeAndMemMonitor.usedMemory()-memoryBeforeLodaingTheTrie));
		
	}
} // class 
