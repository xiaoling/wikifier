package edu.illinois.cs.cogcomp.wikifier.inference;

import static org.junit.Assert.*;
import gurobi.GRB;
import gurobi.GRBEnv;
import gurobi.GRBLinExpr;
import gurobi.GRBModel;
import gurobi.GRBVar;

import org.junit.Test;

public class GurobiTest {

	@Test
	public void test() {
		try {
//			String tempFileName = File.createTempFile("Gurobi", "log").getPath();
			GRBEnv env = new GRBEnv();
			GRBModel model = new GRBModel(env);

			// Create variables

			GRBVar x = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "x");
			GRBVar y = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "y");
			GRBVar z = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "z");

			// Integrate new variables

			model.update();

			// Set objective: maximize x + y + 2 z

			GRBLinExpr expr = new GRBLinExpr();
			expr.addTerm(1.0, x);
			expr.addTerm(1.0, y);
			expr.addTerm(2.0, z);
			model.setObjective(expr, GRB.MAXIMIZE);

			// Add constraint: x + 2 y + 3 z <= 4

			expr = new GRBLinExpr();
			expr.addTerm(1.0, x);
			expr.addTerm(2.0, y);
			expr.addTerm(3.0, z);
			model.addConstr(expr, GRB.LESS_EQUAL, 4.0, "c0");

			// Add constraint: x + y >= 1

			expr = new GRBLinExpr();
			expr.addTerm(1.0, x);
			expr.addTerm(1.0, y);
			model.addConstr(expr, GRB.GREATER_EQUAL, 1.0, "c1");

			// Optimize model

			model.optimize();

			System.out.println(x.get(GRB.StringAttr.VarName) + " " + x.get(GRB.DoubleAttr.X));
			assertEquals(x.get(GRB.DoubleAttr.X), 1.0, Double.MIN_VALUE);
			System.out.println(y.get(GRB.StringAttr.VarName) + " " + y.get(GRB.DoubleAttr.X));
			assertEquals(y.get(GRB.DoubleAttr.X), 0.0, Double.MIN_VALUE);
			System.out.println(z.get(GRB.StringAttr.VarName) + " " + z.get(GRB.DoubleAttr.X));
			assertEquals(z.get(GRB.DoubleAttr.X), 1.0, Double.MIN_VALUE);
			System.out.println("Obj: " + model.get(GRB.DoubleAttr.ObjVal));
			assertEquals(model.get(GRB.DoubleAttr.ObjVal), 3.0, Double.MIN_VALUE);
			// Dispose of model and environment

			model.dispose();
			env.dispose();

		} catch (Exception e) {
			System.out.println("Error: " + e);
		}
	}

}
