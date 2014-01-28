package edu.illinois.cs.cogcomp.wikifier.models.extractors;

import static edu.illinois.cs.cogcomp.wikifier.utils.WordFeatures.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeRangeSet;

import edu.illinois.cs.cogcomp.edison.sentences.Constituent;
import edu.illinois.cs.cogcomp.edison.sentences.TextAnnotation;
import edu.illinois.cs.cogcomp.edison.sentences.View;
import edu.illinois.cs.cogcomp.edison.sentences.ViewNames;
import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;
import edu.illinois.cs.cogcomp.wikifier.common.ParameterPresets;
import edu.illinois.cs.cogcomp.wikifier.models.LinkingProblem;
import edu.illinois.cs.cogcomp.wikifier.models.Mention;
import edu.illinois.cs.cogcomp.wikifier.models.Mention.SurfaceType;
import edu.illinois.cs.cogcomp.wikifier.models.ReferenceInstance;
import edu.illinois.cs.cogcomp.wikifier.models.TextSpan;
import edu.illinois.cs.cogcomp.wikifier.models.WikiCandidate;
import edu.illinois.cs.cogcomp.wikifier.utils.Comparators;
import edu.illinois.cs.cogcomp.wikifier.wiki.access.WikiAccess;
import edu.illinois.cs.cogcomp.wikifier.wiki.access.WikiAccess.WikiMatchData;
import edu.illinois.cs.cogcomp.wikifier.wiki.indexing.TitleNameIndexer;

public class MentionExtractor {
        
    // do not generate single-token wikifiable
    // entities unless you have a good reason to
    public static final int MIN_SUBCHUNK_SIZE = 1;

    // do not generate wikifiable entities with
    // more than problem many tokens unless you have a good reason to
    public static final int MAX_SUBCHUNK_SIZE = 5;
    
    public static boolean displayProgress = false;
    public static boolean displayManuallyDefinedMentions = false;

    private MentionExtractor(){}
    
    
    private static final AbstractMentionGenerator nerGenerator = new AbstractMentionGenerator() {
        @Override
        public List<SurfaceType> types(Constituent c) {
            return Arrays.asList(SurfaceType.NER, SurfaceType.valueOf(c.getLabel()));
        }
    };

    private static final AbstractMentionGenerator chunkGenerator = new AbstractMentionGenerator() {

        private final AbstractMentionGenerator subChunkGenerator = new AbstractMentionGenerator() {
            @Override
            public List<SurfaceType> types(Constituent c) {
                return Arrays.asList(SurfaceType.NPSubchunk);
            }
        };

        @Override
        public List<SurfaceType> types(Constituent c) {
            return Arrays.asList(SurfaceType.NPChunk);
        }

        @Override
        protected boolean filter(Constituent c) {
            return !c.getLabel().startsWith("NP");
        }

        @Override
        public void generate(LinkingProblem problem, Map<String, Mention> entityMap, Iterable<Constituent> candidates) {
            // NP chunks
            super.generate(problem, entityMap, candidates);
            // Add sub chunks
            for (Constituent c : candidates) {
                subChunkGenerator.generate(problem, entityMap, getSubphrases(c, problem.ta));
            }
        }

        private List<Constituent> getSubphrases(Constituent c, TextAnnotation ta) {
            List<Constituent> res = Lists.newArrayList();
            for (int start = c.getStartSpan(); start < c.getEndSpan(); start++){
                
                String startToken = ta.getToken(start);
                boolean startsWithLowerCase = !isCapitalized(startToken);
                
                for (int end = start + MIN_SUBCHUNK_SIZE; end <= Math.min(c.getEndSpan(), start + MAX_SUBCHUNK_SIZE + 1); end++){

                    if (start != c.getStartSpan() || end != c.getEndSpan()){
                        if(startsWithLowerCase && isCapitalized(ta.getToken(end-1)))
                            break;
                        res.add(new Constituent("sub-NP", "sub-NP", ta, start, end));
                    }
                }
            }
            return res;
        }
    };

