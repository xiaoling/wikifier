package edu.illinois.cs.cogcomp.wikifier.inference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.Logger;

import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;
import edu.illinois.cs.cogcomp.wikifier.evaluation.Evaluator;
import edu.illinois.cs.cogcomp.wikifier.evaluation.ProblemEvaluationResult;
import edu.illinois.cs.cogcomp.wikifier.inference.features.FeatureExtractorCoherence;
import edu.illinois.cs.cogcomp.wikifier.inference.features.FeatureExtractorInterface;
import edu.illinois.cs.cogcomp.wikifier.inference.features.FeatureExtractorLexical;
import edu.illinois.cs.cogcomp.wikifier.inference.features.FeatureExtractorTitleMatchAndFrequency;
import edu.illinois.cs.cogcomp.wikifier.inference.features.SimilarityMetrics;
import edu.illinois.cs.cogcomp.wikifier.models.LinkingProblem;
import edu.illinois.cs.cogcomp.wikifier.models.Mention;
import edu.illinois.cs.cogcomp.wikifier.models.Mention.SurfaceType;
import edu.illinois.cs.cogcomp.wikifier.models.ReferenceInstance;
import edu.illinois.cs.cogcomp.wikifier.models.WikiCandidate;
import edu.illinois.cs.cogcomp.wikifier.utils.WordFeatures;
import gnu.trove.list.array.TDoubleArrayList;



public class InferenceEngine {
	public static boolean showLinkerDecisions = false;
	private static final Logger logger = Logger.getLogger(InferenceEngine.class);
	
    public static volatile long timeConsumedOnRanking = 0;
    public static volatile long timeConsumedOnLinking = 0;
	
	private boolean inTraining = false;
	public List<FeatureExtractorInterface> featureExtractors;
	
    private boolean featureUnitialized = true;
    private static final double[] nullSurfaceProperties = new double[9];
    static {
        Arrays.fill(nullSurfaceProperties, 0.0);
    }
	private transient final ReentrantLock levelLock = new ReentrantLock();
    /**
     * This is in place to prevent cross-level ranker feature generation.
     * Since ranker feature relies on different levels feature addition
     * We add more features as level progresses, however if the feature structure has changed
     * 
     */
	public InferenceEngine(boolean _inTraining) throws Exception{
		featureExtractors=initExtractorsAndModels(_inTraining);
		this.inTraining=_inTraining;
	}

	public void refreshPerformanceCounters(){
		for(FeatureExtractorInterface fi:featureExtractors) {
			fi.performanceBeforeLinker = new ProblemEvaluationResult();
			fi.performanceAfterLinker = new ProblemEvaluationResult();
		}
	}

	public void printPerformanceByInferenceLevel(){
		for(int i=0;i<featureExtractors.size();i++) {
			System.out.println("-----------------------------------------------------------------------------------");
			System.out.println("Performance after stage "+ featureExtractors.get(i).extractorName);
			System.out.println("------------- Before the linker: ");
			featureExtractors.get(i).performanceBeforeLinker.printPerformance();
			System.out.println("------------- After the linker: ");
			featureExtractors.get(i).performanceAfterLinker.printPerformance();
			System.out.println("-----------------------------------------------------------------------------------");
		}
	}
	

