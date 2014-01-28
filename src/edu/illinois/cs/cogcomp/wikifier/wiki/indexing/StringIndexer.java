package edu.illinois.cs.cogcomp.wikifier.wiki.indexing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import edu.illinois.cs.cogcomp.wikifier.utils.WikiTitleUtils;
import edu.illinois.cs.cogcomp.wikifier.utils.WordFeatures;
import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.StringCounter;


/**
 * Indexes the string representation of objects
 * @author cheng88
 *
 * @param <T>
 */
public abstract class StringIndexer<T> {
	
	protected Map<String,Set<T>> index = new HashMap<String,Set<T>>();
	protected StringCounter counter = new StringCounter();
	public final Set<T> currentItems = new HashSet<T>();
	
	/**
	 * We require that all tokens of the query has to be present
	 * @param query
	 * @return A list of matching objects with default query operator AND
	 */
	public List<T> search(String query){
		
	    // Search acronyms mode
	    if(WordFeatures.isLikelyAcronym(query)){
	        String normalized = WikiTitleUtils.getAcronym(query).toLowerCase();
	        Set<T> results = index.get(normalized);
	        List<T> retList = new ArrayList<T>();
	        if(results!=null)
	            retList.addAll(results);
	        return retList;
	    }
	    
		Set<T> results = new HashSet<T>();
		String[] tokens = Index.noStemmingParse(query);
		
		if(tokens.length == 0 || index.get(tokens[0]) == null)
			return Collections.emptyList();
		
		// Retrieval
		// initialization
		results.addAll(index.get(tokens[0]));
		
		for(int i = 1 ; i < tokens.length ; i++){
			
		    String token = tokens[i];
			
			Set<T> mentions = index.get(token);
			
			if(mentions!=null)
				results.retainAll(mentions);
			else
				return Collections.emptyList();
		}
		
		return new ArrayList<T>(results);
	}

	public void addToIndex(T entity){
		if(currentItems.contains(entity))
		    return;
		currentItems.add(entity);
		String title = indexStr(entity).trim();
		
		counter.count(Index.stripNonAlphnumeric(title));
		
		String joinedAcronym = WikiTitleUtils.getAcronym(title);
		String fullAcronym = WikiTitleUtils.getAcronymAllLetters(title);

		List<String> tokens = new ArrayList<String>(Arrays.asList(Index.noStemmingParse(title)));
		tokens.add(joinedAcronym.toLowerCase());
		tokens.add(fullAcronym.toLowerCase());
		
		for(String token: tokens){
		    if(StringUtils.isEmpty(token))
		        continue;
			Set<T> invertedIndex = index.get(token);
			
			if(invertedIndex == null){
				invertedIndex = new HashSet<T>();
				index.put(token, invertedIndex);
			}
			
			invertedIndex.add(entity);
		}
	}

	protected abstract String indexStr(T t);
	
	public int getIndexedTitleCount(String title) {
		return counter.getCount(title);
	}
	
}