    /**
     * 
     * @param ta
     * @return
     */
    private static Map<String, Mention> generateEntitiesFromAnnotation(LinkingProblem problem) {

        TextAnnotation ta = problem.ta;
        String sourceFilename = problem.sourceFilename;
        
        Map<String, Mention> wikifiableEntititesMap = Maps.newHashMap();
        // adding the potential wikifiable entities from the noun-phrase shallow parse and the NEs
        System.out.println("Getting the text annotation");
        // adding the NER chunks
        System.out.println("Adding NER candidates for " + sourceFilename);
        nerGenerator.generate(problem, wikifiableEntititesMap, getNERConsituents(ta));

        // adding the shallow parse NP chunks
        System.out.println("Adding SHALLOW_PARSE and subChunk candidates for " + sourceFilename);
        chunkGenerator.generate(problem, wikifiableEntititesMap, ta.getView(ViewNames.SHALLOW_PARSE));

        System.out.println("Done - Getting the text annotation");

        return wikifiableEntititesMap;
    }
    
    /**
     * Filters out overlapping ner entities if demanded
     * @param ta
     * @return
     */
    private static Iterable<Constituent> getNERConsituents(TextAnnotation ta){
        View nerView = ta.getView(ViewNames.NER);
        if(GlobalParameters.params.useNestedNER)
            return nerView;
        List<Constituent> currentNers = nerView.getConstituents();
        List<Constituent> cleanedList = new ArrayList<>(currentNers.size());
        Collections.sort(currentNers,Comparators.longerConstituentFirst);
        RangeSet<Integer> takenTokens = TreeRangeSet.create();
        for(Constituent c:currentNers){
            Range<Integer> currentRange = Range.closed(c.getStartSpan(), c.getEndSpan()-1);
            if(takenTokens.encloses(currentRange)){
                continue;
            }
            takenTokens.add(currentRange);
            cleanedList.add(c);
        }
        return cleanedList;
    }

    /**
     * Merges annotation based entities and manually defined entities as well as generating some
     * super entities that adjuncts adjacent "Top Level" entities
     * 
     * @param ta
     * @return merged entities
     */
    private static Set<Mention> consolidateEntities(
            LinkingProblem problem,
            Map<String,Mention> potentiallyWikifiableEntititesMap,
            List<ReferenceInstance> manuallyDefinedEntititesList){

        Set<Mention> manuallyDefinedEntititesMap = Sets.newHashSet();
        if (manuallyDefinedEntititesList == null)
            return manuallyDefinedEntititesMap;

        System.out.println("Adding manually specified mentions");
        for(ReferenceInstance instance :manuallyDefinedEntititesList){
            
            int start = instance.charStart;
            int len = instance.charLength;
            String surface =  instance.surfaceForm;
            String key = instance.getPositionHashKey();
            if(displayManuallyDefinedMentions)
                System.out.println("*************** Adding the manually defined entity "+instance+"["+key+"] to the problem");
            Mention e = potentiallyWikifiableEntititesMap.get(key);
            if(e == null) {
                // add the entity, but don't add any types to it.
                e = new Mention(surface, start, len, problem);
                potentiallyWikifiableEntititesMap.put(key, e);
            }else{
                // input spell override
                e.surfaceForm = surface;
            }
            instance.setAssignedEntity(e);
            // TAC queries are assumed to be named entities
            if (GlobalParameters.params.preset == ParameterPresets.TAC
                    && "QUERY".equals(instance.chosenAnnotation)){
                e.types.add(SurfaceType.NER);
//                if("LOC".equals(instance.comments)){
//                    e.types.add(SurfaceType.LOC);
//                }
            }
            manuallyDefinedEntititesMap.add(e);
        }
        if (manuallyDefinedEntititesList.size() == 0)
            if (displayManuallyDefinedMentions)
                System.out.println("*************** There were no manually defined entities ****************");

        if(GlobalParameters.params.GENERATE_LONG_ENTITIES)
            addSuperChunkEntities(problem,potentiallyWikifiableEntititesMap);
        
        return manuallyDefinedEntititesMap;
    }