	/*
	 * If the tracePerformance flag is set to true, the function will fill in the complete
	 * breakup of the performance for each stage of inference. Remember that each feature
	 * extractor has a performance variable associated with it
	 * 
	 * Note that here the gold problem is given to track the performance!!! If tracePerformance=false, 
	 * the gold problems are never accessed and the null value can be passed
	 * 
	 * The pruning can be expensive, so in many cases it's a good idea not to do it!
	 * 
	 */
	public  List<ProblemEvaluationResult> annotate(LinkingProblem problem, 
			List<ReferenceInstance> goldProblem, boolean tracePerformance, 
			boolean useLinkerPredictionThreshold, double linkerPredictionThreshold) throws Exception {
		List<ProblemEvaluationResult> res =new ArrayList<ProblemEvaluationResult>();		
		long lastTime = System.currentTimeMillis();
		SimilarityMetrics.refreshCache();

		if(inTraining)
			throw new Exception("Attempt to run inference, while in the training mode");
		
		System.out.println("Inference on the document  -- "+ problem.sourceFilename);
		
		// Each thread should only be locked once
		if(featureUnitialized){
		    levelLock.lock();
		}

		//Doing the pipelined inference , possibly with pruning
		for(int level=0;level<featureExtractors.size();level++) {

			FeatureExtractorInterface extractor = featureExtractors.get(level);
			extractor.extractFeatures(problem);
			rankProblem(problem, extractor);
			// note that I'm applying the performance before running the linker so that I'll be able to estimate the benefit of the linker....
			// Only shows the last level to reduce output clutter
			if(tracePerformance && level == featureExtractors.size() - 1) 
				Evaluator.markWikificationCorrectness(problem, goldProblem, extractor.performanceBeforeLinker, "Level:"+extractor.extractorName);
			applyLinker(problem, extractor, useLinkerPredictionThreshold, linkerPredictionThreshold);
			if(tracePerformance && level == featureExtractors.size() - 1) 
				Evaluator.markWikificationCorrectness(problem, goldProblem, extractor.performanceAfterLinker, "Level:"+extractor.extractorName);
	    }
		
		if(featureUnitialized){
            levelLock.unlock();
            featureUnitialized = false;
        }
		
		// Added in 2013, re-ranks result using coherence relation data
		
		problem.deepRelationalInference();
		
		if(tracePerformance) 
			Evaluator.markWikificationCorrectness(problem, goldProblem, new ProblemEvaluationResult(), "Level: Relational Coherence ");
		
		
		if(GlobalParameters.params.disallowEntityOverlap) {
			System.out.println("Resolving overlapping entities - keeping only the entities with higher score ");				
			problem.resolveOverlapConflicts();
		}
		
		System.out.println("Annotation at test time--"+ (System.currentTimeMillis()-lastTime)+" milliseconds elapsed to annotate the document "+ problem.sourceFilename);

		problem.close();
		return res;
	}
	
	
		
	/*
	 * NOTE !!!
	 * This works with the assumption that all the necessary features 
	 * needed for the disambiguation candidates have been extracted 
	 * Make sure you run feature extractors before running this function!!!!
	 */
	public void rankProblem(LinkingProblem problem, FeatureExtractorInterface ranker) throws Exception{
		long firstTime=System.currentTimeMillis();
		// run the inference and choose the top candidates
		long lastTime =  System.currentTimeMillis();
		for(Mention e:problem.components)
			rankCandidates(e, ranker);
		System.out.println( System.currentTimeMillis()-lastTime+" milliseconds elapsed ranking the candidates at level..."+ranker.extractorName);
		timeConsumedOnRanking += (System.currentTimeMillis()-firstTime);
	}
	

	/*
	 *  Assume that the ranker was already applied
	 */
	public void applyLinker(LinkingProblem problem, FeatureExtractorInterface fe,
			boolean useLinkerPredictionThreshold, double linkerPredictionThreshold) throws Exception{
		long firstTime=System.currentTimeMillis();
		for(int i=0;i<problem.components.size();i++){
			Mention e = problem.components.get(i);
			if(e.topCandidate == null)
				continue;
			double[] linker_features = LiblinearInterface.trimAndScale(getLinkerFeatures(e), fe.linker_scaling_params);			
			 //System.out.println("The length of the linker weight vector = "+ParametersAndGlobalVariables.linkerWeights.length+"; the length of the feature vector is="+linkerFeatures.length);
			double linkerDecision=-1;
			if(e.topCandidate!=null) {
				e.linkerScore = LiblinearInterface.weightedBinaryClassification(linker_features, fe.linker_model);
				if(!useLinkerPredictionThreshold) {
					linkerDecision= LiblinearInterface.make_prediction(linker_features, fe.linker_model);
					if(showLinkerDecisions)
						System.out.println(" The linker decision for "+e.surfaceForm+"--->"+e.finalCandidate.titleName+" is "+linkerDecision);
				} else {
					if(e.linkerScore>linkerPredictionThreshold) 
						linkerDecision = 1.0;
					else
						linkerDecision = -1.0;
					if(showLinkerDecisions)
						System.out.println(" The score decision for "+e.surfaceForm+"--->"+e.finalCandidate.titleName+" is "+e.linkerScore+"; the linker decisions is:"+linkerDecision);
				}
					
			}
			/**
			 * Heuristically force linking single word top disambiguation
			 
				boolean forceLink = !e.surfaceForm.contains(" ");
			*/
			if(linkerDecision<0)
				e.finalCandidate=null;
		}
		timeConsumedOnLinking += (System.currentTimeMillis()-firstTime);
	}

