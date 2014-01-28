package edu.illinois.cs.cogcomp.wikifier.inference.features;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;
import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.BasicTitleDataInfoProto;
import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.LexicalTitleDataInfoProto;
import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.SemanticTitleDataInfoProto;
import edu.illinois.cs.cogcomp.wikifier.wiki.access.WikiAccess;
import edu.illinois.cs.cogcomp.wikifier.wiki.access.WikiAccess.WikiMatchData;


public class SimilarityMetrics {
	public enum Link_Weighting_Scheme {UNWEIGHTED, PREVALENCE, SPECIFICITY, LOG_SPECIFICITY};
	public static final int Number_Concept_Pairs_Relatedness_To_Hash = 1000*1000; // if there are 1000 disambiguation candidates per problem, we need to hash the pairwise relatedness for all of them
		
	private static HashMap<String,double[]> conceptRelatednessHash = new HashMap<String, double[]>(Number_Concept_Pairs_Relatedness_To_Hash);
	
	public static final String[] semanticRelatednessFeaturenames = {
		"TitlesTextSim",
		"TitlesContextSim",
		"TitlesCategoryBowSim",
		"TiltesCategoryIdsSim",
		"IncomingLinkPmi",
		"InlinkCosineSimSpecificity",
		"InlinkCosineSimGoogleEditDistance",
		"OutgoingLinkPmi",
		"OutgoingCosineSimSpecificity",
		"OutgoingCosineSimGoogleEditDistance",
		"OneWayLink",
		"TwoWayLink",
	};
	
	
		/*
		 * res[0] = lexical similarity between the text of the two articles
		 * res[1] = lexical similarity between the context of the two articles
		 * res[2] = category keywords overlap between the two articles
		 * res[3] = category ids (as atomic ids) overlap 
		 * res[4] = PMI of incoming links of the two articles
		 * res[5] = weighted cosine similarity of the incoming links, where the weighting for incoming link titled titleID is SPECIFICITY: (numberWikipediaArticles/numberOutgoingLinks(titleId))
		 * res[6] = 1-normalized Google distance of the incoming links (as described in Milne's "An effective, low-cost measure of semantic relatedness obtained from Wikipedia links")
		 * res[7] = PMI of the outgoing links of the two articles
		 * res[8] = weighted cosine similarity of the outgoing links, where the weighting for outgoing link titled titleID is SPECIFICITY: log(numberWikipediaArticles/numberOutgoingLinks(titleId))
		 * res[9] = normalized Google distance of the outgoing links (as described in Milne's "An effective, low-cost measure of semantic relatedness obtained from Wikipedia links")
		 * res[10] = 1 if one of the articles points to the other, 0 otherwise
		 * res[11] = 1 if both articles point to each other, 0 otherwise.
		 */
	public static  double[] getRelatedness(WikiAccess.WikiMatchData concept1, WikiAccess.WikiMatchData concept2) throws Exception {
		int t1 = concept1.basicTitleInfo.getTitleId();
		int t2 = concept2.basicTitleInfo.getTitleId();
		String key = Math.min(t1, t2)+"-"+Math.max(t1,t2);
		
		double[] res = getCachedRelatedness(key);
		if(res!=null)
			return res;
	
		updateConceptSemanticInfo(concept1, concept2);
		
		res = HashMapSemanticInfoRepresentation.getRelatedness(concept1.dataForSemanticRelatednessComputation, 
				concept2.dataForSemanticRelatednessComputation);
		
		updateCache(key, res);
		return res;
	}
	
	private static synchronized void updateConceptSemanticInfo(WikiAccess.WikiMatchData concept1, WikiAccess.WikiMatchData concept2) throws Exception{
		if(concept1.dataForSemanticRelatednessComputation==null)
			concept1.dataForSemanticRelatednessComputation = new HashMapSemanticInfoRepresentation(concept1);
		if(concept2.dataForSemanticRelatednessComputation==null)
			concept2.dataForSemanticRelatednessComputation = new HashMapSemanticInfoRepresentation(concept2);

	}
	
