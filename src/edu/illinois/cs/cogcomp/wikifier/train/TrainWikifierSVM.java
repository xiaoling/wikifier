package edu.illinois.cs.cogcomp.wikifier.train;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import liblinear.Parameter;
import liblinear.Problem;
import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;
import edu.illinois.cs.cogcomp.wikifier.inference.InferenceEngine;
import edu.illinois.cs.cogcomp.wikifier.inference.LiblinearInterface;
import edu.illinois.cs.cogcomp.wikifier.inference.features.FeatureExtractorInterface;
import edu.illinois.cs.cogcomp.wikifier.inference.features.SimilarityMetrics;
import edu.illinois.cs.cogcomp.wikifier.models.LinkingProblem;
import edu.illinois.cs.cogcomp.wikifier.models.Mention;
import edu.illinois.cs.cogcomp.wikifier.models.ReferenceInstance;
import edu.illinois.cs.cogcomp.wikifier.models.TextSpan;
import edu.illinois.cs.cogcomp.wikifier.models.WikiCandidate;
import edu.illinois.cs.cogcomp.wikifier.wiki.access.TitleNameNormalizer;

public class TrainWikifierSVM {
	public static double unlinkingRatio = 0.0; // with this probability we'll remove the correct solution from the candidates when training the linker
	public static final boolean displayUnsolvableEntities = false;
	public static Random rand = new Random(31);
	// If this is true, there's is no penalty at Wikifying an unmarked phrase. The only penalty happens when  a phrase is wikified INCORRECTLY
	public static boolean unlink_only_ranker_errors = true; 
	// The two parameters below are rather problem-sensitive and feature-set-sensitive.
	public static double weight_of_positive_linker_sample = 1; 
	public static double weight_of_negative_linker_sample = 1;   
	
	
	public static void trainRankerLinkerWeights(String pathToProblems, String pathToRawText) throws Exception{		
		String[] files = (new File(pathToProblems)).list();
		List<String> pathsToReferenceInstances = new ArrayList<String>();
		for (int i=0; i<files.length; i++){
            File f = new File(pathToProblems + "/" + files[i]);
			if(f.isFile())
				pathsToReferenceInstances.add(pathToProblems+"/"+files[i]);
		}
		train(pathToRawText, pathsToReferenceInstances);
	}

		
	 /*
	  * This training procedure trains all the levels, which is pretty wasteful, because we really need the rankers
	  * only before the levels that require FeatureExtractorInterface.doRerankingAndPotentialPruningBeforeThisStage
	  * but to do this properly takes a lot of programming work, so I'm just brute- forcing things....
	  */
	public static void train (String pathToRawText, List<String> pathsToReferenceInstances) throws Exception{
		//int originalNumber = ParametersAndGlobalVariables.maxCandidatesToGenerateInitially;
		//ParametersAndGlobalVariables.maxCandidatesToGenerateInitially = 3;
		InferenceEngine inference = new InferenceEngine(true);
		Problem svm_problem;
		for(int level=0;level<inference.featureExtractors.size();level++) {
			FeatureExtractorInterface extractor = inference.featureExtractors.get(level);
			SvmTrainingData ranker_training_set=new SvmTrainingData();
			// note that here, since I cannot store all the problems in the memory at once, I need to load the problem, and to 
			// simulate inference for level-1 extractors. For this step - it's feature extraction, ranking, AND pruning. Once
			// we're done with it, I'm just applying the ranker for the last level, but no not do the pruning yet!!!
			for (int probid=0;probid<pathsToReferenceInstances.size();probid++) {
				long lastTime = System.currentTimeMillis();
				try{
					List<ReferenceInstance> referenceInstances = ReferenceInstance.loadReferenceProblem(pathsToReferenceInstances.get(probid));
					if(referenceInstances.size()>0) {
						GoldAnnotation gold = new GoldAnnotation(referenceInstances);
						String rawText = edu.illinois.cs.cogcomp.wikifier.utils.io.InFile.readFileText(pathToRawText+"/"+referenceInstances.get(0).rawTextFilename);
						LinkingProblem problem= new LinkingProblem(referenceInstances.get(0).rawTextFilename, 
							GlobalParameters.curator.getTextAnnotation(rawText), 
							referenceInstances);
						prepareProblemForLevel_K(problem, referenceInstances, true, pathToRawText, level, inference);
						extractor.extractFeatures(problem);
						// get the gold annotation and add the training samples to the training dataset
						for(int j=0; j<problem.components.size(); j++) {
							Mention e = problem.components.get(j);
							gold.addTrainingRankerSamples(e, ranker_training_set);
						}
						System.out.println("Training the ranker--"+ (System.currentTimeMillis()-lastTime)+" milliseconds elapsed to annotate the document "+ 
								problem.sourceFilename+ " with the features up to level "+extractor.extractorName+
								"; "+gold.rankerSamplesAdded+" ranker samples generated for this problem");
						problem.close();
					} else {
						System.out.println("Big warning the problem for the file "+pathsToReferenceInstances.get(probid) +" was empty");
					}
				} catch (Exception e){
					System.out.println("Probably a curator problem when processing the problem "+pathsToReferenceInstances.get(probid)+
							"; the path to the text was:" +pathToRawText);
					e.printStackTrace();
				}
			}
			// ok, the training dataset for this level is built -- now train the ranker model 
			 svm_problem = ranker_training_set.BuildSvmProblem();
			double[] scaling = LiblinearInterface.scale(svm_problem);
			Parameter param = LiblinearInterface.getOptimalParam(svm_problem /*, 1 ,1*/);
			extractor = inference.featureExtractors.get(level);
			System.out.println("-----------------------------------------------------------------------------------------");
			System.out.println(svm_problem.x.length+ "training samples generated for the ranker");
			System.out.println("Saving the training data for level "+level+" : "+ extractor.extractorName);
			LiblinearInterface.save_problem(svm_problem, extractor.pathPatternToSavesData+".ranker.trainingData");
			LiblinearInterface.saveScalingParams(scaling, extractor.pathPatternToSavesData+".ranker.scaling");
			System.out.println("Training and saving the ranker model"); 
			LiblinearInterface.train_and_save(svm_problem, param, extractor.pathPatternToSavesData+".ranker.model");
			System.out.println("Loading the ranker model to the feature extractor"); 
			extractor.ranker_model = LiblinearInterface.loadModel(extractor.pathPatternToSavesData+".ranker.model");
			extractor.ranker_scaling_params = LiblinearInterface.loadScalingParams(extractor.pathPatternToSavesData+".ranker.scaling");
			System.out.println("Crossvalidation ranker performance at extraction level "+level+
					" :" +extractor.extractorName+ " is : " + LiblinearInterface.do_cross_validation(svm_problem, param)+
					"("+extractor.ranker_scaling_params.size()+" features at this level)");
			System.out.println("-----------------------------------------------------------------------------------------");

			// now the ranker for this level is trained, so I'm training the  linker for this level
			SvmTrainingData linker_training_set=new SvmTrainingData();
			for (int probid=0;probid<pathsToReferenceInstances.size();probid++) {
				long lastTime = System.currentTimeMillis();
				try{
					List<ReferenceInstance> referenceInstances = ReferenceInstance.loadReferenceProblem(pathsToReferenceInstances.get(probid));
					if(referenceInstances.size()>0){					
						GoldAnnotation gold = new GoldAnnotation(referenceInstances);
						String rawText = edu.illinois.cs.cogcomp.wikifier.utils.io.InFile.readFileText(pathToRawText+"/"+referenceInstances.get(0).rawTextFilename);
						LinkingProblem problem= new LinkingProblem(referenceInstances.get(0).rawTextFilename, 
								GlobalParameters.curator.getTextAnnotation(rawText), 
								referenceInstances);
						// Now I want to delete the correct disambiguation from the disambiguation candidates list
						// I  do it for some random sample of surface forms in order to train the linker on non-trivial data
						for(int componentId=0;componentId<problem.components.size();componentId++){
							Mention e = problem.components.get(componentId);
							if(e.candidates.size()!=1)
								throw new Exception("There should be only one level of candidates at this stage....");
							List<WikiCandidate> firstLevel = e.candidates.get(0);
							if(rand.nextDouble()<unlinkingRatio) 	{
								WikiCandidate solution = gold.getGoldDisambiguationCandidate(e, firstLevel);
								if(solution!=null){
									for(int i=0;i<e.candidates.size();i++) {
										List<WikiCandidate> v = e.candidates.get(i);
										int pos = 0;
										while(pos<v.size()){
											if(v.get(pos).titleName.equals(solution.titleName)) {
												v.remove(pos);
												pos = 0;
											} else {
												pos++;
											}
										}
									}
								}
								for(int i=0;i<e.candidates.size();i++)
									if(gold.getGoldDisambiguationCandidate(e, e.candidates.get(i))!=null)
										throw new Exception("The correct solutions is expected to be removed (if existed), yet it still appears in the disambiguation candidates list....");
							}
						}
						prepareProblemForLevel_K(problem, referenceInstances, true, pathToRawText, level, inference);
						extractor.extractFeatures(problem);
						inference.rankProblem(problem, extractor);
						// get the gold annotation and add the training samples to the training dataset
						for(int j=0; j<problem.components.size(); j++) {
							Mention e = problem.components.get(j);
							gold.addTrainingLinkerSamples(e, linker_training_set, inference);
						}
						System.out.println("Training the linker--"+ (System.currentTimeMillis()-lastTime)+" milliseconds elapsed to annotate the document "+
								problem.sourceFilename+ " with the features up to level "+extractor.extractorName+
								"; "+gold.linkerSamplesAdded+" linker samples generated for this problem");
					} else {
						System.out.println("Big warning the problem for the file "+pathsToReferenceInstances.get(probid) +" was empty");
					}
				} catch (Exception e){
					System.out.println("Probably a curator problem when processing the problem "+pathsToReferenceInstances.get(probid)+
							"; the path to the text was:" +pathToRawText);
					e.printStackTrace();
				}
			}
			// ok, the training dataset for linker is built -- now train the model 
			svm_problem = linker_training_set.BuildSvmProblem();
			scaling = LiblinearInterface.scale(svm_problem);
			param = LiblinearInterface.getOptimalParam(svm_problem /* ,weight_of_positive_linker_sample, weight_of_negative_linker_sample*/);
			System.out.println("-----------------------------------------------------------------------------------------");
			System.out.println(svm_problem.x.length+ "training samples generated for the linker");
			System.out.println("Saving the training data for the linker ");
			LiblinearInterface.save_problem(svm_problem, extractor.pathPatternToSavesData+".linker.trainingData");
			LiblinearInterface.saveScalingParams(scaling, extractor. pathPatternToSavesData+".linker.scaling");
			System.out.println("Training and saving the linker model"); 
			LiblinearInterface.train_and_save(svm_problem,param,extractor.pathPatternToSavesData+".linker.model");
			System.out.println("Loading the linker model"); 
			extractor.linker_model = LiblinearInterface.loadModel(extractor.pathPatternToSavesData+".linker.model");
			extractor.linker_scaling_params =  LiblinearInterface.loadScalingParams(extractor. pathPatternToSavesData+".linker.scaling");
			System.out.println("Crossvalidation linker performance at level : "+ extractor.extractorName+" is: "+ 
					LiblinearInterface.do_cross_validation(svm_problem, param));
			System.out.println("-----------------------------------------------------------------------------------------");		
		} // rankers and linkers by level	
		//ParametersAndGlobalVariables.maxCandidatesToGenerateInitially= originalNumber;
	}

