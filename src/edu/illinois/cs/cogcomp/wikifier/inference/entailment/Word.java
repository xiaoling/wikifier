package edu.illinois.cs.cogcomp.wikifier.inference.entailment;

import java.io.Serializable;

import edu.mit.jwi.item.POS;

public class Word implements Serializable {

	private static final long serialVersionUID = 1L;
	public String word;
	public String original_word;
	public boolean removed = false;
	public int token;
	//public WSDdist dist;
	public String pos;
	public boolean okpos = false;
	public int weight = 1;
	public char pos_type;
	
	public Word(String w, int tok, String pos) {
		this.word = w;
		this.original_word = w;
		this.token = tok;
		this.pos=pos;
		if(pos==null)
			return;
		
		this.pos_type = pos.toLowerCase().charAt(0);
		this.okpos = pos_type == 'n' || pos_type == 'v' || pos_type == 'j' || pos_type == 'r';
		if(pos_type == 'j')
			pos_type = 'a';
		//System.out.println(pos);
	}
	
	public POS getPOS() {
		if(pos==null || pos.length() == 0)
			return null;
		
		if(pos.toLowerCase().charAt(0)== ('n')) {
			return POS.NOUN;
		}
		else if(pos.toLowerCase().charAt(0)== ('v')) {
			return POS.VERB;
		}
		else if(pos.toLowerCase().charAt(0)== ('j')) {
			return POS.ADJECTIVE;
		}
		else if(pos.toLowerCase().charAt(0)== ('r')) {
			return POS.ADVERB;
		}
		else
			return null;
	}
}
