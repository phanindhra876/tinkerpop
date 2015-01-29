package com.tinkerpop.gremlin.process.graph.step.sideEffect;

import com.tinkerpop.gremlin.process.Traversal;
import com.tinkerpop.gremlin.process.computer.MapReduce;
import com.tinkerpop.gremlin.process.graph.marker.FunctionHolder;
import com.tinkerpop.gremlin.process.graph.marker.MapReducer;
import com.tinkerpop.gremlin.process.graph.marker.Reversible;
import com.tinkerpop.gremlin.process.graph.marker.SideEffectCapable;
import com.tinkerpop.gremlin.process.graph.marker.SideEffectRegistrar;
import com.tinkerpop.gremlin.process.graph.marker.TraversalHolder;
import com.tinkerpop.gremlin.process.graph.step.sideEffect.mapreduce.GroupCountMapReduce;
import com.tinkerpop.gremlin.process.traverser.TraverserRequirement;
import com.tinkerpop.gremlin.process.util.MapHelper;
import com.tinkerpop.gremlin.process.util.SmartLambda;
import com.tinkerpop.gremlin.process.util.TraversalHelper;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class GroupCountStep<S> extends SideEffectStep<S> implements SideEffectRegistrar, SideEffectCapable, Reversible, TraversalHolder, FunctionHolder<S, Object>, MapReducer<Object, Long, Object, Long, Map<Object, Long>> {

    private SmartLambda<S, Object> smartLambda = new SmartLambda<>();
    private String sideEffectKey;
    // TODO: onFirst like subgraph so we don't keep getting the map

    public GroupCountStep(final Traversal traversal, final String sideEffectKey) {
        super(traversal);
        this.sideEffectKey = sideEffectKey;
        GroupCountStep.generateConsumer(this);
    }

    @Override
    public void registerSideEffects() {
        if (this.sideEffectKey == null) this.sideEffectKey = this.getId();
        this.traversal.asAdmin().getSideEffects().registerSupplierIfAbsent(this.sideEffectKey, HashMap<Object, Long>::new);
    }

    @Override
    public String getSideEffectKey() {
        return this.sideEffectKey;
    }

    @Override
    public MapReduce<Object, Long, Object, Long, Map<Object, Long>> getMapReduce() {
        return new GroupCountMapReduce(this);
    }

    @Override
    public String toString() {
        return TraversalHelper.makeStepString(this, this.sideEffectKey, this.smartLambda);
    }

    @Override
    public void addFunction(final Function<S, Object> function) {
        this.smartLambda.setLambda(function);
        this.executeTraversalOperations(this.smartLambda.getTraversal(), TYPICAL_LOCAL_OPERATIONS);
    }

    @Override
    public List<Function<S, Object>> getFunctions() {
        return Collections.singletonList(this.smartLambda);
    }

    @Override
    public List<Traversal<S, Object>> getLocalTraversals() {
        return this.smartLambda.getTraversalAsList();
    }

    @Override
    public Set<TraverserRequirement> getRequirements() {
        final Set<TraverserRequirement> requirements = TraversalHolder.super.getRequirements();
        requirements.add(TraverserRequirement.BULK);
        requirements.add(TraverserRequirement.SIDE_EFFECTS);
        return requirements;
    }

    @Override
    public GroupCountStep<S> clone() throws CloneNotSupportedException {
        final GroupCountStep<S> clone = (GroupCountStep<S>) super.clone();
        clone.smartLambda = this.smartLambda.clone();
        clone.executeTraversalOperations(clone.smartLambda.getTraversal(), TYPICAL_LOCAL_OPERATIONS);
        GroupCountStep.generateConsumer(clone);
        return clone;
    }

    /////////////////////////

    private static final <S> void generateConsumer(final GroupCountStep<S> groupCountStep) {
        groupCountStep.setConsumer(traverser -> {
            final Map<Object, Long> groupCountMap = traverser.sideEffects(groupCountStep.sideEffectKey);
            MapHelper.incr(groupCountMap, groupCountStep.smartLambda.apply((S) traverser), traverser.bulk());
        });
    }
}
