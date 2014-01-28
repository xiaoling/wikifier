package edu.illinois.cs.cogcomp.wikifier.inference;

import org.gnu.glpk.GLPK;
import org.gnu.glpk.glp_prob;

import LBJ2.classify.Score;
import LBJ2.infer.ILPSolver;

/**
 * The library is not thread safe
 * TODO: hook the new library
 * @author cheng88
 * @incomplete
 */

public class GLPKHook implements ILPSolver {

    glp_prob problem;
    
    public GLPKHook(){
        int env = GLPK.glp_free_env();
        reset();
    }
    
    @Override
    public int addBooleanVariable(double arg0) {
        int column = GLPK.glp_add_cols(problem, 1);
        GLPK.glp_set_col_kind(problem, column, GLPK.GLP_IV);
        GLPK.glp_set_col_bnds(problem, column, GLPK.GLP_DB, 0.0, 1.0);
        GLPK.glp_set_obj_coef(problem, column, arg0);
        return 0;
    }

    @Override
    public int[] addDiscreteVariable(double[] arg0) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    @Override
    public int[] addDiscreteVariable(Score[] arg0) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    @Override
    public void addEqualityConstraint(int[] arg0, double[] arg1, double arg2) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    @Override
    public void addGreaterThanConstraint(int[] arg0, double[] arg1, double arg2) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    @Override
    public void addLessThanConstraint(int[] arg0, double[] arg1, double arg2) {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean getBooleanValue(int arg0) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    @Override
    public boolean isSolved() {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    @Override
    public double objectiveValue() {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    @Override
    public void reset() {

        problem = GLPK.glp_create_prob();
    }

    @Override
    public void setMaximize(boolean arg0) {
        

    }

    @Override
    public boolean solve() throws Exception {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void write(StringBuffer arg0) {
        
    }

}
