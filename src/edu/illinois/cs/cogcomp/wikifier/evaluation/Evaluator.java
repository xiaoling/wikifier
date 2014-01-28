// $codepro.audit.disable useCharAtRatherThanStartsWith
package edu.illinois.cs.cogcomp.wikifier.evaluation;

import java.util.List;

import edu.illinois.cs.cogcomp.wikifier.models.LinkingProblem;
import edu.illinois.cs.cogcomp.wikifier.models.Mention;
import edu.illinois.cs.cogcomp.wikifier.models.ReferenceInstance;
import edu.illinois.cs.cogcomp.wikifier.models.WikiCandidate;
import edu.illinois.cs.cogcomp.wikifier.train.TrainWikifierSVM;
import edu.illinois.cs.cogcomp.wikifier.train.TrainWikifierSVM.GoldAnnotation;
import edu.illinois.cs.cogcomp.wikifier.utils.datastructure.SortedObjects;
import edu.illinois.cs.cogcomp.wikifier.wiki.access.TitleNameNormalizer;


public class Evaluator {
    
	public static boolean showPredictionCorrectness = true;
	/*
	 * The function marks the correctness of the prediction of the wikifier. This is a pretty big function 
	 *  which both returns the performance of the Wikifier and marks the correctness of the internal data 
	 *  structure for debugging purposes.
	 *  
	 *  Note that another way to evaluate the performance of the Wikifier is to save the output in the 
	 *  "Reference Instance" format and then use the function Evaluator.evaluateWikification(List<ReferenceInstance> predictedWikification, List<ReferenceInstance> goldWikification) 
	 *  However, that latter function will be missing some important information that we might need for a meaningful interpretation of the result. For example,
	 *  it's missing the information about which surface forms did not contain the correct disambiguation in the disambiguation candidates list. If we don't have
	 *  the correct disambiguation as a candidate, we have no chance to improve the performance on that entity, unless we find a way to add the correct solution to the
	 *  candidates list. To do this, we will either need to increase the size of the list, or to improve the distribution P(title|surface form,other context) so that the correct
	 *  solution will appear in the top K of the list.  
	 *  
	 *  NOTE THAT IT'S IMPORTANT THAT THE performance IS PASSED AS A PARAMETER, BECAUSE IT AGGREGATES THE RESULTS OVER MULTIPLE PROBLEMS
	 */
	public static void markWikificationCorrectness(LinkingProblem problem, List<ReferenceInstance> goldSolution, ProblemEvaluationResult preformance, String extraIncorrectWikificationNotes) throws Exception{

		GoldAnnotation gold = new GoldAnnotation(goldSolution);
		WikificationAsBOC eval = new WikificationAsBOC();
		eval.addWikificationGold(goldSolution);
		for(Mention e :problem.components){
			String prediction = "*null*";
			if(e.finalCandidate!=null&&!e.finalCandidate.titleName.equals("*null*")) {
				prediction = e.finalCandidate.titleName;
				eval.addWikificationPredictedOnAllText(e.surfaceForm, e.finalCandidate.titleName);
				if(gold.isReferenceInstance(e.charStart,e.charLength))
					eval.addWikificationPredictedOnProblemInstances(e.surfaceForm, e.finalCandidate.titleName);
			}
			String goldWikification = "*unknown*";
			if(gold.isReferenceInstance(e.charStart,e.charLength)) {
				goldWikification = "*null*";
				goldWikification = TitleNameNormalizer.normalize(gold.getGoldWikificationAsString(e.charStart,e.charLength));

				if (!goldWikification.equals("*null*")&& gold.getGoldDisambiguationCandidate(e, true)!=null) {
					preformance.linkerStats.numSolvableNonNullEntities++;
					if(e.topCandidate !=null && e.topCandidate.titleName.equals(goldWikification))
						preformance.linkerStats.numCorrectForcedPredictionsSolvableNonNullEntities++;
				}
				
				preformance.linkerStats.numTotalLinkerDecisions++;
				if((goldWikification.equals("*null*")&&prediction.equals("*null*"))||
						 (!goldWikification.equals("*null*")&& gold.getGoldDisambiguationCandidate(e, true)==null&& prediction.equals("*null*"))||
						 (!goldWikification.equals("*null*")&& goldWikification.equals(prediction)))						
					preformance.linkerStats.numCorrectLinkerDecisions++;
				// how well would "link everything" linker do?
				if(e.topCandidate !=null && goldWikification.equals(e.topCandidate.titleName))
					preformance.linkerStats.numCorrectLinkEverythingDecisions++;
				
				if (prediction.equals("*null*")) {
					if(prediction.equals(goldWikification))
						preformance.trueNegatives++;
					else
						preformance.falseNegatives++;
				} else {
					// top prediction is not null!
					for(int k=0;k<ProblemEvaluationResult.TOPK;k++) {
						SortedObjects<WikiCandidate> top = e.getRankedTopPredictionsLastLevel(k+1);
						for(int t=0;t<top.topObjects.size();t++) {
							if(top.topObjects.get(t).titleName.equals(goldWikification)) {
								preformance.truePositivesTopK[k]++;
							} else {
								if(goldWikification.equals("*null*"))
									preformance.falsePositivesTopK[k]++;								
								else 
									preformance.mismatchTopK[k]++;
							} // non-null prediction doesn't match the gold
						} //running over the top-k predictions
					}
				}// the prediction is not null;

				// marking some error analysis fields and printing some error logs
				String errorAnalysisInfo = "";
				if(showPredictionCorrectness) {
					SortedObjects<WikiCandidate> top2 = new SortedObjects<WikiCandidate>(2);
					List<WikiCandidate> lastLevel = e.getLastPredictionLevel();
					for(WikiCandidate candidate:lastLevel)
						top2.add(candidate, candidate.rankerScore);
					top2.sort();
					String confusion = "The surface form has no candidate disambiguations....";
					if(top2.topObjects.size()==1)
						confusion = " The surface form has a single disambiguation candidate : " + top2.topObjects.get(0).titleName +"(ranker score=" +top2.topScores.get(0)+") ";
					else if(top2.topObjects.size()>1)
						confusion = " The confusions set is : " + top2.topObjects.get(0).titleName +"(ranker score=" +top2.topScores.get(0)+") Vs: "+
												top2.topObjects.get(1).titleName +"(ranker score=" +top2.topScores.get(1)+")";
					errorAnalysisInfo =  e.surfaceForm+"; ------- ;\n  the prediction is: http://en.wikipedia.org/wiki/"   + 
														prediction+" ; the gold is: http://en.wikipedia.org/wiki/"+goldWikification+
														";\n "+confusion+";\n  The context is: ; ------- ; "+problem.text.substring(Math.max(0, e.charStart-200), 
														Math.min(problem.text.length(), e.charStart+200)).replace("\n", "");
				}
				
				if(extraIncorrectWikificationNotes.startsWith("Final") && 
						(goldWikification == null || goldWikification.equals("*null*"))){
					errorAnalysisInfo = "NIL "+e.types+"Entity:"+errorAnalysisInfo;
				}
				
				if(prediction.equals(goldWikification))  {
					if(showPredictionCorrectness) { 
						System.out.println(extraIncorrectWikificationNotes+"Correct Wikification of: " + e.surfaceForm+" to : http://en.wikipedia.org/wiki/"+ prediction);
						if(e.isCorrectPrediction==Mention.WikificationCorrectness.Incorrect)
							System.out.println(extraIncorrectWikificationNotes+"this fixes the Wikification (!!!)" + errorAnalysisInfo);
					}
					e.isCorrectPrediction = Mention.WikificationCorrectness.Correct;
				} else {
					if(showPredictionCorrectness) {
						
						if(e.isCorrectPrediction==Mention.WikificationCorrectness.Incorrect)
							System.out.println(extraIncorrectWikificationNotes+": Still Incorrect Wikification of: "+errorAnalysisInfo);
						if(e.isCorrectPrediction==Mention.WikificationCorrectness.Correct)
							System.out.println(extraIncorrectWikificationNotes+": this introduces a Wikification error(!!!): "+errorAnalysisInfo);						
					}
					e.isCorrectPrediction = Mention.WikificationCorrectness.Incorrect;
				}
				
				if(extraIncorrectWikificationNotes.startsWith("Final"))
					System.out.println("Candidates Entropy: "+ e.getCurrentDecisionEntropy());
			}
			e.goldAnnotation = goldWikification;
		}
		preformance.predictedEntitiesBocAllSurfaceForms += (eval.getNumberCorrectPredictionsOnAllText()+eval.getNumberIncorrectPredictionsOnAllText());
		preformance.predictedEntitiesBocProblemSurfaceForms += (eval.getNumberCorrectPredictionsOnProblemInstances()+eval.getNumberIncorrectPredictionsOnProblemInstances());
		preformance.goldEntititesBOC += eval.getNumberGoldConcepts();
		preformance.correctlyPredictedEntitiesBocAllSurfaceForms+=eval.getNumberCorrectPredictionsOnAllText();
		preformance.correctlyPredictedEntitiesBocProblemSurfaceForms+=eval.getNumberCorrectPredictionsOnProblemInstances();
	}

