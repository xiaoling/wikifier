// $codepro.audit.disable useCharAtRatherThanStartsWith
package edu.illinois.cs.cogcomp.wikifier.apps;

import java.io.File;
import java.util.HashMap;
import java.util.List;

import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;
import edu.illinois.cs.cogcomp.wikifier.evaluation.ProblemEvaluationResult;
import edu.illinois.cs.cogcomp.wikifier.evaluation.WikificationAsBOC;
import edu.illinois.cs.cogcomp.wikifier.inference.InferenceEngine;
import edu.illinois.cs.cogcomp.wikifier.models.LinkingProblem;
import edu.illinois.cs.cogcomp.wikifier.models.Mention;
import edu.illinois.cs.cogcomp.wikifier.models.ReferenceInstance;
import edu.illinois.cs.cogcomp.wikifier.models.WikiCandidate;
import edu.illinois.cs.cogcomp.wikifier.train.TrainWikifierSVM;
import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.SortedObjects;
import edu.illinois.cs.cogcomp.wikifier.utils.io.InFile;
import edu.illinois.cs.cogcomp.wikifier.utils.io.OutFile;
import edu.illinois.cs.cogcomp.wikifier.wiki.access.TitleNameNormalizer;


public class MonitorDisambiguationProblemStatistics {
	ProblemEvaluationResult oracleResult = new ProblemEvaluationResult();
	int maxNumDisambiguationsToRetrieve = 25;
	int numberOfNonNullGoldEntities = 0;
	int numberOfNonNullGoldEntitieswithCandidates = 0;
	int numberOfGoldConcepts = 0;
	int numberOfIdentifiedGoldConcepts = 0;
	int numberOfSolvableGoldConcepts = 0;
	// numberOfSolvableEntitiesInDefaultRanking[3] = how many entities can we solve if we construct only 3 disambiguation
	// candidates per entity. Note that the disambiguation candidates are constructed by probability
	int[] numberOfSolvableEntitiesInDefaultRanking = new int[maxNumDisambiguationsToRetrieve]; 
	
	public void updateStats(LinkingProblem problem, List<ReferenceInstance> goldAnnotation) throws Exception {
		TrainWikifierSVM.GoldAnnotation gold = new TrainWikifierSVM.GoldAnnotation(goldAnnotation);
		WikificationAsBOC oracleBOC = new WikificationAsBOC();
		oracleBOC.addWikificationGold(goldAnnotation);
		HashMap<String,Boolean> goldEntitiesWithCandidates = new HashMap<String, Boolean>();
		HashMap<String,Boolean> goldConcepts = new HashMap<String, Boolean>();
		for(int i=0;i<goldAnnotation.size();i++){
			String concept = TitleNameNormalizer.normalize(goldAnnotation.get(i).chosenAnnotation);
			if (!concept.equals("null")&&!concept.equals("*null*"))
				goldConcepts.put(concept, true);
		}
		numberOfGoldConcepts += goldConcepts.size(); 
		HashMap<String,Boolean> oracleConcepts = new HashMap<String, Boolean>();
		HashMap<String,Boolean> identifiedGoldConcepts = new HashMap<String, Boolean>();
		
		InferenceEngine engine = new InferenceEngine(false);
		// New annotations will add in candidates
		engine.annotate(problem, goldAnnotation, false, false, 0);
		
		for(Mention e : problem.components) {
			if(gold.isReferenceInstance(e.charStart, e.charLength)) {
				String goldWikification = gold.getGoldWikificationAsString(e.charStart, e.charLength);
				goldEntitiesWithCandidates.put(e.surfaceForm, true);
				if (!goldWikification.equals("null")&&!goldWikification.equals("*null*")) {
					identifiedGoldConcepts.put(goldWikification, true);
					numberOfNonNullGoldEntitieswithCandidates++;
					// This changes to last level
					List<WikiCandidate> lastLevel = e.getLastPredictionLevel();
					for(int i=1; i <= maxNumDisambiguationsToRetrieve; i++) {
						SortedObjects<WikiCandidate> top = new SortedObjects<WikiCandidate>(i);
						for(int j=0;j<lastLevel.size();j++)
							top.add(lastLevel.get(j), lastLevel.get(j).rankerScore);
						if(gold.getGoldDisambiguationCandidate(e, top.topObjects)!=null)
							numberOfSolvableEntitiesInDefaultRanking[i-1]++;
					}
					WikiCandidate oraclePrediction = gold.getGoldDisambiguationCandidate(e,lastLevel); 
					if(oraclePrediction!=null) {
						oracleBOC.addWikificationPredictedOnAllText(e.surfaceForm, oraclePrediction.titleName);
						oracleBOC.addWikificationPredictedOnProblemInstances(e.surfaceForm, oraclePrediction.titleName);
						oracleConcepts.put(oraclePrediction.titleName,true);
						oracleResult.truePositivesTopK[0]++;
					} else {
						oracleResult.falseNegatives++;
					}
				} else {
					// the gold disambiguation is null - we can always predict it correctly as far as the surface form predictions go...
					oracleResult.trueNegatives++;
				}
			}
		}
		numberOfSolvableGoldConcepts+=oracleConcepts.size();
		numberOfIdentifiedGoldConcepts+=identifiedGoldConcepts.size();
		for(int i=0;i<goldAnnotation.size();i++) {
			ReferenceInstance e = goldAnnotation.get(i);
			if(!e.chosenAnnotation.equals("null")&&
					!e.chosenAnnotation.equals("*null*")) {
				numberOfNonNullGoldEntities++;
				if(!goldEntitiesWithCandidates.containsKey(e.surfaceForm))
					System.out.println("Warning: a problem entity without disambiguation candidates : "+e.surfaceForm);
			}
		}
		oracleResult.correctlyPredictedEntitiesBocAllSurfaceForms+=oracleBOC.getNumberCorrectPredictionsOnAllText();
		oracleResult.correctlyPredictedEntitiesBocProblemSurfaceForms += oracleBOC.getNumberCorrectPredictionsOnProblemInstances();
		oracleResult.predictedEntitiesBocAllSurfaceForms += oracleBOC.getNumberIncorrectPredictionsOnAllText()+oracleBOC.getNumberCorrectPredictionsOnAllText();
		oracleResult.predictedEntitiesBocProblemSurfaceForms += oracleBOC.getNumberIncorrectPredictionsOnProblemInstances()+oracleBOC.getNumberCorrectPredictionsOnProblemInstances();
		oracleResult.goldEntititesBOC += oracleBOC.getNumberGoldConcepts();
	}
	
