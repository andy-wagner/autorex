/**
 * autorex - fsm state eliminator
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Julian Thome <julian.thome.de@gmail.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 **/

package org.snt.autorex.trackauto;


import dk.brics.automaton.Automaton;
import dk.brics.automaton.RegExp;
import org.jgrapht.experimental.dag.DirectedAcyclicGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;


/**
 * An automaton that keeps track of all operation which are perfomred on it
 */
public class TrackAutomaton extends DirectedAcyclicGraph<TrackAutomatonNode,
        TrackAutomatonEdge> {

    final static Logger LOGGER = LoggerFactory.getLogger(TrackAutomaton.class);

    private TrackAutomatonNode root = null;
    private int id = 0;

    public TrackAutomaton(String name, String rexp) {
        super(TrackAutomatonEdge.class);
        TrackAutomatonNode mn  = getNewNodeOfKind(TrackAutomatonNode.Kind.LEAF,
                new RegExp(rexp).toAutomaton(), name);
        root = mn;
        addVertex(root);
    }


    public TrackAutomaton(String name) {
        this(name, ".*");
    }


    public TrackAutomaton(TrackAutomatonNode.Kind kind, Automaton a){
        super(TrackAutomatonEdge.class);
        root = getNewNodeOfKind(kind, a, "");
        addVertex(root);
    }


    public TrackAutomaton(TrackAutomaton m) {
        super(TrackAutomatonEdge.class);
        Map<TrackAutomatonNode,TrackAutomatonNode> smap = new HashMap<>();

        for(TrackAutomatonNode s : m.vertexSet()) {
            TrackAutomatonNode mn = getNewNodeOfKind(s.getKind(), s
                    .getAutomaton(), s.getName());
            smap.put(s, mn);
        }

        for(TrackAutomatonEdge s : m.edgeSet()) {
            try {
                addDagEdge(smap.get(s.getSource()),smap.get(s.getTarget()));
            } catch (CycleFoundException e) {
                assert false;
            }
        }

        root = smap.get(m.root);
        id = m.id;
    }

    private TrackAutomatonNode getNewNodeOfKind(TrackAutomatonNode.Kind kind,
                                                Automaton a, String name) {
        return new TrackAutomatonNode(kind, a, id++, name);
    }


    public void setName(String name){
        root.setName(name);
    }

    public TrackAutomatonNode getRoot() {
        return root;
    }

    public void setRoot(TrackAutomatonNode root) {
        this.root = root;
    }


    public TrackAutomaton union(TrackAutomaton a) {
        return performBinOp(TrackAutomatonNode.Kind.UNION, this, a);
    }

    public TrackAutomaton intersect(TrackAutomaton other) {
       return performBinOp(TrackAutomatonNode.Kind.INTERSECTION, this, other);
    }

    public TrackAutomaton complement() {
        return performUnaryOp(TrackAutomatonNode.Kind.COMPLEMENT, this);
    }

    public TrackAutomaton minus(TrackAutomaton other) {
        return performBinOp(TrackAutomatonNode.Kind.MINUS, this, other);
    }

    public TrackAutomaton concatenate(TrackAutomaton other) {
        return performBinOp(TrackAutomatonNode.Kind.CONCAT, this, other);
    }


    private static TrackAutomaton performBinOp(TrackAutomatonNode.Kind kind,
                                         TrackAutomaton a1, TrackAutomaton a2) {

        Automaton a1root = a1.getRoot().getAutomaton();
        Automaton a2root = a2.getRoot().getAutomaton();

        Automaton result = null;

        switch(kind) {
            case UNION:
                result = a1root.union(a2root);
                break;
            case INTERSECTION:
                result = a1root.union(a2root);
                break;
            case MINUS:
                result = a1root.minus(a2root);
                break;
            case CONCAT:
                result = a1root.concatenate(a2root);
                break;
        }

        TrackAutomaton ta = new TrackAutomaton(kind, result);
        ta.addSubGraph(a1);
        ta.addSubGraph(a2);

        return ta;
    }

    private static TrackAutomaton performUnaryOp(TrackAutomatonNode.Kind kind,
                                               TrackAutomaton a1) {

        Automaton a1root = a1.getRoot().getAutomaton();

        Automaton result = null;

        switch(kind) {
            case COMPLEMENT:
                result = a1root.complement();
                break;
        }

        TrackAutomaton ta = new TrackAutomaton(kind, result);
        ta.addSubGraph(a1);

        return ta;
    }



    private void addSubGraph(TrackAutomaton other) {

        Map<TrackAutomatonNode,TrackAutomatonNode> smap = new HashMap<>();

        for(TrackAutomatonNode s : other.getOrderedVertices()) {
            TrackAutomatonNode mn = getNewNodeOfKind(s.getKind(),s
                    .getAutomaton().clone(), s.getName());
            smap.put(s, mn);
        }

        for(TrackAutomatonNode n : smap.values()) {
            LOGGER.debug("add v {}", n.getId());
            addVertex(n);
        }

        for(TrackAutomatonEdge s : other.edgeSet()) {
            try {
                addDagEdge(smap.get(s.getSource()),smap.get(s.getTarget()));
            } catch (CycleFoundException e) {
                assert false;
            }
        }

        try {
            addDagEdge(root, smap.get(other.root));
        } catch (CycleFoundException e) {
            assert false;
        }

    }


    private Set<TrackAutomatonNode> getOrderedVertices() {
        Set<TrackAutomatonNode> tset = new TreeSet<>();
        tset.addAll(vertexSet());
        return tset;
    }



    public String toDot() {

        StringBuilder sb = new StringBuilder();
        sb.append("digraph {\n" +
                "\trankdir=TB;\n");

        sb.append("\tnode [fontname=Helvetica,fontsize=11];\n");
        sb.append("\tedge [fontname=Helvetica,fontsize=10];\n");


        for (TrackAutomatonNode n : getOrderedVertices()) {
            String shape = "";
            String color = "";

            String name = n.getName();

            sb.append("\tn" + n.getId() + " [label=\"" + n.getKind().toString
                    () +"\\n[" + n.getId() + "]" + name + "\"," +
                    "shape=\"" +
                    shape +
                    "\", color=\"" +
                    color + "\"];\n");
        }


        for (TrackAutomatonEdge e : this.edgeSet())  {

            TrackAutomatonNode src = e.getSource();
            TrackAutomatonNode dst = e.getTarget();


            sb.append("\tn" + src.getId() + " -> n" + dst.getId() +"\n");
        }
        sb.append("}\n");

        return sb.toString();
    }



}
