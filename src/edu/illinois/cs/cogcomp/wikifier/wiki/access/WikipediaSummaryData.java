package edu.illinois.cs.cogcomp.wikifier.wiki.access;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.BasicTitleDataInfoProto;
import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.LexicalTitleDataInfoProto;
import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.SemanticTitleDataInfoProto;
import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.WikiDataSummaryProto;
import edu.illinois.cs.cogcomp.wikifier.models.tfidf.Document;
import edu.illinois.cs.cogcomp.wikifier.models.tfidf.FeatureMap;
import edu.illinois.cs.cogcomp.wikifier.models.tfidf.TFIDF;
import edu.illinois.cs.cogcomp.wikifier.models.tfidf.TFIDF.TF_IDF_Doc;
import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.StringMap;
import edu.illinois.cs.cogcomp.wikifier.utils.io.InFile;
import gnu.trove.map.hash.TIntDoubleHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;

public class WikipediaSummaryData {
	public int numWikipediaArticles = 0;
	public FeatureMap tokensFeatureMap = new FeatureMap();
	public TFIDF tfidfTokensWeights =null;
	public Map<String,Integer> coarseNerTypeToTypeId = new StringMap<>();//
	public TIntObjectHashMap<String> coarseNerTypeIdToString = new TIntObjectHashMap<>(); // this is needed to display the compressed docs 
	public Map<String,Integer> topicToTopicId = new StringMap<>();
	public TIntObjectHashMap<String> topicIdToTopicToken = new TIntObjectHashMap<>(); // this is needed to display the compressed docs  
	public Map<String, String> categoryNormalization = new StringMap<>(); // categoryNormalization(token) is the normalized version of the lowercased token
	public Map<String, Integer> normalizedCatTokenToId = new StringMap<>();
	public TIntObjectHashMap<String> catTokenToIdToNormalizedCatToken = new TIntObjectHashMap<>(); // this is needed to display compressed documents
	
	/*
	 * Can return repetitions!
	 */
	public List<String> getStemmedCategoryKeywordsFromText(String inputText) {
		List<String> res= new ArrayList<String>();
		List<String> tokenized = InFile.aggressiveTokenize(inputText.toLowerCase());
		for(String token:tokenized)
			if(categoryNormalization.containsKey(token))
				res.add(categoryNormalization.get(token));
		return res;
	}
	
	/*
	 * Note that this will lowercase the text, even though this is an internal matter.
	 */
	public TF_IDF_Doc getTextRepresentation(String text, boolean preloadHashMaps) {
		TF_IDF_Doc res =tfidfTokensWeights.getRepresentation(new Document(InFile.aggressiveTokenize(text.toLowerCase()), -1, null), false);
		if(preloadHashMaps){
			res.featureMap = new TIntDoubleHashMap();
            for (int i = 0; i < res.activeFids.length; i++)
                res.featureMap.put(res.activeFids[i], res.tfIdfWeight[i]);
		}
		return res;
	}
	
	/*
	 * Can return  repetitions!
	 */
	public List<Integer> getStemmedCategoryKeywordIdsFromText(String inputText) {
		List<Integer> res= new ArrayList<Integer>();
		List<String> cats =  getStemmedCategoryKeywordsFromText(inputText) ;
		for(int i=0; i<cats.size(); i++)
			res.add(normalizedCatTokenToId.get(cats.get(i)));
		return res;
	}

	public WikipediaSummaryData(){
		// do nothing;
	}
	
	public WikipediaSummaryData(WikiDataSummaryProto proto) {
		System.out.println("Consructing wikipedia summary from a proto buffer");
		numWikipediaArticles = proto.getNumberOfTitles();
		for(int i=0;i<proto.getTokensCount(); i++) 
			tokensFeatureMap.addDimension(proto.getTokens(i),proto.getTokenFids(i));
		tfidfTokensWeights = new TFIDF(tokensFeatureMap);
		tfidfTokensWeights.docsCount = numWikipediaArticles;
		for(int i=0;i<proto.getTokensCount(); i++)
			tfidfTokensWeights.IDF[proto.getTokenFids(i)] = proto.getTokensIdfCounts(i);
		for(int i=0; i<proto.getPossibleCoarseNerTypesCount(); i++) {
			coarseNerTypeToTypeId.put(proto.getPossibleCoarseNerTypes(i), proto.getPossibleCoarseNerTypesIds(i));
			coarseNerTypeIdToString.put(proto.getPossibleCoarseNerTypesIds(i),proto.getPossibleCoarseNerTypes(i));
		}
		for(int i=0; i<proto.getPossibleTopicsCount(); i++) {
			topicToTopicId.put(proto.getPossibleTopics(i), proto.getPossibleTopicsIds(i));
			topicIdToTopicToken.put(proto.getPossibleTopicsIds(i),proto.getPossibleTopics(i));
		}
		for(int i=0; i<proto.getUnnormalizedCategoryTokensCount();i++)
			categoryNormalization.put(proto.getUnnormalizedCategoryTokens(i), proto.getMappingToNormalizedCategoryTokenVersion(i));
		for(int i=0; i<proto.getNormalizedCategoryTokensCount();i++) {
			normalizedCatTokenToId.put(proto.getNormalizedCategoryTokens(i), proto.getNormalizedCategoryTokensIds(i));
			catTokenToIdToNormalizedCatToken.put(proto.getNormalizedCategoryTokensIds(i), proto.getNormalizedCategoryTokens(i));
		}
		System.out.println("Done - consructing wikipedia summary from a proto buffer");
	}
	
