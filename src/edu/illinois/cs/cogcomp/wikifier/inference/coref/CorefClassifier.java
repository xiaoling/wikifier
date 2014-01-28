package edu.illinois.cs.cogcomp.wikifier.inference.coref;
//
//import java.io.File;
//import java.util.List;

//import liblinear.Parameter;
//import liblinear.SolverType;
//import CommonSenseWikifier.FeatureExtractors.LinearClassifier;
//import CommonSenseWikifier.ProblemRepresentationDatastructures.GlobalParameters;
//import CommonSenseWikifier.ProblemRepresentationDatastructures.WikifiableEntity;
//
//public class CorefClassifier extends LinearClassifier{
//
//    public CorefClassifier(String dir) throws Exception {
//        super(new File(dir,"Coref.model").getPath(), new File(dir,"Coref.scaler").getPath());
//    }
//
//    public CorefClassifier(String modelPath, String scaler) throws Exception {
//        super(modelPath, scaler);
//    }
//    
//    @Override
//    protected Parameter getParameter(){
//        return new Parameter(SolverType.L2R_LR, 100, 0.015);
//    }
//    
//    
//    /**
//     * Used for training coref
//     * @param candidates
//     * @param index
//     */
//    public static void addInstancesToCoref(WikifiableEntity e,List<WikifiableEntity> candidates,CorefIndexer index){
//        
//        int candidateCount = candidates.size();
//        for(WikifiableEntity other:candidates){
//            boolean correct = 
//                    (other.topDisambiguation!=null && (other.topDisambiguation.titleName.equals(e.goldAnnotation))
//                    || other.isCurrentlyLinkingToNull() && "*null*".equals(e.goldAnnotation));
//            
//            if(!correct && (e.goldAnnotation.equals("*unknown*")))
//                continue;
//            
//            System.out.printf("Pair %s and %s is %s \n",e.surfaceForm,other.surfaceForm,correct? "coreferent":"not coreferent");
//            GlobalParameters.problemBuilder.addInstance(e.corefFeatures(other,candidateCount,index),correct);
//        }
//    }
//    
//    /**
//     * @param args
//     * @throws Exception 
//     */
//    public static void main(String[] args) throws Exception {
//        CorefClassifier classifier = new CorefClassifier(
//                "../Data/Models/TitleMatchPlusLexicalPlusCoherence/Relation.model",
//                "../Data/Models/TitleMatchPlusLexicalPlusCoherence/Relation.scaler"
//                );
//        System.out.println(classifier.classify(new double[31]));
//
//    }
//
//}
