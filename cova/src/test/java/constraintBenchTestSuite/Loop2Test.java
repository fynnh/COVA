/**
 * Copyright (C) 2019 Linghui Luo 
 * 
 * This library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as 
 * published by the Free Software Foundation, either version 2.1 of the 
 * License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 * 
 */
package constraintBenchTestSuite;

import com.microsoft.z3.BoolExpr;

import org.junit.Assert;
import org.junit.Test;

import cova.core.SMTSolverZ3;
import cova.data.ConstraintZ3;
import utils.ConstraintBenchTestFramework;

/**
 * 
 */
public class Loop2Test extends ConstraintBenchTestFramework {

  public Loop2Test() {
    targetTestClassName = "constraintBench.test.loops.Loop2";
  }

  @Test
  public void test() {
    BoolExpr termA = SMTSolverZ3.getInstance().makeBoolTerm(A, false);
    BoolExpr negatedA = SMTSolverZ3.getInstance().negate(termA, false);
    BoolExpr actual = ((ConstraintZ3) results.get(14)).getExpr();
    boolean equivalent = SMTSolverZ3.getInstance().prove(termA, actual);
    Assert.assertTrue(equivalent);

    actual = ((ConstraintZ3) results.get(16)).getExpr();
    equivalent = SMTSolverZ3.getInstance().prove(negatedA, actual);
    Assert.assertTrue(equivalent);
  }
}
