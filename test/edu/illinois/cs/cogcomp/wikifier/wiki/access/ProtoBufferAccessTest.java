package edu.illinois.cs.cogcomp.wikifier.wiki.access;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import edu.illinois.cs.cogcomp.wikifier.inference.features.SimilarityMetrics;
import edu.illinois.cs.cogcomp.wikifier.inference.features.SimilarityMetrics.HashMapSemanticInfoRepresentation;
import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.SemanticTitleDataInfoProto;
import edu.illinois.cs.cogcomp.wikifier.wiki.access.WikiAccess.WikiMatchData;

public class ProtoBufferAccessTest {

    public static void printSurfaceFormInfo(WikiAccess wikiAccess,String surface) throws Exception{
        for(WikiMatchData data:wikiAccess.getDisambiguationCandidates(surface, 20)){
            String probs = "concept=>"+surface+":"+data.conditionalSurfaceFromProb+"\n";
            probs += surface+"=>concept"+data.conditionalTitleProb+"\n";
            System.out.println(data.basicTitleInfo.getTitleSurfaceForm()+" "+probs);
        }
        System.out.println("===");
    }

    public static void test(ProtobufferBasedWikipediaAccess wikiAccess) throws Exception{
//        showSemanticSimilarity(wikiAccess, "Miami_Dolphins", "Nick_Saban");
        printSurfaceFormInfo(wikiAccess,"Milošević");
        printSurfaceFormInfo(wikiAccess,"Socialist Party");
        System.exit(0);
        long lDateTime = System.currentTimeMillis();
        System.out.println("Done loading the indices and data structures; time consumed is: "+((System.currentTimeMillis())-lDateTime)/1000+" seconds");
        String[] stuffToLookAt = new String[]{"Swiss","USA","United States","Thailand","Russia","Microsoft","Apple","Michael Jordan","Times","Chicago","New York","Australia",
                    "Tour De France","love","HIV","Cancer","laptop","Israel","Jerusalem","George Bush","Hillary Clinton","World War II",
                    "UFC","Alaska","Theater","Bill T Jones","Forrest Gump","Nabokov","Lolita","Hiroshima","Irag","Saddam","Scud",
                    "chocolate","Broadway","coffee","school","X rays","X-rays","Metallica","Nirvana","Bon Jovi", "Queen","Ministry of Defense and Armed Forces Logistics","JFK"};
        System.out.println("Generating the disambiguation candidates, and extracting the basic, lexical and semantic data for "+stuffToLookAt.length+" candidates");
        lDateTime = System.currentTimeMillis();
        for(int i=0;i<stuffToLookAt.length;i++) 
            System.out.println("The number of disambiguation candidates for \""+stuffToLookAt[i]+ "\" is: "+wikiAccess.getDisambiguationCandidates(stuffToLookAt[i], 10).size());
//      System.out.println(wikiAccess.surfaceDocIdToTiltesMatcher.size()+" protobuffers for surface forms retrieved and cached");
//      System.out.println(wikiAccess.basicWikiArticleData.size()+" basic entries retrieved and cached");
//      System.out.println(wikiAccess.lexicalData.size()+" lexical entries retrieved and cached");
//      System.out.println(wikiAccess.semanticData.size()+" semantic entries retrieved and cached");
        System.out.println("Comparing the semantic relatedness between the different disambiguations of Russian and American");
        List<WikiAccess.WikiMatchData> candidates1 = wikiAccess.getDisambiguationCandidates("Russian", 2);
        List<WikiAccess.WikiMatchData> candidates2 = wikiAccess.getDisambiguationCandidates("American", 2);
        for(int i=0;i<candidates1.size();i++) {         
            for(int j=0;j<candidates2.size();j++) {
                WikiAccess.WikiMatchData c1 = candidates1.get(i);
                WikiAccess.WikiMatchData c2 = candidates2.get(j);
                System.out.println("Relatedness between "+c1.basicTitleInfo.getTitleSurfaceForm()+" and "+c2.basicTitleInfo.getTitleSurfaceForm()+":");
                SimilarityMetrics.printRelatednessFeatures(SimilarityMetrics.getRelatedness(c1, c2));
            }
        }
        showSemanticSimilarity(wikiAccess,"Cohen","Missile_defense");
        showSemanticSimilarity(wikiAccess,"Russia","United_States");
        showSemanticSimilarity(wikiAccess,"Singular_they","Database");
        showSemanticSimilarity(wikiAccess,"Iran","Ministry_of_Defence");
    }
    
    private static void showSemanticSimilarity(ProtobufferBasedWikipediaAccess wikiAccess,String unnormalizedTilte1, String unnormalizedTilte2) throws Exception{
        System.out.println("Outgoing links for "+ unnormalizedTilte1);
        int tid1 =wikiAccess.getTitleIdOf(unnormalizedTilte1);
        SemanticTitleDataInfoProto data1 = wikiAccess.getSemanticInfo(tid1);
        for(int i=0;i<data1.getOutgoingLinksIdsCount();i++)
            System.out.println("\t\t"+wikiAccess.getBasicInfo(data1.getOutgoingLinksIds(i)).getTitleSurfaceForm());
        System.out.println("Outgoing links for "+unnormalizedTilte2);
        int tid2 = wikiAccess.getTitleIdOf(unnormalizedTilte2);
        SemanticTitleDataInfoProto data2 = wikiAccess.getSemanticInfo(tid2);
        for(int i=0;i<data2.getOutgoingLinksIdsCount();i++)
            System.out.println("\t\t"+wikiAccess.getBasicInfo(data2.getOutgoingLinksIds(i)).getTitleSurfaceForm());
        WikiAccess.WikiMatchData concept1 = new WikiAccess.WikiMatchData();
        concept1.basicTitleInfo=wikiAccess.getBasicInfo(tid1);
        concept1.lexInfo=wikiAccess.getLexicalInfo(tid1);
        concept1.semanticInfo=wikiAccess.getSemanticInfo(tid1);
        WikiAccess.WikiMatchData concept2 = new WikiAccess.WikiMatchData();
        concept2.basicTitleInfo=wikiAccess.getBasicInfo(tid2);
        concept2.lexInfo=wikiAccess.getLexicalInfo(tid2);
        concept2.semanticInfo=wikiAccess.getSemanticInfo(tid2);
        double[] sim = SimilarityMetrics.getRelatedness(concept1, concept2);
        HashMap<Integer, Boolean>  incomingIntersection =  SimilarityMetrics.getIntersection((new HashMapSemanticInfoRepresentation(concept1)).incomingLinks,
                (new HashMapSemanticInfoRepresentation(concept2)).incomingLinks);
        System.out.println("The intersection of the incoming links between "+unnormalizedTilte1+" and "+unnormalizedTilte2);
        for(Iterator<Entry<Integer, Boolean>> iter = incomingIntersection.entrySet().iterator(); iter.hasNext();) {
            int tid = iter.next().getKey();
            System.out.println("\t"+wikiAccess.getBasicInfo(tid).getTitleSurfaceForm());
        }
        System.out.println("Similarity between "+unnormalizedTilte1+" and "+unnormalizedTilte2);
        SimilarityMetrics.printRelatednessFeatures(sim);                    
    }
       
    /**
     * Requires a lot of memory
     * @throws Exception
     */
    public static void test() throws Exception {
        test(ProtobufferBasedWikipediaAccess.getDefaultInstance());
    }

}
