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
 *
 */
public class BooleanSingle6Test extends ConstraintBenchTestFramework {

  public BooleanSingle6Test() {
    targetTestClassName = "constraintBench.test.primTypes.BooleanSingle6";
  }

  @Test
  public void test() {
    BoolExpr expected = SMTSolverZ3.getInstance().makeBoolTerm(A, false);// A
    BoolExpr actual = ((ConstraintZ3) results.get(13)).getExpr();
    boolean equivalent = SMTSolverZ3.getInstance().prove(expected, actual);
    Assert.assertTrue(equivalent);
  }
}
