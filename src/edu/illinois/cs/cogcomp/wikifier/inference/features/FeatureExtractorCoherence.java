package edu.illinois.cs.cogcomp.wikifier.inference.features;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.Maps;

import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;
import edu.illinois.cs.cogcomp.wikifier.common.ParameterPresets;
import edu.illinois.cs.cogcomp.wikifier.models.LinkingProblem;
import edu.illinois.cs.cogcomp.wikifier.models.Mention;
import edu.illinois.cs.cogcomp.wikifier.models.WikiCandidate;
import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.PairwiseSemanticSim;
import edu.illinois.cs.cogcomp.wikifier.wiki.access.PairwiseSemanticSimAccess;

/*
 * This is Cucerzan-style coherence feature extractor that measures the semantic relatedness
 * between each candidates and all the other candidates for all the entities.
 * 
 * Please note that "ALL" means "ALL-NER"
 */
public class FeatureExtractorCoherence extends FeatureExtractorInterface{
	
	public static int numberCandidatesToKeepByThisStage = 3;//only consider the top 3 predictions as defined by the previous layer
	
	public static final String[] featureNames = {"InPmi", "InGoogleSim", "OutPmi","OutGoogleSim", "TwoWayLink",
		    "TwoWayLink&&InPmi", "TwoWayLink&&InGoogleSim", "TwoWayLink&&OutPmi", "TwoWayLink&&OutGoogleSim"};
	public static PairwiseSemanticSimAccess semSimAccess = null; 
	public static final int CoherenceWindowSize = 2000;// if two entities are more than 2000 chars (~200 tokens~paragraph) away from each other, I don't take the coherence
	
	
//	public static boolean useGoldSolutionsForCoherence = false;
//	public static boolean useOnlyLinkedSurfaceFormsForDisambiguationContext = false;
//	public static boolean useNamedEntitiesInDisambiguationContext = true; // Cucerzan-style coherence : use all NEs   as disambiguation context 
//	public static boolean useUnambiguousInDisambiguationContext = true; // Milne-style coherence : use  all unambiguous entities as disambiguation context
//	public static boolean useAllSurfaceFormsInDisambiguationContext = true; // Use all surface forms as disambiguation context
////	List<WikifiableEntity> disambiguationContext = null;
	//ArrayList<ContextEntityWeight> contextEntityWeights = null; // assigns the weight to each context entity by taking the average incoming GoogleEdit from it to all the other entities  
	
	public FeatureExtractorCoherence(String _extractorName,boolean inTraining, String pathToSaves) throws Exception{
		super(_extractorName, inTraining, pathToSaves);
		semSimAccess = new PairwiseSemanticSimAccess();
	}

	public void extractFeatures(LinkingProblem problem) throws Exception {
		long lastTime = System.currentTimeMillis();
		// pruning the last level to numberCandidatesToKeepByThisStage candidates
		for(Mention e:problem.components) {
		    // Reduce pruning for TAC
		    if(GlobalParameters.params.preset==ParameterPresets.TAC && e.hasGoldAnnotation())
		        e.candidates.add(e.getLastPredictionLevel());
		    else
		        e.candidates.add(e.getRankedTopPredictionsLastLevel(numberCandidatesToKeepByThisStage).topObjects);
		}
//		disambiguationContext = problem.getSortedMentions();
		// setDisambiguationContext(problem.components); // the main purpose here is to set the weights of the context entities....
		FeatureExtractorThread[] threads = new FeatureExtractorThread[GlobalParameters.THREAD_NUM];
		for(int i=0;i<threads.length;i++)
			threads[i] = new FeatureExtractorThread(this, problem);
		for(int componentId=0;componentId<problem.components.size(); componentId++)
			threads[componentId%GlobalParameters.THREAD_NUM].addComponent(componentId);
		ExecutorService execSvc = Executors.newFixedThreadPool(GlobalParameters.THREAD_NUM);
		for(Thread t:threads)
			execSvc.execute(t);
		execSvc.shutdown();
		execSvc.awaitTermination(300,TimeUnit.SECONDS);
		//for(int componentId=0;componentId<problem.components.size(); componentId++)
		//	extractFeatures(problem, componentId);
		featureExtractionTime += System.currentTimeMillis()-lastTime;
		System.out.println(System.currentTimeMillis()-lastTime+" milliseconds elapsed extracting features for the level: "+extractorName);
	}
		