	// empties the cache if there are too many elements; returns null if the key is not found
	private static synchronized double[] getCachedRelatedness(String key){
		if(conceptRelatednessHash.size()>Number_Concept_Pairs_Relatedness_To_Hash)
			conceptRelatednessHash.clear();
		if(conceptRelatednessHash.containsKey(key))
			return conceptRelatednessHash.get(key);
		return null;
	}
	
	private static synchronized void updateCache(String key, double[] res) {
		conceptRelatednessHash.put(key, res);
	}
	public static synchronized void refreshCache() {
		conceptRelatednessHash.clear();
	}

	public static void printRelatednessFeatures(double[] relatednessResult){
		for(int i=0;i<relatednessResult.length;i++)
			System.out.println(semanticRelatednessFeaturenames[i]+" - "+relatednessResult[i]);
	}
	

	private static double getWeight(int tid, Link_Weighting_Scheme weighting) throws Exception{
		if(weighting.equals(Link_Weighting_Scheme.UNWEIGHTED))
			return 1;		
		double incomingLinks = GlobalParameters.wikiAccess.getNumberIngoingLinks(tid);
		if(incomingLinks==0)
			incomingLinks = 0.01; // smoothing is done for articles which have no incoming links
		if(weighting.equals(Link_Weighting_Scheme.PREVALENCE))
			return incomingLinks;			
		if(weighting.equals(Link_Weighting_Scheme.LOG_SPECIFICITY))
			return Math.log((double)(GlobalParameters.wikiAccess.getTotalNumberOfWikipediaTitles()+1)/incomingLinks);
		if(weighting.equals(Link_Weighting_Scheme.SPECIFICITY))
				return 1.0/incomingLinks; //(double)ParametersAndGlobalVariables.wikiAccess.getTotalNumberOfWikipediaTitles()
		throw new Exception("Unknown weighting scheme "+weighting);
	}
	
	private static  double cosineSimUnitNormVectors(HashMap<Integer,Double> set1, HashMap<Integer,Double> set2) {
		HashMap<Integer,Double> min = set1;
		HashMap<Integer,Double> max = set2;		
		if(set1.size()>set2.size()) {
			max = set1;
			min = set2;
		}
		double prod = 0;
		for(Iterator<Integer> i = min.keySet().iterator(); i.hasNext() ;){
			Integer key = i.next();
			if(max.containsKey(key))
				prod+=min.get(key)*max.get(key);
		}
		return prod;
	}	
	
	public static  double normalizedGoogleDistanceSimilarity(int set1Size, int set2Size, int intersectionSize) {
		if(set1Size==0||set2Size==0||intersectionSize==0)
			return 0;
		double distance = (Math.log(Math.max(set1Size, set2Size))-Math.log(intersectionSize))/
			(Math.log(GlobalParameters.wikiAccess.getTotalNumberOfWikipediaTitles())- Math.log(Math.min(set1Size, set2Size)));
		return  1.0-distance;
	}

	
	public static  double approxPMI(int set1Size, int set2Size, int intersectionSize) {
		if(set1Size==0||set2Size==0||intersectionSize==0)
			return 0;
		return ((double)(1000*intersectionSize))/((double)(set1Size*set2Size));
	}
	
	public static HashMap<Integer, Boolean> getIntersection(HashMap<Integer,TitleWeightInfo> set1, HashMap<Integer,TitleWeightInfo> set2) {
		if(set1.size()==0||set2.size()==0)
			return new HashMap<Integer, Boolean>();
		HashMap<Integer,TitleWeightInfo> min = set1;
		HashMap<Integer,TitleWeightInfo> max = set2;
		if(set1.size()>set2.size()) {
			max = set1;
			min = set2;
		}
		HashMap<Integer, Boolean> res = new HashMap<Integer, Boolean>(min.size());
		for(Iterator<Integer> i = min.keySet().iterator(); i.hasNext() ;){
			Integer key = i.next();
			if(max.containsKey(key))
				res.put(key, true);
		}
		return res;
	}
	
