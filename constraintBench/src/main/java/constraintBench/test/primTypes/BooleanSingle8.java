package constraintBench.test.primTypes;

import constraintBench.utils.Configuration;

/**
 * 
 * @author Linghui Luo
 */
public class BooleanSingle8 {

  public void test() {
    boolean a = Configuration.featureA();
    if (a) {
      return;// A
    }
    System.out.println();// !A
  }

}
