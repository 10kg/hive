/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hive.ql.io.sarg;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import com.google.common.collect.Sets;

import org.apache.hadoop.hive.common.type.HiveChar;
import org.apache.hadoop.hive.common.type.HiveVarchar;
import org.apache.hadoop.hive.ql.io.orc.TestInputOutputFormat;
import org.apache.hadoop.hive.ql.io.sarg.SearchArgument.TruthValue;
import org.apache.hadoop.hive.ql.io.sarg.SearchArgumentImpl.PredicateLeafImpl;
import org.apache.hadoop.hive.serde2.io.HiveDecimalWritable;
import org.junit.Test;

import java.lang.reflect.Field;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;
import java.util.Set;

/**
 * These test the SARG implementation.
 * The xml files were generated by setting hive.optimize.index.filter
 * to true and using a custom record reader that prints out the value of
 * hive.io.filter.expr.serialized in createRecordReader. This should be
 * replaced by generating the AST using the API and passing that in.
 * <p/>
 * In each case, the corresponding part of the where clause is in the
 * comment above the blob.
 */
@SuppressWarnings("deprecation")
public class TestSearchArgumentImpl {

  private ExpressionTree not(ExpressionTree arg) {
    return new ExpressionTree(ExpressionTree.Operator.NOT, arg);
  }

  private ExpressionTree and(ExpressionTree... arg) {
    return new ExpressionTree(ExpressionTree.Operator.AND, arg);
  }

  private ExpressionTree or(ExpressionTree... arg) {
    return new ExpressionTree(ExpressionTree.Operator.OR, arg);
  }

  private ExpressionTree leaf(int leaf) {
    return new ExpressionTree(leaf);
  }

  private ExpressionTree constant(TruthValue val) {
    return new ExpressionTree(val);
  }

  /**
   * Create a predicate leaf. This is used by another test.
   */
  public static PredicateLeaf createPredicateLeaf(PredicateLeaf.Operator operator,
                                                  PredicateLeaf.Type type,
                                                  String columnName,
                                                  Object literal,
                                                  List<Object> literalList) {
    return new SearchArgumentImpl.PredicateLeafImpl(operator, type, columnName,
        literal, literalList, null);
  }

  @Test
  public void testNotPushdown() throws Exception {
    assertEquals("leaf-1", SearchArgumentImpl.BuilderImpl.pushDownNot(leaf(1))
        .toString());
    assertEquals("(not leaf-1)",
        SearchArgumentImpl.BuilderImpl.pushDownNot(not(leaf(1))).toString());
    assertEquals("leaf-1",
        SearchArgumentImpl.BuilderImpl.pushDownNot(not(not(leaf(1))))
            .toString());
    assertEquals("(not leaf-1)",
        SearchArgumentImpl.BuilderImpl.pushDownNot(not(not(not(leaf(1))))).
            toString());
    assertEquals("(or leaf-1 (not leaf-2))",
        SearchArgumentImpl.BuilderImpl.pushDownNot(not(and(not(leaf(1)),
            leaf(2)))).toString());
    assertEquals("(and (not leaf-1) leaf-2)",
        SearchArgumentImpl.BuilderImpl.pushDownNot(not(or(leaf(1),
            not(leaf(2))))).toString());
    assertEquals("(or (or (not leaf-1) leaf-2) leaf-3)",
        SearchArgumentImpl.BuilderImpl.pushDownNot(or(not(and(leaf(1),
                not(leaf(2)))),
            not(not(leaf(3))))).toString());
    assertEquals("NO", SearchArgumentImpl.BuilderImpl.pushDownNot(
        not(constant(TruthValue.YES))).toString());
    assertEquals("YES", SearchArgumentImpl.BuilderImpl.pushDownNot(
        not(constant(TruthValue.NO))).toString());
    assertEquals("NULL", SearchArgumentImpl.BuilderImpl.pushDownNot(
        not(constant(TruthValue.NULL))).toString());
    assertEquals("YES_NO", SearchArgumentImpl.BuilderImpl.pushDownNot(
        not(constant(TruthValue.YES_NO))).toString());
    assertEquals("YES_NULL", SearchArgumentImpl.BuilderImpl.pushDownNot(
        not(constant(TruthValue.NO_NULL))).toString());
    assertEquals("NO_NULL", SearchArgumentImpl.BuilderImpl.pushDownNot(
        not(constant(TruthValue.YES_NULL))).toString());
    assertEquals("YES_NO_NULL", SearchArgumentImpl.BuilderImpl.pushDownNot(
        not(constant(TruthValue.YES_NO_NULL))).toString());
  }

