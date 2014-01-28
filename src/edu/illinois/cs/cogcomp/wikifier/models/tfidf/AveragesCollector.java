package edu.illinois.cs.cogcomp.wikifier.models.tfidf;

import java.util.HashMap;
import java.util.Iterator;

public class AveragesCollector {
	public HashMap<String,Double> keyToScoresSum = new HashMap<String, Double>(); 
	public HashMap<String,Integer> keyAppearancesCounts = new HashMap<String, Integer>(); 

	public void addKey(String key, double weight, boolean ignoreZero) {
		if(weight==0&&ignoreZero)
			return;
		double sum = weight;
		if(keyToScoresSum.containsKey(key))
			sum += keyToScoresSum.get(key);
		keyToScoresSum.remove(key);
		keyToScoresSum.put(key, sum);
		int app = 1;
		if(keyAppearancesCounts.containsKey(key))
			app += keyAppearancesCounts.get(key);
		keyAppearancesCounts.remove(key);
		keyAppearancesCounts.put(key, app);
	}
	
	public void displayAverages() {
		for(Iterator<String> i=keyAppearancesCounts.keySet().iterator();i.hasNext();){
			String key = i.next();
			System.out.println("Average for "+key+"="+keyToScoresSum.get(key)/((double)keyAppearancesCounts.get(key))+" ("+keyAppearancesCounts.get(key)+" appearances)");
		}
	}
}
