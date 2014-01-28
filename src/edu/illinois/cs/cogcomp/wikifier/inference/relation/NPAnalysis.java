package edu.illinois.cs.cogcomp.wikifier.inference.relation;

import static edu.illinois.cs.cogcomp.edison.sentences.Queries.*;

import java.util.Map;

import com.google.common.collect.Maps;

import edu.illinois.cs.cogcomp.edison.sentences.Constituent;
import edu.illinois.cs.cogcomp.edison.sentences.View;
import edu.illinois.cs.cogcomp.edison.sentences.ViewNames;
import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;
import edu.illinois.cs.cogcomp.wikifier.common.ParameterPresets;
import edu.illinois.cs.cogcomp.wikifier.models.LinkingProblem;
import edu.illinois.cs.cogcomp.wikifier.models.Mention;
/**
 * Analyzing NP level relations
 * @author cheng88
 *
 */
public class NPAnalysis {
    
    private NPAnalysis(){}
    
    public static void infer(LinkingProblem problem){

        ProximityAnalysis.analyze(problem);
        NominalMentionAnalysis.analyze(problem);
        
        // Subchunk links
        forceLinkPERNameChunks(problem);
        if(GlobalParameters.params.preset != ParameterPresets.TAC)
            forceLinkCleanedSubEntity(problem);
    }


    /** Enforce
     * [[James][Stewart]] => [James]==[Stewart]==[James Stewart] in the same span
     * @param problem
     * @param relations
     */
    private static void forceLinkPERNameChunks(LinkingProblem problem){
        View nerview = problem.ta.getView(ViewNames.NER);
        // Force link people names to their surnames and first names etc.

        for(Constituent topC:nerview){
            if (topC.getLabel().equals("PER") && topC.getStartSpan() + 1 < topC.getEndSpan()) {
                Mention topLevelEntity = problem.getComponent(topC);
                if(topLevelEntity !=null && (!topLevelEntity.isTopLevelMention() || topLevelEntity.isSubEntity()))
                    continue;
                for(Constituent subC:problem.entityView.where(containedInConstituent(topC))){
                    // Exclude the entity itself
                    if(subC.getStartSpan()!=topC.getStartSpan() || subC.getEndSpan()!=topC.getEndSpan()){
                        Mention subEntity = problem.getComponent(subC);
                        problem.relations.addAll(subEntity.getCorefRelationsTo(topLevelEntity));
                    }
                }
            }
        }
    }
    
    /**
     * [W.Va.]==[W.Va] Minor segmentation issues
     * @param problem
     * @param relations
     */
    private static void forceLinkCleanedSubEntity(LinkingProblem problem){
        Map<String,Mention> dotted = Maps.newHashMap();

        // Collects surfaces from potentially wrong tokenization
        for(Mention e:problem.components){
            if(e.finalCandidate==null)
                continue;
            if(e.surfaceForm.endsWith(".") && e.surfaceForm.length()<4 ){
                int lenWithoutDot = e.surfaceForm.length()-1;
                dotted.put(e.surfaceForm.substring(0,lenWithoutDot), e);
            }
        }
        
        for (Mention e : problem.components) {
            Mention superEntity = dotted.get(e.surfaceForm);
            if (superEntity != null)
                problem.relations.addAll(e.getCorefRelationsTo(superEntity));

        }
    }

    

}
