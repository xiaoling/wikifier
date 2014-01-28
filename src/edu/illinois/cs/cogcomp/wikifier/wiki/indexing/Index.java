package edu.illinois.cs.cogcomp.wikifier.wiki.indexing;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.util.Version;

import edu.illinois.cs.cogcomp.wikifier.utils.lucene.ASCIIEnglishAnalyzer;


public class Index<T> {

	static final Analyzer analyzer = new ASCIIEnglishAnalyzer(Version.LUCENE_43);
	static final QueryParser parser = new QueryParser(Version.LUCENE_43, "", analyzer);
	public static final Pattern nonAlphaNumericPattern = Pattern.compile(",|_|\\(|\\)|\"|'|-|\\.");
	
	public final String[] tokens;
	public final T reference;
	
	public Index(T ref,String representation){
		this.tokens = parse(representation);
		this.reference = ref;
	}
	
	public static String stripNonAlphnumeric(String s){
	    return nonAlphaNumericPattern.matcher(s).replaceAll(" ").trim().toLowerCase();
	}
	
	public static Set<String> getTokenSet(CharSequence s){
		return new HashSet<String>(Arrays.asList(parse(s)));
	}
	
	public static String[] noStemmingParse(String str){
	    String normalized = nonAlphaNumericPattern.matcher(str.toLowerCase()).replaceAll(" ");
	    return StringUtils.split(normalized,' ');
	}
	
	public static String[] parse(CharSequence str){
		String queryString = QueryParser.escape(nonAlphaNumericPattern.matcher(str).replaceAll(" "));
		try{
		    String[] tokens = StringUtils.split(parser.parse(queryString).toString(), ' ');
			for(int i = 0; i<tokens.length;i++){
			    tokens[i] = StringUtils.stripEnd(tokens[i], ".");
			}
			return tokens;
		}catch(Exception e){
			return new String[0];
		}
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(tokens);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Index<?> other = (Index<?>) obj;
		if (!Arrays.equals(tokens, other.tokens))
			return false;
		return true;
	}

	
}
