package edu.illinois.cs.cogcomp.wikifier.models;


import java.io.Serializable;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;
import edu.illinois.cs.cogcomp.wikifier.common.TrainingParameters;
import edu.illinois.cs.cogcomp.wikifier.inference.features.FeatureStructure;
import edu.illinois.cs.cogcomp.wikifier.wiki.access.TitleNameNormalizer;
import edu.illinois.cs.cogcomp.wikifier.wiki.access.WikiAccess;


@XmlAccessorType(XmlAccessType.NONE)
public class WikiCandidate implements Serializable{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -6532439706927206860L;
	public static final String isCurrentlyTopPredictionFeaturename = "IsCurrentlyTopPrediction";
	// This is the link to the entity this candidate is trying to disambiguate...
	public Mention mentionToDisambiguate=null;
	// basically TitleNameNormalizerAndEvaluator.normalize(basicTitleInfo.getTitleSurfaceForm()), 
	// but we really need to do this only for the solutions for the corresponding entity, that is, we really
	// need to do this if this=this.entityToDisambiguate.bestDisambiguation
	@XmlElement
	public String titleName = null;

	public boolean isAlmostUnambiguousSolution = false; // if entityToDisambiguate.isAlmostUnambiguous AND if this is the candidate which is the solution with high confidence
	
	public WikiAccess.WikiMatchData wikiData = null;
    public transient FeatureStructure titleFeatures = new FeatureStructure("title"); // NOTE THAT SOME TITLE FEATURES ARE ADDED TO THE "OTHER" FEATURES TO AVOID EXCESSIVE CONJUNCTIONS....
    public transient FeatureStructure lexFeatures = new FeatureStructure("lex");
    public transient FeatureStructure coherenceFeatures = new FeatureStructure("coherence");
    public transient FeatureStructure otherFeatures = new FeatureStructure("other");

    @XmlElement
	public double rankerScore = 0; // this field caches the latest ranker prediction

	public WikiCandidate(Mention mentionToDisambiguate){
		this.mentionToDisambiguate = mentionToDisambiguate;
	}
	
	public WikiCandidate() {
		// when doing the re-ranking, I'll reset this feature. But it's very important to add this feature in the constructor for two reasons:
		// 1) To assign it a consistent feature id (in fact, since this will be the first added feature, the expected fid will be always 0)
		// 2) At the first stage, like the title match feature extraction, this feature cannot be updated, so we need to add it to keep a consistent feature dimensionality
		// features.addFeature(isCurrentlyTopPredictionFeaturename, 0.0);
		otherFeatures.addFeature(isCurrentlyTopPredictionFeaturename, 0.0);
	}
	
	public WikiCandidate(Mention mention,WikiAccess.WikiMatchData matchData){
	    this();
	    mentionToDisambiguate = mention;
	    titleName = TitleNameNormalizer.normalize(matchData.basicTitleInfo.getTitleSurfaceForm());
	    wikiData = matchData;
	    if (matchData.conditionalTitleProb >= GlobalParameters.params.unambiguousThreshold) {
            isAlmostUnambiguousSolution = true;
            mention.isAlmostUnambiguous = true;
            mention.almostUnambiguousSolution = this;
        }
	}
	
	public WikiCandidate(WikiCandidate other) {
		this(other,other.mentionToDisambiguate);
	}
	
	public WikiCandidate(WikiCandidate other,Mention entityToDisambiguate) {
		// when doing the re-ranking, I'll reset this feature. But it's very important to add this feature in the constructor for two reasons:
		// 1) To assign it a consistent feature id (in fact, since this will be the first added feature, the expected fid will be always 0)
		// 2) At the first stage, like the title match feature extraction, this feature cannot be updated, so we need to add it to keep a consistent feature dimensionality
		this.mentionToDisambiguate = entityToDisambiguate;
		if(other!=null){
    		this.titleName = other.titleName;
    		this.wikiData = other.wikiData;
    		this.rankerScore = other.rankerScore;
    		this.isAlmostUnambiguousSolution = other.isAlmostUnambiguousSolution;
		}
		else
		    this.titleName = "*null*";

	}

	public double[] getRankerFeatures() throws Exception{

		double[] title = titleFeatures.getFeatureVector();
		double[] lex = lexFeatures.getFeatureVector();
		double[] coherence = coherenceFeatures.getFeatureVector();
		double[] other = otherFeatures.getFeatureVector();
		
		int numFeatures = other.length+title.length+lex.length+coherence.length;
		if(TrainingParameters.generateFeaturesConjunctions)
			numFeatures+=+title.length*coherence.length+title.length*lex.length+lex.length*coherence.length;
		double[] res = new double[numFeatures];
		int pos = 0;
		for(int i=0;i<other.length;i++)
			res[pos++] = other[i];
		for(int i=0;i<title.length;i++)
			res[pos++] = title[i];
		for(int i=0;i<lex.length;i++)
			res[pos++] = lex[i];
		for(int i=0;i<coherence.length;i++)
			res[pos++] = coherence[i];

		if(TrainingParameters.generateFeaturesConjunctions){
			for(int i=0;i<title.length;i++)
				for(int j=0;j<lex.length;j++)
					res[pos++] = title[i]*lex[j];
			for(int i=0;i<title.length;i++)
				for(int j=0;j<coherence.length;j++)
					res[pos++] = title[i]*coherence[j];				
			for(int i=0;i<lex.length;i++)
				for(int j=0;j<coherence.length;j++)
					res[pos++] = lex[i]*coherence[j];
		}
		GlobalParameters.numberOfRankerFeatures = res.length;
		return res;
	}
	
	
	
	public  String toFeatureString(){
		String res="Normalized Title="+titleName.replace('\t', ' ')+"(id="+getTid()+")";
		/*if(features!=null){
				res+=features.toString();
		}else{
			res+="; the similarity scores not initialized!";
		}
		return res;
		*/
		if(otherFeatures!=null){
			res+="<Other features> "+ otherFeatures.toString()+"</Other Features>";
		}else{
			res+="; the otherFeatures scores not initialized!";
		}
		if(titleFeatures!=null){
			res+="<Title features> "+ titleFeatures.toString()+"</Title Features>";
		}else{
			res+="; the titleFeatures scores not initialized!";
		}
		if(lexFeatures!=null){
			res+="<Lexical features> "+ lexFeatures.toString()+"</Lexical Features>";
		}else{
			res+="; the lexFeatures scores not initialized!";
		}
		if(coherenceFeatures!=null){
			res+="<Coherence features> "+ coherenceFeatures.toString()+"</Coherence Features>";
		}else{
			res+="; the coherenceFeatures scores not initialized!";
		}
		return res;
	}
	
	public int getTid(){
		if(wikiData!=null)
			return wikiData.basicTitleInfo.getTitleId();
		else
			return -1;
	}

	@Override
	public String toString() {
		return "[surface=" + mentionToDisambiguate.surfaceForm + ", solution=" + titleName
				+ "]";
	}

	public boolean isTopDisambiguation(){
		return mentionToDisambiguate.topCandidate == this;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((mentionToDisambiguate == null) ? 0 : mentionToDisambiguate.hashCode());
		result = prime * result + ((titleName == null) ? 0 : titleName.hashCode());
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
		WikiCandidate other = (WikiCandidate) obj;
		if (mentionToDisambiguate == null) {
			if (other.mentionToDisambiguate != null)
				return false;
		} else if (!mentionToDisambiguate.equals(other.mentionToDisambiguate))
			return false;
		if (titleName == null) {
			if (other.titleName != null)
				return false;
		} else if (!titleName.equals(other.titleName))
			return false;
		return true;
	}

	
}
