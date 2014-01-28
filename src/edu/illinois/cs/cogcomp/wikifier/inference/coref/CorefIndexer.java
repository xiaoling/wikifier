package edu.illinois.cs.cogcomp.wikifier.inference.coref;

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import edu.illinois.cs.cogcomp.wikifier.models.LinkingProblem;
import edu.illinois.cs.cogcomp.wikifier.models.Mention;
import edu.illinois.cs.cogcomp.wikifier.wiki.indexing.StringIndexer;

/**
 * Hashes the entity string tokens, including acronyms for coreference
 * to retrieve
 * @author cheng88
 *
 */
public class CorefIndexer extends StringIndexer<Mention>{

    public static enum IndexType{
        NER, TOPLEVEL
    }
    
    public CorefIndexer(Iterable<Mention> entities){
        this(entities,IndexType.NER);
    }

    public CorefIndexer(Iterable<Mention> entities,IndexType type){

        for(Mention e : entities){
            switch(type){
            case NER:
                if(e.isNamedEntity())
                    addToIndex(e);
                break;
            case TOPLEVEL:
                if(e.isTopLevelMention() && e.isAllCaps())
                    addToIndex(e);
                break;
            default:
                    break;
            }
        }
    }
    
    public CorefIndexer(LinkingProblem problem){
        for(Mention e : problem.components){
            if(e.isNamedEntity())
                addToIndex(e);
        }
//        CoreferenceView cv = problem.getCorefView();
//        if(cv!=null)
//            for(Constituent c:cv){
//                WikifiableEntity e = problem.getComponent(c);
//                if(e!=null && e.isAllCaps() && e.isTopLevelEntity() && !GlobalParameters.stops.isStopword(e.surfaceForm.toLowerCase()))
//                    addToIndex(e);
//            }
    }


    /**
	 * Excludes candidates below a ranker score
	 * @param problem
	 * @param pruningRankerScore
	 */
	public CorefIndexer(LinkingProblem problem,boolean old){
        for(Mention e : problem.components){
//          corefClusters.put(e.surfaceForm,e);
            
            if ((!e.isAllCaps() && !e.isTopLevelMention())
               || e.tokenLength()==1
               // Ignore nickname-like mentions
               || (e.topCandidate!=null && 
                  !e.lexicallyFuzzyMatchesTopDisambiguation()
                  )
               // Only indexes multi word
               || StringUtils.isAlphanumeric(e.surfaceForm)
               )
                continue;
            addToIndex(e);
        }
	}
	
	public Set<Mention> searchSet(String surface){
	    return new HashSet<Mention>(search(surface));
	}
	
	

//	/**
//	 * Only returns super-strings, which is asymmetric
//	 * e.g. Index: AB, ABC, AC
//	 * Search: AB => ABC
//	 * Search: ABC => null
//	 * Search: AC => null
//	 * @param surface
//	 * @return pruned co-ref results with shorter entity first
//	 */
//	@Override
//	public List<WikifiableEntity> search(String surface) {
//
//		List<WikifiableEntity> retval = new ArrayList<WikifiableEntity>();
//		
//		boolean isLikelyAcronym = WordFeatures.isLikelyAcronym(surface);
//		// filtering identical surfaces, as they would not provide more information in terms of coref
//		// also filters out shorter surfaces as we only need 1 for each pair
//		for(WikifiableEntity entity:super.search(surface)){
//			String resultSurface = entity.surfaceForm;
//
//			if(isLikelyAcronym || resultSurface.contains(surface))
//				retval.add(entity);
//		}
//		
//		Collections.sort(retval,Comparators.shorterEntityFirst);
//
//		return retval;
//	}

	@Override
	protected String indexStr(Mention entity) {
        if (entity.topCandidate != null)
	        counter.count(entity.topCandidate.titleName);
		return	entity.surfaceForm;
	}
	

	
//	
//	
//
//
//	Map<String,CoherenceRelation> topRecord = Maps.newHashMap();
//	
//	public void recordDecision(CoherenceRelation relation){
//	    String from = relation.arg1.entityToDisambiguate.surfaceForm;
//
//	    CoherenceRelation previousRelation  =  topRecord.get(from);
//	    if(previousRelation == null || relation.weight > previousRelation.weight){
//	        topRecord.put(from, relation);
//	    }
//	}
//	
//	public WikifiableEntity getExistingMapping(String surface){
//	    CoherenceRelation relation = topRecord.get(surface);
//	    if(relation != null)
//	        return relation.arg2.entityToDisambiguate;
//	    return null;  
//	}
//	
//	Set<WikifiableEntity> unmappedEntities = Sets.newHashSet();
//	public void checkForLater(WikifiableEntity e){
//	    unmappedEntities.add(e);
//	}
//	
//	/**
//	 * This method generates relations from previous mappings
//	 * @return
//	 */
//	public List<CoherenceRelation> getPostInferenceRelations(){
//	    List<CoherenceRelation> retlist = Lists.newArrayList();
//	    for(WikifiableEntity e:unmappedEntities){
//	        CoherenceRelation prevRelation = topRecord.get(e.surfaceForm);
//	        if(prevRelation!=null){
//	            DisambiguationCandidate appended = 
//	                    WikifiableEntity.ensureCorefCandidateExists(e,prevRelation.arg2.entityToDisambiguate);
//	            retlist.add(new CoherenceRelation(appended, prevRelation.arg2, prevRelation.weight));
//	        }
//	    }
//	    return retlist;
//	}
//
//

	
	

}