	/*
	 *  unfortunately in this setting we don't know which entities are solvable and which are not.... we also don't know which are NER entities and which are not
	 *  
	 *   NOTE THAT IT'S IMPORTANT THAT THE preformance IS PASSED AS A PARAMETER, BECAUSE IT AGGREGATES THE RESULTS OVER MULTIPLE PROBLEMS
	 */
	public static void  evaluateWikification(List<ReferenceInstance> predictedWikification, List<ReferenceInstance> goldWikification, ProblemEvaluationResult performance) throws Exception {
		TrainWikifierSVM.GoldAnnotation gold = new TrainWikifierSVM.GoldAnnotation(goldWikification);
		WikificationAsBOC eval = new WikificationAsBOC();
		for(int i=0; i<predictedWikification.size(); i++){
			ReferenceInstance ref = predictedWikification.get(i);
			String prediction = TitleNameNormalizer.normalize(ref.chosenAnnotation);
			if(!prediction.equals("*null*")) {
				eval.addWikificationPredictedOnAllText(ref.surfaceForm, prediction);
				if(gold.isReferenceInstance(ref.charStart, ref.charLength))
					eval.addWikificationPredictedOnProblemInstances(ref.surfaceForm, prediction);
			}
			if(gold.isReferenceInstance(ref.charStart, ref.charLength)) {
				String goldAnswer = TitleNameNormalizer.normalize(gold.getGoldWikificationAsString(ref.charStart, ref.charLength));
				if(prediction.equals("*null*")){
					if(prediction.equals(goldAnswer))
						performance.trueNegatives++;
					else
						performance.falseNegatives++;					
				} else {
					if(goldAnswer.equals(prediction))
						performance.truePositivesTopK[0]++;
					else {
						if(goldAnswer.equals("*null*"))
							performance.falsePositivesTopK[0]++;
						else
							performance.mismatchTopK[0]++;
					}
				}
			}
		}
		eval.addWikificationGold(goldWikification);
		performance.correctlyPredictedEntitiesBocAllSurfaceForms+=eval.getNumberCorrectPredictionsOnAllText();
		performance.correctlyPredictedEntitiesBocProblemSurfaceForms+=eval.getNumberCorrectPredictionsOnProblemInstances();
		performance.goldEntititesBOC+=eval.getNumberGoldConcepts();
		performance.predictedEntitiesBocAllSurfaceForms+=(eval.getNumberCorrectPredictionsOnAllText()+eval.getNumberIncorrectPredictionsOnAllText());
		performance.predictedEntitiesBocProblemSurfaceForms+=(eval.getNumberCorrectPredictionsOnProblemInstances()+eval.getNumberIncorrectPredictionsOnProblemInstances());
	}

} // evaluator
