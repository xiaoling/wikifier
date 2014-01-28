package edu.illinois.cs.cogcomp.wikifier.models;

import static org.apache.commons.lang3.StringUtils.*;

import java.io.Closeable;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javatools.parsers.NounGroup;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.commons.lang3.StringUtils;

import LBJ2.classify.ScoreSet;
import LBJ2.learn.Softmax;

import com.google.common.collect.Lists;

import edu.illinois.cs.cogcomp.edison.sentences.Constituent;
import edu.illinois.cs.cogcomp.edison.sentences.Queries;
import edu.illinois.cs.cogcomp.edison.sentences.TextAnnotation;
import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;
import edu.illinois.cs.cogcomp.wikifier.common.ParameterPresets;
import edu.illinois.cs.cogcomp.wikifier.models.WikipediaProtobuffers.SurfaceFormSummaryProto;
import edu.illinois.cs.cogcomp.wikifier.models.tfidf.TFIDF.TF_IDF_Doc;
import edu.illinois.cs.cogcomp.wikifier.utils.Comparators;
import edu.illinois.cs.cogcomp.wikifier.utils.WikiTitleUtils;
import edu.illinois.cs.cogcomp.wikifier.utils.WordFeatures;
import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.SortedObjects;
import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.StringMap;
import edu.illinois.cs.cogcomp.wikifier.wiki.access.WikiAccess;
import edu.illinois.cs.cogcomp.wikifier.wiki.indexing.Index;
import edu.illinois.cs.cogcomp.wikifier.wiki.indexing.TitleNameIndexer;
import gnu.trove.set.hash.TIntHashSet;

