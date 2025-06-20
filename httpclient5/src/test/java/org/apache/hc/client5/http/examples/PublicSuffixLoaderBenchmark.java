/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.hc.client5.http.examples;

import org.apache.hc.client5.http.psl.PublicSuffixMatcherLoader;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class PublicSuffixLoaderBenchmark {
    public static void main(final String[] args) {
        benchmarkGarbageCollection();
        loadPublicSuffixList();
        benchmarkGarbageCollection();
    }

    private static void loadPublicSuffixList() {
        final long start = System.nanoTime();
        System.out.print("Loading public suffix list...");
        PublicSuffixMatcherLoader.getDefault();
        final long end = System.nanoTime();
        System.out.printf(" done (took %,d ms)%n", NANOSECONDS.toMillis(end - start));
    }

    private static void benchmarkGarbageCollection() {
        for (int i = 0; i < 3; i++) {
            final long start = System.nanoTime();
            System.gc();
            final long end = System.nanoTime();
            System.out.printf("GC took %,d ms%n", NANOSECONDS.toMillis(end - start));
        }
    }
}