	/*
	 * Assumes the entities were ranked, and the ranker scores have been assigned
	 */
	public static double[] getLinkerFeatures(Mention e) throws Exception{
		if(GlobalParameters.numberOfRankerFeatures==-1)
			throw new Exception("The number of expected ranker features is not set. " +
					"Is it possible that you're applying a linker before calling a Ranker, " +
					"and in particular: DisambiguationCandidate.getRankerFeatures() at any stage?");
		WikiCandidate best = e.topCandidate;
		double[] bestFeatures = e.topCandidate.getRankerFeatures();
		WikiCandidate secondBest = null;
		double[] secondBestFeatures = null;
		List<WikiCandidate> lastLevel = e.getLastPredictionLevel();
		for(int i=0;i<lastLevel.size();i++) {
			WikiCandidate c = lastLevel.get(i);
			if(!c.titleName.equals(best.titleName)) {
				if(secondBest==null||c.rankerScore>secondBest.rankerScore) {
					secondBest = c;
					secondBestFeatures = c.getRankerFeatures();
				}
			}
		}
		if(secondBest!=null&&best.rankerScore<secondBest.rankerScore)
			throw new Exception("Fatal error when extracting linker features-- the ranker score of the top prediction is lower than that of the second-ranked one");
		
		TDoubleArrayList feats=new TDoubleArrayList();
		// adding the surface form features: linkability, ambiguity, etc...
		feats.addAll(getSurfaceFormProperties(e));

		// adding 4 features: the ranker score of the top-ranked prediction, the ranker margin between the 
		// top-ranked prediction and the second-best, and the ratio of the scores (with and without platt scaling).....
		feats.add(best.rankerScore);
		if(secondBest!=null){
			feats.add(best.rankerScore-secondBest.rankerScore);
			if(best.rankerScore+secondBest.rankerScore>0)
				feats.add(best.rankerScore/(best.rankerScore+secondBest.rankerScore));
			else
				feats.add(0.0);
			double expConf = Math.exp(best.rankerScore-secondBest.rankerScore);
			feats.add(expConf/(expConf+1));
		} else {
			feats.add(best.rankerScore);
			feats.add(1.0);
			feats.add(1.0);
		}
	
		for(int i=0;i<bestFeatures.length;i++)
			feats.add(bestFeatures[i]);
		
		if(secondBest==null) {
			for(int i=0;i<bestFeatures.length;i++)
				feats.add(bestFeatures[i]);
			for(int i=0;i<bestFeatures.length;i++)
				feats.add(1.0);				
		} else {
			for(int i=0;i<bestFeatures.length;i++)
				feats.add(bestFeatures[i]-secondBestFeatures[i]);
			for(int i=0;i<bestFeatures.length;i++) {
				if(bestFeatures[i]+secondBestFeatures[i]>0)
					feats.add(bestFeatures[i]/(bestFeatures[i]+secondBestFeatures[i]));
				else
					feats.add(0.0);
			}
		}

		return feats.toArray();
	}

	private static double[] getSurfaceFormProperties(Mention e){
	    if(!e.types.contains(SurfaceType.NER)){
	        return nullSurfaceProperties;
	    }
	    TDoubleArrayList res = new TDoubleArrayList(9);
		double linkability = 0;
		double appCount = e.topCandidate.wikiData.linkabilityAppearanceCountTotal;			
        if (appCount > 0)
            linkability = ((double) e.topCandidate.wikiData.linakabilityAppearanceCountLinked) / appCount;	

//		Mention.SurfaceType[] types = new Mention.SurfaceType[]{Mention.SurfaceType.NER};

        // this is the feature from the web counts. I don't like it...
        // logDiff =
        // e.topDisambiguation.wikiData.logProbOnWebGoogleEstimate-e.topDisambiguation.wikiData.logProbAppearaceInWikipedia;
        res.add(e.topCandidate.wikiData.expectedProbOutOfWikipediaEntityGoodTuring);// "expectedProbOfOutOfWikipediaInstanceGoodTuring"+types[j]
        res.add(e.topCandidate.wikiData.surfaceFormAmbiguity);// "surfaceFormAmbiguity"+types[j],
        res.add(linkability);// "SurfaceFormLinkability"+types[j],
    
        res.add(appCount > 10 ? linkability : 0.0);// "ReliableSurfaceFormLinkability"+types[j]

        res.add(appCount > 50 ? linkability : 0.0);// "FrequentSurfaceFormLinkability"+types[j]

        res.add(appCount > 100 ? linkability : 0.0);// "ProminentSurfaceFormLinkability"+types[j],

        res.add(e.isAlmostUnambiguous ? 1.0 : 0.0);// "AlmostUnambiguousSurfaceForm",

        res.add(e.topCandidate.isAlmostUnambiguousSolution ? 1.0 : 0.0);// "AlmostUnambiguousSolution",

        res.add(WordFeatures.isCapitalized(e.surfaceForm) ? 1.0 : 0.0);// "UppercasedSurfaceForm"+types[j],      

        return res.toArray();
	}