	protected void extractFeatures(LinkingProblem problem,int componentToResolve) throws Exception{
	    
		Mention targetEntity=problem.components.get(componentToResolve);
		
		Map<String,String> maxSemanticRelatednessByTypeS= Maps.newHashMap();
		Map<String,Double> maxSemanticRelatednessByTypeD= Maps.newHashMap();
		for(WikiCandidate targetCandidate : targetEntity.getLastPredictionLevel()){
			double[] similaritiesSum = new double[featureNames.length];
			double[] similaritiesMax = new double[featureNames.length];
			double sumCount = 0;
			if(targetCandidate.getTid()==-1){
			    System.out.printf("Critical Warning: Wiki Title %s has incomplete WikiData\n",targetCandidate.titleName);
			    continue;
			}
			    
			//TODO provide a more efficient way of traversing list within bounds
			for(Mention otherEntity:problem.getContext(componentToResolve, CoherenceWindowSize)) {
			    
//				int diff = otherEntity.charStart-targetEntity.charStart;
//				if(diff > CoherenceWindowSize)
//				    break;
			    // TODO allow different surface to be coreferent
//			    boolean differentSurface = otherEntity.surfaceForm.equals(targetEntity.surfaceForm);
				// if j is not identical to the component I'm trying to resolve (otherwise the similarity is trivially 1 in every aspect)
				if (otherEntity != targetEntity){
					WikiCandidate compareTo = null;
					if(GlobalParameters.params.useUnambiguousInDisambiguationContext && otherEntity.isAlmostUnambiguous)
						compareTo = otherEntity.almostUnambiguousSolution;
					if(GlobalParameters.params.useNamedEntitiesInDisambiguationContext && 
							otherEntity.isNamedEntity() && 
							otherEntity.topCandidate!=null) 
						compareTo = otherEntity.topCandidate;
					if(GlobalParameters.params.useAllSurfaceFormsInDisambiguationContext && otherEntity.topCandidate!=null) 
						compareTo = otherEntity.topCandidate;
					if(GlobalParameters.params.useOnlyLinkedSurfaceFormsForDisambiguationContext&&otherEntity.finalCandidate==null)
						compareTo = null;
					if(GlobalParameters.params.useGoldSolutionsForCoherence)
						compareTo = otherEntity.goldCandidate;

		            if(compareTo!=null && compareTo.getTid()==-1){
		                System.out.printf("Critical Warning: Wiki Title %s has incomplete WikiData\n",targetCandidate.titleName);
		                continue;
		            }
					
	                /*
                     * There are a lot of coreferent phrases, and I don't want cliques to be formed when the same title is in two coreferent entities
                     */
					if(compareTo!=null&& (compareTo.getTid()!=targetCandidate.getTid() /*|| differentSurface*/ )) {
						sumCount++;
						//double[] sim =SimilarityMetrics.getRelatedness(targetCandidate.wikiData, compareTo.wikiData);
						PairwiseSemanticSim simProto = semSimAccess.getRelatednessProtobuffer(targetCandidate.getTid(), compareTo.getTid());
						double[] sim = new double[featureNames.length];
						if(simProto!=null){
							//ContextEntityWeight weight = contextEntityWeights.get(j);
							sim = new double[]{simProto.getIncomingLinksPmi(),//*weight.avgIncomingPmiRelatedness, 
															 simProto.getIncomingLinksNormalizedGoogleDistanceSim(),//*weight.avgIncomingNormalizedGoogleDistanceRelatedness, 
															  simProto.getOutgoingLinksPmi(),//*weight.avgOutgoingPmiRelatedness, 
															  simProto.getOutgoingLinksNormalizedGoogleDistanceSim(),//*weight.avgOutgoingNormalizedGoogleDistanceRelatedness, 
															  0, 0, 0, 0, 0};
								if(simProto.getTwoWayLink()) {
									sim[4]=1.0; //do not weight this one!!!
									// sim[5]=1.0*weight.avgIncomingPmiRelatedness; 
									sim[5]=simProto.getIncomingLinksPmi();//*weight.avgIncomingPmiRelatedness; 
									sim[6]= simProto.getIncomingLinksNormalizedGoogleDistanceSim();//*weight.avgIncomingNormalizedGoogleDistanceRelatedness; 
									sim[7]=simProto.getOutgoingLinksPmi();//*weight.avgOutgoingPmiRelatedness;
									sim[8]=simProto.getOutgoingLinksNormalizedGoogleDistanceSim();//*weight.avgOutgoingNormalizedGoogleDistanceRelatedness; 
								}
						}
						for(int i=0;i<sim.length;i++) {
							similaritiesSum[i] += sim[i];
							if (similaritiesMax[i] < sim[i] )
								similaritiesMax[i] = sim[i];
							if(!maxSemanticRelatednessByTypeD.containsKey(featureNames[i]) ||
									maxSemanticRelatednessByTypeD.get(featureNames[i])<sim[i]) {
								maxSemanticRelatednessByTypeD.put(featureNames[i], sim[i]);
								maxSemanticRelatednessByTypeS.put(featureNames[i], targetCandidate.wikiData.basicTitleInfo.getTitleSurfaceForm()+
										"(normalized to:"+targetCandidate.titleName+")-"
										+compareTo.wikiData.basicTitleInfo.getTitleSurfaceForm()+
										"(normalized to:"+compareTo.titleName+")");
							}										
						}
					} // if there's something to compare to
				} // if the entity is linked or we allow non-linked entities as well
			}//for j;
			
			if(sumCount>0) {
				// Normalizing the coherence score by the number of non-coref entities. Otherwise,
				// if the documents are big, the scores will be big, and we don't want that to happen.
				for(int i=0;i<similaritiesSum.length;i++)
					similaritiesSum[i] = similaritiesSum[i]/sumCount;
			} 
			for(int i=0;i<similaritiesSum.length;i++) {
				targetCandidate.coherenceFeatures.addFeature("coherence-Avg-"+featureNames[i], similaritiesSum[i]);
			}
			for(int i=0;i<similaritiesMax.length;i++) {
				targetCandidate.coherenceFeatures.addFeature("coherence-Max-"+featureNames[i], similaritiesMax[i]);
			}
		} // running over all the components
		//System.out.println("Most related articles by semantic relation type for the entity "+targetEntity.surfaceForm+" :");
		//for(int i=0;i<featureNames.length;i++)
		//	System.out.println("\t"+featureNames[i]+"->"+
		//			maxSemanticRelatednessByTypeS.get(featureNames[i])+":"+
		//			maxSemanticRelatednessByTypeD.get(featureNames[i]));
	}
	
	
	
//	/*
//	 * This is basically a copy of the "extractFeatures" function, but the goal here is to set the weights for each 
//	 * context entity
//	 */
//	private void setDisambiguationContext(List<WikifiableEntity> problemComponents) throws Exception {
//		disambiguationContext = problemComponents;
//		contextEntityWeights = new ArrayList<ContextEntityWeight>(disambiguationContext.size());   
//		for(int componentId=0; componentId<problemComponents.size(); componentId++) {
//			WikifiableEntity targetEntity=problemComponents.get(componentId);
//			ContextEntityWeight similaritiesSum = new ContextEntityWeight();
//			int sumCount = 0;
//			DisambiguationCandidate bestTargetDisambiguation =targetEntity.finalSolutionDisambiguation;
//			for(int i=0;bestTargetDisambiguation!=null&&i<problemComponents.size();i++) {
//				WikifiableEntity otherEntity=disambiguationContext.get(i);
//				if (Math.abs(otherEntity.startOffsetCharsInText-targetEntity.startOffsetCharsInText)<CoherenceWindowSize &&
//						i!=componentId) {
//					DisambiguationCandidate compareTo = null;
//					if(useUnambiguousInDisambiguationContext && otherEntity.isAlmostUnambiguous)
//						compareTo = otherEntity.almostUnambiguousSolution;
//					if(useNamedEntitiesInDisambiguationContext && 
//							otherEntity.types.containsKey(FeatureExractorGenerator.SurfaceFormEntitiesTypes.NER) && 
//							otherEntity.topDisambiguation!=null) 
//						compareTo = otherEntity.topDisambiguation;
//					if(useAllSurfaceFormsInDisambiguationContext && otherEntity.topDisambiguation!=null) 
//						compareTo = otherEntity.topDisambiguation;
//					if(useOnlyLinkedSurfaceFormsForDisambiguationContext&&otherEntity.finalSolutionDisambiguation==null)
//						compareTo = null;
//					if(useGoldSolutionsForCoherence)
//						compareTo = otherEntity.goldDisambiguationIfExists;
//					/*
//					 * There are a lot of coreferent phrases, and I don't want cliques to be formed when the same title is in two coreferenct entities
//					 */
//					if(compareTo!=null&&compareTo.getTid()!=bestTargetDisambiguation.getTid()) {
//						sumCount++;
//						PairwiseSemanticSim simProto = semSimAccess.getRelatednessProtobuffer(bestTargetDisambiguation.getTid(), compareTo.getTid());
//						if(simProto!=null) {
//							similaritiesSum.avgIncomingPmiRelatedness += simProto.getIncomingLinksPmi();
//							similaritiesSum.avgIncomingNormalizedGoogleDistanceRelatedness += simProto.getIncomingLinksNormalizedGoogleDistanceSim();
//							similaritiesSum.avgOutgoingPmiRelatedness  += simProto.getOutgoingLinksPmi();
//							similaritiesSum.avgOutgoingNormalizedGoogleDistanceRelatedness  += simProto.getOutgoingLinksPmi();
//							if(simProto.getTwoWayLink())
//								similaritiesSum.avgMutualLinks ++;
//						}
//					}// if there's something to compare to
//				} // if the entity is linked or we allow non-linked entities as well
//			}//for i;
//			if(sumCount>0) {
//				similaritiesSum.avgIncomingPmiRelatedness /= sumCount;
//				similaritiesSum.avgIncomingNormalizedGoogleDistanceRelatedness /= sumCount;
//				similaritiesSum.avgOutgoingPmiRelatedness  /= sumCount;
//				similaritiesSum.avgOutgoingNormalizedGoogleDistanceRelatedness  /= sumCount;
//				similaritiesSum.avgMutualLinks /=sumCount;
//			}
//			contextEntityWeights.insertElementAt(similaritiesSum, componentId);
//		}
//	}
	
//	public static class ContextEntityWeight{
//		double avgIncomingPmiRelatedness =0;
//		double avgIncomingNormalizedGoogleDistanceRelatedness = 0;
//		double avgOutgoingPmiRelatedness =0;
//		double avgOutgoingNormalizedGoogleDistanceRelatedness = 0;
//		double avgMutualLinks = 0;
//	}
}