	/*
	 * level must range between 0 and inference.featureExtractors.size()-1
	 * 
	 * NOTE THAT THIS GUY RANKS THE CANDIDATES ONLY WITH K-1
	 * LEVELS. THE REASON IS THAT WHEN TRAINING FOR LEVEL K, WE DO
	 * NOT HAVE THE MODEL FOR THAT LAYER, SO WE CANNOT RANK!!!
	 */
	public static void prepareProblemForLevel_K(LinkingProblem problem,
			List<ReferenceInstance> referenceInstances,  boolean doStageByStagePruning, 
			String pathToRawText, int level, InferenceEngine inference) throws Exception{
		System.out.println("Preparing the problem for annotation with level "+level);
		System.out.println("refreshing the similarity metric hash...");
		SimilarityMetrics.refreshCache();
		System.out.println("reading the problem definition...");
		// here I'm mimicking the inference for level-1 layers.
		for (int sublevel = 0; sublevel < level ; sublevel ++) {
			FeatureExtractorInterface extractor = inference.featureExtractors.get(sublevel);
			System.out.println("extracting features level "+extractor.extractorName+"...");
			extractor.extractFeatures(problem);
			System.out.println("Re-ranking the  candidates for level "+extractor.extractorName+"...");
			inference.rankProblem(problem, extractor);
			System.out.println("Linking the  candidates for level "+extractor.extractorName+"...");
			inference.applyLinker(problem, extractor, false, 0);
		}			
		System.out.println("Done - Constructing and preparing the problem for annotation with level "+level);
	}
	