	public static class HashMapSemanticInfoRepresentation implements Serializable{
		/**
		 * 
		 */
		private static final long serialVersionUID = 4782241571941589060L;
		// I don't save the norms because all the hashmap-vectors will be normalized (rescaled by the norm) during the construction
		int titleId = -1;
		HashMap<Integer,Double> textData = new HashMap<Integer, Double>(); 
		HashMap<Integer,Double> contextData = new HashMap<Integer, Double>();
		HashMap<Integer,Double> categoriesBowsData = new HashMap<Integer, Double>();
		HashMap<Integer,Double> categoriesIdsData = new HashMap<Integer, Double>();
		public HashMap<Integer,TitleWeightInfo> incomingLinks = new HashMap<Integer, TitleWeightInfo>();
		HashMap<Integer,TitleWeightInfo> outgoingLinks = new HashMap<Integer, TitleWeightInfo>();
				
		public HashMapSemanticInfoRepresentation(WikiAccess.WikiMatchData concept) throws Exception {
			init(concept.basicTitleInfo, concept.lexInfo, concept.semanticInfo);
			
		}
		public HashMapSemanticInfoRepresentation(BasicTitleDataInfoProto basicInfo, LexicalTitleDataInfoProto lexInfo, SemanticTitleDataInfoProto semanticInfo) throws Exception {
			init(basicInfo, lexInfo, semanticInfo);
		}
		public void init(BasicTitleDataInfoProto basicInfo, LexicalTitleDataInfoProto lexInfo, SemanticTitleDataInfoProto semanticInfo) throws Exception {
			titleId = basicInfo.getTitleId();
			double norm=0;
			for(int i=0; i<lexInfo.getTextTokensFidsCount(); i++)  
				norm += Math.pow(lexInfo.getTextTokensFidsWeights(i),2);
			norm = Math.sqrt(norm);
			for(int i=0; i<lexInfo.getTextTokensFidsCount(); i++) 
				textData.put(lexInfo.getTextTokensFids(i), lexInfo.getTextTokensFidsWeights(i)/norm);
			
			norm=0;
			for(int i=0; i<lexInfo.getContextTokensFidsCount(); i++) 
				norm += Math.pow(lexInfo.getContextTokensFidsWeights(i),2);
			norm = Math.sqrt(norm);
			for(int i=0; i<lexInfo.getContextTokensFidsCount(); i++) 
				contextData.put(lexInfo.getContextTokensFids(i), lexInfo.getContextTokensFidsWeights(i)/norm);
						
			norm=Math.sqrt(semanticInfo.getNormalizedCategoryTokensIdsCount()); // all the weights here are 1
			for(int i=0; i<semanticInfo.getNormalizedCategoryTokensIdsCount(); i++) 
				categoriesBowsData.put(semanticInfo.getNormalizedCategoryTokensIds(i),1.0/norm);
			
			norm=Math.sqrt(semanticInfo.getCategoryIdsCount()); // all the weights here are 1
			for(int i=0; i<semanticInfo.getCategoryIdsCount(); i++) 
				categoriesIdsData.put(semanticInfo.getCategoryIds(i), 1.0/norm);
			
			double specificityNorm = 0, unweightedNorm = 0, prevalenceNorm =0,  logSpecificityNorm=0;
			for(int i=0; i<semanticInfo.getIncomingLinksIdsCount(); i++) {
				TitleWeightInfo incoming = new TitleWeightInfo();
				int tid = semanticInfo.getIncomingLinksIds(i);
				incoming.specificity = getWeight(tid, Link_Weighting_Scheme.SPECIFICITY);
				specificityNorm +=incoming.specificity*incoming.specificity;
				incoming.unweighted = getWeight(tid, Link_Weighting_Scheme.UNWEIGHTED);
				unweightedNorm +=incoming.unweighted*incoming.unweighted;
				incoming.prevalence = getWeight(tid, Link_Weighting_Scheme.PREVALENCE);
				prevalenceNorm +=incoming.prevalence*incoming.prevalence;
				incoming.logSpecificity = getWeight(tid, Link_Weighting_Scheme.LOG_SPECIFICITY);
				logSpecificityNorm +=incoming.logSpecificity*incoming.logSpecificity;
				incomingLinks.put(tid, incoming);
			}						
			specificityNorm = Math.sqrt(specificityNorm);
			unweightedNorm = Math.sqrt(unweightedNorm);
			prevalenceNorm =Math.sqrt(prevalenceNorm);
			logSpecificityNorm = Math.sqrt(logSpecificityNorm);
			for(Iterator<Entry<Integer, TitleWeightInfo>> iter = incomingLinks.entrySet().iterator(); iter.hasNext();) {
				TitleWeightInfo info = iter.next().getValue();
				info.unweighted/=unweightedNorm;
				info.prevalence/=prevalenceNorm;
				info.specificity/=specificityNorm;
				info.logSpecificity/=logSpecificityNorm;
			}

			specificityNorm = unweightedNorm = prevalenceNorm =  logSpecificityNorm = 0;
			for(int i=0; i<semanticInfo.getOutgoingLinksIdsCount(); i++) {
				TitleWeightInfo outgoing = new TitleWeightInfo();
				int tid = semanticInfo.getOutgoingLinksIds(i);
				outgoing.specificity = getWeight(tid, Link_Weighting_Scheme.SPECIFICITY);
				specificityNorm +=outgoing.specificity*outgoing.specificity;
				outgoing.unweighted = getWeight(tid, Link_Weighting_Scheme.UNWEIGHTED);
				unweightedNorm +=outgoing.unweighted*outgoing.unweighted;
				outgoing.prevalence = getWeight(tid, Link_Weighting_Scheme.PREVALENCE);
				prevalenceNorm +=outgoing.prevalence*outgoing.prevalence;
				outgoing.logSpecificity = getWeight(tid, Link_Weighting_Scheme.LOG_SPECIFICITY);
				logSpecificityNorm +=outgoing.logSpecificity*outgoing.logSpecificity;
				outgoingLinks.put(tid, outgoing);
			}
			specificityNorm = Math.sqrt(specificityNorm);
			unweightedNorm = Math.sqrt(unweightedNorm);
			prevalenceNorm =Math.sqrt(prevalenceNorm);
			logSpecificityNorm = Math.sqrt(logSpecificityNorm);
			for(Iterator<Entry<Integer, TitleWeightInfo>> iter = outgoingLinks.entrySet().iterator(); iter.hasNext();) {
				TitleWeightInfo info = iter.next().getValue();
				info.specificity/=specificityNorm;
				info.unweighted/=unweightedNorm;
				info.prevalence/=prevalenceNorm;
				info.logSpecificity/=logSpecificityNorm;
			}
		}
		
