/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package compiler.c2.irTests;

import jdk.test.lib.Asserts;
import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8282365
 * @summary Test that Ideal transformations of UDivLNode and UModLNode are
 * being performed as expected.
 *
 * @library /test/lib /
 * @run driver compiler.c2.irTests.UDivLNodeIdealizationTests
 */
public class UDivLNodeIdealizationTests {
    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = {"constantDiv", "identity", "identityAgain", "identityThird",
                 "retainDenominator", "divByPow2", "largeDivisorCon", "largeDivisorVar",
                 "magicDiv19", "magicDiv7", "magicDiv28", "magicDiv13Bounded",
                 "constantMod", "constantModAgain", "modByPow2", "magicMod19"})
    public void runMethod() {
        long a = RunInfo.getRandom().nextLong();
             a = (a == 0) ? 1 : a;
        long b = RunInfo.getRandom().nextLong();
             b = (b == 0) ? 1 : b;

        long min = Long.MIN_VALUE;
        long max = Long.MAX_VALUE;

        assertResult(0, 0, true);
        assertResult(a, b, false);
        assertResult(min, min, false);
        assertResult(max, max, false);
    }

    @DontCompile
    public long udiv(long a, long b) {
        return Long.divideUnsigned(a, b);
    }

    @DontCompile
    public long umod(long a, long b) {
        return Long.remainderUnsigned(a, b);
    }

    @DontCompile
    public void assertResult(long a, long b, boolean shouldThrow) {
        try {
            Asserts.assertEQ(udiv(a, a), constantDiv(a));
            Asserts.assertFalse(shouldThrow, "Expected an exception to be thrown.");
        }
        catch (ArithmeticException e) {
            Asserts.assertTrue(shouldThrow, "Did not expected an exception to be thrown.");
        }

        try {
            Asserts.assertEQ(udiv(a * b, b), retainDenominator(a, b));
            Asserts.assertFalse(shouldThrow, "Expected an exception to be thrown.");
        }
        catch (ArithmeticException e) {
            Asserts.assertTrue(shouldThrow, "Did not expected an exception to be thrown.");
        }

        try {
            Asserts.assertEQ(a, identityThird(a, b));
            Asserts.assertFalse(shouldThrow, "Expected an exception to be thrown.");
        }
        catch (ArithmeticException e) {
            Asserts.assertTrue(shouldThrow, "Did not expected an exception to be thrown.");
        }

        try {
            Asserts.assertEQ(umod(a, a), constantMod(a));
            Asserts.assertFalse(shouldThrow, "Expected an exception to be thrown.");
        }
        catch (ArithmeticException e) {
            Asserts.assertTrue(shouldThrow, "Did not expected an exception to be thrown.");
        }

        Asserts.assertEQ(a, identity(a));
        Asserts.assertEQ(a, identityAgain(a));
        Asserts.assertEQ(udiv(a, 8), divByPow2(a));
        Asserts.assertEQ(udiv(a, -7), largeDivisorCon(a));
        Asserts.assertEQ(udiv(a, Math.min((int)b, -1)), largeDivisorVar(a, b));
        Asserts.assertEQ(udiv(a, 19), magicDiv19(a));
        Asserts.assertEQ(udiv(a, 7), magicDiv7(a));
        Asserts.assertEQ(udiv(a, 28), magicDiv28(a));
        Asserts.assertEQ(udiv(Integer.toUnsignedLong((int)a), 13), magicDiv13Bounded(a));
        Asserts.assertEQ(umod(a, 1), constantModAgain(a));
        Asserts.assertEQ(umod(a, 8), modByPow2(a));
        Asserts.assertEQ(umod(a, 19), magicMod19(a));
    }

    @Test
    @IR(failOn = {IRNode.UDIV_L})
    @IR(counts = {IRNode.DIV_BY_ZERO_TRAP, "1"})
    // Checks x / x => 1
    public long constantDiv(long x) {
        return Long.divideUnsigned(x, x);
    }

    @Test
    @IR(failOn = {IRNode.UDIV_L})
    // Checks x / 1 => x
    public long identity(long x) {
        return Long.divideUnsigned(x, 1);
    }

    @Test
    @IR(failOn = {IRNode.UDIV_L})
    // Checks x / (c / c) => x
    public long identityAgain(long x) {
        return Long.divideUnsigned(x, Long.divideUnsigned(13, 13));
    }

    @Test
    @IR(failOn = {IRNode.UDIV_L})
    @IR(counts = {IRNode.DIV_BY_ZERO_TRAP, "1"}, applyIfPlatform = {"x64", "true"})
    // Checks x / (y / y) => x
    public long identityThird(long x, long y) {
        return Long.divideUnsigned(x, Long.divideUnsigned(y, y));
    }

    @Test
    @IR(counts = {IRNode.MUL_L, "1",
                  IRNode.UDIV_L, "1",
                  IRNode.DIV_BY_ZERO_TRAP, "1"
                 }, applyIfPlatform = {"x64", "true"})
    // Hotspot should keep the division because it may cause a division by zero trap
    public long retainDenominator(long x, long y) {
        return Long.divideUnsigned(x * y, y);
    }

    @Test
    @IR(failOn = {IRNode.UDIV_L})
    @IR(counts = {IRNode.URSHIFT_L, "1"}, applyIfPlatform = {"x64", "true"})
    // Checks x / 2^c0 => x >>> c0
    public long divByPow2(long x) {
        return Long.divideUnsigned(x, 8);
    }

    @Test
    @IR(failOn = {IRNode.UDIV_L})
    @IR(counts = {IRNode.CMP_UL, "1",
                  IRNode.CMOVE_L, "1"
                 }, applyIfPlatform = {"x64", "true"})
    // Checks x / d => x u>= d ? 1 : 0 for large d
    public long largeDivisorCon(long x) {
        return Long.divideUnsigned(x, -7);
    }

    @Test
    @IR(failOn = {IRNode.UDIV_L})
    @IR(counts = {IRNode.CMP_UL, "1",
                  IRNode.CMOVE_L, "1"
                 }, applyIfPlatform = {"x64", "true"})
    // Checks x / d => x u>= d ? 1 : 0 for large d
    public long largeDivisorVar(long x, long y) {
        return Long.divideUnsigned(x, Math.min((int)y, -1));
    }

    @Test
    @IR(failOn = {IRNode.UDIV_L}, applyIfPlatform = {"x64", "true"})
    @IR(counts = {IRNode.URSHIFT_L, "1",
                  IRNode.UMUL_HI_L, "1"
                 }, applyIfPlatform = {"x64", "true"})
    // Checks magic long division occurs in general when dividing by a non power of 2.
    // The constant derived from 19 lies inside the limit of a u64
    public long magicDiv19(long x) {
        return Long.divideUnsigned(x, 19);
    }

    @Test
    @IR(failOn = {IRNode.UDIV_L}, applyIfPlatform = {"x64", "true"})
    @IR(counts = {IRNode.URSHIFT_L, "2",
                  IRNode.UMUL_HI_L, "1",
                  IRNode.ADD_L, "1",
                  IRNode.SUB_L, "1"
                 }, applyIfPlatform = {"x64", "true"})
    // Checks magic long division occurs in general when dividing by a non power of 2.
    // The constant derived from 7 lies outside the limit of a u64 but inside the limit
    // of a u65
    public long magicDiv7(long x) {
        return Long.divideUnsigned(x, 7);
    }

    @Test
    @IR(failOn = {IRNode.UDIV_L}, applyIfPlatform = {"x64", "true"})
    @IR(counts = {IRNode.URSHIFT_L, "2",
                  IRNode.UMUL_HI_L, "1"
                 }, applyIfPlatform = {"x64", "true"})
    // Checks magic long division occurs in general when dividing by a non power of 2.
    // The constant derived from 28 lies outside the limit of a u64 but we can transform
    // x / 28 into (x / 4) / 7
    public long magicDiv28(long x) {
        return Long.divideUnsigned(x, 28);
    }

    @Test
    @IR(failOn = {IRNode.UDIV_L}, applyIfPlatform = {"x64", "true"})
    @IR(counts = {IRNode.URSHIFT_L, "1",
                  IRNode.MUL_L, "1"
                 }, applyIfPlatform = {"x64", "true"})
    // Checks magic long division occurs in general when dividing by a non power of 2.
    // When the dividend is bounded, we can use smaller constant and do not need to use
    // u128 arithmetic
    public long magicDiv13Bounded(long x) {
        return Long.divideUnsigned(Integer.toUnsignedLong((int)x), 13);
    }

    @Test
    @IR(failOn = {IRNode.UMOD_L})
    @IR(counts = {IRNode.DIV_BY_ZERO_TRAP, "1"})
    // Checks x % x => 0
    public long constantMod(long x) {
        return Long.remainderUnsigned(x, x);
    }

    @Test
    @IR(failOn = {IRNode.UMOD_L})
    // Checks x % 1 => 0
    public long constantModAgain(long x) {
        return Long.remainderUnsigned(x, 1);
    }

    @Test
    @IR(failOn = {IRNode.UMOD_L})
    @IR(counts = {IRNode.AND_L, "1"}, applyIfPlatform = {"x64", "true"})
    // Checks x % 2^c0 => x & (2^c0 - 1)
    public long modByPow2(long x) {
        return Long.remainderUnsigned(x, 8);
    }

    @Test
    @IR(failOn = {IRNode.UMOD_L}, applyIfPlatform = {"x64", "true"})
    @IR(counts = {IRNode.URSHIFT_L, "1",
                  IRNode.UMUL_HI_L, "1",
                  IRNode.MUL_L, "1",
                  IRNode.SUB_L, "1"
                 }, applyIfPlatform = {"x64", "true"})
    // Checks magic long division occurs in general when dividing by a non power of 2.
    // The constant derived from 19 lies inside the limit of a u64
    public long magicMod19(long x) {
        return Long.remainderUnsigned(x, 19);
    }
}
