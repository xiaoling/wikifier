package edu.illinois.cs.cogcomp.wikifier.evaluation;


public class ProblemEvaluationResult {
    
    ExtendedLinkabilityStatistics linkerStats = new ExtendedLinkabilityStatistics();
    public static final int TOPK = 5; // we measure the precision and recall in up to top 5 predictions
    // This is the per-position (per entity) part of the evaluation, in this part each wikipedia title appears as many times
    // as it appears in the text.       
    public double[] truePositivesTopK = new double[TOPK]; // NOTE THAT WE MEASURE THIS ONLY ON THE ENTITIES THAT WE CHOOSE TO LINK!!!! how many did we predict correctly in the top K predictions?
    public double trueNegatives = 0;// how many I mapped to null and they were indeed null
    public double[] mismatchTopK = new double[TOPK]; // NOTE THAT WE MEASURE THIS ONLY ON THE ENTITIES THAT WE CHOOSE TO LINK!!!! how many did we predict correctly in the top K predictions?
    public double[] falsePositivesTopK = new double[TOPK]; // NOTE THAT WE MEASURE THIS ONLY ON THE ENTITIES THAT WE CHOOSE TO LINK!!!! 
    public double falseNegatives = 0;// how many I mapped to null and they were non-null
    
    // This is the bag of concepts (BOC) evaluation. Each Wikipedia title can appear only once here!
    public double correctlyPredictedEntitiesBocAllSurfaceForms = 0; // how many did we predict correctly - consider all the text?
    public double predictedEntitiesBocAllSurfaceForms = 0; // how many entities did we predict - consider all the text
    public double correctlyPredictedEntitiesBocProblemSurfaceForms = 0; // how many did we predict correctly - consider the bag-of-concepts only over the problem entities
    public double predictedEntitiesBocProblemSurfaceForms = 0; // how many entities did we predict - consider the bag-of-concepts only over the problem entities
    public double goldEntititesBOC = 0; // how many entities were there, 

    public void printPerformance(){
        System.out.println("------------------------------------------------------------------- ");
        System.out.println("Per position performance:");
        for(int i=0;i<TOPK;i++) {
            System.out.println("\t ---->With respect to top "+(i+1)+" predictions:");
            double precisionPerEnt =   truePositivesTopK[i]/(truePositivesTopK[i]+falsePositivesTopK[i]+mismatchTopK[i]);
            double recallPerEnt = truePositivesTopK[i]/(truePositivesTopK[i]+falseNegatives+mismatchTopK[i]);
            double accPerEnt = (truePositivesTopK[i]+trueNegatives)/(truePositivesTopK[i]+falsePositivesTopK[i]+trueNegatives+falseNegatives+mismatchTopK[i]);
            double f1PerEnt = 2*precisionPerEnt*recallPerEnt/(precisionPerEnt+recallPerEnt);
            System.out.println("\t\t\tPrecision: " + precisionPerEnt);
            System.out.println("\t\t\tRecall (per position on all entitites): " + recallPerEnt);
            System.out.println("\t\t\tF1 Per entity = "+f1PerEnt);
            System.out.println("\t\t\tAccuracy (per position on all entitites): " + accPerEnt);
        }
        
        double precisionBocAllText =   correctlyPredictedEntitiesBocAllSurfaceForms/predictedEntitiesBocAllSurfaceForms;
        double precisionBocProblemInstances =   correctlyPredictedEntitiesBocProblemSurfaceForms/predictedEntitiesBocProblemSurfaceForms;
        double recallBocAllText = correctlyPredictedEntitiesBocAllSurfaceForms/goldEntititesBOC;
        double recallBocProblemInstances = correctlyPredictedEntitiesBocProblemSurfaceForms/goldEntititesBOC;
        System.out.println("------------------------------------------------------------------- ");
        System.out.println("Bag of Concepts(BOC) performace:");
        System.out.println("Precision (on all surface forms): " + precisionBocAllText);
        System.out.println("Recall (on all surface forms): " + recallBocAllText);
        System.out.println("F1(on all surface forms):  "+2*precisionBocAllText*recallBocAllText/(precisionBocAllText+recallBocAllText));
        System.out.println("Precision (only on problem instances): " + precisionBocProblemInstances);
        System.out.println("Recall (only on problem instances): " + recallBocProblemInstances);
        System.out.println("F1 (only on problem instances): "+2*precisionBocProblemInstances*recallBocProblemInstances/(precisionBocProblemInstances+recallBocProblemInstances));
        System.out.println("------------------------------------------------------------------- ");
        System.out.println("Linker stats:");
        System.out.println("------------------------------------------------------------------- ");
        linkerStats.printPerformance();
    }
} // class  ProblemEvaluationResult
