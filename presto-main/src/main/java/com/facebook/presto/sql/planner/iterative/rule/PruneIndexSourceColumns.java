/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.sql.planner.iterative.rule;

import com.facebook.presto.spi.ColumnHandle;
import com.facebook.presto.spi.predicate.TupleDomain;
import com.facebook.presto.sql.planner.PlanNodeIdAllocator;
import com.facebook.presto.sql.planner.Symbol;
import com.facebook.presto.sql.planner.plan.IndexSourceNode;
import com.facebook.presto.sql.planner.plan.PlanNode;
import com.google.common.collect.Maps;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.facebook.presto.sql.planner.plan.Patterns.indexSource;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

public class PruneIndexSourceColumns
        extends ProjectOffPushDownRule<IndexSourceNode>
{
    public PruneIndexSourceColumns()
    {
        super(indexSource());
    }

    @Override
    protected Optional<PlanNode> pushDownProjectOff(PlanNodeIdAllocator idAllocator, IndexSourceNode indexSourceNode, Set<Symbol> referencedOutputs)
    {
        Set<Symbol> prunedLookupSymbols = indexSourceNode.getLookupSymbols().stream()
                .filter(referencedOutputs::contains)
                .collect(toImmutableSet());

        Map<Symbol, ColumnHandle> prunedAssignments = Maps.filterEntries(
                indexSourceNode.getAssignments(),
                entry -> referencedOutputs.contains(entry.getKey()) ||
                        tupleDomainReferencesColumnHandle(indexSourceNode.getEffectiveTupleDomain(), entry.getValue()));

        List<Symbol> prunedOutputList =
                indexSourceNode.getOutputSymbols().stream()
                        .filter(referencedOutputs::contains)
                        .collect(toImmutableList());

        return Optional.of(
                new IndexSourceNode(
                        indexSourceNode.getId(),
                        indexSourceNode.getIndexHandle(),
                        indexSourceNode.getTableHandle(),
                        indexSourceNode.getLayout(),
                        prunedLookupSymbols,
                        prunedOutputList,
                        prunedAssignments,
                        indexSourceNode.getEffectiveTupleDomain()));
    }

    private static boolean tupleDomainReferencesColumnHandle(
            TupleDomain<ColumnHandle> tupleDomain,
            ColumnHandle columnHandle)
    {
        return tupleDomain.getDomains()
                .map(domains -> domains.containsKey(columnHandle))
                .orElse(false);
    }
}
