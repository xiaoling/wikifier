package edu.illinois.cs.cogcomp.wikifier.inference.features;

import static edu.illinois.cs.cogcomp.wikifier.models.tfidf.TFIDF.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;
import edu.illinois.cs.cogcomp.wikifier.models.LinkingProblem;
import edu.illinois.cs.cogcomp.wikifier.models.Mention;
import edu.illinois.cs.cogcomp.wikifier.models.WikiCandidate;
import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.LexicalTitleDataInfoProto;
import edu.illinois.cs.cogcomp.wikifier.models.tfidf.TFIDF.TF_IDF_Doc;
import gnu.trove.map.hash.TIntDoubleHashMap;

public class FeatureExtractorLexical extends FeatureExtractorInterface{
	 
    
	public FeatureExtractorLexical(String _extractorName, boolean inTraining, String pathToSaves)  throws Exception{
		super(_extractorName, inTraining, pathToSaves);
	}
	
	public void extractFeatures(LinkingProblem problem) throws Exception {
		long lastTime = System.currentTimeMillis();
		FeatureExtractorThread[] threads = new FeatureExtractorThread[GlobalParameters.THREAD_NUM];
        for (int i = 0; i < threads.length; i++)
            threads[i] = new FeatureExtractorThread(this, problem);
        for (int componentId = 0; componentId < problem.components.size(); componentId++)
            threads[componentId % GlobalParameters.THREAD_NUM].addComponent(componentId);
		ExecutorService execSvc = Executors.newFixedThreadPool(GlobalParameters.THREAD_NUM);
		for(Thread thread:threads)
			execSvc.execute(thread);
		execSvc.shutdown();
		execSvc.awaitTermination(300,TimeUnit.SECONDS);
		featureExtractionTime += System.currentTimeMillis()-lastTime;
		System.out.println( System.currentTimeMillis()-lastTime+" milliseconds elapsed extracting features for the level: "+extractorName);
	}
	
	protected void extractFeatures(LinkingProblem problem,int componentId) throws Exception{
		Mention component = problem.components.get(componentId);		
        List<WikiCandidate> lastLevel = component.getLastPredictionLevel();
		// I first prepare the tf-idf lexical vectors. I prepare 2 versions : the tf-idf vectors corresponding to the original representation of the candidates
		// and a weighted one. For example if, I need to wikify the token "Chicago", it can mean the city the play and the musical. But all of them
		// will contain rather similar words relatively to the entire Wikipedia. Therefore, we treet the disambiguation candidates as a separate
		// document collection, and do a version of TF-IDF weighting on them. The problem is that the text and the context of the disambiguation
		// candidates is a TF-IDF weighted summary of the original text and context, so I need to do some weighted version of TF-IDF counting 
		List<TF_IDF_Doc> originalTextInfos = new ArrayList<>(lastLevel.size()); 
		List<TF_IDF_Doc> originalContextInfos = new ArrayList<>(lastLevel.size());
		double weightedTextDocCount = 0; // this works the the document count for TF-IDF, but instead of adding 1 for each doc, I add maxWeight in the document weight vector
		double weightedContextDocCount = 0;  // this works the the document count for TF-IDF, but instead of adding 1 for each doc, I add maxWeight in the document weight vector

//		double[] weightedTextIdfCount = new double[GlobalParameters.wikiAccess.getWikiSummaryData().tokensFeatureMap.dim];
//		double[] weightedContextIdfCount = new double[GlobalParameters.wikiAccess.getWikiSummaryData().tokensFeatureMap.dim];
		TIntDoubleHashMap weightedTextIdfCount = new TIntDoubleHashMap();
		TIntDoubleHashMap weightedContextIdfCount = new TIntDoubleHashMap();
		
		for(WikiCandidate c:lastLevel){
		    
		    LexicalTitleDataInfoProto lex = c.wikiData.lexInfo;
		    
		    // All text fids ini
		    TF_IDF_Doc originalText = new TF_IDF_Doc(lex.getTextTokensFidsCount(), true);
		    originalTextInfos.add(originalText);

			for(int j=0;j<lex.getTextTokensFidsCount();j++) {
				int fid = lex.getTextTokensFids(j);
				double weight = lex.getTextTokensFidsWeights(j);
				originalText.featureMap.put(fid, weight);
				weightedTextIdfCount.adjustOrPutValue(fid, weight, weight);
			}
			
            if (lex.getTextTokensFidsWeightsList().size() > 0)
                weightedTextDocCount += Collections.max(lex.getTextTokensFidsWeightsList());

			// Context ini
			TF_IDF_Doc originalContext = new TF_IDF_Doc(lex.getContextTokensFidsCount(), true);
			originalContextInfos.add(originalContext);
			
			for(int j=0;j<lex.getContextTokensFidsCount();j++) {
                int fid = lex.getContextTokensFids(j);
                double weight = lex.getContextTokensFidsWeights(j);
                originalContext.featureMap.put(fid, weight);
                weightedContextIdfCount.adjustOrPutValue(fid, weight, weight);
			}
            if (lex.getContextTokensFidsWeightsList().size() > 0)
                weightedContextDocCount += Collections.max(lex.getContextTokensFidsWeightsList());
		}
		
		List<TF_IDF_Doc> candidatesReweightedTextInfo = new ArrayList<>(lastLevel.size()); 
		List<TF_IDF_Doc> candidatesReweightedContextInfo = new ArrayList<>(lastLevel.size());
		for(TF_IDF_Doc originalText:originalTextInfos)
		    candidatesReweightedTextInfo.add(new TF_IDF_Doc(originalText,weightedTextDocCount,weightedTextIdfCount));
	    for(TF_IDF_Doc originalContext:originalContextInfos)
	        candidatesReweightedContextInfo.add(new TF_IDF_Doc(originalContext,weightedContextDocCount,weightedContextIdfCount));
		

		// ok, all the TF-IDF vectors have been computed, now we actually add the features
	    int i = -1;
		for(WikiCandidate c:lastLevel) { 
		    i++;
			if(GlobalParameters.params.useLexicalFeaturesNaive) {
	            generateFeatures(
	                     c.otherFeatures,
	                     problem.textVec,
	                     component.localContext,
	                     originalTextInfos.get(i),
	                     originalContextInfos.get(i)
	            );
			}
			if(GlobalParameters.params.useLexicalFeaturesReweighted) {
			    generateFeatures(
			            c.lexFeatures,
			            problem.textVec,
			            component.localContext,
			            candidatesReweightedTextInfo.get(i),
			            candidatesReweightedContextInfo.get(i)
			    );
			}
		}
	}
	
	private static void generateFeatures(
	        FeatureStructure f,
	        TF_IDF_Doc problemText,
	        TF_IDF_Doc[] problemContexts,
	        TF_IDF_Doc textInfo,
	        TF_IDF_Doc contextInfo){
        f.addFeature("Text-Text-TFIDF", getCosineSim(textInfo, problemText));
        f.addFeature("Context-Text-TFIDF", getCosineSim(contextInfo, problemText));
        
        int j = -1; for(TF_IDF_Doc local:problemContexts){ j++;
            f.addFeature("Text-Context-TFIDFwin"+j, getCosineSim(textInfo, local));
            f.addFeature("Context-Context-TFIDFwin"+j, getCosineSim(contextInfo, local));
        }
	}
}
	