    protected static String namedGroup(String name,String pattern){
        return String.format("(?<%s>%s)", name,pattern);
    }
    // Regex match super entities
    public static final String capitalizedWord = "([A-Z](\\.([\\w\\-]{1,4}\\.)*|[\\w\\-']*))";
    public static final String topLevelEntity = String.format("(%s(\\s+%s)*)", capitalizedWord, capitalizedWord);
//    public static final String completeSplitConnective = "(\\s*(in)|(at)|(of)|(the)|(for)|(and)|(on)\\s+)";
    public static final String leftConcatConnective = "(('s)|(\\&)| \\& )";
    public static final String hardConnective = "(\\s*(,|'|(`+)|(in)|(at)|(of)|(the)|(for)|(and)|(on)|(with)|(vs)))";
    public static final String connective = String.format("(%s|%s)\\s*",leftConcatConnective,hardConnective);
    public static final String atMost2Connectives = String.format("((%s)?%s)", connective,connective);
    public static final String joinedPattern = String.format("(%s(%s\\s*%s)+)", topLevelEntity,atMost2Connectives, topLevelEntity);

    static final Pattern superEntityPattern = Pattern.compile("(?=(\\b"+joinedPattern+"))");
    
    static final Pattern multipleSpaces = Pattern.compile("\\s+");
    
    private static void addSuperChunkEntities(
            LinkingProblem problem,
            Map<String, Mention> entityMap) {
        
        TextAnnotation ta = problem.ta;
        String text = problem.text;
        Matcher matcher = superEntityPattern.matcher(text);
        
        System.out.println("Regex matching...");
        Mention prev = null;
        while (matcher.find()) {
            // look ahead match is group 1
            int startChar = matcher.start(1);
            int endChar = matcher.end(1);
            int length = endChar - startChar;
            String key = TextSpan.getPositionHashKey(startChar, length);
            String surface = text.substring(startChar, endChar);
            
            Mention e;
            if (entityMap.containsKey(key)) {
                e = entityMap.get(key);
            } else {

                surface = multipleSpaces.matcher(surface).replaceAll(" ");
                e = new Mention(surface, startChar, length, problem);


                // Do not count entities across sentences
                String previousToken = e.startTokenId == 0 ? null : ta.getToken(e.startTokenId - 1);
                if (!sameSentence(ta, e.startTokenId, e.endTokenId)
                        || StringUtils.countMatches(surface, ",") > 1 // filter excessive list structures
                        || (isCapitalized(previousToken) 
                                && !GlobalParameters.stops.isStopword(previousToken.toLowerCase()) 
                                && sameSentence(ta, e.startTokenId - 2, e.startTokenId - 1))){
                    e.close();
                    continue;
                }
            }
            System.out.println("Matched regex entity " + e);
            if (prev != null && prev.surfaceForm.startsWith("The ") && prev.surfaceForm.contains(surface) && prev.isNamedEntity()) {
                if (!e.isNamedEntity()) {
                    e.types.addAll(prev.types);
                }
            }
            prev = e;
            e.setTopLevelEntity();
            entityMap.put(key, e);
//            matcher.region(ta.getTokenCharacterOffset(e.startTokenId+1).getSecond(), text.length());
            
        }
        System.out.println("Finished adding regex large chunk matching");
    }
    
    private static boolean sameSentence(TextAnnotation ta,int id1,int id2){
        int max = ta.getTokens().length;
        if(id1<0 || id2< 0|| id1>=max || id2>=max)
            return false;
        return ta.getSentenceId(id1)==ta.getSentenceId(id2);
    }

