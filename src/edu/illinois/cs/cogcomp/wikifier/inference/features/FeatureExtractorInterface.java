package edu.illinois.cs.cogcomp.wikifier.inference.features;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import liblinear.Model;
import edu.illinois.cs.cogcomp.wikifier.evaluation.ProblemEvaluationResult;
import edu.illinois.cs.cogcomp.wikifier.inference.LiblinearInterface;
import edu.illinois.cs.cogcomp.wikifier.models.LinkingProblem;



public abstract class FeatureExtractorInterface {
    
	public long featureExtractionTime=0;// the total feature construction time consumed on all the problems
	public String extractorName=null;
	public Model ranker_model=null; // Note that this ranker will be applied on the features extracted in the previous stage as well. That is, feature extraction is incremental
	public List<Double> ranker_scaling_params=null;
	public Model linker_model=null; // Note that this ranker will be applied on the features extracted in the previous stage as well. That is, feature extraction is incremental
	public List<Double> linker_scaling_params=null;
	public String pathPatternToSavesData=null;//where to keep the training data for that stage
	
	// this one is used to store the performance by each stage of the inference during testing. 
	public ProblemEvaluationResult performanceBeforeLinker = new ProblemEvaluationResult();
	public ProblemEvaluationResult performanceAfterLinker = new ProblemEvaluationResult();

	/*
	 * If the mode is in training, the model is not loaded from the file, otherwise, it is
	 */
	public FeatureExtractorInterface(String extractorName, boolean inTraining ,String pathToSaves) throws Exception{
		this.extractorName=extractorName;
		this.pathPatternToSavesData = pathToSaves+"/"+extractorName;
		if(inTraining) {
			ranker_scaling_params = null;
			ranker_model=new liblinear.Model();
			linker_scaling_params = null;
			linker_model=new liblinear.Model();
		}
		else  {
			String  pathToScalingDataRanker = pathPatternToSavesData+".ranker.scaling";
			String  pathToScalingDataLinker = pathPatternToSavesData+".linker.scaling";
			String  pathToModelRanker =  pathPatternToSavesData+".ranker.model";
			String  pathToModelLinker = pathPatternToSavesData+".linker.model";
			if((!new File(pathToScalingDataRanker).exists())||(!new File(pathToScalingDataLinker).exists())
					||(!new File(pathToModelRanker).exists())||(!new File(pathToModelLinker).exists())){
				System.out.println("Warning - error when loading the SVM ranker models for feature extractor "+ extractorName);
				System.out.println("This does not necessarily mean that something's wrong. We only need the ranker for " +
						"levels where we do feature pruning. If I have an intermediate feature extractor that does not do feature pruning," +
						"there is no reason to have a ranking model for it. However this also might mean that the models for rankers that" +
						"we need are missing. So please check yourself! The exception was");				
			}
			else{
				ranker_scaling_params = LiblinearInterface.loadScalingParams(pathToScalingDataRanker);
				ranker_model = LiblinearInterface.loadModel(pathToModelRanker);
				linker_scaling_params = LiblinearInterface.loadScalingParams(pathToScalingDataLinker);
				linker_model = LiblinearInterface.loadModel(pathToModelLinker);
			}
		}
	}
	
	public abstract void extractFeatures(LinkingProblem problem) throws Exception;
	protected abstract void extractFeatures(LinkingProblem problem,int componentToResolve)  throws Exception;

	public class FeatureExtractorThread extends Thread {
        private LinkingProblem problem;
        private FeatureExtractorInterface featureExtractor = null;
        private List<Integer> componentsIdsToAnnotate = new ArrayList<Integer>();
		
		public FeatureExtractorThread(FeatureExtractorInterface featureExtractor,LinkingProblem problem){
			this.featureExtractor = featureExtractor;
			this.problem = problem;
		}
		
		public void addComponent(int cid){
			componentsIdsToAnnotate.add(cid);
		}
		
		public void run(){
			for(int componentId : componentsIdsToAnnotate) {
				try {
					featureExtractor.extractFeatures(problem, componentId);
				} catch (Exception e) {
					System.out.println("Fatal exception while extracting features");
					e.printStackTrace();
					System.exit(0);
				}
			}
		}
	}

}
