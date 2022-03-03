// Copyright 2022 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.firestore;

/**
 * The mode in which to execute an aggregate query.
 *
 * <p>The choice of mode has a dramatic impact on the cost, computation, and latency of aggregate
 * queries. It also has impacts on the <em>correctness</em> of aggregations in the presence of local
 * writes that have not yet been sync'd with the server.
 */
public enum AggregateExecutionMode {

  /**
   * Run the full query and perform the aggregations locally.
   *
   * <p>The advantage of this mode is that the aggregate results are always correct and consistent
   * with any other query results, even in the presence of local writes that have not yet been
   * sync'd to the server and even when offline.
   *
   * <p>The drawback of this mode is that query execution is quite costly, as it simply executes the
   * underlying query and performs the aggregations locally. This involves downloading all documents
   * that match the underlying query, and therefore is slow and incurs billed document reads just as
   * if the underlying query were executed directly.
   */
  LOCAL,

  /**
   * Run the query on the server and perform the aggregations on the server.
   *
   * <p>The advantage of this mode is that it is fast and incurs an order of magnitude fewer billed
   * document reads. It is fast because only the <em>aggregate results</em> are downloaded from the
   * server and not the documents' full contents.
   *
   * <p>The drawback of this mode is that the aggregate results can be temporarily inaccurate when
   * the client is offline and/or there are local writes that have no yet been sync'd to the server.
   * For example, counts could be off by 1 for each mutated document and mutated documents could be
   * members of the wrong group.
   */
  REMOTE,
}