/**
 * This class contain the information about the entity we need to disambiguate.
 * @modified cheng88
 *
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.NONE)
public class Mention extends TextSpan implements Serializable,Closeable {

    private static final long serialVersionUID = 1599224183834664994L;

    public static enum SurfaceType {
        NER, LOC, PER, ORG, MISC, ESAEntity, NPChunk, NPSubchunk, RELATIONAL;
        public static final Set<SurfaceType> NERTypes = EnumSet.of(LOC, PER, ORG, MISC);
        public static Set<SurfaceType> getNERTypes(){
            return NERTypes;
        }
    };

    public static final int[] localContextWindowSizes = { 20, 50, 100, 150, 200 };
    
    public static double fuzzyTokenMatchSim = 0.98;
    
    public static final double SOFTMAX_NORMALIZATION_FACTOR = 1.0;

    protected static final double DEFAULT_RANKER_SCORE = 0.5;

    // if the gold annotation is not marked, then the correctness is unknown, because typically only
    // a small subset of potentially wikifiable phrases is annotated
    public static enum WikificationCorrectness {
        Correct, Incorrect, Unknown
    };

    // can stay null too, and can also be set to null by the linker
    public WikiCandidate finalCandidate = null;
    // if some entity has no matches against the wikipedia, this can be null, but otherwise, this is
    // the best candidate; the linker can never set it to null
    @XmlElement
    public WikiCandidate topCandidate = null;
    // when we have training data, this is set to the gold disambiguation if one exists, otherwise
    // it's set to null.
    public WikiCandidate goldCandidate = null;
    // is the prediction on this entity correct
    public WikificationCorrectness isCorrectPrediction = WikificationCorrectness.Unknown;
    // if the gold data is known, this can keep the gold annotation. note that this may be non-null
    // even if goldDisambiguationIfExists=null.
    // That is, if the gold disambiguation does not appear in the candidates,
    // goldDisambiguationIfExists will be null, but goldAnnotation will be the correct one!
    public String goldAnnotation = "*null*";

    // this is the token offset
    @XmlElement
    public int startTokenId = -1;
    // this one is startTokenId+number of tokens!!! So do < rather than <= when iterating....
    @XmlElement
    public int endTokenId = -1;
    // this is part of the text that surrounds the entity (100 characters window, or around 10
    // tokens)

    public TF_IDF_Doc[] localContext = null;
    // was it an NER entity? LOC/RG/PER? Or ESA entity? Or aggressive linker entity?

    @XmlElementWrapper(name="mentionTypes")
    public Set<SurfaceType> types = EnumSet.noneOf(SurfaceType.class);
    // The entity is almost unambiguous if the conditional probability of title given the surface
    // form is above X (e.g. above 99%)

    public boolean isAlmostUnambiguous = false;
    public WikiCandidate almostUnambiguousSolution = null;
    public List<List<WikiCandidate>> candidates = null;
    // What was the actual score that justified the linker decision. Typically,
    // linkerPredictionIsLinkable <-- linkerScore>thres;

    @XmlElement
    public double linkerScore = 0;

    protected Map<String, WikiCandidate> candidatesByTitles = null;

    public LinkingProblem parentProblem = null;

//    public LinearFeatureStructure entityFeatures = null;
    public Constituent constituent = null;

    private Boolean isTopLevel = null;

    private NounGroup nounGroup = null;

    private boolean redirectedOnly = false;

	/**
	 * This constructor is only provided for testing purposes, should not be
	 * instantiated otherwise
	 */
	public Mention(){
        charStart = -1;// where does the entity start in the text
        charLength = -1;// the length of the entity in chars
	}
	
	/*
	 * This leaves many important fields uninitialized, be careful!!!!
	 */
	public Mention(Constituent c,LinkingProblem problem){
		int start = c.getStartCharOffset();
		int end = c.getEndCharOffset();
		charStart = start;
		startTokenId = c.getStartSpan();
		charLength = end-start;
		endTokenId = c.getEndSpan();
		surfaceForm = c.getSurfaceString().trim();
		parentProblem = problem;
//		if(tokenLength()>1 && !surfaceForm.contains(" ")){
//		    System.out.println("Wrong tokenization");
//		}
	}

	/**
	 * surface.length() is not necessarily equal to len, as we allow external spell correction
	 * @param surface
	 * @param start
	 * @param len
	 * @param problem
	 */
	public Mention(String surface,int start,int len,LinkingProblem problem){
        this(surface, start, len, 0, 0, problem);
        TextAnnotation ta = problem.ta;

        int charEnd = start + len;
        int tidStart = ta.getTokenIdFromCharacterOffset(start);
        int tidEnd = ta.getTokenIdFromCharacterOffset(charEnd) + 1;

        int max = ta.getTokens().length;
        // double-check token bounds
        if (tidEnd > max)
            tidEnd = max;

        String strippedSurface = strip(surfaceForm,".'");
        
        while(tidEnd>1 && ta.getTokenCharacterOffset(tidEnd-1).getSecond()>charEnd){
            String strippedToken = stripToken(ta,tidEnd);
            if(strippedSurface.endsWith(strippedToken))
                break;
            tidEnd--;
        }

        this.startTokenId = tidStart;
        this.endTokenId = tidEnd;
	}
	
	private static boolean isAllPunct(String s){
	    for(int i=0;i<s.length();i++){
	        char c = s.charAt(i);
	        if(Character.isLetter(c) || Character.isDigit(c))
	            return false;
	    }
	    return true;
	}

	
	private static String stripToken(TextAnnotation ta,int end){
	    String token = ta.getToken(end-1);
	    if(isAllPunct(token))
	        return token;
        return strip(token,".'");

	}
	/*
	 * This leaves many important fields uninitialized, be careful!!!!
	 */
	private Mention(
	        String surface,
	        int start,
	        int len,
	        int startTokenId, 
	        int endTokenId,
	        LinkingProblem problem){
		// Disallow any constructors. The generation of Wikifiable Entities in a document must be handled 
	    // in the getWikifiableEntities(String text).
		this.startTokenId=startTokenId;
		this.endTokenId=endTokenId;
		charStart = start;
		charLength = len;
	    // Unicode single quote encoding problem
		surfaceForm = surface.trim().replace('\uFFFD', '\'');
		parentProblem = problem;
	}
	
	@XmlElementWrapper(name="candidates")
	public List<WikiCandidate> getLastPredictionLevel(){
	    if(candidates == null)
	        candidates = new ArrayList<>();
	    if(candidates.size()==0)
	        candidates.add(new ArrayList<WikiCandidate>(0));
		return candidates.get(candidates.size()-1);
	}
	
	/*
	 * Returns the top K disambiguation candidates from the last level of disambiguation candidates,
	 * sorted by the ranker scores make sure that this function is called after calling
	 *      InferenceEngine.rankDisambiguationCandidatesAndMakeRankingPrediction
	 *  Otherwise, the scores will not be initialized the ranking will be meaningless.
	 *  use numPredictions=-1 if you just wanna return all the candidates, but in a sorted order
	 */
	public SortedObjects<WikiCandidate> getRankedTopPredictionsLastLevel(int numPredictions){
		List<WikiCandidate> lastLevel = getLastPredictionLevel();
		SortedObjects<WikiCandidate> res = 
		        new SortedObjects<WikiCandidate>(Math.min(numPredictions,lastLevel.size()));
		for(WikiCandidate candidate:lastLevel)
			res.add(candidate, candidate.rankerScore);
		res.sort();
		return res;
	}

	/**
	 * Appends a copy of the candidate to this entity in the last level
	 * @param other
	 * @return the newly appended candidate, or the existing one if name matches
	 */
	public WikiCandidate getCandidate(WikiCandidate other,boolean useSameRankerScore){
	    String titleKey = other == null ? "*null*" : other.titleName;
        if(candidatesByTitles==null)
            initializeCandidateSet();

		if(!candidatesByTitles.containsKey(titleKey)){
			WikiCandidate newCandidate = new WikiCandidate(other,this);

			newCandidate.rankerScore = useSameRankerScore? other.rankerScore : DEFAULT_RANKER_SCORE;
			candidatesByTitles.put(newCandidate.titleName, newCandidate);
			return newCandidate;
		}else{
		    // Returns the existing one, we want to show the explanation anyway
		    WikiCandidate candidate = candidatesByTitles.get(titleKey);
		    if(useSameRankerScore)
		        candidate.rankerScore = other.rankerScore;
			return candidate;
		}
	}
	
	/**
	 * Appends a candidate with default ranker score
	 * @param other
	 * @return
	 */
	public WikiCandidate getCandidate(WikiCandidate other){
	    return getCandidate(other,false);
	}
	
	/**
	 * Appends a new candidate to this entity
	 * @param title
	 * @param ranker score for the candidate
	 * @return whether a new candidate was created
	 */
	public WikiCandidate getCandidate(String normalizedTitle,double score){
	    if(candidatesByTitles==null)
	        initializeCandidateSet();
	    WikiCandidate candidate = candidatesByTitles.get(normalizedTitle);
		if (candidate != null)
			return candidate;

		WikiAccess.WikiMatchData wikiData = null;
		try {
		    if(!"*null*".equals(normalizedTitle))
		        wikiData = new WikiAccess.WikiMatchData(surfaceForm, normalizedTitle, 1);
		} catch (Exception e1) {

		}
		candidate = new WikiCandidate(this);
		candidate.titleName = normalizedTitle;
		candidate.wikiData = wikiData;
		candidate.rankerScore = score;
		candidatesByTitles.put(normalizedTitle,candidate);
		if(topCandidate == null)
			topCandidate = candidate;
		return candidate;
	}
	
	public Set<String> getCandidates(){
	    return candidatesByTitles.keySet();
	}
	
	
	/**
	 * Create default candidate with {@link Mention#DEFAULT_RANKER_SCORE}
	 * @param normalizedTitle
	 * @return
	 */
	public WikiCandidate getCandidate(String normalizedTitle){
	    return getCandidate(normalizedTitle,DEFAULT_RANKER_SCORE);
	}
	
	public boolean isPerfectMatch(){
	    if(topCandidate == null)
	        return false;
	    String title = topCandidate.titleName;
	    return !hasZeroSurfaceProb()&& title.equals(surfaceForm.replace(' ', '_'));
	}

	/**
	 * This mostly happens for redirected candidates
	 * @return whether this candidate is almost never seen in probability distribution
	 * before
	 */
	public boolean hasZeroSurfaceProb(){
	    return     topCandidate == null 
	            || topCandidate.wikiData == null
	            || (topCandidate.wikiData != null 
                    && topCandidate.wikiData.conditionalSurfaceFromProb < 0.000001
                );
	}
	
	public boolean hasLeftBoundary(){
	    if( !WordFeatures.isCapitalized(surfaceForm))
	        return false;
	    if(startTokenId == 0 || isFirstTokenInSentence(startTokenId, parentProblem.ta))
	        return true;
	    String previousToken = parentProblem.ta.getToken(startTokenId-1);
	    char lastChar = previousToken.charAt(previousToken.length()-1);
	    return !WordFeatures.isCapitalized(previousToken) || !Character.isLetterOrDigit(lastChar);
	}
	
	public boolean hasRightBoundary(){
	    if( !WordFeatures.isCapitalized(surfaceForm))
            return false;
        if(endTokenId >= parentProblem.ta.getTokens().length-1)
            return true;
        String nextToken = parentProblem.ta.getToken(endTokenId);
        return !WordFeatures.isCapitalized(nextToken);
	}
		
	public int tokenLength(){
	    return endTokenId - startTokenId;
	}
	
	public int maxNormLength(){
	    if(topCandidate==null || !isPER())
	        return tokenLength();
	    return Math.max(tokenLength(),StringUtils.countMatches(topCandidate.titleName, "_")+1);
	}
	
	public void setTopLevelEntity(){
	    isTopLevel = true;
	}
	
	// Make sure it is in the form of a a a [A A] a a
	// This method lazy loads and please make sure the parent problem is not null
	public boolean isTopLevelMention(){
		
	    if(isTopLevel!=null)
	        return isTopLevel;

        if(!isAllCaps()){
            isTopLevel = false;
        }else{
            
//            boolean popularEntity = disambiguationCandidates!=null 
//                    && disambiguationCandidates.size() > 0
//                    && getLastPredictionLevel().size() >= GlobalParameters.maxCandidatesToGenerateInitially;
//            
    	    isTopLevel = (hasLeftBoundary() && hasRightBoundary());
        }
	    
	    return isTopLevel;	    
	}

	
	// No lower case or stopword allowed inside the candidates
	public boolean isAllCaps(){
		
		String[] tokens = StringUtils.split(surfaceForm," ");
		
		// First determinant is not allowed when context is available
		if(tokens.length > 0
		        && (parentProblem!=null && isFirstTokenInSentence(startTokenId, parentProblem.ta))
		        && ("A".equals(tokens[0]) || "The".equals(tokens[0]))
           )
		{
			return false;
		}
		
		for(String s:tokens){
			if(!WordFeatures.isCapitalized(s)){
				return false;
			}
		}
		return true;
	}
	
	

	public String toString() {
		return surfaceForm+"["+charStart+"-"+(charStart+charLength)+"]{"+startTokenId+"-"+endTokenId+"}";
	}

	
	/**
	 * Some times we want to generate new candidates for the entity
	 * after the initial round
	 */
	public void prepareForStructuralRerank(boolean newLevel){

	    initializeCandidateSet();
	    softmaxNormalization(SOFTMAX_NORMALIZATION_FACTOR);
		// Make a copy of the last prediction, we do not want to mess up the original ranking
		// for comparison reasons
		if(newLevel){
			List<WikiCandidate> prevLevel = getLastPredictionLevel();
			List<WikiCandidate> curLevel = new ArrayList<WikiCandidate>();
			for(WikiCandidate prev:prevLevel){
				curLevel.add(new WikiCandidate(prev));
			}
			candidates.add(curLevel);
		}
	}
	
	public boolean overlaps(Mention other){
		return startTokenId < other.endTokenId && other.startTokenId < endTokenId;
	}
	
	public boolean lexicallyFuzzyMatchesTopDisambiguation(){
		if(topCandidate == null)
			return false;
		
		String candidateName = WikiTitleUtils.stripCommaSpace(topCandidate.titleName);
		
		Set<String> candidateTokens = Index.getTokenSet(candidateName);
		Set<String> tokens = Index.getTokenSet(surfaceForm);
		
		candidateTokens.retainAll(tokens);
		
		return candidateTokens.size()>0;
	}
	
	/**
	 * @param candidate
	 * @param tokenDifference
	 * @return
	 */
	public boolean tokenCountDiffWithin(WikiCandidate candidate,int tokenDifference){
		if(candidate == null)
			return false;
		
		int spaceCount = WordFeatures.numOfCaptialLetters(surfaceForm);
		int candidateSpaceCount = WordFeatures.numOfCaptialLetters(candidate.titleName);
		
		return candidateSpaceCount - spaceCount <= tokenDifference;
	}

	/**
	 * This function is used to prune the inference search space
	 * Only coref single tokens
	 * @return whether we should perform coref changes on this entity
	 */
	public boolean shouldNotBeCoRefed(){
	            // We do not want to mess with very confident results
		return  (topCandidate!=null && topCandidate.rankerScore > 1.65 && linkerScore > 0)
		        // If this candidate has a lot of candidates, probably it's not about coref
//				|| candidatesByTitles.size() >= GlobalParameters.maxCandidatesToGenerateInitially
				|| surfaceForm.contains(" ")
				|| !isTopLevelMention()
				;
	}

    public double getCurrentDecisionConfusion(){
        if(topCandidate == null || topCandidate.rankerScore <= 0)
            return 1.0;
        return getCurrentDecisionEntropy()/topCandidate.rankerScore;
    }
    
    public boolean isCurrentlyLinkingToNull(){
        return topCandidate == null 
                || linkerScore < 0 
                || "*null*".equals(topCandidate.titleName);
    }

	/**
	 * Ensure both this entity and the coref parent has the relation
	 * @param corefEntity
	 * @return
	 */
	public WikiCandidate ensureCorefArgsExists(Mention corefEntity){
        // If we are referring to someone not in index, this should not be
        // linked neither
        if (corefEntity.isCurrentlyLinkingToNull()) {

            if (hasZeroSurfaceProb()) {
                linkerScore = -1;
                corefEntity.topCandidate = corefEntity.getCandidate("*null*");
            }else
                return null;

        }

        // We also only takes top result, as most of our results are correct
        WikiCandidate other = corefEntity.topCandidate;

        return getCandidate(other);
	}
	
	
	public double getLinkability(){
        SurfaceFormSummaryProto surfaceFormData;
        try {
            surfaceFormData = GlobalParameters.wikiAccess.getSurfaceFormInfo(surfaceForm);
            double linkability = ((double)surfaceFormData.getLinkedAppearanceCount())
                    /surfaceFormData.getTotalAppearanceCount();
            return linkability;
        } catch (Exception e) {
        }
        return 1;
    }

	private static final Pattern acronymConcat = Pattern.compile("(\\.)([A-Z])");
	/**
	 * Either add new candidates or remove ambiguous top answers
	 */
	 public void cleanCurrentDecision() {

        if (topCandidate != null 
                || (candidatesByTitles!=null && candidatesByTitles.size() > 0)
                || isNotAnEntity()
                || isSubEntity()){
            return;
        }

        String cleaned = surfaceForm.replace(' ', '_');
        
        // Tier 1 search, redirect and exact match
        String redirect = TitleNameIndexer.normalize(cleaned);
        if(redirect==null && isNamedEntity() && surfaceForm.contains(".")){
            String newSurface = acronymConcat.matcher(cleaned).replaceAll("$1_$2");
            if(!newSurface.equals(cleaned))
                redirect = TitleNameIndexer.normalize(newSurface);
        }
        
        // Tier 2 search, longer surfaces with token level edit distance filter
        if(GlobalParameters.params.USE_FUZZY_SEARCH && redirect == null && isTopLevelMention()
                && !conjunction.matcher(cleaned).find() && !cleaned.contains(",")){
            int tokenCount = countMatches(cleaned, "_") + 1;
            // Tokens too short needs to be spell checked
            if(tokenCount >= 3){
                List<String> fuzzyMatches = TitleNameIndexer.tokenLevelEditDistanceMatches(cleaned, fuzzyTokenMatchSim);
                // No need to match substring and ambiguous results
                if (fuzzyMatches.size() == 1 && !cleaned.contains(fuzzyMatches.get(0))) {
                    redirect = fuzzyMatches.get(0);
                }
            }
        }
        
        if (redirect != null && !TitleNameIndexer.isDisambiguationPage(redirect)) {
            finalCandidate = topCandidate = getCandidate(redirect);
            redirectedOnly = true;
            linkerScore = 1.0;
        }
    }
	
	private static final Pattern conjunction = Pattern.compile("(\\band)|(and\\b)");
	 
	public double getCurrentDecisionEntropy(){
		double rankerScoreSum = 0;
		
		for(WikiCandidate candidate:getLastPredictionLevel()){
			if(candidate.rankerScore>0)
				rankerScoreSum+=candidate.rankerScore;
		}
		
		double entropy = 0;

		for(WikiCandidate candidate:getLastPredictionLevel()){
			if(candidate.rankerScore>0){
				double p = candidate.rankerScore / rankerScoreSum;
				entropy -= p * Math.log(p);
			}
		}
		return entropy;		
	}
	
	/**
	 * Standard softmax on the current candidate set
	 * See <a>https://en.wikipedia.org/wiki/Softmax_activation_function</a>
	 * @param smoothingParam
	 */
	public void softmaxNormalization(double smoothingParam){
	    if(candidatesByTitles == null || candidatesByTitles.size()==0)
	        return;
	    
	    ScoreSet scoreSet = new ScoreSet();
        for(WikiCandidate c:candidatesByTitles.values()){
            scoreSet.put(c.titleName, c.rankerScore);
        }
        
        scoreSet = new Softmax(smoothingParam).normalize(scoreSet);
        
        for(WikiCandidate c:candidatesByTitles.values()){
            c.rankerScore = scoreSet.get(c.titleName);
        }
	}

	void syncCandidates() {
		if(candidates == null){
			candidates = new ArrayList<List<WikiCandidate>>();
		}
		List<WikiCandidate> newCandidateList = new ArrayList<WikiCandidate>();
		for(WikiCandidate candidate:candidatesByTitles.values()){
		    if(!candidate.titleName.equals("*null*"))
		        newCandidateList.add(candidate);
		}
		candidates.add(newCandidateList);
	}
	
	public int topDisamgbiguationTokenCount(){
        if (topCandidate == null)
            return 0;
	    return StringUtils.countMatches(topCandidate.titleName, "_");
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + endTokenId;
		result = prime * result + startTokenId;
		result = prime * result + ((surfaceForm == null) ? 0 : surfaceForm.hashCode());
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
		Mention other = (Mention) obj;
		if (endTokenId != other.endTokenId)
			return false;
		if (startTokenId != other.startTokenId)
			return false;
		if (surfaceForm == null) {
			if (other.surfaceForm != null)
				return false;
		} else if (!surfaceForm.equals(other.surfaceForm))
			return false;
		return true;
	}

    public int getSentenceId(){
        return parentProblem.ta.getSentenceId(startTokenId);
    }
    
    /**
     * Filters out things like "The celebration-> The Celebration" mappings
     * @return
     */
    public boolean isNotAnEntity(){
        String firstToken = parentProblem.ta.getToken(startTokenId);
        boolean startsWithStopWord = GlobalParameters.stops.isStopword(firstToken.toLowerCase());
        if(startsWithStopWord){
            for (int tokenId = startTokenId + 1; tokenId < endTokenId; tokenId++) {
                String token = parentProblem.ta.getToken(tokenId);
                if (WordFeatures.isCapitalized(token))
                    return false;
            }
            return true;
        }
        return false;
    }
    
    public void generateLocalContext(String text, WikiAccess wikipedia) {

        localContext = new TF_IDF_Doc[localContextWindowSizes.length];

        for (int i = 0; i < localContextWindowSizes.length; i++) {
            int start = charStart - localContextWindowSizes[i];
            if (start < 0)
                start = 0;
            else {
                while (start < charStart && text.charAt(start) != ' ')
                    start++;
            }
            int end = charStart + charLength + localContextWindowSizes[i];
            if (end >= text.length())
                end = text.length();
            else {
                while (end > 0 && end > charStart + charLength && text.charAt(end) != ' ')
                    end--;
            }
            String leftContext = "";
            if (start < charStart)
                leftContext = text.substring(start, charStart);
            String rightContext = "";
            if (charStart + charLength < end)
                rightContext = text.substring(charStart + charLength, end);
            localContext[i] = wikipedia.getWikiSummaryData().getTextRepresentation(leftContext + " " + rightContext, true);
        }
    }

    public synchronized void initializeCandidateSet(){
        if (candidatesByTitles != null)
            return;

        candidatesByTitles = new StringMap<>();
        Collections.sort(getLastPredictionLevel(),Comparators.confidenceFirst);
        ListIterator<List<WikiCandidate>> li = candidates.listIterator(candidates.size());
        while(li.hasPrevious()){
            for(WikiCandidate c:li.previous()){
                // Duplicate candidates, only keep the more prominent one
                if(!candidatesByTitles.containsKey(c.titleName))
                    candidatesByTitles.put(c.titleName,c);
            }
        }
    }
    
    /**
     * If previous token is the first letter in sentence or
     * is "The" we can still consider the current entity
     * being a top level entity
     * @param tokenId
     * @param ta
     * @return
     */
    private static boolean isFirstTokenInSentence(int tokenId,TextAnnotation ta){
        if(tokenId<=0)
            return true;
        int prevTokenId = tokenId-1;
        return ta.getSentenceId(tokenId) != ta.getSentenceId(prevTokenId)
                || GlobalParameters.stops.isStopword(ta.getToken(prevTokenId).toLowerCase());
    }
    
    /**
     * Checks whether this mention is part of a known concept
     * @return
     */
    public boolean isSubEntity() {
        // Query mentions are never sub entities
        if(GlobalParameters.params.preset == ParameterPresets.TAC && hasGoldAnnotation())
            return false;
        for (Constituent c : parentProblem.entityView.where(Queries.containsConstituentExclusive(constituent))) {
            Mention coveringMention = parentProblem.getComponent(c);
            if (coveringMention != null) {
                if (coveringMention.isTopLevelMention() && coveringMention.topCandidate != null) {
                    return true;
                }
            }
        }
        return false;
    }

    public double getCandidateScore(String title){
        WikiCandidate candidate = candidatesByTitles.get(title);
        return candidate == null ? 0.0 : candidate.rankerScore;
    }
    
    /**
     * 
     * @return null if unknown
     */
    public Boolean isCurrentlyCorrect(){
        if(goldAnnotation == null || "*unknown*".equals(goldAnnotation))
            return null;
        if(finalCandidate == null)
            return "*null*".equals(goldAnnotation);
        else
            return finalCandidate.titleName.equals(goldAnnotation);
    }
    
    public int distanceTo(Mention other){
        if(overlaps(other))
            return 0;
        return Math.min(Math.abs(endTokenId-other.startTokenId), Math.abs(startTokenId-other.endTokenId));
    }
