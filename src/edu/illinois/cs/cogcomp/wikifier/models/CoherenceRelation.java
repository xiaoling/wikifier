package edu.illinois.cs.cogcomp.wikifier.models;

import java.io.Serializable;

import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.Triple;
import edu.illinois.cs.cogcomp.wikifier.wiki.access.TitleNameNormalizer;




public class CoherenceRelation implements Serializable{

	/**
	 * 
	 */
	private static final long serialVersionUID = -4997847950225162550L;
	
	public static final double TITLE_COHERENCE_SCORE = 4;
	public static final double INFOBOX_COHERENCE_SCORE = 2;
	public static final double HARD_PREFERENCE_SCORE = 100;
	public static final double LINK_COHERENCE_SCORE = 1;
	public static final double COREF_PREFERENCE = 10;
	public static final double NOM_PREFERENCE = 20;



	public static enum RelationType{
		LINK, INFOBOX, TITLE, COREF, HARD_PREFERENCE
	}
	
	public final WikiCandidate arg1,arg2;
	public double weight;
	
	/**
	 * Constructs a relation between entities according to the template
	 * @param e1
	 * @param e2
	 * @param template
	 */
	public CoherenceRelation(Mention e1,Mention e2,Triple template){
        this(
                e1.getCandidate(TitleNameNormalizer.normalize(template.getArg1()),0.0),
                e2.getCandidate(TitleNameNormalizer.normalize(template.getArg2()),0.0),
                template.getNormalizedScore()
            );
    }
	
	/**
	 * Some relations might be dependent on proximity or as penalty, 
	 * which can not be simply accounted for by type
	 * @param arg1
	 * @param arg2
	 * @param weight
	 */
	public CoherenceRelation(WikiCandidate arg1,WikiCandidate arg2,double weight){
		this(arg1,arg2,weight,1.0);
	}
	
	/**
	 * Constructs a relation with the maximum score of the two arguments as weight
	 * @param arg1
	 * @param arg2
	 */
    public CoherenceRelation(WikiCandidate arg1,WikiCandidate arg2,double weight,double scale){
        this.arg1 = arg1;
        this.arg2 = arg2;
        this.weight = weight * scale;
    }
	
	public double getNormalizedScore(){
		return ( arg1.rankerScore + arg2.rankerScore ) * weight;
	}
	
	
	/**
	 * Constructs a relation with default weights with different relation types
	 * @param arg1
	 * @param arg2
	 * @param types
	 */
	public CoherenceRelation(WikiCandidate arg1,WikiCandidate arg2,RelationType... types){
		this.arg1 = arg1;
		this.arg2 = arg2;
		double weightSum = 0;
		for(RelationType type:types){
			switch(type){
			case TITLE: weightSum += TITLE_COHERENCE_SCORE;
			case INFOBOX: weightSum += INFOBOX_COHERENCE_SCORE;
			case LINK: weightSum += LINK_COHERENCE_SCORE;
			case COREF: weightSum += HARD_PREFERENCE_SCORE;
			case HARD_PREFERENCE : weightSum += COREF_PREFERENCE;
			default:
			}
		}
		weight = weightSum;
	}

	@Override
	public String toString() {
		return "CoherenceRelation "+ arg2.mentionToDisambiguate.charStart
				+" [arg1=" + arg1 + ", arg2=" + arg2 + ", weight=" + weight + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((arg1 == null) ? 0 : arg1.hashCode());
		result = prime * result + ((arg2 == null) ? 0 : arg2.hashCode());
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
		CoherenceRelation other = (CoherenceRelation) obj;
		if (arg1 == null) {
			if (other.arg1 != null)
				return false;
		} else if (!arg1.equals(other.arg1))
			return false;
		if (arg2 == null) {
			if (other.arg2 != null)
				return false;
		} else if (!arg2.equals(other.arg2))
			return false;
		return true;
	}
	
	
	
	
}