	public static class GoldAnnotation{
		public int rankerSamplesAdded =0;
		public int linkerSamplesAdded =0;
		HashMap<String, String> gold_mention_mapping=new HashMap<String, String>(); // the key is the start and the end of the mention, he value is the Wikipedia page  
		/*
		 * The evaluator is used to resolve the gold and the predicted wikifications to the latest version of Wikipedia
		 * this solves all sorts of versioning problems
		 */
		public GoldAnnotation(List<ReferenceInstance> goldAnnotation) throws Exception{
			for (int i=0; i<goldAnnotation.size(); i++) {
				ReferenceInstance ri = goldAnnotation.get(i);
				int start = ri.charStart;
				int end = ri.charStart+ri.charLength;
				gold_mention_mapping.put(start+"-"+end, TitleNameNormalizer.normalize(ri.chosenAnnotation));
			}
		}
		
		/*
		 * The component is needed for knowing the disambiguation candidates of ne. Many entities can be clustered into a single
		 * component, and the disambiguation candidates are generated per component.
		 */
		public void addTrainingRankerSamples(Mention ne,  SvmTrainingData trainingSet) throws Exception {
			//System.out.println("Checking out training samples for entity "+ne.surfaceForm+"[" +ne.startOffsetCharsInText+"-"  + ne.entityLengthChars+"]");
			if(isReferenceInstance(ne)) {
				WikiCandidate gold = getGoldDisambiguationCandidate(ne, false);
				if(gold!=null) {
					List<WikiCandidate> lastLevel = ne.getLastPredictionLevel();
					if(lastLevel.size()==1)
						System.out.println("\t- Only a single option for the entity at this level (possibly due to pruning), not generating any taining data for the ranker");
					for(int i=0;i<lastLevel.size();i++) {
						WikiCandidate candidate = lastLevel.get(i);
						if (!candidate.titleName.equals(gold.titleName)) {
							double[] gold_feats = gold.getRankerFeatures();
							double[] other_feats = candidate.getRankerFeatures();
							if(Math.random()<0.01) {
								System.out.println("\t- Adding a ranker training point: "+gold.titleName+" >> "+ candidate.titleName);
								System.out.println("Features for "+gold.titleName+":");
								System.out.println(gold.toString());
								System.out.println("Features for "+candidate.titleName+":");
								System.out.println(candidate.toString());
							}
							try {
								if(rand.nextDouble()<0.5)
									trainingSet.addDataPoint(InferenceEngine.vecDiff(other_feats, gold_feats),1);
								else
									trainingSet.addDataPoint(InferenceEngine.vecDiff(gold_feats, other_feats),-1);
								rankerSamplesAdded++;
							} catch (Exception e) {
								System.out.println("Exception caught when generating the ranker training data. The feature vectors were:\nGold:");
								System.out.println(gold.toString());
								System.out.println("Non-gold:");
								System.out.println(candidate.toString());
								e.printStackTrace();
								System.exit(0);
							}
						}
					}
				} else {
					// if the surface form has no correct solution
					System.out.println("\t - The entity is mapped to *null* in the gold data, cannot train a ranker with it, only the linker");
				}
			} else {
				//System.out.println("\t-The entity was not specified in the taining data.");
			}
		}
		
