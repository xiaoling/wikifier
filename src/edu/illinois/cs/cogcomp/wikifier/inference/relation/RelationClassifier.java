package edu.illinois.cs.cogcomp.wikifier.inference.relation;


//
//@Deprecated
//public class RelationClassifier extends LinearClassifier{
//
//    public RelationClassifier(String modelPath, String scaler) throws Exception {
//        super(modelPath, scaler);
//    }
//    
//    /**
//     * Returns whether the triple relation should exist between the two entities
//     * @param center
//     * @param neighbor
//     * @param relation
//     * @return the unnormalized probability of this relation holds given the entities
//     */
//    public double scale(WikifiableEntity center,WikifiableEntity neighbor,Triple relation){
//       return classify(extractFeatures(center,neighbor,relation));
//    }
//
//    public static double[] extractFeatures(WikifiableEntity center,WikifiableEntity neighbor,Triple relation){
//        LinearFeatureStructure features = new LinearFeatureStructure();
//
//        features.add(center.entityFeatures, 0);
//        features.add(neighbor.entityFeatures, 1);
//        
//        // Triple Features
//        features.add(RelationFeature.Arg1TokenCount, StringUtils.countMatches(relation.getArg1(),"_"));
//        features.add(RelationFeature.Arg2TokenCount, StringUtils.countMatches(relation.getArg2(),"_"));
//        features.add(RelationFeature.NumOfMatchedTopDisambiguation, getMatchedTopDisambiguationCount(center,neighbor,relation));
//        features.add(RelationFeature.Arg1ExistingScore, center.getCandidateScore(relation.getArg1()));
//        features.add(RelationFeature.Arg2ExistingScore, neighbor.getCandidateScore(relation.getArg2()));
//        features.add(RelationFeature.LexicalScore, relation.getScore());
//        features.add(RelationFeature.SoftmaxScore, relation.getNormalizedScore());
//        
//        return features.vectorize();
//    }
//    
//    public static Boolean isGoldSolution(WikifiableEntity center,WikifiableEntity neighbor,Triple relation){
//        if(center.goldAnnotation.equals("*unknown*") || neighbor.goldAnnotation.equals("*unknown*") )
//            return null;
//        return relation.getArg1().equals(center.goldAnnotation) && relation.getArg2().equals(neighbor.goldAnnotation);
//    }
//    
//    /**
//     * Counts the number matched argument between the two entities and the hypothetical relation
//     * @param center
//     * @param neighbor
//     * @param relation
//     * @return 0,1 or 2
//     */
//    private static int getMatchedTopDisambiguationCount(WikifiableEntity center,WikifiableEntity neighbor, Triple relation){
//        int matched = center.topDisambiguation!=null && relation.getArg1().equals(center.topDisambiguation.titleName) ? 1: 0;
//        matched += neighbor.topDisambiguation!=null && relation.getArg2().equals(neighbor.topDisambiguation.titleName) ? 1: 0;
//        return matched;
//    }
//
//    
//}