  @Test
  public void testFlatten() throws Exception {
    assertEquals("leaf-1", SearchArgumentImpl.BuilderImpl.flatten(leaf(1)).toString());
    assertEquals("NO",
        SearchArgumentImpl.BuilderImpl.flatten(constant(TruthValue.NO)).toString());
    assertEquals("(not (not leaf-1))",
        SearchArgumentImpl.BuilderImpl.flatten(not(not(leaf(1)))).toString());
    assertEquals("(and leaf-1 leaf-2)",
        SearchArgumentImpl.BuilderImpl.flatten(and(leaf(1), leaf(2))).toString());
    assertEquals("(and (or leaf-1 leaf-2) leaf-3)",
        SearchArgumentImpl.BuilderImpl.flatten(and(or(leaf(1), leaf(2)), leaf(3))
        ).toString());
    assertEquals("(and leaf-1 leaf-2 leaf-3 leaf-4)",
        SearchArgumentImpl.BuilderImpl.flatten(and(and(leaf(1), leaf(2)),
            and(leaf(3), leaf(4)))).toString());
    assertEquals("(or leaf-1 leaf-2 leaf-3 leaf-4)",
        SearchArgumentImpl.BuilderImpl.flatten(or(leaf(1), or(leaf(2), or(leaf(3),
            leaf(4))))).toString());
    assertEquals("(or leaf-1 leaf-2 leaf-3 leaf-4)",
        SearchArgumentImpl.BuilderImpl.flatten(or(or(or(leaf(1), leaf(2)), leaf(3)),
            leaf(4))).toString());
    assertEquals("(or leaf-1 leaf-2 leaf-3 leaf-4 leaf-5 leaf-6)",
        SearchArgumentImpl.BuilderImpl.flatten(or(or(leaf(1), or(leaf(2), leaf(3))),
            or(or(leaf(4), leaf(5)), leaf(6)))).toString());
    assertEquals("(and (not leaf-1) leaf-2 (not leaf-3) leaf-4 (not leaf-5) leaf-6)",
        SearchArgumentImpl.BuilderImpl.flatten(and(and(not(leaf(1)), and(leaf(2),
                not(leaf(3)))), and(and(leaf(4), not(leaf(5))), leaf(6)))
        ).toString());
    assertEquals("(not (and leaf-1 leaf-2 leaf-3))",
        SearchArgumentImpl.BuilderImpl.flatten(not(and(leaf(1), and(leaf(2), leaf(3))))
        ).toString());
  }

