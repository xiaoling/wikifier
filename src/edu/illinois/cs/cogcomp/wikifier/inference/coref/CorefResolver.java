package edu.illinois.cs.cogcomp.wikifier.inference.coref;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Sets;

import edu.illinois.cs.cogcomp.edison.sentences.Constituent;
import edu.illinois.cs.cogcomp.edison.sentences.CoreferenceView;
import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;
import edu.illinois.cs.cogcomp.wikifier.models.LinkingProblem;
import edu.illinois.cs.cogcomp.wikifier.models.Mention;
import edu.illinois.cs.cogcomp.wikifier.utils.Comparators;
import edu.illinois.cs.cogcomp.wikifier.utils.WordFeatures;
/**
 * Resolves coreference relations via voting ( sum/max )
 * Coref view is not required
 * @author cheng88
 *
 */
public final class CorefResolver {

    public static boolean useNEROnly = true;

    // Only PER allows to adjunct out of coref cluster, if not ambiguous
    // All matches restricted by lexical sim (lower-cased), thus using a coref-index based approach
    public static CorefClusters generateClusters(LinkingProblem problem){

//        View nerView = problem.ta.getView(ViewNames.NER);
        
        CoreferenceView corefView = problem.getCorefView();
                
        CorefIndexer index = new CorefIndexer(problem);

        CorefClusters clusters = new CorefClusters();
        
        List<Mention> indexedEntities = new ArrayList<Mention>(index.currentItems);
        Collections.sort(indexedEntities,Comparators.shorterEntityFirst);
        
        for(Mention ne:indexedEntities){
            // Ignore already clustered entities
            if(ne==null || clusters.containsKey(ne))
                continue;

            
            // Locations are dangerous to coref
            if (ne.isLOC() && (GlobalParameters.params.DISABLE_LOC_COREF || ne.tokenLength() > 1))
                continue;
            
            EntityCluster eCluster = getNormalizedCluster(ne, corefView, index);
            if (eCluster.size() > 1)
                clusters.addCluster(eCluster);

        }
        return clusters;
    }
    
    public static void resolveCorefRelations(LinkingProblem problem,CorefClusters clusters){
        for(EntityCluster cluster:clusters){
            if (cluster.size() > 1)
                resolveCorefChain(cluster, problem);
        }
    }
 
    // If more than 2 different appearances that have different top disambiguation
    private static EntityCluster getNormalizedCluster(
            Mention query,
            CoreferenceView corefView,
            CorefIndexer corefIndex){


        // If the surface is the same as query, we can vote later, but if different surfaces
        // have we have to rely on coref clusters
        Set<Mention> neMatches = corefIndex.searchSet(query.surfaceForm);
        neMatches.removeAll(query.parentProblem.getCorefExceptions(query));
        
        EntityCluster cluster = new EntityCluster();
        
        // We do not want to coref locations
        if(notCorefable(query,neMatches))
            return cluster;
 
        Set<String> crossClusterExceptions = Sets.newHashSet();
        // First collect in-cluster NEs, filter out non-NEs and non-matching NEs
        if(corefView!=null)
            for(Constituent c:corefView.getCoreferentMentions(query.constituent)){
                Mention ne = query.parentProblem.getComponent(c);
                // The cluster has to be lexically matching TODO: remove
                // lexical matching constraints when coref gets better
                // and must be a named entity
                if (neMatches.contains(ne)) {
                    cluster.add(ne);
                    // These surfaces are known to be OK to coref
                    crossClusterExceptions.add(ne.getCleanedSurface());
                }
            }
        int ambiguity = getAmbiguity(query,cluster,neMatches);
        // Due to the presence of ambiguity, we will not add anything cross cluster
        // For locations, we require coref cluster to allow for cross type reference
        if (ambiguity <= 1 && !(query.isLOC() && crossClusterExceptions.size() <= 1)) {
            boolean isAcronym = query.isAcronym();
            boolean ignoreType = corefIndex.getIndexedTitleCount(query.getCleanedSurface()) >= 3
                    || query.topCandidate == null
                    || isAcronym;
            String cleanSurface = query.getCleanedSurface();
            for(Mention e:neMatches){
                String coreferentSurface = e.getCleanedSurface();
                // Acronym can only coref to those having same surfaces or fully spelled, not those containing an abbreviation
                if (isAcronym && coreferentSurface.contains(cleanSurface) && coreferentSurface.length() != cleanSurface.length())
                    continue;
                // Cross cluster allows only same type and same surface
                if(ignoreType 
                        || e.types.equals(query.types)
                        || crossClusterExceptions.contains(coreferentSurface))
                    cluster.add(e);
            }
        }else{
            System.out.println(ambiguity+" too ambiguous for "+query+", not adding cross cluster candidates.");
        }
 
        
        return cluster;
    }
    
