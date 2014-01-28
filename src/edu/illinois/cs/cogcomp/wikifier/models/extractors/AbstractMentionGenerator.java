package edu.illinois.cs.cogcomp.wikifier.models.extractors;

import java.util.List;
import java.util.Map;

import edu.illinois.cs.cogcomp.edison.sentences.Constituent;
import edu.illinois.cs.cogcomp.wikifier.models.LinkingProblem;
import edu.illinois.cs.cogcomp.wikifier.models.TextSpan;
import edu.illinois.cs.cogcomp.wikifier.models.Mention;
import edu.illinois.cs.cogcomp.wikifier.models.Mention.SurfaceType;

abstract class AbstractMentionGenerator {

    public void generate(LinkingProblem problem, Map<String, Mention> entityMap, Iterable<Constituent> candidates) {
        for (Constituent c : candidates) {
            if (filter(c)||c.getStartSpan()>=c.getEndSpan())
                continue;
            try {
                String key = TextSpan.getPositionHashKey(c);
                if (entityMap.containsKey(key)) {
                    Mention existingEntity = entityMap.get(key);
                    if (existingEntity.types.size() == 0) {
                        existingEntity.types.addAll(types(c));
                    }
                } else {
                    Mention e = new Mention(c, problem);
                    e.types.addAll(types(c));
                    if(e.isNamedEntity())
                        e.setTopLevelEntity();
                    entityMap.put(key, e);
                }
            } catch (Exception e) {
                System.out.println("Warning -- a nasty exception caught:");
                e.printStackTrace();
            }
        }
    }

    public abstract List<SurfaceType> types(Constituent c);

    protected boolean filter(Constituent c) {
        // Filter null strings
        return false;
    }

}