  @Test
  public void testFoldMaybe() throws Exception {
    assertEquals("(and leaf-1)",
        SearchArgumentImpl.BuilderImpl.foldMaybe(and(leaf(1),
            constant(TruthValue.YES_NO_NULL))).toString());
    assertEquals("(and leaf-1 leaf-2)",
        SearchArgumentImpl.BuilderImpl.foldMaybe(and(leaf(1),
            constant(TruthValue.YES_NO_NULL), leaf(2))).toString());
    assertEquals("(and leaf-1 leaf-2)",
        SearchArgumentImpl.BuilderImpl.
            foldMaybe(and(constant(TruthValue.YES_NO_NULL),
                leaf(1), leaf(2), constant(TruthValue.YES_NO_NULL))).toString());
    assertEquals("YES_NO_NULL",
        SearchArgumentImpl.BuilderImpl.
            foldMaybe(and(constant(TruthValue.YES_NO_NULL),
                constant(TruthValue.YES_NO_NULL))).toString());
    assertEquals("YES_NO_NULL",
        SearchArgumentImpl.BuilderImpl.
            foldMaybe(or(leaf(1),
                constant(TruthValue.YES_NO_NULL))).toString());
    assertEquals("(or leaf-1 (and leaf-2))",
        SearchArgumentImpl.BuilderImpl.foldMaybe(or(leaf(1),
            and(leaf(2), constant(TruthValue.YES_NO_NULL)))).toString());
    assertEquals("(and leaf-1)",
        SearchArgumentImpl.BuilderImpl.foldMaybe(and(or(leaf(2),
            constant(TruthValue.YES_NO_NULL)), leaf(1))).toString());
    assertEquals("(and leaf-100)", SearchArgumentImpl.BuilderImpl.foldMaybe(
        SearchArgumentImpl.BuilderImpl.convertToCNF(and(leaf(100),
            or(and(leaf(0), leaf(1)),
                and(leaf(2), leaf(3)),
                and(leaf(4), leaf(5)),
                and(leaf(6), leaf(7)),
                and(leaf(8), leaf(9)),
                and(leaf(10), leaf(11)),
                and(leaf(12), leaf(13)),
                and(leaf(14), leaf(15)),
                and(leaf(16), leaf(17)))))).toString());
  }

