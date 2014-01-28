package edu.illinois.cs.cogcomp.wikifier;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.xeustechnologies.googleapi.spelling.SpellChecker;
import org.xeustechnologies.googleapi.spelling.SpellCorrection;
import org.xeustechnologies.googleapi.spelling.SpellResponse;

import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;
import edu.illinois.cs.cogcomp.wikifier.wiki.access.ProtobufferBasedWikipediaAccess;
import edu.illinois.cs.cogcomp.wikifier.wiki.access.TitleNameNormalizer;
import edu.illinois.cs.cogcomp.wikifier.wiki.access.WikiAccess;
import gnu.trove.map.hash.TIntDoubleHashMap;


public class XiaoExperiement {
	

	
	public static void printCandidates(String text,int max) throws Exception{
		System.out.println("Loading the indices and data structures");
        if (GlobalParameters.wikiAccess == null) {
            ProtobufferBasedWikipediaAccess wikiAccess = new ProtobufferBasedWikipediaAccess("../Data/Lucene4Index");
            GlobalParameters.wikiAccess = wikiAccess;
        }
		List<WikiAccess.WikiMatchData> data = GlobalParameters.wikiAccess.getDisambiguationCandidates(text, max);
		if(data.size()==0)
			System.out.println("No candidates!");
		for(WikiAccess.WikiMatchData match:data){
			String title = match.basicTitleInfo.toString();
			System.out.println(StringUtils.substringBefore(title, "topicsIds:")+'\n');
		}
		
	}
	
	public static void testRedirects(String title) throws Exception{
		System.out.println(title+" ===> "+TitleNameNormalizer.normalize(title));
	}
	
	public static void testSpellChecker(){
		SpellChecker checker = new SpellChecker();
		
		SpellResponse response = checker.check("Califorainia");
		if(response.getCorrections()==null)
			System.out.println("No errors");
		else
			for( SpellCorrection sc : response.getCorrections() )
			    System.out.println( sc.getValue() );
	}
	
	public static void main(String[] args) throws Exception{


//        org.slf4j.Logger l2 = LoggerFactory.getLogger(XiaoExperiement.class);
//        LoggerContext lc = (LoggerContext)LoggerFactory.getILoggerFactory();
//        Logger logger = lc.getLogger(XiaoExperiement.class);
//        logger.setLevel(Level.OFF);
        
//        l2.debug("blah blah");
	    TIntDoubleHashMap map = new TIntDoubleHashMap();
        map.adjustValue(1, 1);
        System.out.println(map);
        map.put(1, 0);
        map.adjustValue(1, -1);
        System.out.println(map);
        map.clear();
        map.adjustOrPutValue(1, 2, 2);
        System.out.println(map);
//	    
//		GlobalParameters.pathToSpellCheckCache = "../Data/OtherData/SpellCheck.cache";
//		System.out.println(SurfaceFormSpellChecker.getCorrection("Ruth Bater Ginsburg"));
//		
//		ParametersAndGlobalVariables.pathToEvaluationRedirectsData="../Data/WikiData/RedirectsForEvaluationData/RedirectsFeb2011.txt";
//		printCandidates("Ministry of Defense",20);
//		printCandidates("the World Trade Center towers",20);
//		printCandidates("D.C.",20);
//		testRedirects("China");
//		testRedirects("People's Republic of China");
//		
		//testRedirects();
		//testWikiAccess();
	}

}
