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

public class PronounMismatchConstraints extends NegativeConstraints implements Constraint{
	private final static String m_NAME = "ProMismatch";
	
	Map<String,Integer>cityList = loadExclusiveSet("countTable/city");
	Map<String,Integer> firstNameList;
	Map<String,Integer> lastNameList;
	public PronounMismatchConstraints() throws Exception
	{
		super();
		init();
	}


	private String[][] pronounGroup = new String[][]{{"he","his","him","himself"},{"she","her","herself"}
	,{"it","its","itself"},{"they","their", "them","themselves"},{"we","our","ourselves"},{"you","your","yourselves"}};
	
	private Map<String, Integer> pronounGroupMap;
	private void init(){
		pronounGroupMap = new HashMap<String, Integer>();
		for(int i=0; i< pronounGroup.length; i++)
			for(String s: pronounGroup[i])
				pronounGroupMap.put(s, i+1);
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



		private String[][] pronounGroup1 = new String[][]{{"he","his","him","himself"},{"she","her","herself"}
	/*,{"it","its","itself"}*/,{"they","their", "them","themselves","we","our","ourselves"}};
	private String[][] pronounGroup2 = new String[][]{{"it","its","itself"},{"my","I","you","your","me"}};
	private String[][] pronounGroup3 = new String[][]{{"they","their", "them","themselves"},{"my","I","me"}};
	
	private int getGroupNum(String[][] lists, String head){
		for(int i=0; i< lists.length;i++){
			if(myAux.isStringEquals(head, lists[i], false))
				return i+1;
		}
		return -1;
	}
	protected double computeSimilarityScore( Mention m_, Mention a_ ) 
	{
		double score = 0.0;
		Doc d = m_.getDoc();
		int proGroup = -1;
		// Check pronoun match:

		if(m_.getType().equals("PRO") && a_.getType().equals("PRO")){
			int mg = getGroupNum(pronounGroup1, m_.getSurfaceText());
			int ag = getGroupNum(pronounGroup1, a_.getSurfaceText());
			if(mg!=-1 && ag!=-1 && mg!=ag){
				score = 1.0;
			}

			mg = getGroupNum(pronounGroup2, m_.getSurfaceText());
			ag = getGroupNum(pronounGroup2, a_.getSurfaceText());
			if(mg!=-1 && ag!=-1 && mg!=ag){
				score = 1.0;
			}
			
			mg = getGroupNum(pronounGroup3, m_.getSurfaceText());
			ag = getGroupNum(pronounGroup3, a_.getSurfaceText());
			if(mg!=-1 && ag!=-1 && mg!=ag){
				score = 1.0;
			}
		}
		else if(m_.getType().equals("PRO")){
			if(pronounGroupMap.containsKey(m_.getSurfaceText()))
				proGroup = pronounGroupMap.get(m_.getSurfaceText());
		}
		else if(a_.getType().equals("PRO")){
			if(pronounGroupMap.containsKey(m_.getSurfaceText()))
				proGroup = pronounGroupMap.get(m_.getSurfaceText());
		}
		else
			return 0.0;
		
		
		
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