  @Test
  public void testCNF() throws Exception {
    assertEquals("leaf-1", SearchArgumentImpl.BuilderImpl.convertToCNF(leaf(1)).
        toString());
    assertEquals("NO", SearchArgumentImpl.BuilderImpl.convertToCNF(
        constant(TruthValue.NO)).toString());
    assertEquals("(not leaf-1)", SearchArgumentImpl.BuilderImpl.convertToCNF(
        not(leaf(1))).toString());
    assertEquals("(and leaf-1 leaf-2)", SearchArgumentImpl.BuilderImpl.
        convertToCNF(
            and(leaf(1), leaf(2))).toString());
    assertEquals("(or (not leaf-1) leaf-2)", SearchArgumentImpl.BuilderImpl.
        convertToCNF(
            or(not(leaf(1)), leaf(2))).toString());
    assertEquals("(and (or leaf-1 leaf-2) (not leaf-3))",
        SearchArgumentImpl.BuilderImpl.convertToCNF(
            and(or(leaf(1), leaf(2)), not(leaf(3)))).toString());
    assertEquals("(and (or leaf-1 leaf-3) (or leaf-2 leaf-3)" +
        " (or leaf-1 leaf-4) (or leaf-2 leaf-4))",
        SearchArgumentImpl.BuilderImpl.convertToCNF(
            or(and(leaf(1), leaf(2)), and(leaf(3), leaf(4)))).toString());
    assertEquals("(and" +
        " (or leaf-1 leaf-5) (or leaf-2 leaf-5)" +
        " (or leaf-3 leaf-5) (or leaf-4 leaf-5)" +
        " (or leaf-1 leaf-6) (or leaf-2 leaf-6)" +
        " (or leaf-3 leaf-6) (or leaf-4 leaf-6))",
        SearchArgumentImpl.BuilderImpl.convertToCNF(
            or(and(leaf(1), leaf(2), leaf(3), leaf(4)),
                and(leaf(5), leaf(6)))).toString());
    assertEquals("(and" +
        " (or leaf-5 leaf-6 (not leaf-7) leaf-1 leaf-3)" +
        " (or leaf-5 leaf-6 (not leaf-7) leaf-2 leaf-3)" +
        " (or leaf-5 leaf-6 (not leaf-7) leaf-1 leaf-4)" +
        " (or leaf-5 leaf-6 (not leaf-7) leaf-2 leaf-4))",
        SearchArgumentImpl.BuilderImpl.convertToCNF(
            or(and(leaf(1), leaf(2)),
                and(leaf(3), leaf(4)),
                or(leaf(5), leaf(6)),
                not(leaf(7)))).toString());
    assertEquals("(and" +
        " (or leaf-8 leaf-0 leaf-3 leaf-6)" +
        " (or leaf-8 leaf-1 leaf-3 leaf-6)" +
        " (or leaf-8 leaf-2 leaf-3 leaf-6)" +
        " (or leaf-8 leaf-0 leaf-4 leaf-6)" +
        " (or leaf-8 leaf-1 leaf-4 leaf-6)" +
        " (or leaf-8 leaf-2 leaf-4 leaf-6)" +
        " (or leaf-8 leaf-0 leaf-5 leaf-6)" +
        " (or leaf-8 leaf-1 leaf-5 leaf-6)" +
        " (or leaf-8 leaf-2 leaf-5 leaf-6)" +
        " (or leaf-8 leaf-0 leaf-3 leaf-7)" +
        " (or leaf-8 leaf-1 leaf-3 leaf-7)" +
        " (or leaf-8 leaf-2 leaf-3 leaf-7)" +
        " (or leaf-8 leaf-0 leaf-4 leaf-7)" +
        " (or leaf-8 leaf-1 leaf-4 leaf-7)" +
        " (or leaf-8 leaf-2 leaf-4 leaf-7)" +
        " (or leaf-8 leaf-0 leaf-5 leaf-7)" +
        " (or leaf-8 leaf-1 leaf-5 leaf-7)" +
        " (or leaf-8 leaf-2 leaf-5 leaf-7))",
        SearchArgumentImpl.BuilderImpl.convertToCNF(or(and(leaf(0), leaf(1),
                leaf(2)),
            and(leaf(3), leaf(4), leaf(5)),
            and(leaf(6), leaf(7)),
            leaf(8))).toString());
    assertEquals("YES_NO_NULL", SearchArgumentImpl.BuilderImpl.
        convertToCNF(or(and(leaf(0), leaf(1)),
            and(leaf(2), leaf(3)),
            and(leaf(4), leaf(5)),
            and(leaf(6), leaf(7)),
            and(leaf(8), leaf(9)),
            and(leaf(10), leaf(11)),
            and(leaf(12), leaf(13)),
            and(leaf(14), leaf(15)),
            and(leaf(16), leaf(17)))).toString());
    assertEquals("(and leaf-100 YES_NO_NULL)", SearchArgumentImpl.BuilderImpl.
        convertToCNF(and(leaf(100),
            or(and(leaf(0), leaf(1)),
                and(leaf(2), leaf(3)),
                and(leaf(4), leaf(5)),
                and(leaf(6), leaf(7)),
                and(leaf(8), leaf(9)),
                and(leaf(10), leaf(11)),
                and(leaf(12), leaf(13)),
                and(leaf(14), leaf(15)),
                and(leaf(16), leaf(17))))).toString());
    assertNoSharedNodes(SearchArgumentImpl.BuilderImpl.
        convertToCNF(or(and(leaf(0), leaf(1), leaf(2)),
            and(leaf(3), leaf(4), leaf(5)),
            and(leaf(6), leaf(7)),
            leaf(8))), Sets.<ExpressionTree>newIdentityHashSet());
  }

  private static void assertNoSharedNodes(ExpressionTree tree,
                                          Set<ExpressionTree> seen
  ) throws Exception {
    if (seen.contains(tree) &&
        tree.getOperator() != ExpressionTree.Operator.LEAF) {
      assertTrue("repeated node in expression " + tree, false);
    }
    seen.add(tree);
    if (tree.getChildren() != null) {
      for (ExpressionTree child : tree.getChildren()) {
        assertNoSharedNodes(child, seen);
      }
    }
  }

