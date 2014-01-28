package edu.illinois.cs.cogcomp.wikifier.models.tfidf;
import java.util.HashMap;
import java.util.List;

import edu.illinois.cs.cogcomp.wikifier.utils.io.InFile;





public class UnigramStatistics {
	public static boolean verbose=false;
	
	public HashMap<String, Integer> wordCounts=new HashMap<String, Integer>();
	boolean countRepsWithinDocs=false;
	public int totalWordCount=0;
	/*
	 * if countRepsWithinDoc is false, increase occurrence count
	 * only when the word appears in distince documents
	 */
	public  UnigramStatistics(String filename,FeatureMap map){
		InFile in=new InFile(filename);
		List<String> tokens=in.readLineTokens();
		while(tokens!=null){
			for(int i=0;i<tokens.size();i++)
				if(map.wordToFid.containsKey(tokens.get(i)))
					addWord(tokens.get(i));
			tokens=in.readLineTokens();
		}
	}

	public  UnigramStatistics(DocumentCollection docs,boolean _countRepsWithinDocs)
	{
		countRepsWithinDocs=_countRepsWithinDocs;
		if(verbose)
			System.out.println("Building unigram statistics");
		for(int i=0;i<docs.docs.size();i++)
		{
			addDoc(docs.docs.get(i));
		}			
		if(verbose)
			System.out.println("Done building unigram statistics");
	}
	/*
	 * if countRepsWithinDoc is false, increase occurrence count
	 * only when the word appears in distince documents
	 */		
	public  UnigramStatistics(boolean _countRepsWithinDocs)
	{
		countRepsWithinDocs=_countRepsWithinDocs;
	}
	
	public void addDoc(Document doc)
	{
		HashMap<String, Boolean> alreadyAppreared=new HashMap<String, Boolean>();
		List<String> words=doc.words;
		for(String word:words)
		{
			if(countRepsWithinDocs||!alreadyAppreared.containsKey(word))
			{
				addWord(word);
				alreadyAppreared.put(word, true);
			}
		}		
	}
	
	public void addWord(String w){
		totalWordCount++;
		if(!wordCounts.containsKey(w))
		{
			wordCounts.put(w, 1);
		}
		else
		{
			int count=wordCounts.get(w);
			wordCounts.remove(w);
			wordCounts.put(w, count+1);
		}
	}
}
