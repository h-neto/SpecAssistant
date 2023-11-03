package pt.haslab.specassistant.data.policy;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PolicyOption {
    PolicyRule rule;
    Double identity;
    Objective objective;

    public enum Objective {
        MAX, MIN
    }


    public static PolicyOption maximize(PolicyRule r) {
        return new PolicyOption(r, 0.0, Objective.MAX);
    }

    public static PolicyOption minimize(PolicyRule r) {
        return new PolicyOption(r, 0.0, Objective.MIN);
    }

    public static PolicyOption maxPercentage(PolicyRule r) {
        return new PolicyOption(r, 1.0, Objective.MAX);
    }

    public static final Map<String, PolicyOption> samples = Map.ofEntries(
            Map.entry("TEDCOMPXxArrival", minimize(Binary.oneMinusPrefTimesCost(Var.arrivals(), Binary.sum(Binary.scale(0.5, Var.ted()), Binary.scale(0.5, Var.complexity()))))),
            Map.entry("BalancedTEDCOMP", minimize(Binary.sumOld(Binary.sum(Binary.scale(0.5, Var.ted()), Binary.scale(0.5, Var.complexity()))))),
            Map.entry("Arrival", maxPercentage(Binary.mult(Var.arrivals(), Var.old()))),
            Map.entry("MINMAXCOMPX", minimize(Binary.max(Var.complexity(), Var.old()))),
            Map.entry("MAXFREQ", maximize(Binary.sum(Var.visits(), Var.old()))),
            Map.entry("ONE", minimize(Binary.sumOld(Constant.of(1.0)))),
            Map.entry("POPULARITY", maxPercentage(Binary.mult(Var.departures(), Var.old()))),
            Map.entry("MAXIMINFREQ", maximize(Binary.max(Var.visits(), Var.old()))),
            Map.entry("TED", minimize(Binary.sumOld(Var.ted()))),
            Map.entry("MINMAXTED", minimize(Binary.max(Var.ted(), Var.old()))),
            Map.entry("TEDxArrival", minimize(Binary.oneMinusPrefTimesCost(Var.arrivals(), Var.ted()))),
            Map.entry("COMP", minimize(Binary.sumOld(Var.complexity()))),
            Map.entry("COMPxArrival", minimize(Binary.oneMinusPrefTimesCost(Var.arrivals(), Var.complexity()))),
            Map.entry("MINMAXCOMP", minimize(Binary.max(Var.complexity(), Var.old())))
    );

}