	/*
	 *This function ranks the last level of candidates! 
	 * 
	 * The function fills the component.bestDisambiguation and the  component.rankerConfidence fields, 
	 * so it does not have to return anything however, we do return some debugging info- the confidence of the prediction
	 * 
	 * This function does two things:
	 * 1) A "looser is out" tournament between the candidates to determine the best prediction.
	 * 2) It applies the ranker prediction value on each element, and gets the ranker scores 
	 */
	public void rankCandidates(Mention entity, FeatureExtractorInterface fe) throws Exception{

        if (entity.candidates.size() == 0 || entity.getLastPredictionLevel().size() == 0) {
            entity.finalCandidate = null;
            entity.topCandidate = null;
            return;
        }
		
		// now refreshing all the ranker-related fields : reassigning the "top disambiguation at the previous stage" feature
		// first of all -- I have to refresh the "isTopDisambiguation" feature - set it to 0
		// also setting the ranker score to 0
		List<WikiCandidate> firstLevel = entity.candidates.get(0);
		for(WikiCandidate firstLevelCandidate:firstLevel) {
		    firstLevelCandidate.otherFeatures.addFeature(WikiCandidate.isCurrentlyTopPredictionFeaturename, 0.0);
		    firstLevelCandidate.rankerScore = 0;
		}

		List<WikiCandidate> candidates = entity.getLastPredictionLevel();
        entity.finalCandidate = candidates.get(0);
		for (WikiCandidate candidate:candidates) {
			try{
				double[] feat_diff = 
					 LiblinearInterface.trimAndScale( 
						vecDiff(candidate.getRankerFeatures(),entity.finalCandidate.getRankerFeatures()),
						fe.ranker_scaling_params);
					if(LiblinearInterface.make_prediction(feat_diff, fe.ranker_model) < 0)
						entity.finalCandidate = candidate;				
			} catch (Exception e) {
				logger.fatal("Exception caught at reranking level " + fe.getClass().getName());
		        logger.fatal("The current feature vector is: "+Arrays.toString(candidate.getRankerFeatures()));
		        logger.fatal("vs.: "+ Arrays.toString(entity.finalCandidate.getRankerFeatures()));
		        logger.fatal("There are "+fe.ranker_scaling_params.size()+ " elements in the scaling vector, which is: ");
		        logger.fatal(fe.ranker_scaling_params,e);
				e.printStackTrace();
				System.exit(0);
			}
		}
		entity.topCandidate = entity.finalCandidate;
		double maxRankerScore = Double.NEGATIVE_INFINITY;
		for (WikiCandidate c:candidates) {
			double[] features = LiblinearInterface.trimAndScale(c.getRankerFeatures(), fe.ranker_scaling_params);
			double ranker_score = LiblinearInterface.weightedBinaryClassification(features, fe.ranker_model);
			c.rankerScore = ranker_score;
			if(maxRankerScore<ranker_score)
				maxRankerScore = ranker_score;
		}
		
		if(entity.finalCandidate.rankerScore<maxRankerScore)
			throw new Exception("The top disambiguation does not have the top ranker score?!");
		// adding the feature marking that the top candidate is the top candidate.....
		entity.topCandidate.otherFeatures.addFeature(WikiCandidate.isCurrentlyTopPredictionFeaturename, 1.0);
	}
	
	/*
	 * returns v2-v1
	 */
	public static double[] vecDiff(double[] v1,double[] v2) throws IllegalArgumentException {
		if(v1.length!=v2.length)
			throw new IllegalArgumentException("Feature vectors of different length : v1.length="+v1.length+" vs v2.length="+v2.length);
		double[] res=new double[v1.length];
		for(int i=0;i<v1.length;i++)
			res[i]=v2[i]-v1[i];
		return res;
	}
	
	private  List<FeatureExtractorInterface> initExtractorsAndModels(boolean inTraining) throws Exception{
		List<FeatureExtractorInterface> extractors=new ArrayList<FeatureExtractorInterface>();
			extractors.add(new FeatureExtractorTitleMatchAndFrequency(
					"FeatureExtractorTitlesMatch",
					inTraining, GlobalParameters.paths.models));
			if(GlobalParameters.params.useLexicalFeaturesNaive||GlobalParameters.params.useLexicalFeaturesReweighted)
				extractors.add(new FeatureExtractorLexical(
						"FeatureExtractorLexical",
						inTraining, GlobalParameters.paths.models));
			if(GlobalParameters.params.useCoherenceFeatures)
				extractors.add(new FeatureExtractorCoherence(
						"FeatureExtractorCoherence",
						inTraining, GlobalParameters.paths.models));
		return extractors;
	}	
}
