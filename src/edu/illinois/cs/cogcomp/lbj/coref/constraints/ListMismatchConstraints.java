package edu.illinois.cs.cogcomp.lbj.coref.constraints;


import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import edu.illinois.cs.cogcomp.lbj.coref.Parameters;
import edu.illinois.cs.cogcomp.lbj.coref.features.GenderFeatures;
import edu.illinois.cs.cogcomp.lbj.coref.features.MentionProperties;
import edu.illinois.cs.cogcomp.lbj.coref.features.PronounResolutionFeatures;
import edu.illinois.cs.cogcomp.lbj.coref.ir.Mention;
import edu.illinois.cs.cogcomp.lbj.coref.ir.docs.Doc;
import edu.illinois.cs.cogcomp.lbj.coref.util.aux.myAux;
import edu.illinois.cs.cogcomp.lbj.coref.util.io.myIO;

public class ListMismatchConstraints extends NegativeConstraints implements Constraint{
	private final static String m_NAME = "ListMismatch";
	Map<String,Integer> exclusiveList;
	Map<String,Integer> firstNameList;
	Map<String,Integer> lastNameList;
	
	public ListMismatchConstraints() throws Exception
	{
		super();
		exclusiveList = loadExclusiveSet(Parameters.pathToExclusiveList);
		firstNameList = loadExclusiveSet(Parameters.pathToFirstName);
		lastNameList = loadExclusiveSet(Parameters.pathToLastName);
	}

	/**
	 * find all mentions for which this constraint has an effect, given the index of 
	 *   a specific mention in the document
	 */



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

	
	protected double computeSimilarityScore( Mention m_, Mention a_ ) 
	{
		double score = 0.0;
		Doc d = m_.getDoc();
		//only constraints on short mention
		if(m_.getSurfaceText().split(" ").length > 3 || a_.getSurfaceText().split(" ").length > 3)
			return 0.0;
		String mfirst = m_.getSurfaceText().split(" ")[0].toLowerCase();
		String afirst = a_.getSurfaceText().split(" ")[0].toLowerCase();
		
		int me = getGroupNum(exclusiveList, mfirst);
		int ae = getGroupNum(exclusiveList, afirst);
		int mf = getGroupNum(firstNameList, mfirst);
		int af = getGroupNum(firstNameList, afirst);
		int ml = getGroupNum(lastNameList, mfirst);
		int al = getGroupNum(lastNameList, afirst);
		if(me!=-1 && ae!=-1 && me!=ae)
			score = 1.0;
		if(mf!=-1 && af!=-1 && mf!=af)
			score = 1.0;
		if(ml!=-1 && al!=-1 && ml!=al)
			score = 1.0;
		if((me!=-1 && af!=-1 && ml == -1 && al ==-1) || (ae!=-1 && mf!=-1 && ml == -1 && al ==-1) ||
			(me!=-1 && al!=-1) || (ae!=-1 && ml!=-1))
			score = 1.0;
		
		mfirst = m_.getSurfaceText();
		afirst = a_.getSurfaceText();
		me = getGroupNum(exclusiveList, mfirst);
		ae = getGroupNum(exclusiveList, afirst);
		mf = getGroupNum(firstNameList, mfirst);
		af = getGroupNum(firstNameList, afirst);
		ml = getGroupNum(lastNameList, mfirst);
		al = getGroupNum(lastNameList, afirst);
		if(me!=-1 && ae!=-1 && me!=ae)
			score = 1.0;
		if(mf!=-1 && af!=-1 && mf!=af)
			score = 1.0;
		if(ml!=-1 && al!=-1 && ml!=al)
			score = 1.0;
		if((me!=-1 && af!=-1 && ml == -1 && al ==-1) || (ae!=-1 && mf!=-1 && ml == -1 && al ==-1) ||
			(me!=-1 && al!=-1) || (ae!=-1 && ml!=-1))
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