    /*
     * use manuallyDefinedEntitites=null if they're not given. wikipedia is used to check which
     * mentions have disambiguation candidates and prune those which don't
     */
    public static List<Mention> extract(
            LinkingProblem problem,
            List<ReferenceInstance> refInstances,
            WikiAccess wikipedia) throws Exception {

        TextAnnotation ta = problem.ta;
        String text = ta.getText();
        System.out.println("Getting the Wikifiable entitites");
        // TokenIdToCharOffsetMapper textMapper = new TokenIdToCharOffsetMapper(text);
        // both of the maps are keyed by "<start-position-in-text>-<end-position-in-text>"
        Map<String, Mention> candidateEntities = generateEntitiesFromAnnotation(problem);
        Map<String, ReferenceInstance> refInstanceMap = Maps.newHashMap();
        // problem part hashes the reference instances. the reason is that when we finally return the
        // wikifiable entitites, we
        // want to "bind" them to the reference instances (or rather, we want to store in the
        // reference instances the
        // wikifiable entity bound to it). problem is used later in evaluation
        if (refInstances != null) {
            for (ReferenceInstance ri : refInstances) {
                refInstanceMap.put(ri.getPositionHashKey(), ri);
                ri.setAssignedEntity(null);
                if (GlobalParameters.wikiAccess.isKnownUnlinkable(ri.surfaceForm))
                    System.out.println("Important warning: the manually defined entity " + ri.surfaceForm + " is known to be unlinkable.");
            }
        }

        Set<Mention> manualEntities = consolidateEntities(problem, candidateEntities, refInstances);

        // adding manually defined entities

        // OK, now return only those WikifiableEntities which contain disambiguation candidates
        // also, don't forget to initialize the context string and its TF-IDF representation
        System.out.println("Extracting the candidate disambiguations for the mentions");
        List<Mention> res = new ArrayList<Mention>();

        // Collect match data for entities
        for (Mention e : candidateEntities.values()) {

            if ((!e.isTopLevelMention() && GlobalParameters.stops.isStopword(e.surfaceForm.toLowerCase())) 
                    // Fast unlink
                    || GlobalParameters.wikiAccess.isKnownUnlinkable(e.surfaceForm)) {
                continue;
            }

            // problem is slow
            List<WikiMatchData> matchData = CandidateGenerator.getGlobalCandidates(ta, e);

            // Here we allow zero candidate entities
            if (matchData.size() > 0 
                    || e.isTopLevelMention()
                    || TitleNameIndexer.normalize(e.surfaceForm) != null) {

                e.generateLocalContext(text, wikipedia);

                e.candidates = Lists.newArrayList();
                List<WikiCandidate> firstLayer = Lists.newArrayList();
                for (WikiMatchData wikiMatchData : matchData) {
                    WikiCandidate candidate = new WikiCandidate(e, wikiMatchData);
                    firstLayer.add(candidate);
                }
                e.candidates.add(firstLayer);

                // now mark the corresponding reference instance (if exists) that problem wikifiable
                // entity tries to disambiguate it
                if (refInstanceMap.containsKey(e.getPositionHashKey()))
                    refInstanceMap.get(e.getPositionHashKey()).setAssignedEntity(e);
                res.add(e);
            } else {
                // there was no wiki match data for the entity
                if (manualEntities.contains(e)) {
                    System.out.println("Important warning: the manually defined entity " + e.surfaceForm
                            + " had 0 disambiguation candidates");
                }

            }
        } // running on all potentially the wikifiable entitites

        if (displayProgress) {
            printProgress(res);
        }

        System.out.println("Done constructing the Wikifiable entities");
        expandNER(res);
        return res;
    } // function

    private static void printProgress(List<Mention> res) {
        System.out.println("The list of potentially Wikifiable entities: (" + res.size() + ") in total");
        int counter = 0;
        for (Mention e : res) {
            List<WikiCandidate> lastLevel = e.getLastPredictionLevel();
            System.out.println("\t(" + counter + ") " + e.surfaceForm + "[" + e.charStart + "-" + e.charLength + "]; " + lastLevel.size()
                    + " disambiguation candidates:");
            for (WikiCandidate candidate : lastLevel)
                System.out.println("\t\t-" + candidate.titleName);
            counter++;
        }
    }
    
    private static void expandNER(Iterable<Mention> entities) {
        
        Map<String,Set<SurfaceType>> nerSurfaces = Maps.newHashMap();
        for(Mention e:entities){
            if(e.isNamedEntity())
                nerSurfaces.put(e.surfaceForm,e.types);
        }
        
        for(Mention e:entities){
            if(!e.isNamedEntity() && e.isTopLevelMention() && nerSurfaces.containsKey(e.surfaceForm)){
                e.types.addAll(nerSurfaces.get(e.surfaceForm));
            }
        }
    }

}
