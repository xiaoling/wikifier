package edu.illinois.cs.cogcomp.wikifier.utils.datastructure;

import edu.illinois.cs.cogcomp.core.datastructures.Pair;

public class Tuple<P> extends Pair<P,P>{

	/**
	 * 
	 */
	private static final long serialVersionUID = 3466080229070628409L;

	public Tuple(P obj, P obj2) {
		super(obj, obj2);
	}
	
	public void reverse(){
		P temp = this.getFirst();
		this.setFirst(this.getSecond());
		this.setSecond(temp);
	}
	
}