	public static void writeProblemEntitiesAndDisambiguations(LinkingProblem problem, 
			List<ReferenceInstance> goldAnnotation, String outputFilename) throws Exception {
		TrainWikifierSVM.GoldAnnotation gold = new TrainWikifierSVM.GoldAnnotation(goldAnnotation);
		OutFile out = new OutFile(outputFilename);
		out.println("<TEXT>\n"+problem.text.replace('\n', ' ')+"\n</TEXT>");
		for(int entityId = 0; entityId < problem.components.size() ; entityId++) {
			Mention e = problem.components.get(entityId);
			if(gold.isReferenceInstance(e.charStart, e.charLength)) {
				String goldWikification = gold.getGoldWikificationAsString(e.charStart, e.charLength);
				out.println("<SURFACE_FORM>\n\t"+e.surfaceForm+"["+e.charStart+"-"+e.charLength+"]\n</SURFACE_FORM>");
				out.println("<GOLD>\n\t"+goldWikification+"\n</GOLD>");
				out.println("<CANDIDATES>");				
				List<WikiCandidate> firstLevel = e.candidates.get(0);
				for(int i=0; i < firstLevel.size(); i++) 
					out.println("\t"+firstLevel.get(i).titleName);				
				out.println("</CANDIDATES>");				
			}
		}
		out.close();
	}
	
	public static void main(String[] args) throws Exception {
		String pathToProblems=args[0];
		String pathToRawText=args[1];
		String pathToOutput=args[2]; // this is the path where I put the texts, the entities and the disambiguation candidates in a format which will be easy for Doug to parse...
		GlobalParameters.loadConfig(args[args.length-1]);

		File f=new File(pathToProblems);
		InferenceEngine inference=new InferenceEngine(false);
		inference.refreshPerformanceCounters();
		MonitorDisambiguationProblemStatistics stats = new MonitorDisambiguationProblemStatistics();
		if(f.isDirectory()){
			String[] files=f.list();
			for(int i=0;i<files.length;i++)
				if(!files[i].startsWith(".")){
					List<ReferenceInstance> goldAnnotation = ReferenceInstance.loadReferenceProblem(pathToProblems+"/"+files[i]);
					String text=InFile.readFileText(pathToRawText+"/"+files[i]);
					LinkingProblem problem = new LinkingProblem(pathToRawText+"/"+files[i], 
							GlobalParameters.curator.getTextAnnotation(text), 
							goldAnnotation);
					stats.updateStats(problem, goldAnnotation);
					MonitorDisambiguationProblemStatistics.writeProblemEntitiesAndDisambiguations(problem, goldAnnotation, pathToOutput+"/"+files[i]);
					problem.close();
				}
		}
		else{
			throw new Exception("Expecting a folder here...");
		}
		System.out.println("------------ Performance with an Oracle ----------------");
		stats.oracleResult.printPerformance();
		System.out.println("Number of non-null entities in the dataset : "+stats.numberOfNonNullGoldEntities);
		System.out.println("Number of non-null entities in the dataset with disambiguation candidates: "+stats.numberOfNonNullGoldEntitieswithCandidates);
		System.out.println("Max per-position performance possible when retrieving K candidates: ");
		for(int i=0;i<stats.numberOfSolvableEntitiesInDefaultRanking.length;i++)
			System.out.println("K="+(i+1)+"Solvable Entities: "+stats.numberOfSolvableEntitiesInDefaultRanking[i]+"\t Max Recall="+((double)stats.numberOfSolvableEntitiesInDefaultRanking[i])/((double)stats.numberOfNonNullGoldEntitieswithCandidates));
		System.out.println("Number of gold concepts = "+stats.numberOfGoldConcepts+" ;  number of identified gold concepts = "+stats.numberOfIdentifiedGoldConcepts+" ; number of solvable gold concepts = "+stats.numberOfSolvableGoldConcepts);
	}
}
