package edu.illinois.cs.cogcomp.wikifier.models;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Test;

import com.google.common.collect.Lists;

import edu.illinois.cs.cogcomp.edison.sentences.TextAnnotation;
import edu.illinois.cs.cogcomp.wikifier.inference.RelationSolver;
import edu.illinois.cs.cogcomp.wikifier.inference.RelationSolver.SolverType;
import edu.illinois.cs.cogcomp.wikifier.inference.coref.CorefIndexer;

public class ILPModelTest {

	@Test
	public void testCoreference() throws Exception {
		
		
		Mention e1 = new Mention();
		
		WikiCandidate e1c1 = new WikiCandidate(e1);
		e1c1.titleName = "Nick_Saban";
		e1c1.rankerScore = 1.8377621126362371;
		
		e1.surfaceForm = "Nick Saban";
		e1.candidates = Lists.newArrayList();
		e1.candidates.add(Lists.newArrayList(Arrays.asList(e1c1)));
		e1.topCandidate = e1c1;
		
		
		
		Mention e2 = new Mention();
		
		WikiCandidate e2c1 = new WikiCandidate(e2);
		e2c1.titleName = "Saban_Entertainment";
		e2c1.rankerScore = 1.0931675393960676;
		WikiCandidate e2c2 = new WikiCandidate(e2);
		e2c2.titleName = "Saban_of_Baekje";
		e2c2.rankerScore = 0.03600872525054621;
		WikiCandidate e2c3 = new WikiCandidate(e2);
		e2c3.titleName = "Nick_Saban";
		e2c3.rankerScore = 0.0;
		
		e2.surfaceForm = "Saban";
		e2.candidates = Lists.newArrayList();
		e2.candidates.add(Arrays.asList(e2c1,e2c2,e2c3));
		e2.topCandidate = e2c1;
		
		
		CoherenceRelation corefereceRelation = new CoherenceRelation(e1c1, e2c3, 1.1);
		
		LinkingProblem problem = new LinkingProblem();
		problem.ta = new TextAnnotation("", "", Arrays.asList("Hello","you"));
		problem.components = Lists.newArrayList(Arrays.asList(e1,e2));
		problem.relations = new ArrayList<CoherenceRelation>(Arrays.asList(corefereceRelation));
		problem.prepareForRerank();
				
		
		RelationSolver solver = new RelationSolver(problem,SolverType.GUROBI);
		
		solver.solve();
		
		solver.explain();
		
		assertEquals("Nick_Saban",e1.topCandidate.titleName);
		assertEquals("Nick_Saban",e2.topCandidate.titleName);
		
	}
	
	
	private static class TopLevelMention extends Mention{
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		
		public TopLevelMention(){
		    types.add(SurfaceType.NER);
		}

		public boolean isTopLevelMention(){
			return true;
		}
	}
	
	@Test
	public void testAcronymCoreference() throws Exception {
		
		Mention e1 = new TopLevelMention();

		e1.surfaceForm = "Automatic Data Processing";

		Mention e2 = new TopLevelMention();

		e2.surfaceForm = "ADP";

		LinkingProblem problem = new LinkingProblem();
		
		Mention e3 = new TopLevelMention();
		
	    e3.surfaceForm = "Bad Data Processing Inc.";
		
		problem.components = Arrays.asList(e1,e2,e3);

		
		CorefIndexer indexer = new CorefIndexer(problem);

		assertEquals(2,indexer.search("ADP").size());
		assertEquals(1,indexer.search("A.D.P.").size());
		assertEquals(2,indexer.search("A.D.P").size());
		assertEquals(1,indexer.search("BDP").size());
		
	}

}
