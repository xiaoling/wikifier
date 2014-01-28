package edu.illinois.cs.cogcomp.lbj.coref.constraints;


import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectInputStream.GetField;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import edu.illinois.cs.cogcomp.core.datastructures.IntPair;
import edu.illinois.cs.cogcomp.lbj.coref.features.GenderFeatures;
import edu.illinois.cs.cogcomp.lbj.coref.features.MentionProperties;
import edu.illinois.cs.cogcomp.lbj.coref.features.PronounResolutionFeatures;
import edu.illinois.cs.cogcomp.lbj.coref.ir.Mention;
import edu.illinois.cs.cogcomp.lbj.coref.ir.docs.Doc;
import edu.illinois.cs.cogcomp.lbj.coref.util.aux.myAux;
import edu.illinois.cs.cogcomp.lbj.coref.util.io.myIO;

abstract class NegativeConstraints extends ConstraintBase implements Constraint{

	public NegativeConstraints() throws Exception
	{
		super();
	}

	/**
	 * find all mentions for which this constraint has an effect, given the index of 
	 *   a specific mention in the document
	 */

	public Map< Mention, Double > findAppropriate(Doc d, int mIndex, boolean useGoldStandard ) 
	{  
		Double score = 1.0; // indicates forced coref with high belief
		HashMap< Mention, Double > corefs = new HashMap< Mention, Double >();

		List<Mention> allMentions = getAllMentions( d, useGoldStandard );

		Mention m = getMention( d, mIndex, useGoldStandard );

		String text = ConstraintBase.getMentionText( m ).toLowerCase();
		if ( m_DEBUG )
			System.err.println( "IPN.findAppropriate(): text is '" + text + "'" );

		if ( checkFirstMention( m ) )
		{
			for ( Mention laterMent : allMentions ) 
			{
				if ( laterMent.getExtentFirstWordNum() <= m.getExtentFirstWordNum() )
					continue;

				if ( checkFirstMention( laterMent ) )
				{
					String laterMentText = ConstraintBase.getMentionText( laterMent ).toLowerCase();
					if ( m_DEBUG )
						System.err.println( "## IPN.findAppropriate(): ment 1 is '" +
								text + "; ment 2 is '" + laterMentText + "'" );

					if ( computeSimilarityScore( m, laterMent ) > m_SIM_THRESHOLD )
						corefs.put( laterMent, score );

				}
			}
		}

		return corefs;
	}

	
	/**
	 * returns 'true' if Mention has properties indicating a person, 'false' otherwise
	 * @param m_
	 * @return
	 */

	


	public double checkConstraint(Mention firstMent_, Mention secondMent_,
			boolean useGoldStandard) 
	{
		double score = 0.0;



		if ( checkFirstMention( firstMent_ ) )
		{

			if ( checkFirstMention( secondMent_ ) )
				score = computeSimilarityScore( firstMent_, secondMent_ );
		}

		return score;
	}
	
	protected static Map<String,Integer> loadExclusiveSet(String filename) {
		Map<String, Integer> exclusiveSet = new HashMap<String, Integer>();
		List<String> lines = (new myIO()).readLines(filename);
		int idx = 0;
		for (String line : lines) {
			idx++;
			if (line.length() <= 0 || line.startsWith("#"))
				continue;
			for(String s : line.split(" ### ")){
				exclusiveSet.put(s.toLowerCase(), idx);
			}
		}
		return exclusiveSet;
	}
	protected int getGroupNum(Map<String,Integer> set, String head){
		head = head.toLowerCase();
		if(set.containsKey(head))
			return set.get(head);
		else
			return -1;
	}
	
	
	private int getGroupNum(String[][] lists, String head){
		for(int i=0; i< lists.length;i++){
			if(myAux.isStringEquals(head, lists[i], false))
				return i+1;
		}
		return -1;
	}
	
	protected abstract double computeSimilarityScore( Mention m_, Mention a_ ); 
	

	protected boolean checkFirstMention(Mention m_) 
	{
		try {
			return checkMention(m_);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}

	@Override	
	protected void configure(Properties p_) 
	{
		;
	}

	@Override
	protected boolean checkMention(Mention m_) throws Exception {
			return true;
	}
}
