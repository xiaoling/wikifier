/**
 * 
 */
package edu.illinois.cs.cogcomp.wikifier.models.tfidf;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import edu.illinois.cs.cogcomp.wikifier.utils.io.StopWords;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;



public class Document
{
	
	public int classID=-1;
	public List<String> words;
	public String originalString=null;
	public int[] activeFeatures=null;
    public static boolean keepOriginaString = false;
	public String comment=null;//can be used to store different problem-specific stuff
	
	//public HashMap<String, Boolean> wordsHash=new HashMap<String, Boolean>();
	
	
	public Document(Document d)
	{
		if(keepOriginaString)
			this.originalString=d.originalString;
		this.classID=d.classID;
		words=new ArrayList<String>(d.words.size());
		for(int i=0;i<d.words.size();i++)
			words.add(d.words.get(i));
	}
	public Document(List<String> _words,int _classID,StopWords stops){
		classID=_classID;
		words=_words;
		if(stops!=null)
			words=stops.filterStopWords(words);
		originalString="";
		if(keepOriginaString)
			for(String word:words)
				originalString+=" "+word;
		//for(int i=0;i<words.size();i++)
		//	wordsHash.put(words.get(i), true);
	}

	public Document(String[] _words,int _classID,StopWords stops){
		classID=_classID;
		words=new ArrayList<String>();
		if(stops!=null)
			words=stops.filterStopWords(words);
		if(keepOriginaString)
			originalString="";
		for(String word:_words){
			words.add(word);
			if(keepOriginaString)
				originalString+=" "+word;
		}
		//for(int i=0;i<words.size();i++)
		//	wordsHash.put(words.get(i), true);
	}
	public boolean containsWord(String w)
	{
	    for(String word:words){
			if(word.equalsIgnoreCase(w))
				return true;
	    }
		return false;
		//return wordsHash.containsKey(w);
	}
	
	public int[] getActiveFid(FeatureMap map){
        TIntSet activeFids = new TIntHashSet((int) Math.floor((words.size()*1.3)));
		 for(String word:words){
			if(map.wordToFid.containsKey(word))
			{
                int fid = map.wordToFid.get(word);
				if(!activeFids.contains(fid))
					activeFids.add(fid);
			}
		}
		return activeFids.toArray();
	}
	
	public double[] getFeatureVec(FeatureMap map){
		double[] res=new double[map.dim];
		 for(String word:words)
			if(map.wordToFid.containsKey(word))
				res[map.wordToFid.get(word)]++;
		return res;
	}
	
	public void toCompactFeatureRep(FeatureMap map){
		if(words==null)
		{
			activeFeatures=null;
			return;
		}
		this.activeFeatures=getActiveFid(map);
		this.words=null;
	}
	
	public void tokenize(){
		StringTokenizer st=new StringTokenizer(tokenize(this.toString()));
		words=new ArrayList<String>();
		while(st.hasMoreTokens())
			words.add(st.nextToken());
	}
	
	public static String tokenize(String s)
	{
		String delims=",.?!;:<>-*&^%$#[]{}()/\\";
		StringBuffer res=new StringBuffer((int)(s.length()*1.5));
		for(int i=0;i<s.length();i++)
		{
			if(delims.indexOf(s.charAt(i))>-1)				
				res.append(' ');
			res.append(s.charAt(i));
			if(delims.indexOf(s.charAt(i))>-1)				
				res.append(' ');
		}
		s=res.toString();
		delims="'`";
		res=new StringBuffer((int)(s.length()*1.5));
		for(int i=0;i<s.length();i++)
		{
			if(delims.indexOf(s.charAt(i))>-1)				
				res.append(' ');
			res.append(s.charAt(i));
		}
		return res.toString();
	}


	public boolean allLetters(String w){
		w=w.toLowerCase();
		for(int i=0;i<w.length();i++)
			if((w.charAt(i)<'a')||(w.charAt(i)>'z'))
				return false;
		return true;
	}
	public String toString() {
		StringBuffer res=new StringBuffer(words.size()*10);
		for(String word:words)
			res.append(word+ " ");
		return res.toString();
	}
	public String toString(FeatureMap map,boolean verbose) {
		StringBuffer res=new StringBuffer(words.size()*10);
		for(String word:words){
			if(map.wordToFid.containsKey(word))
			{
				if(verbose)
					res.append(word+ " , ");
				else
					res.append(word+ " ");
			}
			else
				if(verbose)
					res.append("(?"+word+ "?) ");
		}
		return res.toString();
	}
}