		/*
		 * The component is needed for knowing the disambiguation candidates of ne. Many entities can be clustered into a single
		 * component, and the disambiguation candidates are generated per component.
		 */
		public void addTrainingLinkerSamples(Mention e, SvmTrainingData trainingSet,InferenceEngine inference) throws Exception {
			// there is some code duplication here to avoid printing warnings to the screen
			if(unlink_only_ranker_errors) {
				// Here we unlink only the surface forms for which the ranker makes a mistake.
				// So first of all, we need to check that the ranker even makes a prediction. The second
				// criteria is that we can only track  mistakes on surface form for which we have a gold disambiguation,
				// so the surface form must be defined as a part of the reference instances
				if(e.topCandidate!=null) {
					linkerSamplesAdded++;
					if(isReferenceInstance(e)) {
					// In the below, gold can still be null if the correct solution does not appear in the disambiguation candidates list
						if (getGoldDisambiguationCandidate(e, false)!=null  && 
								getGoldDisambiguationCandidate(e, false).getTid()==   
								e.topCandidate.getTid())  {
								trainingSet.addDataPoint(inference.getLinkerFeatures(e),1);
						} else {
								trainingSet.addDataPoint(inference.getLinkerFeatures(e),-1);
						}
					}
				}
			} else {
				// here, we try to unlink not only the entities where we're making mistakes, but also the entities which 
				// were not marked in the problem. Even here, it makes sense to add the surface forms as negative samples
				// only if the ranker makes some kind of prediction. If it doesn't, the surface form is not going to be linked
				// anyway. However, in this scenario, if the surface form is not given in the reference instance, it's considered
				// non- linkable. So the criterion for adding a positive training point is that the surface form is linkable,
				// and the disambiguation is done correctly
				if(e.topCandidate!=null) {
					linkerSamplesAdded++;
					if(isReferenceInstance(e) && 
							getGoldDisambiguationCandidate(e, false) !=null  && 
							getGoldDisambiguationCandidate(e, false).getTid()
							==e.topCandidate.getTid()) {
						trainingSet.addDataPoint(inference.getLinkerFeatures(e),1);
				}
				else
						trainingSet.addDataPoint(inference.getLinkerFeatures(e),-1);
				}
			}
		}
		
