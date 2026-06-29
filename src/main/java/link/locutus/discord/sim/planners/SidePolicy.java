package link.locutus.discord.sim.planners;

import link.locutus.discord.sim.Actor;
import link.locutus.discord.sim.StrategicObjective;

import java.util.List;

public record SidePolicy(
        String name,
        StrategicObjective objective,
        SidePlannerSettings planner,
        SideOpeningSettings opening,
        SideProjectionPolicies projection,
    Actor turnActor,
    boolean allowInitialDeclarations
) {
    public static final Actor NO_OP_ACTOR = (world, self, ctx) -> List.of();

    public SidePolicy {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (objective == null) {
            throw new IllegalArgumentException("objective must not be null");
        }
        if (planner == null) {
            throw new IllegalArgumentException("planner must not be null");
        }
        if (opening == null) {
            throw new IllegalArgumentException("opening must not be null");
        }
        if (projection == null) {
            throw new IllegalArgumentException("projection must not be null");
        }
        if (turnActor == null) {
            throw new IllegalArgumentException("turnActor must not be null");
        }
    }

    public static SidePolicy heuristicActing(String name, StrategicObjective objective) {
        if (objective == null) {
            throw new IllegalArgumentException("objective must not be null");
        }
        return new SidePolicy(
                name,
                objective,
                SidePlannerSettings.actingDefaults(),
                SideOpeningSettings.defaults(objective),
                SideProjectionPolicies.heuristic(),
                NO_OP_ACTOR,
                true
        );
    }

    public static SidePolicy heuristicPassive(String name, StrategicObjective objective) {
        if (objective == null) {
            throw new IllegalArgumentException("objective must not be null");
        }
        return new SidePolicy(
                name,
                objective,
                SidePlannerSettings.defaults(),
                SideOpeningSettings.defaults(objective),
                SideProjectionPolicies.heuristic(),
                NO_OP_ACTOR,
                false
        );
    }

    public static SidePolicy noDeclarations(String name, StrategicObjective objective) {
        if (objective == null) {
            throw new IllegalArgumentException("objective must not be null");
        }
        return new SidePolicy(
                name,
                objective,
                SidePlannerSettings.defaults(),
                SideOpeningSettings.defaults(objective),
                SideProjectionPolicies.noDeclarations(),
                NO_OP_ACTOR,
                false
        );
    }

    public static SidePolicy objectiveDrivenProjection(String name, StrategicObjective objective) {
        if (objective == null) {
            throw new IllegalArgumentException("objective must not be null");
        }
        SideOpeningSettings opening = SideOpeningSettings.defaults(objective);
        return new SidePolicy(
                name,
                objective,
            SidePlannerSettings.actingDefaults(),
                opening,
                SideProjectionPolicies.objectiveDriven(objective, opening),
                NO_OP_ACTOR,
                true
        );
    }

    public static SidePolicy objectiveDrivenProjectionPassive(String name, StrategicObjective objective) {
        if (objective == null) {
            throw new IllegalArgumentException("objective must not be null");
        }
        SideOpeningSettings opening = SideOpeningSettings.defaults(objective);
        return new SidePolicy(
                name,
                objective,
            SidePlannerSettings.defaults(),
                opening,
                SideProjectionPolicies.objectiveDriven(objective, opening),
                NO_OP_ACTOR,
                false
        );
    }

}