		/*
		 * res[0] = lexical similarity between the text of the two articles
		 * res[1] = lexical similarity between the context of the two articles
		 * res[2] = category keywords overlap between the two articles
		 * res[3] = category ids (as atomic ids) overlap 
		 * res[4] = PMI of incoming links of the two articles
		 * res[5] = weighted cosine similarity of the incoming links, where the weighting for incoming link titled titleID is SPECIFICITY: (numberWikipediaArticles/numberOutgoingLinks(titleId))
		 * res[6] = 1-normalized Google distance of the incoming links (as described in Milne's "An effective, low-cost measure of semantic relatedness obtained from Wikipedia links")
		 * res[7] = PMI of the outgoing links of the two articles
		 * res[8] = weighted cosine similarity of the outgoing links, where the weighting for outgoing link titled titleID is SPECIFICITY: log(numberWikipediaArticles/numberOutgoingLinks(titleId))
		 * res[9] = normalized Google distance of the outgoing links (as described in Milne's "An effective, low-cost measure of semantic relatedness obtained from Wikipedia links")
		 * res[10] = 1 if one of the articles points to the other, 0 otherwise
		 * res[11] = 1 if both articles point to each other, 0 otherwise.
		 */
		public static  double[] getRelatedness(HashMapSemanticInfoRepresentation concept1, 
				HashMapSemanticInfoRepresentation concept2) throws Exception {
			double[] res = new double[12];
			res[0] = cosineSimUnitNormVectors(concept1.textData, concept2.textData);
			res[1] = cosineSimUnitNormVectors(concept1.contextData, concept2.contextData);
			res[2] = cosineSimUnitNormVectors(concept1.categoriesBowsData, concept2.categoriesBowsData);
			res[3] = cosineSimUnitNormVectors(concept1.categoriesIdsData, concept2.categoriesIdsData);
			
			HashMap<Integer, Boolean>  incomingIntersection =  getIntersection(concept1.incomingLinks,concept2.incomingLinks);
			res[4] = approxPMI(concept1.incomingLinks.size(),concept2.incomingLinks.size(), incomingIntersection.size());
			// I can do just the weighted intersection because the vectors are normalized
			for (Iterator<Integer> iter = incomingIntersection.keySet().iterator(); iter.hasNext(); ) {
				int tid = iter.next();
				// res[5]  += concept1.incomingLinks.get(tid).unweighted * concept2.incomingLinks.get(tid).unweighted;
				//res[6] += concept1.incomingLinks.get(tid).prevalence * concept2.incomingLinks.get(tid).prevalence;
				res[5] += concept1.incomingLinks.get(tid).specificity * concept2.incomingLinks.get(tid).specificity;
				// res[8] += concept1.incomingLinks.get(tid).logSpecificity * concept2.incomingLinks.get(tid).logSpecificity;
			}
			res[6] = normalizedGoogleDistanceSimilarity(concept1.incomingLinks.size(), concept2.incomingLinks.size(), incomingIntersection.size());
			
			HashMap<Integer, Boolean>  outgoingIntersection =  getIntersection(concept1.outgoingLinks,concept2.outgoingLinks);			
			res[7] = approxPMI(concept1.outgoingLinks.size(),concept2.outgoingLinks.size(), outgoingIntersection.size());
			for (Iterator<Integer> iter = outgoingIntersection.keySet().iterator(); iter.hasNext(); ) {
				int tid = iter.next();
				// res[9] += concept1.outgoingLinks.get(tid).unweighted * concept2.outgoingLinks.get(tid).unweighted;
				//res[11] += concept1.outgoingLinks.get(tid).prevalence * concept2.outgoingLinks.get(tid).prevalence;
				res[8] += concept1.outgoingLinks.get(tid).specificity * concept2.outgoingLinks.get(tid).specificity;
				// res[14] += concept1.outgoingLinks.get(tid).logSpecificity * concept2.outgoingLinks.get(tid).logSpecificity;
			}
			res[9] = normalizedGoogleDistanceSimilarity(concept1.outgoingLinks.size(), concept2.outgoingLinks.size(), outgoingIntersection.size());
			
			
			if(concept1.outgoingLinks.containsKey(concept2.titleId)||concept2.outgoingLinks.containsKey(concept1.titleId)) {
				//res[0] = Math.min(getWeight(concept1.titleId, Link_Weighting_Scheme.SPECIFICITY), getWeight(concept2.titleId, Link_Weighting_Scheme.SPECIFICITY));
				//res[1] = Math.max(getWeight(concept1.titleId, Link_Weighting_Scheme.SPECIFICITY), getWeight(concept2.titleId, Link_Weighting_Scheme.SPECIFICITY));
				//res[2] = Math.sqrt(getWeight(concept1.titleId, Link_Weighting_Scheme.SPECIFICITY) * getWeight(concept2.titleId, Link_Weighting_Scheme.SPECIFICITY));
				//res[1] =  approxPMI(concept1.incomingLinks.size(),concept2.incomingLinks.size(), incomingIntersection.size());
				res[10] =  1.0;
			}
			if(concept1.outgoingLinks.containsKey(concept2.titleId)&&concept2.outgoingLinks.containsKey(concept1.titleId)) {
				//res[3] = Math.min(getWeight(concept1.titleId, Link_Weighting_Scheme.SPECIFICITY), getWeight(concept2.titleId, Link_Weighting_Scheme.SPECIFICITY));
				//res[4] = Math.max(getWeight(concept1.titleId, Link_Weighting_Scheme.SPECIFICITY), getWeight(concept2.titleId, Link_Weighting_Scheme.SPECIFICITY));
				//res[5] = Math.sqrt(getWeight(concept1.titleId, Link_Weighting_Scheme.SPECIFICITY) * getWeight(concept2.titleId, Link_Weighting_Scheme.SPECIFICITY));
				// res[2] =  approxPMI(concept1.incomingLinks.size(),concept2.incomingLinks.size(), incomingIntersection.size());
				res[11] =  1.0;
			}
			return res;
		}		
	}
	public static class TitleWeightInfo{
		double unweighted = 1.0;
		double prevalence = 1.0;
		double specificity = 1.0;
		double logSpecificity = 1.0;
	}
}
