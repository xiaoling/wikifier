package edu.illinois.cs.cogcomp.lbj.coref.constraints;


import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import edu.illinois.cs.cogcomp.lbj.coref.ir.Mention;
import edu.illinois.cs.cogcomp.lbj.coref.ir.docs.Doc;

public class NameEntitySurface extends ConstraintBase implements Constraint{
	private final static String m_NAME = "NameEntitySurface";
	private final static double m_SIM_THRESHOLD = 0.8;
	private final boolean m_DEBUG = false;
	Map <String, Integer> mentSurfaceToEntityIdx = null;

	@SuppressWarnings("unchecked")
    public NameEntitySurface() throws Exception
	{
		super();
		try {
    		ObjectInputStream oos = new ObjectInputStream(new FileInputStream("resources/mentPair.trainall"));
    		mentSurfaceToEntityIdx =(Map<String,Integer>)oos.readObject();
    		oos.close();
    	} catch (IOException e) {
    		e.printStackTrace();
    	} catch (ClassNotFoundException e) {
    		e.printStackTrace();
    	}
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

					if ( computeSimilarityScore( text, laterMentText ) > m_SIM_THRESHOLD )
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
				score = computeSimilarityScore( ConstraintBase.getMentionText( firstMent_ ).toLowerCase(), 
						ConstraintBase.getMentionText( secondMent_ ).toLowerCase() );
		}

		return score;
	}



	private double computeSimilarityScore( String text_, String text2_ ) 
	{
		double score = 0.0;
		
		if ( mentSurfaceToEntityIdx.get(text_) == mentSurfaceToEntityIdx.get(text2_))
			score = 1.0;

		return score;
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
		// TODO Auto-generated method stub
		if ( "NAM".equalsIgnoreCase( m_.getType() ) )
			return true;
		return false;
	}


}