//    
//    /**
//     * Loads new feature regardless of existing features
//     */
//    public void refreshEntityFeatures(){
//
//        LinearFeatureStructure features = new LinearFeatureStructure();
//        
//        features.add(EntityFeature.CandidateCount, candidatesByTitles.size());
//        features.add(EntityFeature.Entropy, getCurrentDecisionEntropy());
//        features.add(EntityFeature.IsPerfectMatch ,isPerfectMatch());
//        features.add(EntityFeature.SurfaceTokenCount, WordFeatures.numOfCaptialLetters(surfaceForm));
//        features.add(EntityFeature.TopDisambiguationTokenCount, topDisamgbiguationTokenCount());
//        features.add(EntityFeature.IsFinalSolutionNull, finalSolutionDisambiguation == null);
//        
//        if(topDisambiguation == null)
//            features.loadDefaultTopDisambiguationFeatures();
//        else{
//            
//            features.add(EntityFeature.TopRankerScore, topDisambiguation.rankerScore);
//    
//            features.add(EntityFeature.TopDisambiguationLogIncomingLinkCount, Math.log(topDisambiguation.getIncomingLinkCount()));
//    
//            features.add(EntityFeature.TopDisambiguationLogOutgoingLinkCount, Math.log(topDisambiguation.getOutgoingLinkCount()));
//    
//            features.add(EntityFeature.TopDisambiguationIsDisambiguationPage, isDisambiguationPage(topDisambiguation.titleName));
//            
//            features.add(EntityFeature.LinkerScore, linkerScore);
//            
//            features.add(EntityFeature.TopDisambiguationLinkability, getLinkability());
//        }
//        
//        entityFeatures = features;
//    }
//    
//    /**
//     * Loads new feature regardless if no feature exists
//     */
//    public LinearFeatureStructure extractEntityFeatures() {
//        if (entityFeatures == null)
//            refreshEntityFeatures();
//        return entityFeatures;
//    }
//        
//    public double[] corefFeatures(WikifiableEntity other,int resultSize,CorefIndexer index){
//
//        double[] thisEntityFeatures = extractEntityFeatures().entityFeatureVector();
//        double[] otherEntityFeatures = other.extractEntityFeatures().entityFeatureVector();
//        
//        double[] flatFeatures = ArrayUtils.addAll(thisEntityFeatures, otherEntityFeatures);
//        
//        int inTextFreq = index.getIndexedTitleCount(other.surfaceForm);
//        if(other.topDisambiguation !=null)
//            inTextFreq+=index.getIndexedTitleCount(other.topDisambiguation.titleName);
//        
//        int selfTextFreq = index.getIndexedTitleCount(surfaceForm);
//        if(topDisambiguation !=null)
//            selfTextFreq+=index.getIndexedTitleCount(topDisambiguation.titleName);
//        
//        boolean alreadyContainsTop = other.topDisambiguation!=null 
//                && candidatesByTitles.containsKey(other.topDisambiguation.titleName);
//        
//        double[] jointFeatures = new double[]{
//                resultSize,
//                inTextFreq,
//                selfTextFreq,
//                WordFeatures.isLikelyAcronym(surfaceForm) ? 1 : 0,
//                hasZeroSurfaceProb() ? 1 :0,
//                alreadyContainsTop ? 1 : 0,
//                // Token ratio
//                other.entityFeatures.get(EntityFeature.SurfaceTokenCount)/entityFeatures.get(EntityFeature.SurfaceTokenCount),
//                other.isCurrentlyLinkingToNull()? 1 : 0,
//                // Whether the head matches
//                surfaceForm.equals(new NounGroup(other.surfaceForm).head())? 1 : 0
//        };
//        return ArrayUtils.addAll(flatFeatures, jointFeatures);
//    }
//    
//    @Deprecated
//    public void addInstanceToRelationLinker(boolean correct){
//        
//        extractEntityFeatures();
//        GlobalParameters
//        .problemBuilder
//        .addInstance(entityFeatures.entityFeatureVector(),
//                correct);
//    }

    @Override
    public void close(){
        parentProblem = null;
//        entityFeatures = null;
        candidatesByTitles = null;
        localContext = null;
        constituent = null;
        if(candidates!=null)
            for (List<WikiCandidate> levels : candidates) {
                for (WikiCandidate candidate : levels) {
                    candidate.mentionToDisambiguate = null;
                    candidate.lexFeatures = null;
                    candidate.coherenceFeatures = null;
                    candidate.titleFeatures = null;
                    if (candidate.wikiData != null)
                        candidate.wikiData.lexInfo = null;
                }
            }
    }
    
    /**
     * Returns a list of relations that enforces both entity have the same
     * disambiguation, including *null*
     * @param other
     * @return
     */
    public List<CoherenceRelation> getCorefRelationsTo(Mention other){
        List<CoherenceRelation> relations = Lists.newArrayList();
        
        // Be careful removing candidates as others might depend upon it
        candidatesByTitles.keySet().retainAll(other.candidatesByTitles.keySet());
        
        for(WikiCandidate corefedCandidate:other.candidatesByTitles.values()){
            
            WikiCandidate clonedCandidate = getCandidate(corefedCandidate,true);
            
            relations.add(new CoherenceRelation(clonedCandidate,corefedCandidate,CoherenceRelation.COREF_PREFERENCE));
        }
        
        
        // Keep consistency of the null link decision
        if (other.finalCandidate == null) {
            //Special care for null answers
            WikiCandidate nil = other.getCandidate(other.finalCandidate);
            nil.rankerScore = other.topCandidate == null? DEFAULT_RANKER_SCORE : other.topCandidate.rankerScore + Math.abs(other.linkerScore);
            WikiCandidate nilCopy = getCandidate(nil,true);
            relations.add(new CoherenceRelation(nilCopy,nil,CoherenceRelation.COREF_PREFERENCE));
        }
        
        
        finalCandidate = getCandidate(other.finalCandidate);
        topCandidate = getCandidate(other.topCandidate);
        
        return relations;
    }

    
    
    public boolean hasGoldAnnotation(){
        return goldAnnotation != null && !"*null*".equals(goldAnnotation);
    }
    
    public double getTopRankScore(){
        return topCandidate == null ? 0.0:topCandidate.rankerScore;
    }
    
    public boolean isNamedEntity(){
        return types.contains(SurfaceType.NER);
    }
    
    public boolean isPER(){
        return types.contains(SurfaceType.PER);
    }
    
    public boolean isORG(){
        return types.contains(SurfaceType.ORG);
    }
    
    public boolean isLOC(){
        return types.contains(SurfaceType.LOC);
    }
    
    public boolean isConditionallyLOC(Mention commaAfter){
        if(isLOC())
            return true;
        if(topCandidate==null || !topCandidate.titleName.contains(","))
            return false;
        String postfix = WikiTitleUtils.getSecondaryEntity(topCandidate.titleName);
        return commaAfter.candidatesByTitles.containsKey(postfix);
    }
    
    public boolean isMISC() {
        return types.contains(SurfaceType.MISC);
    }
    
    public Collection<WikiCandidate> getRerankCandidates(){
        return candidatesByTitles.values();
    }

    public NounGroup getNounGroup() {
        if(nounGroup==null)
            nounGroup = new NounGroup(StringUtils.strip(surfaceForm,"."));
        return nounGroup;
    }

    public String headWord(){
        return getNounGroup().head();
    }
    
    public boolean sameHeadWord(Mention other){
        return StringUtils.equals(headWord(), other.headWord());
    }

    public boolean isAcronym(){
        return WordFeatures.isLikelyAcronym(surfaceForm);
    }
    
    public String getCleanedSurface(){
        return Index.stripNonAlphnumeric(surfaceForm);
    }

    public String getPseudoTitle(){
        return surfaceForm.replace(' ', '_');
    }
    
    public boolean longerThan(Mention other){
        if(other == null)
            return true;
        return Comparators.longerEntityFirst.compare(this, other)<0;
    }
    
    public void forceLinkToNull(){
        if (linkerScore >= 0)
            linkerScore = -8;
        candidatesByTitles.remove("*null*");
        if("*null*".equals(getTopTitle()))
            topCandidate = null;
        finalCandidate = null;
    }
    
    public String getTopTitle(){
        if (topCandidate == null)
            return null;
        return topCandidate.titleName;
    }
    
    private TIntHashSet allPossibleCandidates = null;
    

    /**
     * Crucial function for determining whether a url is likely to be entailed
     * by this mention
     * @param url
     * @return
     */
    public boolean hasPotentialCandidate(String url){
        
        if (StringUtils.isEmpty(url))
            return false;
        
        if(isAcronym() && url.length()>surfaceForm.length() && url.contains(surfaceForm))
            return false;
        
        if (candidatesByTitles.containsKey(url))
            return true;
        
        if(headWord().equals(WikiTitleUtils.getHead(url)))
            return true;
        

        
        // HashMap access should be fast
        for(String alternateNames:TitleNameIndexer.getAlternativeNames(url)){
            if(headWord().equals(WikiTitleUtils.getHead(alternateNames)))
                return true;
        }

        // Disk access might be slower
        if (allPossibleCandidates == null) {
            SurfaceFormSummaryProto sur = GlobalParameters.wikiAccess.getSurfaceFormInfo(surfaceForm);
            if(sur==null)
                return false;
            allPossibleCandidates = new TIntHashSet(sur.getTitleIdsList());
        }
        return allPossibleCandidates.contains(GlobalParameters.wikiAccess.getTitleIdOf(url));        
    }

    
    /**
     * @return Whether the candidate for this mention is only
     * due to redirects, no context information considered
     */
    public boolean isRedrectedOnly() {
        return redirectedOnly;
    }
    
    /**
     * 
     * @return Whether this mention should be considered 
     * not currently mapping to any other entity
     */
    public boolean isUnmapped(){
        return topCandidate == null || isRedrectedOnly();
    }


}
