/*
* autorex - fsm state eliminator
*
* Copyright 2016, Julian Thomé <julian.thome@uni.lu>
*
* Licensed under the EUPL, Version 1.1 or – as soon they will be approved by
* the European Commission - subsequent versions of the EUPL (the "Licence");
* You may not use this work except in compliance with the Licence. You may
* obtain a copy of the Licence at:
*
* https://joinup.ec.europa.eu/sites/default/files/eupl1.1.-licence-en_0.pdf
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the Licence is distributed on an "AS IS" basis, WITHOUT
* WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the Licence for the specific language governing permissions and
* limitations under the Licence.
*/

package org.snt.autorex;

import dk.brics.automaton.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class AutomatonTrans {

    final static Logger LOGGER = LoggerFactory.getLogger(AutomatonTrans.class);

    public static enum Kind {

        SUFFIX(1,"suffix"),
        NORMAL(3,"normal"),
        CAMEL(5, "camel"),
        SUBSTRING(7, "substring");

        private final String sval;
        private final int ival;

        Kind(int ival, String sval) {
            this.sval = sval;
            this.ival = ival;
        }

        public int getId() {
            return this.ival;
        }

        public String toString() {
            return this.sval;
        }

        public static Kind KindFromString(String kind) {
            switch(kind) {
                case "suffix": return SUFFIX;
                case "normal": return NORMAL;
                case "camel" : return CAMEL;
                case "substring": return SUBSTRING;

            }
            // should never ever happen
            assert(false);
            return null;
        }

        public boolean isSuffix() {
            return this == SUFFIX;
        }
        public boolean isNormal() {
            return this == NORMAL;
        }
        public boolean isCamel() {
            return this == CAMEL;
        }
        public boolean isSubstring() {return this == SUBSTRING;}


    }

    private Kind kind = Kind.NORMAL;

    protected Automaton auto = null;
    protected Set<State>  states = new HashSet<>();
    protected Map<State, Integer> statenumber = new HashMap<>();
    protected State init = null;

    HashMap<State, HashSet<FullTransition>> incoming = new HashMap<>();
    HashMap<State, HashSet<FullTransition>> outgoing = new HashMap<>();
    HashSet<FullTransition> transitions = new HashSet<>();

    private int stateId;

    private static Automaton any = new RegExp(".*").toAutomaton();

    public AutomatonTrans() {
        super();
        stateId = 0;
    }

    public AutomatonTrans(Automaton a) {
        this();
        this.auto = a.clone();
        this.init = this.auto.getInitialState();
        prepare();
        finalize();
    }

    public AutomatonTrans(String rexp) {
        this(new RegExp(rexp).toAutomaton());
    }

    private void set() {
        for (State s : auto.getStates()) {
            s.setAccept(true);
        }
    }

    private void setAccepting() {
        for (State s : auto.getStates()) {
            s.setAccept(true);
        }
    }


    private void prepare() {

        // init state has no incomings

        // get all transitions
        for (State s : auto.getStates()) {
            for (Transition t : s.getTransitions()) {
                FullTransition ft = new FullTransition(s, t, t.getDest());
                addTransition(ft);
            }
        }
    }

    public void addTransitions(Collection<FullTransition> ft) {

        for(FullTransition t : ft) {
            addTransition(t);
        }
        Set<State> visited = new HashSet<>();
        dfsNumering(init, visited);
    }

    public void addTransition(FullTransition ft) {
        states.add(ft.getSourceState());
        states.add(ft.getTargetState());
        addToIncoming(ft);
        addToOutgoing(ft);
        transitions.add(ft);
    }

    public void delTransitions(Collection<FullTransition> ts) {
        ts.forEach(t -> delTransition(t));
    }

    public void delTransition(FullTransition t) {
        State target = t.getTargetState();
        State source = t.getSourceState();
        transitions.remove(t);
        outgoing.get(source).remove(t);
        incoming.get(target).remove(t);

        if(outgoing.get(source).size() == 0){
            outgoing.remove(source);
        }
        if(incoming.get(target).size() == 0){
            incoming.remove(target);
        }

    }

    public void delState(State s) {
        incoming.remove(s);
        outgoing.remove(s);
        statenumber.remove(s);
        states.remove(s);
    }


    private void addToIncoming(FullTransition ft) {
        if (!incoming.containsKey(ft.getTargetState())) {
            incoming.put(ft.getTargetState(), new HashSet<>());
        }
        incoming.get(ft.getTargetState()).add(ft);
    }

    private void addToOutgoing(FullTransition ft) {
        if (!outgoing.containsKey(ft.getSourceState())) {
            outgoing.put(ft.getSourceState(), new HashSet<>());
        }
        outgoing.get(ft.getSourceState()).add(ft);
    }

    private void setEpsilon() {

        boolean binit = init.isAccept();

        Set<StatePair> spairs = new HashSet<StatePair>();
        for (State s : auto.getStates()) {
            if (!s.equals(auto.getInitialState())) {
                spairs.add(new StatePair(auto.getInitialState(), s));
            }
        }
        auto.addEpsilons(spairs);
        init.setAccept(binit);
    }

    protected void convertToCamelCaseAutomaton() {

        Set<Transition> handled = new HashSet<Transition>();

        for (State s : auto.getStates()) {

            Set<Transition> transitions = new HashSet<Transition>();

            transitions.addAll(s.getTransitions());

            for (Transition t : transitions) {

                if (handled.contains(t))
                    continue;

                char min = t.getMin();
                char max = t.getMax();

                if (CharUtils.isLowerCase(min)) {
                    min = Character.toUpperCase(min);
                } else if (CharUtils.isUpperCase(min)) {
                    min = Character.toLowerCase(min);
                }

                if (CharUtils.isLowerCase(max)) {
                    max = Character.toUpperCase(min);
                } else if (CharUtils.isUpperCase(max)) {
                    max = Character.toLowerCase(min);
                }

                Transition tnew = new Transition(min, max, t.getDest());
                s.addTransition(tnew);
                handled.add(tnew);
                handled.add(t);
            }
        }

        auto.removeDeadTransitions();
        auto.determinize();
        auto.reduce();
        this.kind = Kind.CAMEL;
        this.prepare();

    }

    protected void convertToSubstringAutomaton() {
        setAccepting();
        setEpsilon();
        this.kind = Kind.SUBSTRING;
        this.prepare();
        this.finalize();
    }

    protected void convertToSuffixAutomaton() {
        setEpsilon();
        this.kind = Kind.SUFFIX;
        this.prepare();
        this.finalize();
    }

    public void finalize() {
        stateId = 0;
        statenumber.clear();
        Set<State> visited = new HashSet<State>();
        dfsNumering(init, visited);
    }

    private void dfsNumering(State s, Set<State> visited) {

        if(!visited.contains(s))
            visited.add(s);
        else
            return;

        this.stateId++;
        this.statenumber.put(s, this.stateId);

        if(!outgoing.containsKey(s))
            return;

        for (FullTransition t : outgoing.get(s)) {
            dfsNumering(t.getTargetState(), visited);
        }
    }

    static void appendCharString(char var0, StringBuilder var1) {
        if (var0 >= 33 && var0 <= 126 && var0 != 92 && var0 != 34) {
            var1.append(var0);
        } else {
            var1.append("\\u");
            String var2 = Integer.toHexString(var0);
            if (var0 < 16) {
                var1.append("000").append(var2);
            } else if (var0 < 256) {
                var1.append("00").append(var2);
            } else if (var0 < 4096) {
                var1.append("0").append(var2);
            } else {
                var1.append(var2);
            }
        }

    }

    void appendDot(StringBuilder sbuilder, FullTransition ft) {
        sbuilder.append(" -> ").append(
                "n" + statenumber.get(ft.getTargetState())).append(" [label=\"");

        //Transition t = ft.getLastTran();
        //if(t != null) {
            /**appendCharString(t.getMin(), sbuilder);
            if (t.getMin() != t.getMax()) {
                sbuilder.append("-");
                appendCharString(t.getMax(), sbuilder);
            }**/
            sbuilder.append(ft.getCarry());
        //}
        sbuilder.append("\"");

        if(ft.isEpsilon()){
            sbuilder.append(",color=red");
        }
        sbuilder.append("];\n");
    }

    @Override
    public AutomatonTrans clone() {

        AutomatonTrans a = new AutomatonTrans();

        HashMap<State, State> m = new HashMap<State, State>();
        Set<State> states = auto.getStates();

        for (State s : states)
            m.put(s, new State());

        for (State s : states) {
            State p = m.get(s);

            assert (p != null);
            p.setAccept(s.isAccept());

            if (s.equals(auto.getInitialState())) {
                auto.setInitialState(p);
                assert auto.getInitialState() != null;
                //LOGGER.info("INITIAL STATE");
            }

            for (Transition t : s.getTransitions()) {
                p.getTransitions().add(new Transition(t.getMin(), t.getMax(), m.get(t.getDest())));
            }

            if (this.statenumber.containsKey(s)) {
                a.statenumber.put(p, this.statenumber.get(s));
            }
        }

        return a;
    }

    public String toDot() {

        StringBuilder sbuilder = new StringBuilder("digraph Automaton {\n");
        sbuilder.append("  rankdir = LR;\n");

        for(State state : states){
            sbuilder.append("  ").append("n" + this.statenumber.get(state));
            if (state.isAccept()) {
                sbuilder.append(" [shape=doublecircle,label=\"" + this.statenumber.get(state) + "\"];\n");
            } else {
                sbuilder.append(" [shape=circle,label=\"" + this.statenumber.get(state) + "\"];\n");
            }

        }

        for(FullTransition ft : transitions){
            sbuilder.append("  n" + this.statenumber.get(ft.getSourceState
                    ()));
            appendDot(sbuilder, ft);
        }

        return sbuilder.append("}\n").toString();
    }


    public int getNumberOfState(State s) {
        if (this.statenumber.containsKey(s))
            return this.statenumber.get(s);
        else
            return -1;
    }
}
