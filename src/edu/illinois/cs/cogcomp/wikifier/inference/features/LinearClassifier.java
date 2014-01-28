/**
 * 
 */
package edu.illinois.cs.cogcomp.wikifier.inference.features;

//import java.util.Arrays;
//import java.util.List;
//
//import edu.illinois.cs.cogcomp.wikifier.inference.LiblinearInterface;
//
//import liblinear.Model;
//import liblinear.Parameter;
//import liblinear.SolverType;
//
///**
// * Decides whether a retrieved relation is a good fit for a pair of entities
// * @author Xiao Cheng
// *
// */
//public abstract class LinearClassifier {
//   
//    private Model model = null;
//    private List<Double> scaling = null;
//    protected final Parameter param;
//
//    
//    public LinearClassifier(String modelPath,String scaler) throws Exception{
//        this.model = LiblinearInterface.loadModel(modelPath);
//        this.scaling = LiblinearInterface.loadScalingParams(scaler);
//        param = getParameter();
//    }
//    
//    public double classify(double[] features){
//        try {
//            double[] scaled = LiblinearInterface.trimAndScale(features, scaling);
////            double decision = LiblinearSvmInterface.make_prediction(scaled, model);
//            return LiblinearInterface.weightedBinaryClassification(scaled, model);
//        } catch (Exception e) {
//            e.printStackTrace();
//            System.exit(0);
//        }
//        return 0;
//    }
//    
//    
//    protected Parameter getParameter(){
//        return new Parameter(SolverType.L2R_LR, 180, 0.015);
//    }
//    
//    /**
//     * This class manages the features to double[] conversion
//     * @author cheng88
//     *
//     */
//    public static class LinearFeatureStructure{
//        
//        private static final int ENTITY_FEATURE_COUNT = EntityFeature.values().length;
//        private static final int TRIPLE_FEATURE_OFFSET = ENTITY_FEATURE_COUNT * 2;
//        private static final int TOTAL_FEATURE_COUNT = ENTITY_FEATURE_COUNT * 2 + RelationFeature.values().length;
//        
//        // Features to be extracted from the WikifiableEntities
//        public static enum EntityFeature{
//            TopDisambiguationTokenCount,
//            TopRankerScore,
//            SurfaceTokenCount,
//            Entropy,
//            CandidateCount,
//            IsPerfectMatch,
//            LinkerScore,
//            IsFinalSolutionNull,
//            TopDisambiguationLogIncomingLinkCount,
//            TopDisambiguationLogOutgoingLinkCount,
//            TopDisambiguationIsDisambiguationPage,
//            TopDisambiguationLinkability
//        }
//        
//        // Features to be extracted from the Triples
//        public static enum RelationFeature{
//            NumOfMatchedTopDisambiguation,
//            LexicalScore,
//            SoftmaxScore,
//            
//            Arg1TokenCount,
//            Arg2TokenCount,
//            Arg1ExistingScore,
//            Arg2ExistingScore
//        }
//        
//        private double[] features;
//        
//        public LinearFeatureStructure(){
//            features = new double[TOTAL_FEATURE_COUNT];
//        }
//        
//        // Here we allow adding features from the other partial features
//        // by copying them over
//        public void add(LinearFeatureStructure entityFeatures,int argNum){
//            System.arraycopy(entityFeatures.features, 0, features, ENTITY_FEATURE_COUNT * argNum, ENTITY_FEATURE_COUNT);
//        }
//        
//        // Default we fill the array from 0
//        public void add(EntityFeature feature,double value){
//            add(feature,0,value);
//        }
//        
//        public void add(EntityFeature feature,boolean value){
//            add(feature,value?1:0);
//        }
//        
//        public void add(EntityFeature feature,int argNum,double value){
//            int offset = argNum*ENTITY_FEATURE_COUNT;
//            features[feature.ordinal() + offset] = value;
//        }
//        
//        public void add(EntityFeature feature,int num,boolean value){
//            add(feature,num,value?1:0);
//        }
//        
//        public void add(RelationFeature feature,double value){
//            features[feature.ordinal()+TRIPLE_FEATURE_OFFSET] = value;
//        }
//        
//        public void add(RelationFeature feature,boolean value){
//            add(feature,value?1:0);
//        }
//        
//        public double get(EntityFeature feature){
//            return features[feature.ordinal()];
//        }
//        
//        public void loadDefaultTopDisambiguationFeatures(){
//            add(EntityFeature.TopRankerScore, 0.0);
//            add(EntityFeature.TopDisambiguationLogIncomingLinkCount,1);
//            add(EntityFeature.TopDisambiguationLogOutgoingLinkCount,1);
//            add(EntityFeature.TopDisambiguationIsDisambiguationPage,false);
//            add(EntityFeature.TopDisambiguationLinkability,1);
//        }
//        
//        public double[] vectorize(){
//            return Arrays.copyOf(features,features.length);
//        }
//        
//        // Only returns the first part
//        public double[] entityFeatureVector(){
//            return Arrays.copyOf(features, ENTITY_FEATURE_COUNT);
//        }
//
//    }
//
//    
//
//}
