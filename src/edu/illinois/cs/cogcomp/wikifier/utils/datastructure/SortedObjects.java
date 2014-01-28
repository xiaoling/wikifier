package edu.illinois.cs.cogcomp.wikifier.utils.datastructure;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import edu.illinois.cs.cogcomp.wikifier.utils.Comparators;

public class SortedObjects<T> {
	int maxSize;
	public List<T> topObjects=new ArrayList<T>();
	public List<Double> topScores=new ArrayList<Double>();

	
	public SortedObjects(int capacity)
	{
		maxSize=capacity;
	}
	
	public SortedObjects(Map<T,? extends Number> scores){
	    for(Entry<T,? extends Number> e:scores.entrySet()){
	        add(e.getKey(),e.getValue().doubleValue());
	    }
	}
	
	public void add(T o,double score){
		topObjects.add(o);
		topScores.add(score);
		if(topObjects.size()>maxSize&&maxSize>-1){
			int minId=0;
			for(int i=0;i<topScores.size();i++)
				if(topScores.get(minId)>topScores.get(i))
					minId=i;
			topScores.remove(minId);
			topObjects.remove(minId);
		}
	}
	
	public String toString()
	{
		String res="";
		for(int i=0;i<topScores.size();i++)
			res+=(topObjects.get(i)+ "\t-\t"+topScores.get(i)+"\n");
		return res;
	}
	
	
	public void sort()
	{	
	    IdentityHashMap<T, Double> valueMap = new IdentityHashMap<>();
	    for(int i=0;i<topScores.size();i++){
	        valueMap.put(topObjects.get(i), topScores.get(i));
	    }
	    Collections.sort(topObjects,new Comparators.MapValueComparator(valueMap));
	    topScores.clear();
	    for(T t:topObjects){
	        topScores.add(valueMap.get(t));
	    }
//	    
//		for(int i=0;i<topScores.size();i++)
//			for(int j=i+1;j<topScores.size();j++){
//				if(topScores.get(i)<topScores.get(j))
//				{
//					double tempScore=topScores.get(i);
//					T tempWord=topObjects.get(i);
//					topObjects.set(i,topObjects.get(j));
//					topScores.set(i,topScores.get(j));
//					topObjects.set(j,tempWord);
//					topScores.set(j,tempScore);
//				}
//			}
	}	
	
	public static class SortedWords extends SortedObjects<String>{
	//    
	//  int maxSize;
	//  public List<String> topWords=new ArrayList<String>();
	//  public List<Double> topScores=new ArrayList<Double>();
	//
	    public SortedWords(int capacity)
	    {
	        super(capacity);
	    }
	    
	    public SortedWords(Map<String,? extends Number> scores){
	        super(scores);
	    }
	}
}
