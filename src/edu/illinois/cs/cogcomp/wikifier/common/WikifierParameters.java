package edu.illinois.cs.cogcomp.wikifier.common;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

/**
 * These parameters affects the performance of the Wikifier
 * @author cheng88
 *
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class WikifierParameters {
    
    // if the linkability is below this number, the surface form will not be considered as wikifiable entity
    public double minLinkability;
    
    // Consider Acronyms
    public double minSurfaceLen;
    
    public int maxCandidatesToGenerateInitially;

    /**********************************************************************
     * Alation study section
     ***********************************************************************/
    // Depends on the annotation standards, sometimes we do not want to output disambiguation pages
    // Note this might affect the performance about 1-2% for different data sets
    public boolean EXCLUDE_DISAMBIGUATION_PAGE = false;
    // This controls heuristics that lowercases and expands the surface, almost always needs to be true
    public boolean GENERATE_CONTEXTUAL_CANDIDATES = true;
    // This generates entities longer than 5 tokens by scanning with regex patterns
    public boolean GENERATE_LONG_ENTITIES = true;
    // This checks the current Wikipedia index and mark unindexed gold annotations as null
    public boolean UNINDEXED_AS_NULL = true;
    /**
     * Inference parameters
     */
    
    //--------Exact search related options----------
    // This heuristic basically gets exact matches (only case/punctuation changes allowed) from Wikipedia index 
    // that is not a disambiguation page. This change only affects mentions without candidates
    // as we know they are not ambiguous
    public boolean USE_LEXICAL_SEARCH_HEURSTICS = true;
    
    public boolean USE_SPELL_CHECK = true;
    // Enable this for TAC, the four datasets we have are conservatively annotated
    public boolean USE_FUZZY_SEARCH = true;
    //--------Coref related options----------
    /**
     * Note that this option does not require coreference view from TextAnnotation. However the
     * performance will likely improve provided some coref clusters
     * {@link GlobalParameters#ANNOTATE_COREF_AT_RUNTIME} that serves as a filter for clustering
     * similar entity mentions.
     */
    public boolean USE_COREF = true;
    //turn on this for tac
    public boolean DISABLE_LOC_COREF = true;
    public boolean USE_RELATIONAL_INFERENCE = true;
    
    public boolean RESOLVE_NOMINAL_COREF;
    /**
     *  End of Inference parameters
     */

    // This is to save inference overhead if we do not use them
    public boolean useLexicalFeaturesNaive = false;
    
    // if the conditional probability p(title|surface form) is larger than this number, 
    // the title is considered to be an almost unambiguous solution
    public double unambiguousThreshold; 
    public boolean disallowEntityOverlap;
    public boolean useNestedNER = true;

    public boolean useOnlyConditionalTitleProbabilitiesInTitleMatchFeatures;
    public boolean useLexicalFeaturesReweighted;
    public boolean useUnambiguousInDisambiguationContext;
    public boolean useNamedEntitiesInDisambiguationContext;
    public boolean useAllSurfaceFormsInDisambiguationContext;
    public boolean useOnlyLinkedSurfaceFormsForDisambiguationContext;
    public boolean useCoherenceFeatures;
    public boolean useGoldSolutionsForCoherence;
    
    public boolean generateFeaturesConjunctions;
    public int numCandidatesAtCoherenceLevel;
    public ParameterPresets preset;


    
    public static final WikifierParameters defaultInstance() {
        return new WikifierParameters() {
            {
                useLexicalFeaturesReweighted = true;
                useAllSurfaceFormsInDisambiguationContext = true;
                useOnlyLinkedSurfaceFormsForDisambiguationContext = true;
                useCoherenceFeatures = true;
                numCandidatesAtCoherenceLevel = 3;

                unambiguousThreshold = 0.99;
                maxCandidatesToGenerateInitially = 20;
                minSurfaceLen = 0;
                minLinkability = -1;
                preset = ParameterPresets.DEFAULT;
            }
        };
    }
    
}