		// Tells whether the surface form was mentioned in the problem definition.
		// NOTE that even if it was mentioned, it can be marked as non-linkable (that is a surface form
		// which doesn't have a corresponding Wikipedia page)
		public boolean isReferenceInstance(int entityStart,int entityLen) {
			return gold_mention_mapping.containsKey(entityStart+"-"+(entityStart+entityLen));
		}
		
        public boolean isReferenceInstance(TextSpan span) {
            return isReferenceInstance(span.charStart,span.charLength);
        }
		
		// will return null if the surface form (entity) is not part of the problem definition by the user, 
		// that is it will return null if the entity is not a reference instance. Otherwise, it'll return the
		// mapping, which can take the value "null" (as a string) in the case the surface form does not
		// have a corresponding Wikipedia page
		public String getGoldWikificationAsString(int entityStart,int entityLen) {
			if(isReferenceInstance(entityStart,entityLen)) {
				String gold = gold_mention_mapping.get(entityStart+"-"+(entityStart+entityLen));
				return gold;
			}
			return null;			
		}
		
		/* 
		 * If the solution exists in the disambiguation candidate list, return the solution. Otherwise, return null
		* this function is used to train ranker by pairing up the solution with all the non-solution candidates
		* Note that this function has 2 different flavors - during the training, the solution is checked against
		* the last layer, because only the last layer has all the necessary features extracted. But during the
		* evaluation, we check for the solution against the first layer, because it checks if the solution
		* ever was in the candidates list, before all the pruning
		*/
		public WikiCandidate getGoldDisambiguationCandidate(Mention e, boolean useFirstLevel) throws Exception {
			List<WikiCandidate> disambiguationCandidates = e.getLastPredictionLevel();
			if(useFirstLevel)
				disambiguationCandidates = e.candidates.get(0);
			return getGoldDisambiguationCandidate(e,disambiguationCandidates);
		}		
		/* 
		 * If the solution exists in the disambiguation candidate list, return the solution. Otherwise, return null
		* this function is used to train ranker by pairing up the solution with all the non-solution candidates
		* Note that this function has 2 different flavors - during the training, the solution is checked against
		* the last layer, because only the last layer has all the necessary features extracted. But during the
		* evaluation, we check for the solution against the first layer, because it checks if the solution
		* ever was in the candidates list, before all the pruning
		*/
		public WikiCandidate getGoldDisambiguationCandidate(Mention e, 
				List<WikiCandidate>  disambiguationCandidates) throws Exception {
			if(isReferenceInstance(e)) {
				String gold = getGoldWikificationAsString(e.charStart,e.charLength);
				for(int i=0;i<disambiguationCandidates.size();i++) {
					WikiCandidate candidate =disambiguationCandidates.get(i);
					if(TitleNameNormalizer.normalize(candidate.titleName).equals(
							TitleNameNormalizer.normalize(gold)))
						return candidate;
				}
				if(displayUnsolvableEntities)
					System.out.println("Warning: no correct disambiguation found for: "
							+e.surfaceForm+"["+e.charStart+","+(e.charStart+e.charLength) +
							"] gold solution =" +gold+"; useFirstLevel="+disambiguationCandidates+" ; There were "+disambiguationCandidates.size()+
							" disambiguation candidates for this entity");
			}
			return null;
		}		
	}
		