  @Test
  public void testBuilder() throws Exception {
    SearchArgument sarg =
        SearchArgumentFactory.newBuilder()
            .startAnd()
            .lessThan("x", PredicateLeaf.Type.LONG, 10L)
            .lessThanEquals("y", PredicateLeaf.Type.STRING, "hi")
            .equals("z", PredicateLeaf.Type.FLOAT, 1.0)
            .end()
            .build();
    assertEquals("leaf-0 = (LESS_THAN x 10), " +
        "leaf-1 = (LESS_THAN_EQUALS y hi), " +
        "leaf-2 = (EQUALS z 1.0), " +
        "expr = (and leaf-0 leaf-1 leaf-2)", sarg.toString());
    sarg = SearchArgumentFactory.newBuilder()
        .startNot()
        .startOr()
        .isNull("x", PredicateLeaf.Type.LONG)
        .between("y", PredicateLeaf.Type.LONG, 10L, 20L)
        .in("z", PredicateLeaf.Type.LONG, 1L, 2L, 3L)
        .nullSafeEquals("a", PredicateLeaf.Type.STRING, "stinger")
        .end()
        .end()
        .build();
    assertEquals("leaf-0 = (IS_NULL x), " +
        "leaf-1 = (BETWEEN y 10 20), " +
        "leaf-2 = (IN z 1 2 3), " +
        "leaf-3 = (NULL_SAFE_EQUALS a stinger), " +
        "expr = (and (not leaf-0) (not leaf-1) (not leaf-2) (not leaf-3))", sarg.toString());
  }

  @Test
  public void testBuilderComplexTypes() throws Exception {
    SearchArgument sarg =
        SearchArgumentFactory.newBuilder()
            .startAnd()
            .lessThan("x", PredicateLeaf.Type.DATE,
                Date.valueOf("1970-1-11"))
            .lessThanEquals("y", PredicateLeaf.Type.STRING,
                new HiveChar("hi", 10).toString())
            .equals("z", PredicateLeaf.Type.DECIMAL, new HiveDecimalWritable("1.0"))
            .end()
            .build();
    assertEquals("leaf-0 = (LESS_THAN x 1970-01-11), " +
        "leaf-1 = (LESS_THAN_EQUALS y hi        ), " +
        "leaf-2 = (EQUALS z 1), " +
        "expr = (and leaf-0 leaf-1 leaf-2)", sarg.toString());

    sarg = SearchArgumentFactory.newBuilder()
        .startNot()
        .startOr()
        .isNull("x", PredicateLeaf.Type.LONG)
        .between("y", PredicateLeaf.Type.DECIMAL,
            new HiveDecimalWritable("10"), new HiveDecimalWritable("20.0"))
        .in("z", PredicateLeaf.Type.LONG, 1L, 2L, 3L)
        .nullSafeEquals("a", PredicateLeaf.Type.STRING,
            new HiveVarchar("stinger", 100).toString())
        .end()
        .end()
        .build();
    assertEquals("leaf-0 = (IS_NULL x), " +
        "leaf-1 = (BETWEEN y 10 20), " +
        "leaf-2 = (IN z 1 2 3), " +
        "leaf-3 = (NULL_SAFE_EQUALS a stinger), " +
        "expr = (and (not leaf-0) (not leaf-1) (not leaf-2) (not leaf-3))",
        sarg.toString());
  }

