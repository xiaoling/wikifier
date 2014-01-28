package edu.illinois.cs.cogcomp.wikifier.inference.features;
 
import java.util.List;

import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;
import edu.illinois.cs.cogcomp.wikifier.models.WikiCandidate;
import edu.illinois.cs.cogcomp.wikifier.models.LinkingProblem;
import edu.illinois.cs.cogcomp.wikifier.models.Mention;



public class FeatureExtractorTitleMatchAndFrequency extends FeatureExtractorInterface {
    
//	public static boolean useOnlyConditionalTitleProbabilitiesInTitleMatchFeatures = false;
	
	public FeatureExtractorTitleMatchAndFrequency(String _extractorName, boolean inTraining, String pathToSaves) throws Exception{
		super(_extractorName, inTraining, pathToSaves);
	}
	
	public void extractFeatures(LinkingProblem problem) throws Exception {
		// This is the only place where we cannot do parallelization! 
		// The reason is that William Cohen's string similarity metric is not thread safe 
		long lastTime = System.currentTimeMillis();
		for(int componentId=0;componentId<problem.components.size();componentId++)
			extractFeatures(problem, componentId);
		featureExtractionTime += System.currentTimeMillis()-lastTime;
		System.out.println( System.currentTimeMillis()-lastTime+" milliseconds elapsed extracting features for the level: "+extractorName);
	}

	protected void extractFeatures(LinkingProblem problem,int componentId) throws Exception {
		//at the first stage we need to extract the first layer of disambiguation candidates,
		//that is, just to match the named entity title to wikipedia titles
		Mention component = problem.components.get(componentId);
		List<WikiCandidate> lastLevel=component.getLastPredictionLevel();
		for (int i=0; i<lastLevel.size(); i++){
			WikiCandidate candidate=lastLevel.get(i);

			candidate.titleFeatures.addFeature("P(title|surfaceForm)", candidate.wikiData.conditionalTitleProb);
			if(!GlobalParameters.params.useOnlyConditionalTitleProbabilitiesInTitleMatchFeatures) {
				candidate.titleFeatures.addFeature("ArticleFrequency",
						(double)candidate.wikiData.basicTitleInfo.getTitleAppearanceCount()/
						(double)GlobalParameters.wikiAccess.getTotalNumberOfWikipediaTitles());
				candidate.otherFeatures.addFeature("TitlePosInCandidateList", candidate.wikiData.disambiguationRank);
				candidate.otherFeatures.addFeature("P(surfaceForm|title)", candidate.wikiData.conditionalSurfaceFromProb);
			}
		}
	}	
}
