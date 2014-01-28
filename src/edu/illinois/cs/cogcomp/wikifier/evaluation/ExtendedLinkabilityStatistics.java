package edu.illinois.cs.cogcomp.wikifier.evaluation;

import edu.illinois.cs.cogcomp.wikifier.models.WikiCandidate;
import edu.illinois.cs.cogcomp.wikifier.models.tfidf.AveragesCollector;

public class ExtendedLinkabilityStatistics{     
    public double numSolvableNonNullEntities = 0; // for how many surface forms that map to non-null Wikipedia concepts, we have the correct answer in the list of disambiguation candidates?
    public double numNullEntities = 0; // how many entities were mapped to null in the gold annotation
    public double numUnSolvableNonNullEntities = 0;
    public double numCorrectForcedPredictionsSolvableNonNullEntities = 0; //if we force the decision (no null predictions) on solvable entities, how many predictions are we getting correctly
    public double numCorrectLinkerDecisions = 0;
    public double numCorrectLinkEverythingDecisions = 0;// we we decide to link everything by default, how good do we get?
    public double numTotalLinkerDecisions = 0;
    
    public AveragesCollector interestingLinkabilityStatisticsByType = new AveragesCollector();
    
    /*
     * comment should be {Solvable-NonNull, Null, Unsolvable-NonNull}
     */
    public void addInterestingLinkabilityStatistics(WikiCandidate topDisambiguation, String comment){
        /*
        if(!FeatureExtractorTitleMatchAndFrequency.useOnlyConditionalTitleProbabilitiesInTitleMatchFeatures){
            SurfaceFormEntitiesTypes[] types = FeatureExractorGenerator.SurfaceFormEntitiesTypes.values();
            for(int j=0;j<types.length;j++) {
                interestingLinkabilityStatisticsByType.addKey(comment+"-GT-"+types[j], 
                        topDisambiguation.otherFeatures.getFeatureValue("expectedProbOfOutOfWikipediaInstanceGoodTuring"+types[j]),true);
                interestingLinkabilityStatisticsByType.addKey(comment+"-Ambiguity-"+types[j], 
                        topDisambiguation.otherFeatures.getFeatureValue("surfaceFormAmbiguity"+types[j]),true);
                interestingLinkabilityStatisticsByType.addKey(comment+"-logDiff-"+types[j], 
                        topDisambiguation.otherFeatures.getFeatureValue("logProbDiffByType"+types[j]),true);
                interestingLinkabilityStatisticsByType.addKey(comment+"-Linkability-"+types[j], 
                        topDisambiguation.otherFeatures.getFeatureValue("reliableLinkabilityByType"+types[j]),true);
            }
        }*/
    }
    
    public void printPerformance(){
        System.out.println("FORCED ranker accuracy on the solvable entities:"+numCorrectForcedPredictionsSolvableNonNullEntities/numSolvableNonNullEntities);
        System.out.println("Linker performance: " +numCorrectLinkerDecisions/numTotalLinkerDecisions);
        System.out.println("Linker performance if we link every surface form: " +numCorrectLinkEverythingDecisions/numTotalLinkerDecisions);
        System.out.println("-------   interesting linkability statistics -----------");
        interestingLinkabilityStatisticsByType.displayAverages();
    }
}