  @Test
  public void testBuilderComplexTypes2() throws Exception {
    SearchArgument sarg =
        SearchArgumentFactory.newBuilder()
            .startAnd()
            .lessThan("x", PredicateLeaf.Type.DATE, Date.valueOf("2005-3-12"))
            .lessThanEquals("y", PredicateLeaf.Type.STRING,
                new HiveChar("hi", 10).toString())
            .equals("z", PredicateLeaf.Type.DECIMAL,
                new HiveDecimalWritable("1.0"))
            .end()
            .build();
    assertEquals("leaf-0 = (LESS_THAN x 2005-03-12), " +
        "leaf-1 = (LESS_THAN_EQUALS y hi        ), " +
        "leaf-2 = (EQUALS z 1), " +
        "expr = (and leaf-0 leaf-1 leaf-2)", sarg.toString());

    sarg = SearchArgumentFactory.newBuilder()
        .startNot()
        .startOr()
        .isNull("x", PredicateLeaf.Type.LONG)
        .between("y", PredicateLeaf.Type.DECIMAL, new HiveDecimalWritable("10"),
            new HiveDecimalWritable("20.0"))
        .in("z", PredicateLeaf.Type.LONG, 1L, 2L, 3L)
        .nullSafeEquals("a", PredicateLeaf.Type.STRING,
            new HiveVarchar("stinger", 100).toString())
        .end()
        .end()
        .build();
    assertEquals("leaf-0 = (IS_NULL x), " +
        "leaf-1 = (BETWEEN y 10 20), " +
        "leaf-2 = (IN z 1 2 3), " +
        "leaf-3 = (NULL_SAFE_EQUALS a stinger), " +
        "expr = (and (not leaf-0) (not leaf-1) (not leaf-2) (not leaf-3))",
        sarg.toString());
  }

  @Test
  public void testBuilderFloat() throws Exception {
    SearchArgument sarg =
        SearchArgumentFactory.newBuilder()
            .startAnd()
            .lessThan("x", PredicateLeaf.Type.LONG, 22L)
            .lessThan("x1", PredicateLeaf.Type.LONG, 22L)
            .lessThanEquals("y", PredicateLeaf.Type.STRING,
                new HiveChar("hi", 10).toString())
            .equals("z", PredicateLeaf.Type.FLOAT, Double.valueOf(0.22))
            .equals("z1", PredicateLeaf.Type.FLOAT, Double.valueOf(0.22))
            .end()
            .build();
    assertEquals("leaf-0 = (LESS_THAN x 22), " +
        "leaf-1 = (LESS_THAN x1 22), " +
        "leaf-2 = (LESS_THAN_EQUALS y hi        ), " +
        "leaf-3 = (EQUALS z 0.22), " +
        "leaf-4 = (EQUALS z1 0.22), " +
        "expr = (and leaf-0 leaf-1 leaf-2 leaf-3 leaf-4)", sarg.toString());
  }

  @Test
  public void testTimestampSerialization() throws Exception {
    // There is a kryo which after serialize/deserialize,
    // Timestamp becomes Date. We get around this issue in
    // SearchArgumentImpl.getLiteral. Once kryo fixed the issue
    // We can simplify SearchArgumentImpl.getLiteral
    Timestamp now = new Timestamp(new java.util.Date().getTime());
    SearchArgument sarg =
      SearchArgumentFactory.newBuilder()
        .startAnd()
        .lessThan("x", PredicateLeaf.Type.TIMESTAMP, now)
        .end()
        .build();

    String serializedSarg = TestInputOutputFormat.toKryo(sarg);
    SearchArgument sarg2 = ConvertAstToSearchArg.create(serializedSarg);

    Field literalField = PredicateLeafImpl.class.getDeclaredField("literal");
    literalField.setAccessible(true);
    assertTrue(literalField.get(sarg2.getLeaves().get(0)) instanceof java.util.Date);
    Timestamp ts = (Timestamp)sarg2.getLeaves().get(0).getLiteral();
    assertEquals(ts, now);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testBadLiteral() throws Exception {
    SearchArgumentFactory.newBuilder()
        .startAnd()
        .lessThan("x", PredicateLeaf.Type.LONG, "hi")
        .end()
        .build();
  }

  @Test(expected = IllegalArgumentException.class)
  public void testBadLiteralList() throws Exception {
    SearchArgumentFactory.newBuilder()
            .startAnd()
            .in("x", PredicateLeaf.Type.STRING, "hi", 23)
            .end()
            .build();
  }
}
