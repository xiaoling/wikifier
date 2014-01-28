package edu.illinois.cs.cogcomp.wikifier.evaluation;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;
import edu.illinois.cs.cogcomp.wikifier.models.ReferenceInstance;
import edu.illinois.cs.cogcomp.wikifier.train.TrainWikifierSVM;
import edu.illinois.cs.cogcomp.wikifier.train.TrainWikifierSVM.GoldAnnotation;
import edu.illinois.cs.cogcomp.wikifier.utils.io.InFile;
import edu.illinois.cs.cogcomp.wikifier.wiki.access.TitleNameNormalizer;


public class WikificationAsBOC{
    
    private Map<String,Map<String,Boolean>> surfaceFormsToConceptsGold = new HashMap<String, Map<String,Boolean>>();
    private Map<String,Map<String,Boolean>> conceptsToSurfaceFormsGold = new HashMap<String, Map<String,Boolean>>();
    private Map<String,Map<String,Boolean>> conceptsToSurfaceFormsPredictedOnAllText = new HashMap<String, Map<String,Boolean>>();
    private Map<String,Map<String,Boolean>> conceptsToSurfaceFormsPredictedOnProblemInstances = new HashMap<String, Map<String,Boolean>>();
    
    public void addWikificationGold(List<ReferenceInstance> goldAnnotation) throws Exception {  
        for(int i=0;i<goldAnnotation.size();i++){
            String surfaceForm = goldAnnotation.get(i).surfaceForm;
            String concept = TitleNameNormalizer.normalize(goldAnnotation.get(i).chosenAnnotation);
            if(!concept.equals("null")&&!concept.equals("*null*")) {
                if(surfaceFormsToConceptsGold.containsKey(surfaceForm))
                    surfaceFormsToConceptsGold.get(surfaceForm).put(concept, true);
                else {
                    HashMap<String,Boolean> h= new HashMap<String,Boolean>();
                    h.put(concept, true);
                    surfaceFormsToConceptsGold.put(surfaceForm, h);             
                }
                if(conceptsToSurfaceFormsGold.containsKey(concept))
                    conceptsToSurfaceFormsGold.get(concept).put(surfaceForm, true);
                else {
                    HashMap<String,Boolean> h= new HashMap<String,Boolean>();
                    h.put(surfaceForm, true);
                    conceptsToSurfaceFormsGold.put(concept, h);             
                }
            }
        }
    }
    
    public void addWikificationPredictedOnAllText(String surfaceForm, String concept) {
        if(!concept.equals("null")&&!concept.equals("*null*")) {
            if(conceptsToSurfaceFormsPredictedOnAllText.containsKey(concept))
                conceptsToSurfaceFormsPredictedOnAllText.get(concept).put(surfaceForm, true);
            else {
                HashMap<String,Boolean> h= new HashMap<String,Boolean>();
                h.put(surfaceForm, true);
                conceptsToSurfaceFormsPredictedOnAllText.put(concept, h);               
            }           
        }
    }
    public void addWikificationPredictedOnProblemInstances(String surfaceForm, String concept) {
        if(!concept.equals("null")&&!concept.equals("*null*")) {
            if(conceptsToSurfaceFormsPredictedOnProblemInstances.containsKey(concept))
                conceptsToSurfaceFormsPredictedOnProblemInstances.get(concept).put(surfaceForm, true);
            else {
                HashMap<String,Boolean> h= new HashMap<String,Boolean>();
                h.put(surfaceForm, true);
                conceptsToSurfaceFormsPredictedOnProblemInstances.put(concept, h);              
            }
        }
    }
    /*
     * Note that here, getNumberCorrectPredictionsOnAllText() + getNumberIncorrectPredictionsOnAllText() != conceptsToSurfaceFormsGold.size();
     */
    public int getNumberCorrectPredictionsOnAllText() {
        int res=0;
        for(Iterator<String> i=conceptsToSurfaceFormsPredictedOnAllText.keySet().iterator();i.hasNext();) {
            if(conceptsToSurfaceFormsGold.containsKey( i.next())) 
                    res++;
        }
        return res;
    }
    /*
     * Note that here, getNumberCorrectPredictions() + getNumberIncorrectPredictions() != conceptsToSurfaceFormsGold.size();
     */
    public int getNumberCorrectPredictionsOnProblemInstances() {
        int res=0;
        for(Iterator<String> i=conceptsToSurfaceFormsPredictedOnProblemInstances.keySet().iterator();i.hasNext();) {
            if(conceptsToSurfaceFormsGold.containsKey( i.next())) 
                    res++;
        }
        return res;
    }
    /*
     * Note that here, getNumberCorrectPredictionsOnAllText() + getNumberIncorrectPredictionsOnAllText() != conceptsToSurfaceFormsPredictedOnAllText.size();
     */
    public int getNumberIncorrectPredictionsOnAllText() {
        int res=0;
        for(Iterator<String> i=conceptsToSurfaceFormsPredictedOnAllText.keySet().iterator();i.hasNext();) {
            String pconcept = i.next();
            if(!conceptsToSurfaceFormsGold.containsKey(pconcept)) {
                boolean isMistake = false;
                for(Iterator<String> j=conceptsToSurfaceFormsPredictedOnAllText.get(pconcept).keySet().iterator();j.hasNext();) {
                    String psurfaceForm = j.next();
                    if(surfaceFormsToConceptsGold.containsKey(psurfaceForm))
                        isMistake = true; // we know that the concept does not appear in the gold heap, but the surface form was hyperlinked to something, obviosly different!
                }
                if(isMistake)
                    res++;
            }
        }
        return res;
    }
    
