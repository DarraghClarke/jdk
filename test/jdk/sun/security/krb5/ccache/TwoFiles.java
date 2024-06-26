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

/*
 * @test
 * @bug 8307143
 * @summary CredentialsCache.cacheName should not be static
 * @modules java.security.jgss/sun.security.krb5
 *          java.security.jgss/sun.security.krb5.internal.ccache
 * @library /test/lib
 */

import jdk.test.lib.Asserts;
import sun.security.krb5.PrincipalName;
import sun.security.krb5.internal.ccache.CredentialsCache;

public class TwoFiles {
    public static void main(String[] args) throws Exception {
        PrincipalName pn = new PrincipalName("me@HERE");
        CredentialsCache cc1 = CredentialsCache.create(pn, "cc1");
        CredentialsCache cc2 = CredentialsCache.create(pn, "cc2");
        // name is canonicalized
        Asserts.assertTrue(cc1.cacheName().endsWith("cc1"), cc1.cacheName());
        Asserts.assertTrue(cc2.cacheName().endsWith("cc2"), cc2.cacheName());
    }
}
