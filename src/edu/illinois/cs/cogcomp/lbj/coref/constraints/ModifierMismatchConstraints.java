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

public class ModifierMismatchConstraints extends ConstraintBase implements Constraint{
	private final static String m_NAME = "ModifierMismatch";

	public ModifierMismatchConstraints() throws Exception
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

	public String getName() 
	{
		return m_NAME;
	}


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

	String[][] uncompatiable_firstWord = new String[][]{
			new String[]{"north","south","east","west"},new String[]{"northern","southern","eastern","western"}};

	protected static List<String[]> loadExclusiveSet(String filename) {
		ArrayList<String[]> exclusiveSet = new ArrayList<String[]>();
		List<String> lines = (new myIO()).readLines(filename);
		for (String line : lines) {
			if (line.length() <= 0 || line.startsWith("#"))
				continue;
			exclusiveSet.add(line.split(" ### "));
		}
		return exclusiveSet;
	}
	
	private double computeSimilarityScore( Mention m_, Mention a_ ) 
	{
		double score = 0.0;
		Doc d = m_.getDoc();
		// Check if pronounGender mismatch:
		String[] mwords = m_.getSurfaceText().toLowerCase().split(" ");
		String[] awords = a_.getSurfaceText().toLowerCase().split(" ");
		
		if(mwords.length>1 && mwords[0].equals("vice") && mwords[1].equals(awords[0]))
			score = 1.0;
		if(awords.length>1 && awords[0].equals("vice") && awords[1].equals(mwords[0]))
			score = 1.0;
		if(!mwords[0].equals(awords[0])){
			for(String[] set: uncompatiable_firstWord){
				if(myAux.isStringEquals(awords[0], set, false) &&myAux.isStringEquals(mwords[0], set, false))
					score = 1.0;
			}
		}
			
		return score*-1;
	}

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
