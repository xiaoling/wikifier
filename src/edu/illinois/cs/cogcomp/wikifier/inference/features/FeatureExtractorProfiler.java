package edu.illinois.cs.cogcomp.wikifier.inference.features;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import profiler.MentionExtractor;
import profiler.data.Query;
import profiler.embedding.EmbeddedProfileDB;
import profiler.pattern.PatternExtractor;
import profiler.prototype.Exp;
import profiler.util.DocAnnotationUtils;
import edu.illinois.cs.cogcomp.wikifier.common.GlobalParameters;
import edu.illinois.cs.cogcomp.wikifier.models.LinkingProblem;
import edu.illinois.cs.cogcomp.wikifier.models.Mention;
import edu.illinois.cs.cogcomp.wikifier.models.WikiCandidate;
import edu.stanford.nlp.pipeline.Annotation;

/**
 * 
 * @author Xiao Ling
 */

public class FeatureExtractorProfiler extends FeatureExtractorInterface {
	public FeatureExtractorProfiler(String extractorName, boolean inTraining,
			String pathToSaves) throws Exception {
		super(extractorName, inTraining, pathToSaves);
	}

	private final static Logger logger = LoggerFactory
			.getLogger(FeatureExtractorProfiler.class);

	@Override
	public void extractFeatures(LinkingProblem problem) throws Exception {
		long lastTime = System.currentTimeMillis();
		FeatureExtractorThread[] threads = new FeatureExtractorThread[GlobalParameters.THREAD_NUM];
		for (int i = 0; i < threads.length; i++)
			threads[i] = new FeatureExtractorThread(this, problem);
		for (int componentId = 0; componentId < problem.components.size(); componentId++)
			threads[componentId % GlobalParameters.THREAD_NUM]
					.addComponent(componentId);
		ExecutorService execSvc = Executors
				.newFixedThreadPool(GlobalParameters.THREAD_NUM);
		for (Thread thread : threads)
			execSvc.execute(thread);
		execSvc.shutdown();
		execSvc.awaitTermination(300, TimeUnit.SECONDS);
		featureExtractionTime += System.currentTimeMillis() - lastTime;
		System.out.println(System.currentTimeMillis() - lastTime
				+ " milliseconds elapsed extracting features for the level: "
				+ extractorName);
	}

	@Override
	protected void extractFeatures(LinkingProblem problem, int componentId)
			throws Exception {
		Annotation ann = DocAnnotationUtils.instance().readDocument(
				problem.sourceFilename);
		if (Exp.me == null) {
			Exp.me = MentionExtractor.instance();
			Exp.pe = PatternExtractor.instance();
		}
		Mention component = problem.components.get(componentId);
		Query q = new Query();
		q.qid = problem.sourceFilename + "_c" + componentId;
		q.startToken = component.startTokenId;
		q.endToken = component.endTokenId;
		q.mention = component.surfaceForm;
		q.docid = problem.sourceFilename;
		q.sentid = component.getSentenceId();
		Set<String> profile = Exp.getMentionProfile(ann, q);
		List<double[]> profileVectors = null;
		EmbeddedProfileDB epdb = EmbeddedProfileDB.instance();
		// if (X.getBoolean("use_embedding"))
		{
			profileVectors = new ArrayList<double[]>();
			for (String action : profile) {
				double[] verbVector = epdb.getVerbVector(action);
				if (verbVector != null) {
					profileVectors.add(verbVector);
				}
			}
			if (profileVectors.isEmpty()) {
				logger.info("[{}] has an empty profile", new Object[] { q.qid });
			}
		}
		// component.
		List<WikiCandidate> lastLevel = component.getLastPredictionLevel();

		// ok, all the TF-IDF vectors have been computed, now we actually add
		// the features
		int i = -1;
		for (WikiCandidate c : lastLevel) {
			i++;
			c.otherFeatures.addFeature("profile_score",
					epdb.score(c.getTid(), profileVectors));
		}
	}
}