	public static class SvmTrainingData {
		private List<double[]> samples=new ArrayList<double[]>();
		private List<Integer> values = new ArrayList<Integer>();
		
		public void addDataPoint(double[] x,int y) {
			samples.add(x);
			values.add(y);
		}
		
		public liblinear.Problem BuildSvmProblem() throws Exception {
			if(samples.size()==0)
				throw new Exception("No training data!!!");
			liblinear.Problem res = new liblinear.Problem();
			res.l=samples.size();
			res.x=new liblinear.FeatureNode[samples.size()][];
			res.y=new int[samples.size()];
			for( int i=0;i<samples.size();i++){
				liblinear.FeatureNode[] sample = LiblinearInterface.featureVectorToSvmFormat(samples.get(i));
				res.x[i]=sample;
				res.y[i]=values.get(i);
			}
			res.bias = 0;
			res.l = res.y.length;
			res.n = res.x[0].length+1;
			return res;
		}//func BuildSvmProblem
	}//class SvmTrainingData
			
	public static void main(String[] args) throws Exception{
		//ParametersAndGlobalVariables.loadConfig("./Config/ACE_Dev_With_Gold_Entities.txt");
		//trainRankerLinkerWeights("SelectedCorpora/Milne/MilneProblemsKnownBoundaries/", "SelectedCorpora/Milne/MilneWikifiedStoriesRawText/");
		System.out.println("Usage: CommonSenseWikifier.TrainingAndInference.TrainWikifierSVM <pathToGoldAnnotations> <pathToRawTexts> <pathToConfigFile> ");
		GlobalParameters.loadConfig(args[2]);
		trainRankerLinkerWeights(args[0],args[1]);
	}
}