	public WikiDataSummaryProto saveToProtobuffer(){
		WikiDataSummaryProto.Builder res = WikiDataSummaryProto.newBuilder();
		res.setNumberOfTitles(numWikipediaArticles);
		// token information
		for( Iterator<Entry<String, Integer>> i = tokensFeatureMap.wordToFid.entrySet().iterator();i.hasNext();) {
			Entry<String, Integer> e = i.next();
			res.addTokens(e.getKey());
			res.addTokenFids(e.getValue());
			res.addTokensIdfCounts(tfidfTokensWeights.IDF[e.getValue()]);
		}
		// coarse NER types
		for( Iterator<Entry<String, Integer>> i = coarseNerTypeToTypeId.entrySet().iterator();i.hasNext();) {
			Entry<String, Integer> e = i.next();
			res.addPossibleCoarseNerTypes(e.getKey());
			res.addPossibleCoarseNerTypesIds(e.getValue());
		}
		// topics
		for( Iterator<Entry<String, Integer>> i = topicToTopicId.entrySet().iterator();i.hasNext();) {
			Entry<String, Integer> e = i.next();
			res.addPossibleTopics(e.getKey());
			res.addPossibleTopicsIds(e.getValue());
		}
		// category keyword normalization
		for( Iterator<Entry<String, String>> i = categoryNormalization.entrySet().iterator();i.hasNext();) {
			Entry<String, String> e = i.next();
			res.addUnnormalizedCategoryTokens(e.getKey());
			res.addMappingToNormalizedCategoryTokenVersion(e.getValue());
		}
		// normalized category keyword to id map
		for( Iterator<Entry<String, Integer>> i = normalizedCatTokenToId.entrySet().iterator();i.hasNext();) {
			Entry<String, Integer> e = i.next();
			res.addNormalizedCategoryTokens(e.getKey());
			res.addNormalizedCategoryTokensIds(e.getValue());
		}
		return res.build();
	}
		
	public String toDebugString(BasicTitleDataInfoProto basic) {
		StringBuffer res = new StringBuffer(10000);
		res.append("Title Id = "+basic.getTitleId()+"\n");
		res.append("Title Form = "+basic.getTitleSurfaceForm()+"\n");
		res.append("Title Appearance count = "+basic.getTitleAppearanceCount()+"\n");
		res.append("Number of outgoing links = "+basic.getNumberOfOugoingLinks()+"\n");
		res.append("Number of incoming links = "+basic.getNumberOfIngoingLinks()+"\n");
		res.append("Coarse Ner Types =");
		for (int i=0;i<basic.getCoarseNerTypesIdsCount();i++)
			res.append(" "+coarseNerTypeIdToString.get(basic.getCoarseNerTypesIds(i)));
		res.append("\nTopics:");
		for (int i=0;i<basic.getTopicsIdsCount();i++)
			res.append(" ["+topicIdToTopicToken.get(basic.getTopicsIds(i))/*+"-"+basic.getTopicsRelevanceScores(i)*/+"]");
		res.append('\n');
		return res.toString();
	}
	public String toDebugString(LexicalTitleDataInfoProto lex) {
		StringBuffer res = new StringBuffer(10000);
		res.append("Text Summary =");
		for (int i=0;i<lex.getTextTokensFidsCount();i++)
			res.append(" ["+tokensFeatureMap.fidToWord.get(lex.getTextTokensFids(i))+"-"+lex.getTextTokensFidsWeights(i)+"]");
		res.append('\n');
		res.append("Context Summary =");
		for (int i=0;i<lex.getContextTokensFidsCount();i++)
			res.append(" ["+tokensFeatureMap.fidToWord.get(lex.getContextTokensFids(i))+"-"+lex.getContextTokensFidsWeights(i)+"]");
		res.append('\n');
		return res.toString();
	}
	public String toDebugString(SemanticTitleDataInfoProto sem) {
		StringBuffer res = new StringBuffer(10000);
		res.append("Normalized category keywords =");
		for (int i=0;i<sem.getNormalizedCategoryTokensIdsCount();i++)
			res.append(" "+catTokenToIdToNormalizedCatToken.get(sem.getNormalizedCategoryTokensIds(i)));
		res.append('\n');
		res.append("Category ids as atomic ids =");
		for (int i=0;i<sem.getCategoryIdsCount();i++)
			res.append(" "+sem.getCategoryIds(i));
		res.append('\n');
		res.append("Incoming links (title ids) =");
		for (int i=0;i<sem.getIncomingLinksIdsCount();i++)
			res.append(" "+sem.getIncomingLinksIds(i));
		res.append('\n');
		res.append("Outgoing links (title ids) =");
		for (int i=0;i<sem.getOutgoingLinksIdsCount();i++)
			res.append(" "+sem.getOutgoingLinksIds(i));
		res.append('\n');
		return res.toString();
	}
}