    public int getNumberIncorrectPredictionsOnProblemInstances() {
        return conceptsToSurfaceFormsPredictedOnProblemInstances.size() - getNumberCorrectPredictionsOnProblemInstances();
    }

    public int getNumberGoldConcepts() {
        return conceptsToSurfaceFormsGold.size();
    }
    
    /*
     * This function has nothing to do with my Wikifier. It is used to print the BOC performance of the Milne Wikifier.
     * The Milne Wikifier output that I get is a bunch of lines that look like this:
     * APW19980603_0791.htm Tumor|0.811483| Patient|0.774633|patients   Tissue (biology)|0.744164|tissues
     * APW19980603_1617.htm Economy|0.236591|economic   English articles|0.203252|the
     * ...
     * So it's one problem per line, the first token in the line is the filename, followed by a list of [WikiTitle|score|surfaceForm]*
     * 
     * The optimistic evaluation accounts for the scenario where the gold annotation wikifies only a subset of
     * the true entities. For example in the ACe coref dataset, only the first mentions of NE coref chains were
     * wikified. Non-NEs were not annotated. In Cucerzan, many surface form of the same NE were
     * wikified, but again, non-NEs were not Wikified. 
     * If we are using the optimistic evaluation, then we count a precision mistake only if a concept that *did not* 
     * appear in the gold annotation was bound to a string that appeared in the gold annotation. It indicates that 
     * some string in the gold annotation was misdisambiguated. The recall is calculated the good old way
     * 
     */
    protected static ProblemEvaluationResult evaluateMilneOutputAsBOC(String pathToMilnePredictions,  String pathToRawTextFiles,
            List<List<ReferenceInstance>> goldWikificationInstances, List<String> goldAnnotationProblemDefinitionFilenames) throws Exception{
        ProblemEvaluationResult res = new ProblemEvaluationResult();

        HashMap<String,TrainWikifierSVM.GoldAnnotation> goldWikificationsByFile = new HashMap<String,TrainWikifierSVM.GoldAnnotation>();
        HashMap<String,WikificationAsBOC> wikificationsByFile = new HashMap<String, WikificationAsBOC>();

        System.out.println(goldWikificationInstances.size()+" problems in the dataset");
        for (int i=0; i<goldWikificationInstances.size(); i++) {
            String filename = goldAnnotationProblemDefinitionFilenames.get(i);
            WikificationAsBOC eval = new WikificationAsBOC();
            eval.addWikificationGold(goldWikificationInstances.get(i));
            for (int j=0;j<goldWikificationInstances.get(i).size();j++) {
                ReferenceInstance annotation = goldWikificationInstances.get(i).get(j);
                filename = annotation.rawTextFilename;
                if(!wikificationsByFile.containsKey(annotation.rawTextFilename))
                    wikificationsByFile.put(annotation.rawTextFilename,eval);
            }
            System.out.println("gold file for "+filename);
            wikificationsByFile.put(filename, eval);
            goldWikificationsByFile.put(filename, new TrainWikifierSVM.GoldAnnotation(goldWikificationInstances.get(i)));
        }

        InFile in = new InFile(pathToMilnePredictions);
        String line = in.readLine();
        while(line!=null) {
            StringTokenizer st=new StringTokenizer(line,"\t");
            String filename = st.nextToken();
            String rawText = InFile.readFileText(pathToRawTextFiles+"/"+filename);
            if(!wikificationsByFile.containsKey(filename) || !goldWikificationsByFile.containsKey(filename))
                throw new Exception("Reading Milne annotation for file "+filename+" but the gold annotation for the file was not specified!!!");
            WikificationAsBOC eval = wikificationsByFile.get(filename);
            GoldAnnotation gold = goldWikificationsByFile.get(filename);
            System.out.println("Filename = "+filename);
            for(Iterator<String> i=eval.surfaceFormsToConceptsGold.keySet().iterator();i.hasNext();){
                String s = i.next();
                String out = "Surface form:"+s+"; Referred concepts:";
                for(Iterator<String> j = eval.surfaceFormsToConceptsGold.get(s).keySet().iterator();j.hasNext();)
                    out+=" "+j.next();
                System.out.println("\tGold Reference Instance:"+out);
            }
            System.out.println("------------------Predictions:-------------------");
            while(st.hasMoreTokens()){
                StringTokenizer st2 = new StringTokenizer(st.nextToken(),"|");
                String concept = TitleNameNormalizer.normalize(st2.nextToken());
                Double.parseDouble(st2.nextToken()); // skip the score
                String surfaceForm = concept;
                if(st2.hasMoreTokens())
                    surfaceForm = st2.nextToken();
                //System.out.println("Surface form:"+surfaceForm+"* ; Title:"+concept);
                eval.addWikificationPredictedOnAllText(surfaceForm, concept);
                boolean isReferenceInstance = false;
                String goldWikification ="-------------";
                int nextPos = rawText.indexOf(surfaceForm,0);
                while(nextPos>-1) {
                    if(gold.isReferenceInstance(nextPos, surfaceForm.length())) {
                        isReferenceInstance = true;
                        goldWikification = gold.getGoldWikificationAsString(nextPos, surfaceForm.length());
                    }
                    nextPos = rawText.indexOf(surfaceForm, nextPos+1);
                }
                if(isReferenceInstance){
                    System.out.println("\tSurface form:"+surfaceForm+"* is a reference instance ; Predicted Title:"+concept+" ; gold title: "+goldWikification);
                    eval.addWikificationPredictedOnProblemInstances(surfaceForm, concept);
                }
            }
            System.out.println("-------------------------------------------------");

            wikificationsByFile.put(filename, eval);
            line = in.readLine();
        }
        in.close();
        for(Iterator<String> i = wikificationsByFile.keySet().iterator(); i.hasNext(); ) {
            WikificationAsBOC eval = wikificationsByFile.get(i.next());
            res.correctlyPredictedEntitiesBocAllSurfaceForms+=eval.getNumberCorrectPredictionsOnAllText();
            res.correctlyPredictedEntitiesBocProblemSurfaceForms+=eval.getNumberCorrectPredictionsOnProblemInstances();
            res.predictedEntitiesBocAllSurfaceForms+=eval.getNumberCorrectPredictionsOnAllText()+eval.getNumberIncorrectPredictionsOnAllText();
            res.predictedEntitiesBocProblemSurfaceForms+=eval.getNumberCorrectPredictionsOnProblemInstances()+eval.getNumberIncorrectPredictionsOnProblemInstances();
            res.goldEntititesBOC += eval.getNumberGoldConcepts();
        }
        return res;
    }
    
