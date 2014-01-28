package edu.illinois.cs.cogcomp.lbj.coref.constraints;


import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import edu.illinois.cs.cogcomp.lbj.coref.features.GenderFeatures;
import edu.illinois.cs.cogcomp.lbj.coref.features.MentionProperties;
import edu.illinois.cs.cogcomp.lbj.coref.features.PronounResolutionFeatures;
import edu.illinois.cs.cogcomp.lbj.coref.ir.Mention;
import edu.illinois.cs.cogcomp.lbj.coref.ir.docs.Doc;
import edu.illinois.cs.cogcomp.lbj.coref.util.aux.myAux;

public class GenderMismatchConstraints extends ConstraintBase implements Constraint{
	private final static String m_NAME = "GenderMismatch";

	public GenderMismatchConstraints() throws Exception
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

	private String[] femaleHeadNoun = new String[]{"sister", "mother", "girl", "woman", "women", "grandmother",
			"godmother", "aunt","girl","wife","daughter","niece","congresswoman","wife","princess","ms","mrs.","miss"};
	
	private String[] maleHeadNoun = new String[]{"mr.", "mister", "king", "president", "prince", "brother",
			"grandfather","godfather","uncle","boy","husband","son","nephew","congressman"};
	private String[] malePRONoun = new String[]{"he", "his", "him", "himself"};
	private String[] femalePRONoun = new String[]{"she", "her", "herself"};
	
	
	

	private double computeSimilarityScore( Mention m_, Mention a_ ) 
	{
		double score = 0.0;
		
		
		boolean infemaleGroup = false;
		boolean inmaleGroup = false;


		// Check if pronounGender mismatch:
		if(myAux.isStringEquals(m_.getSurfaceText(), malePRONoun, false)){
			inmaleGroup = true;
		}
		if(myAux.isStringEquals(a_.getSurfaceText(), malePRONoun, false))
			inmaleGroup = true;
		if(myAux.isStringEquals(m_.getSurfaceText(), femalePRONoun, false))
			infemaleGroup = true;
		if(myAux.isStringEquals(a_.getSurfaceText(), femalePRONoun, false))
			infemaleGroup = true;
		
		// check head
		String mhead = m_.getHead().getText();
		String ahead = m_.getHead().getText();
		
		if(myAux.isStringEquals(mhead, maleHeadNoun, false))
			inmaleGroup = true;
		if(myAux.isStringEquals(ahead, maleHeadNoun, false))
			inmaleGroup = true;
		if(myAux.isStringEquals(mhead, femaleHeadNoun, false))
			infemaleGroup = true;
		if(myAux.isStringEquals(ahead, femaleHeadNoun, false))
			infemaleGroup = true;
		
		
		if(MentionProperties.resolvedMentionProp(m_).containsKey("gender")){
			if(MentionProperties.resolvedMentionProp(m_).get("gender").equals("male"))
				inmaleGroup= true;
			else
				infemaleGroup= true;
		}
		if(MentionProperties.resolvedMentionProp(a_).containsKey("gender")){
			if(MentionProperties.resolvedMentionProp(a_).get("gender").equals("male"))
				inmaleGroup= true;
			else
				infemaleGroup= true;
		}
		// check firstname
		
		if(infemaleGroup && inmaleGroup)
			score = 1.0;
			
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
