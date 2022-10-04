package graphql.schema.diffing;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import com.google.common.util.concurrent.AtomicDouble;
import com.google.common.util.concurrent.AtomicDoubleArray;
import graphql.schema.GraphQLSchema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import static graphql.Assert.assertTrue;

public class SchemaDiffing {


    SchemaGraph sourceGraph;
    SchemaGraph targetGraph;

    public List<EditOperation> diffGraphQLSchema(GraphQLSchema graphQLSchema1, GraphQLSchema graphQLSchema2) throws Exception {
        sourceGraph = new SchemaGraphFactory("source-").createGraph(graphQLSchema1);
        targetGraph = new SchemaGraphFactory("target-").createGraph(graphQLSchema2);
        return diffImpl(sourceGraph, targetGraph);

    }

    List<EditOperation> diffImpl(SchemaGraph sourceGraph, SchemaGraph targetGraph) throws Exception {
        int sizeDiff = targetGraph.size() - sourceGraph.size();
        System.out.println("graph diff: " + sizeDiff);
        FillupIsolatedVertices fillupIsolatedVertices = new FillupIsolatedVertices(sourceGraph, targetGraph);
        fillupIsolatedVertices.ensureGraphAreSameSize();
        FillupIsolatedVertices.IsolatedVertices isolatedVertices = fillupIsolatedVertices.isolatedVertices;

        assertTrue(sourceGraph.size() == targetGraph.size());
//        if (sizeDiff != 0) {
//            SortSourceGraph.sortSourceGraph(sourceGraph, targetGraph, isolatedVertices);
//        }
        Mapping fixedMappings = isolatedVertices.mapping;
        System.out.println("fixed mappings: " + fixedMappings.size() + " vs " + sourceGraph.size());
        if (fixedMappings.size() == sourceGraph.size()) {
            ArrayList<EditOperation> result = new ArrayList<>();
            DiffImpl.editorialCostForMapping(fixedMappings, sourceGraph, targetGraph, result);
            return result;
        }
        DiffImpl diffImpl = new DiffImpl(sourceGraph, targetGraph, isolatedVertices);
        List<Vertex> nonMappedSource = new ArrayList<>(sourceGraph.getVertices());
        nonMappedSource.removeAll(fixedMappings.getSources());

        List<Vertex> nonMappedTarget = new ArrayList<>(targetGraph.getVertices());
        nonMappedTarget.removeAll(fixedMappings.getTargets());

        // the non mapped vertices go to the end
        List<Vertex> sourceVertices = new ArrayList<>();
        sourceVertices.addAll(fixedMappings.getSources());
        sourceVertices.addAll(nonMappedSource);

        List<Vertex> targetGraphVertices = new ArrayList<>();
        targetGraphVertices.addAll(fixedMappings.getTargets());
        targetGraphVertices.addAll(nonMappedTarget);


        List<EditOperation> editOperations = diffImpl.diffImpl(fixedMappings, sourceVertices, targetGraphVertices);


//        Mapping overallMapping = new Mapping();
//        ArrayList<EditOperation> overallEdits = new ArrayList<>();
//
//        for (List<String> contextId : isolatedVertices.contexts.rowKeySet()) {
//            Set<Vertex> sourceList = isolatedVertices.contexts.row(contextId).keySet().iterator().next();
//            Set<Vertex> targetList = isolatedVertices.contexts.get(contextId, sourceList);
//            assertTrue(sourceList.size() == targetList.size());
//            System.out.println();
//            if (sourceList.size() == 1) {
//                Vertex sourceVertex = sourceList.iterator().next();
//                Vertex targetVertex = targetList.iterator().next();
//                overallMapping.add(sourceVertex, targetVertex);
//                continue;
//            }
//            System.out.println("contextId: " + contextId + " with vertices: " + sourceList.size());
//            List<EditOperation> editOperations = diffImpl.diffImpl(new ArrayList<>(sourceList), new ArrayList<>(targetList));
//
//            for (EditOperation editOperation : editOperations) {
//                if (editOperation.getOperation() == EditOperation.Operation.CHANGE_VERTEX ||
//                        editOperation.getOperation() == EditOperation.Operation.INSERT_VERTEX ||
//                        editOperation.getOperation() == EditOperation.Operation.DELETE_VERTEX) {
//                    overallMapping.add(editOperation.getSourceVertex(), editOperation.getTargetVertex());
//                    overallEdits.add(editOperation);
//                }
//            }
//        }

//        List<EditOperation> edgeOperations = calcEdgeOperations(overallMapping);
//        overallEdits.addAll(edgeOperations);
        return editOperations;
    }

    private List<EditOperation> calcEdgeOperations(Mapping mapping) {
        List<Edge> edges = sourceGraph.getEdges();
        List<EditOperation> result = new ArrayList<>();
        // edge deletion or relabeling
        for (Edge sourceEdge : edges) {
            Vertex target1 = mapping.getTarget(sourceEdge.getOne());
            Vertex target2 = mapping.getTarget(sourceEdge.getTwo());
            Edge targetEdge = targetGraph.getEdge(target1, target2);
            if (targetEdge == null) {
                result.add(EditOperation.deleteEdge("Delete edge " + sourceEdge, sourceEdge));
            } else if (!sourceEdge.getLabel().equals(targetEdge.getLabel())) {
                result.add(EditOperation.changeEdge("Change " + sourceEdge + " to " + targetEdge, sourceEdge, targetEdge));
            }
        }

        //TODO: iterates over all edges in the target Graph
        for (Edge targetEdge : targetGraph.getEdges()) {
            // only subgraph edges
            Vertex sourceFrom = mapping.getSource(targetEdge.getOne());
            Vertex sourceTo = mapping.getSource(targetEdge.getTwo());
            if (sourceGraph.getEdge(sourceFrom, sourceTo) == null) {
                result.add(EditOperation.insertEdge("Insert edge " + targetEdge, targetEdge));
            }
        }
        return result;
    }


//        List<String> debugMap = getDebugMap(bestFullMapping.get());
//        for (String debugLine : debugMap) {
//            System.out.println(debugLine);
//        }
//        System.out.println("edit : " + bestEdit);
//        for (EditOperation editOperation : bestEdit.get()) {
//            System.out.println(editOperation);
//        }
//    private List<EditOperation> diffImplImpl() {

//    private void logUnmappable(AtomicDoubleArray[] costMatrix, int[] assignments, List<Vertex> sourceList, ArrayList<Vertex> availableTargetVertices, int level) {
//        for (int i = 0; i < assignments.length; i++) {
//            double value = costMatrix[i].get(assignments[i]);
//            if (value >= Integer.MAX_VALUE) {
//                System.out.println("i " + i + " can't mapped");
//                Vertex v = sourceList.get(i + level - 1);
//                Vertex u = availableTargetVertices.get(assignments[i]);
//                System.out.println("from " + v + " to " + u);
//            }
//        }
//    }
//
//    private List<String> getDebugMap(Mapping mapping) {
//        List<String> result = new ArrayList<>();
////        if (mapping.size() > 0) {
////            result.add(mapping.getSource(mapping.size() - 1).getType() + " -> " + mapping.getTarget(mapping.size() - 1).getType());
////        }
//        for (Map.Entry<Vertex, Vertex> entry : mapping.getMap().entrySet()) {
////            if (!entry.getKey().getType().equals(entry.getValue().getType())) {
////                result.add(entry.getKey().getType() + "->" + entry.getValue().getType());
////            }
//            result.add(entry.getKey().getDebugName() + "->" + entry.getValue().getDebugName());
//        }
//        return result;
//    }
//
//    // minimum number of edit operations for a full mapping
//

}