    /*
     * This is a simplified case which test the Evaluator for evaluating the output of the Milne system
     */
    public static void main(String[] args) throws Exception{        
        // check that the redirects were loaded correctly       
        GlobalParameters.paths.compressedRedirects = "./WikiData/RedirectsForEvaluationData/RedirectsAug2010.txt";
        
        System.out.println("Usage: java CommonSenseWikifier.TrainingAndInference.Evaluator <path to latest redirects file> <path to Milne BOC output file>  <path to raw text files> <path to gold ReferenceInstances, all files>");
        System.out.println("This will evaluate the Bag-Of-Concepts performance of the Milne system");
        System.out.println("Note that with different version of Wikipedia title names move around, therefore we need the latest version of Wikipedia to 'normalize' the title names.");
        System.out.println("Also note that the output of the Milne system is currently given in this format:");
        System.out.println("\tAPW19980603_0791.htm  Tumor|0.811483| Patient|0.774633|patients   Tissue (biology)|0.744164|tissues ...");
        System.out.println("\tAPW19980603_1617.htm  Economy|0.236591|economic   English articles|0.203252|the ...");
        System.out.println("Hence, all the files in the dataset are given in one file. So for evaluation, I need the path to ALL the gold ReferenceInstance problems for the dataset");

        GlobalParameters.paths.compressedRedirects = args[0];
        String pathToMilneFile = args[1];
        String pathToRawTextFiles = args[2];
        String[] files = (new File(args[3])).list();
        List<List<ReferenceInstance>> gold = new ArrayList<List<ReferenceInstance>>();
        List<String> goldAnnotationProblemDefinitionFilenames = new ArrayList<String>(); // some of the problem files contain no reference instances!
        for(int i = 0; i<files.length; i++)
            if(!files[i].startsWith(".")) {
                System.out.println("Problem :"+args[3]+"/"+files[i]);
                List<ReferenceInstance> ri = ReferenceInstance.loadReferenceProblem(args[3]+"/"+files[i]);
                gold.add(ri);
                goldAnnotationProblemDefinitionFilenames.add(files[i]);
                System.out.println("filename: "+files[i]);
            }
        evaluateMilneOutputAsBOC(pathToMilneFile,pathToRawTextFiles, gold, goldAnnotationProblemDefinitionFilenames).printPerformance();
    }
    
}