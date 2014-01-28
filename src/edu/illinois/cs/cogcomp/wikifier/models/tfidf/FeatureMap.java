package edu.illinois.cs.cogcomp.wikifier.models.tfidf;

import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.OccurrenceCounter;
import edu.illinois.cs.cogcomp.wikifier.utils.io.InFile;
import edu.illinois.cs.cogcomp.wikifier.utils.io.OutFile;
import edu.illinois.cs.cogcomp.wikifier.utils.io.StopWords;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


import com.google.common.collect.Lists;
import com.google.common.collect.Maps;



public class FeatureMap {
	public static boolean verbose=false;
	public static StopWords stops=null;
	
    public Map<String, Integer> wordToFid;
    public TIntObjectMap<String> fidToWord;
	public int dim=0;
	
	public FeatureMap(){
        wordToFid = new HashMap<String, Integer>();
        fidToWord = new TIntObjectHashMap<String>();
        dim = 0;
	}
	
	public void setStopWords(StopWords _stops){
		stops=_stops;
	}

	public FeatureMap(DocumentCollection docs,int appearanceThres,boolean countRepsWithinDoc){
        wordToFid = new HashMap<String, Integer>();
        fidToWord = new TIntObjectHashMap<String>();
		dim=0;
		addDocs(docs, appearanceThres, countRepsWithinDoc);
	}
	
	public FeatureMap(OccurrenceCounter invertedIndex,int appearanceThres){
		wordToFid=new HashMap<String, Integer>();
		fidToWord= new TIntObjectHashMap<String>();
        dim = 0;
		for(String token:invertedIndex){
			if(invertedIndex.getCount(token)>=appearanceThres&&
					(stops==null||!stops.isStopword(token)))
			{
				wordToFid.put(token,dim);
				fidToWord.put(dim, token);
				dim++;
			}
		}
	}
	
	public FeatureMap(FeatureMap _map){
		wordToFid=new HashMap<String, Integer>();
		fidToWord= new TIntObjectHashMap<String>();
		dim=_map.dim;
		for(Iterator<String> iter=_map.wordToFid.keySet().iterator();iter.hasNext();)
		{
			String w=iter.next();
			wordToFid.put(w, _map.wordToFid.get(w));
			fidToWord.put(_map.wordToFid.get(w), w);
		}
			
	}
	
	public void readFromFile(String countFiles,int thres){
		InFile in=new InFile(countFiles);
		List<String> tokens=in.readLineTokens();
		while(tokens!=null){
			int count=Integer.parseInt(tokens.get(0));
			if(count>=thres&&(stops==null||!stops.isStopword(tokens.get(1))))
			{
				wordToFid.put(tokens.get(1), dim);
				fidToWord.put(dim, tokens.get(1));
				dim++;
			}
			tokens=in.readLineTokens();
		}
	}
	
	public void save(String filename, int defaultCount){
		OutFile out=new OutFile(filename);
		for(Iterator<String> i=wordToFid.keySet().iterator();i.hasNext();out.println(defaultCount+"\t"+i.next()));
		out.close();
	}
	
	/*
	 * if countRepsWithinDoc is false, we basically require the word to appear in
	 * at least appearanceThres documents
	 */	
	public void addDocs(DocumentCollection docs,int appearanceThres,boolean countRepsWithinDoc)
	{
		UnigramStatistics stat=new UnigramStatistics(docs,countRepsWithinDoc);	
		for(Iterator<String> iter=stat.wordCounts.keySet().iterator();iter.hasNext();)
		{
			String w=iter.next();
			if(stat.wordCounts.get(w)>=appearanceThres&&(stops==null||!stops.isStopword(w)))
			{
				wordToFid.put(w, dim);
				fidToWord.put(dim,w);
				dim++;
			}
		}

		if(verbose)
			System.out.println("Done building a feature map, the dimension is: "+dim);
	}	
	public void addMoreDocsIgnoreAppearanceThres(DocumentCollection docs)
	{
		for(int i=0;i<docs.docs.size();i++)
		{
			List<String> words=docs.docs.get(i).words;
			for(String word:words)
				if(stops==null||!stops.isStopword(word)&&!wordToFid.containsKey(word))
				{
					wordToFid.put(word, dim);
					fidToWord.put(dim,word);
					dim++;
				}
		}			
		if(verbose)
			System.out.println("Done adding docs to a feature map, the dimension is: "+dim);
	}	
		
	public void addDimension(String dimensionName){
		if((stops==null||!stops.isStopword(dimensionName))&&!wordToFid.containsKey(dimensionName))
		{
			wordToFid.put(dimensionName, dim);
			fidToWord.put(dim,dimensionName);
			dim++;
		}		
	}
	
	public void addDimension(String featureName, int featureId){
		if((stops==null||!stops.isStopword(featureName))&&!wordToFid.containsKey(featureName))
		{
			wordToFid.put(featureName, featureId);
			fidToWord.put(featureId,featureName);
			if(featureId+1>dim)
				dim=featureId+1;
		}		
	}

	public static FeatureMap union(FeatureMap m1,FeatureMap m2){
		OccurrenceCounter count=new OccurrenceCounter();
		for(Iterator<String> iter=m1.wordToFid.keySet().iterator();iter.hasNext();)
			count.addToken(iter.next());
		for(Iterator<String> iter=m2.wordToFid.keySet().iterator();iter.hasNext();)
			count.addToken(iter.next());
		return new FeatureMap(count,0);
	}
	
	public static List<String> keepFeatureMapTokensOnlyNoRepetitions(List<String> tokens, FeatureMap map){
		 List<String>res= Lists.newArrayList();
		 Map<String,Boolean> h = Maps.newHashMapWithExpectedSize(tokens.size()*2);
		 for(int i=0;i<tokens.size();i++){
			 String token = tokens.get(i);
			 if(map.wordToFid.containsKey(token)&& !h.containsKey(token))
				 res.add(token);
			 h.put(token,true);
		 }
		 return res;		
	}
	
	public static List<String> keepFeatureMapTokensOnly(List<String> tokens, FeatureMap map){
		 List<String>res= Lists.newArrayList();
		 for(int i=0;i<tokens.size();i++){
			 String token = tokens.get(i);
			 if(map.wordToFid.containsKey(token))
				 res.add(token);
		 }
		 return res;		
	}

	public static List<String> removeRepetitions(List<String> v){
		 ArrayList<String>res= new  ArrayList<String>(v.size());
		 HashMap<String,Boolean> h =new HashMap<String,Boolean>(v.size()*2);
		 for(int i=0;i<v.size();i++){
			 if(!h.containsKey(v.get(i)))
				 res.add(v.get(i));
			 h.put(v.get(i),true);
		 }
		 return res;
	}
	public static String vecToStr(List<String> v){
		StringBuffer res=new StringBuffer(v.size()*20);
		for(int j=0;j<v.size();j++){
			res.append(v.get(j));
			res.append(' ');
		}
		return res.toString();
	}
}