    private static int getAmbiguity(
            Mention query,
            EntityCluster existingCluster,
            Iterable<Mention> neMatches){
     // Optional for cross cluster merges, this assumes type-safety
        LexicalCluster ambiguity = new LexicalCluster();
        for(Mention candidate:neMatches){
            boolean sameSurface = candidate.getCleanedSurface().equals(query.getCleanedSurface());
            
            if(!sameSurface
               && (candidate.types.equals(query.types))
               && !existingCluster.contains(candidate) 
              )
                ambiguity.put(candidate);
        }
        return ambiguity.size();
    }
    
    /**
     * Either the entity is never refered to as sth other than LOC
     * or it is in the form of an adjective-like MISC like Russian.
     * @param query
     * @param occurrences
     * @return
     */
    private static boolean notCorefable(Mention query,Iterable<Mention> occurrences){
        if(query.isMISC()){
            String nounForm = WordFeatures.nounForm(query.surfaceForm);
            if(!query.surfaceForm.equals(nounForm))
                return true;
        }
        
        if(!query.isLOC())
            return false;

        for(Mention e:occurrences){
            if(e.getCleanedSurface().equals(query.getCleanedSurface())&& !e.isLOC())
                return false;
        }
        return true;
    }
    
    /**
     * Resolves the coref relation in the cluster by
     * 1. Vote for the best candidate
     * 2. Use it as the head for all other members
     * @param corefCluster
     * @param problem
     */
    private static void resolveCorefChain(EntityCluster corefCluster,LinkingProblem problem){
        
        CorefElection election = new CorefElection(corefCluster);
        
        String winner = election.elect();
        
        if(winner==null)// all nulls
            corefCluster.setHead(election.getSortedEntities().get(0));
        else{
            for(Mention e:election.getSortedEntities()){
                if(e.topCandidate!=null && e.topCandidate.titleName.equals(winner)){
                    corefCluster.setHead(e);
                    break;
                }
            }
        }
        
        if (corefCluster.getHead() != null){
            for (Mention child : corefCluster) {
                if (validCorefLink(child, corefCluster.getHead())) {
                    problem.relations.addAll(child.getCorefRelationsTo(corefCluster.getHead()));
                }
            }
        }else{
            System.out.println("Invalid coref head in coref voting "+corefCluster.getHead());
        }
    }

    
    private static boolean validCorefLink(Mention child,Mention head){
//        !(head.isCurrentlyLinkingToNull() && !child.isCurrentlyLinkingToNull())
        if(head==child)
            return false;
        if(child.tokenLength()>2 && child.isPerfectMatch())
            return false;
        // Compositional entity | organizations with modifiers need head word enforcement
        if(!child.isAcronym() 
                && (!head.isAllCaps() && (head.isORG() || head.isMISC()) )
                && (head.surfaceForm.contains(" and ") || !head.sameHeadWord(child))
           )
//        if(!child.isAcronym() 
//                && (head.isORG() || head.isMISC())
//                && (head.surfaceForm.contains(" and ") 
//                        || !head.sameHeadWord(child) && child.topDisambiguation==null)
//           )
            return false;
        return true;
    }

}
