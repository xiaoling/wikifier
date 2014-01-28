package edu.illinois.cs.cogcomp.wikifier.inference;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import LBJ2.infer.GurobiHook;
import LBJ2.infer.ILPSolver;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import edu.illinois.cs.cogcomp.infer.ilp.BeamSearch;
import edu.illinois.cs.cogcomp.wikifier.models.CoherenceRelation;
import edu.illinois.cs.cogcomp.wikifier.models.LinkingProblem;
import edu.illinois.cs.cogcomp.wikifier.models.Mention;
import edu.illinois.cs.cogcomp.wikifier.models.WikiCandidate;
import gnu.trove.list.array.TIntArrayList;
import gurobi.GRBException;

/**
 * 
 * @author cheng88
 *
 */
public class RelationSolver{
	
	// We model whether each candidate is chosen as a binary variable
	private Map<WikiCandidate,Integer> varMap = Maps.newHashMap();
	private Map<CoherenceRelation,Integer> relationMap = Maps.newHashMap();
	
	private ILPSolver solver;
	private LinkingProblem problem;
	
	public enum SolverType{
	    GUROBI{
            @Override
            public ILPSolver getSolver() {
                return new GurobiHook();
            }
	        
	    },BEAM_SEARCH{
	        public static final int BEAM_SIZE = 20;
            @Override
            public ILPSolver getSolver() {
                return new BeamSearch(BEAM_SIZE);
            }
            
	    },GLPK{
            @Override
            public ILPSolver getSolver() {
                
                return new GLPKHook();
            }
	    };
	    public abstract ILPSolver getSolver();
	}
	
	public RelationSolver(LinkingProblem problem,SolverType type){
	    solver = type.getSolver();
	    this.problem = problem;
	    intializeVaraibles();
	    intializeConstraints();
	    
	    solver.setMaximize(true);
	}
	
	/**
	 * Adds one variable for each disambiguation candidate as well as constraining for unique solutions
	 */
	private void intializeVaraibles() {
        for(Mention entity:problem.components){

            TIntArrayList uniqueSolution = new TIntArrayList();
            for(WikiCandidate candidate:entity.getRerankCandidates()){
                
                
                // Each variable is binary in [0,1] with objective value as its ranker score,
                // When a candidate is chosen we gain the same as rankerScore
                int varId = solver.addBooleanVariable(candidate.rankerScore);
                
                //Saves the candidate variable for later coherence objectives
                varMap.put(candidate,varId);
                uniqueSolution.add(varId);
            }
            
            // Constrains that at most one candidate can be chosen
            // \sum isSolution <= 1
            int[] constraintVars = uniqueSolution.toArray();
            double[] coefficients = newVector(constraintVars.length, 1.0);
            solver.addLessThanConstraint(constraintVars, coefficients, 1.0);
        }
    }
	
	private static double[] newVector(int length,double init){
	    double[] values = new double[length];
	    Arrays.fill(values, init);
	    return values;
	}
	
	
    private static final double[] RELATION_CONSTRAINT_COEFFCIENTS = new double[] { 2, -1, -1 };
	/**
	 * Constructs the conjunction variable Z = X^Y
     * and gives Z an objective weight though relations
	 */
	private void intializeConstraints() {
        for(CoherenceRelation relation:problem.relations){
            Integer x = varMap.get(relation.arg1);
            Integer y = varMap.get(relation.arg2);
            
            // Sometimes candidates are removed due to hard constraints, ignoring the
            // relation
            if(x==null || y==null)
                continue;
            
            // Create Z
            int z = solver.addBooleanVariable(relation.weight);
            
            // Z <= X ^ Z <=Y
            // ==>
            // 2Z <= X+Y
            // ==>
            // 2Z - X - Y <= 0
            
            solver.addLessThanConstraint(new int[]{z,x,y}, RELATION_CONSTRAINT_COEFFCIENTS, 0);
            
//            GRBVar z = model.addVar(0.0, 1.0, relation.weight , GRB.BINARY, varName(relation));
            relationMap.put(relation, z);
        }
    }
	

	public void solve() throws Exception{

		solver.solve();
		
		// Force links active relations
        for(CoherenceRelation relation:relationMap.keySet()){
            if(isRelationActive(relation)){
                relation.arg1.mentionToDisambiguate.linkerScore = 1;
                relation.arg2.mentionToDisambiguate.linkerScore = 1;
            }
        }
        
		//Collect results and apply new decisions
		for(Mention e : problem.components){
			
			e.finalCandidate = null;
			for(WikiCandidate candidate: e.getRerankCandidates()){
			    if(isCandidateActive(candidate)){
					// Respect previous linking decisions, adjust score once we have
					// a good scoring mechanism, record whether the candidate was promoted?
                    if (e.topCandidate != null
                            && e.topCandidate.titleName.equals(candidate.titleName)
                            && e.linkerScore < 0) {
                        e.forceLinkToNull();
                        break;
                    }
					
					// A null decision
					if(candidate.titleName.equals("*null*")){
					    e.forceLinkToNull();
					}else
    					// Otherwise use the newly promoted candidate
    					e.finalCandidate = e.topCandidate = candidate;

					break;
				}
			}
		}
	}
	
    private boolean isRelationActive(CoherenceRelation relation) {
        return solver.getBooleanValue(relationMap.get(relation));
    }

    private boolean isCandidateActive(WikiCandidate candidate) {
        return solver.getBooleanValue(varMap.get(candidate));
    }

	/**
	 * Explains the reason behind the optimization
	 * @throws GRBException
	 */
	public List<CoherenceRelation> explain() throws GRBException{
		List<CoherenceRelation> activeRelations = Lists.newArrayList();
		List<CoherenceRelation> rejectedRelations = Lists.newArrayList();
		for(CoherenceRelation relation:relationMap.keySet()){
			if(isRelationActive(relation)){
				System.out.println(relation+" is captured by ILP inference.");
				activeRelations.add(relation);
			}else{
			    rejectedRelations.add(relation);
			}
		}
		
		System.out.printf("Discarded %d hypothesis\n",rejectedRelations.size());
//		for(CoherenceRelation rejection:rejectedRelations){
//		    System.out.println(rejection);
//		}
		return activeRelations;
	}

}
