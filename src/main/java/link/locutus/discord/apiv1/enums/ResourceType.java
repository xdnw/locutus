package link.locutus.discord.apiv1.enums;

import com.politicsandwar.graphql.model.Bankrec;
import link.locutus.discord.Locutus;
import link.locutus.discord.config.Settings;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.util.IOUtil;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

public enum ResourceType {
    MONEY("money", "withmoney", 16),
    CREDITS("credits", "withcredits", -1),

    FOOD("food", "withfood", 11, 400, 2, 0, 20, 1.25d, () -> Projects.MASS_IRRIGATION, 0) {
            @Override
            public double getBaseProduction(Continent continent, double rads, Predicate<Project> hasProject, double land, long date) {
                int factor = 500;
                if (hasProject.test(getProject())) {
                    factor = 400;
                }
                if (hasProject.test(Projects.FALLOUT_SHELTER)) {
                    rads = Math.max(0.1, rads);
                } else {
                    rads = Math.max(0, rads);
                }

                double season = date <= 0 ? continent.getSeasonModifier() : continent.getSeasonModifier(date);

                return Math.max(0, (land / factor) * 12 * season * rads);
            }

        @Override
        public double getProduction(Continent continent, double rads, Predicate<Project> hasProject, double land, int improvements, long date) {
            return improvements * (1 + ((0.5 * (improvements - 1)) / (20 - 1))) * getBaseProduction(continent, rads, hasProject, land, date);
        }
    },
    COAL("coal", "withcoal", 10, 600, 12, 3, 10),
    OIL("oil", "withoil", 21, 600, 12, 3, 10),
    URANIUM("uranium", "withuranium", 24, 5000, 20, 3, 5, 2, () -> Projects.URANIUM_ENRICHMENT_PROGRAM, 0),
    LEAD("lead", "withlead", 15, 1500, 12, 3, 10),
    IRON("iron", "withiron", 14, 1600, 12, 3, 10),
    BAUXITE("bauxite", "withbauxite", 9, 1600, 12, 3, 10),
    GASOLINE("gasoline", "withgasoline", 13, 4000, 32, 6, 5, 2, () -> Projects.EMERGENCY_GASOLINE_RESERVE, 3, OIL),
    MUNITIONS("munitions", "withmunitions", 19, 3500 , 32, 18, 5, 1.34, () -> Projects.ARMS_STOCKPILE, 6, LEAD),
    STEEL("steel", "withsteel", 23, 4000, 40, 9, 5, 1.36, () -> Projects.IRON_WORKS, 3, IRON, COAL),
    ALUMINUM("aluminum", "withaluminum", 8, 2500, 40, 9, 5, 1.36, () -> Projects.BAUXITEWORKS, 3, BAUXITE);

    public static final ResourceType parseChar(Character s) {
        if (s == '$') return MONEY;
        // ignore MONEY and CREDITS
        for (ResourceType type : values) {
            if (type == MONEY || type == CREDITS) continue;
            if (Character.toLowerCase(type.getName().charAt(0)) == Character.toLowerCase(s)) {
                return type;
            }
        }
        return null;
    }
    public static final ResourceType parse(String input) {
        if (input.equalsIgnoreCase("ALUMINIUM")) {
            return ALUMINUM;
        }
        return valueOf(input.toUpperCase());
    }

    public static final ResourceType[] values = values();
    public static final List<ResourceType> valuesList = Arrays.asList(values);

    public static boolean isEmpty(double[] resources) {
        for (double i : resources) {
            if (Math.round(i * 100) != 0) return false;
        }
        return true;
    }

    public static double[] floor(double[] resources, double min) {
        for (int i = 0; i < resources.length; i++) {
            if (resources[i] < min) resources[i] = min;
        }
        return resources;
    }

    public static double[] ceil(double[] resources, double max) {
        for (int i = 0; i < resources.length; i++) {
            if (resources[i] > max) resources[i] = max;
        }
        return resources;
    }

    public static double[] set(double[] resources, double[] values) {
        for (int i = 0; i < values.length; i++) {
            resources[i] = values[i];
        }
        return resources;
    }

    public static double[] subtract(double[] resources, double[] values) {
        for (int i = 0; i < values.length; i++) {
            resources[i] -= values[i];
        }
        return resources;
    }

    public static double[] add(Collection<double[]> values) {
        double[] result = getBuffer();
        for (double[] value : values) {
            add(result, value);
        }
        return result;
    }
    public static double[] add(double[] resources, double[] values) {
        for (int i = 0; i < values.length; i++) {
            resources[i] += values[i];
        }
        return resources;
    }

    public static double[] negative(double[] resources) {
        for (int i = 0; i < resources.length; i++) {
            resources[i] = -resources[i];
        }
        return resources;
    }

    public static boolean equals(Map<ResourceType, Double> amtA, Map<ResourceType, Double> amtB) {
        for (ResourceType type : ResourceType.values) {
            if (Math.round(100 * (amtA.getOrDefault(type, 0d) - amtB.getOrDefault(type, 0d))) != 0) {
                System.out.println(type + " | " + amtA.getOrDefault(type, 0d) + " | " + amtB.getOrDefault(type, 0d) + " | " + Math.round(100 * (amtA.getOrDefault(type, 0d) - amtB.getOrDefault(type, 0d))));
                return false;
            }
        }
        return true;
    }

    public double[] toArray(double amt) {
        double[] result = getBuffer();
        result[ordinal()] = amt;
        return result;
    }

    public static double getEquilibrium(double[] total) {
        double consumeCost = 0;
        double taxable = 0;
        for (ResourceType type : values) {
            double amt = total[type.ordinal()];
            if (amt < 0) {
                consumeCost += Math.abs(PnwUtil.convertedTotal(type, -amt));
            } else if (amt > 0){
                taxable += Math.abs(PnwUtil.convertedTotal(type, amt));
            }
        }
        if (taxable > consumeCost) {
            double requiredTax = consumeCost / taxable;
            return requiredTax;
        }
        return -1;
    }

    public Building getBuilding() {
        return Buildings.RESOURCE_BUILDING.get(this);
    }

    public static double[] read(ByteBuffer buf, double[] output) {
        if (output == null) output = getBuffer();
        for (int i = 0; i < output.length; i++) {
            if (!buf.hasRemaining()) break;
            output[i] += buf.getDouble();
        }
        return output;
    }

    public static double[] getBuffer() {
        return new double[ResourceType.values.length];
    }

    public static ResourcesBuilder builder() {
        return new ResourcesBuilder();
    }

    public ResourcesBuilder builder(double amt) {
        return builder().add(this, amt);
    }

    public static class ResourcesBuilder {
        private double[] resources = null;

        private double[] getResources() {
            if (resources == null) resources = getBuffer();
            return resources;
        }

        public ResourcesBuilder add(ResourceType type, double amt) {
            if (amt != 0) {
                getResources()[type.ordinal()] += amt;
            }
            return this;
        }

        public ResourcesBuilder add(double[] amt) {
            for (ResourceType type : ResourceType.values) {
                add(type, amt[type.ordinal()]);
            }
            return this;
        }

        public ResourcesBuilder add(Map<ResourceType, Double> amt) {
            for (Map.Entry<ResourceType, Double> entry : amt.entrySet()) {
                add(entry.getKey(), entry.getValue());
            }
            return this;
        }

        public ResourcesBuilder addMoney(double amt) {
            return add(ResourceType.MONEY, amt);
        }

        public boolean isEmpty() {
            return resources == null || ResourceType.isEmpty(resources);
        }

        public double[] build() {
            return getResources();
        }


        public ResourcesBuilder subtract(double[] resources) {
            for (int i = 0; i < resources.length; i++) {
                add(ResourceType.values[i], -resources[i]);
            }
            return this;
        }
    }

    public static double[] fromApiV3(Bankrec rec, double[] buffer) {
        double[] resources = buffer == null ? getBuffer() : buffer;
        resources[ResourceType.MONEY.ordinal()] = rec.getMoney();
        resources[ResourceType.COAL.ordinal()] = rec.getCoal();
        resources[ResourceType.OIL.ordinal()] = rec.getOil();
        resources[ResourceType.URANIUM.ordinal()] = rec.getUranium();
        resources[ResourceType.IRON.ordinal()] = rec.getIron();
        resources[ResourceType.BAUXITE.ordinal()] = rec.getBauxite();
        resources[ResourceType.LEAD.ordinal()] = rec.getLead();
        resources[ResourceType.GASOLINE.ordinal()] = rec.getGasoline();
        resources[ResourceType.MUNITIONS.ordinal()] = rec.getMunitions();
        resources[ResourceType.STEEL.ordinal()] = rec.getSteel();
        resources[ResourceType.ALUMINUM.ordinal()] = rec.getAluminum();
        resources[ResourceType.FOOD.ordinal()] = rec.getFood();
        return resources;
    }

    private final double baseProduction;
    private final double baseProductionInverse;
    private final int cap;
    private final double capInverse;
    private final double boostFactor;
    private final Supplier<Project> project;
    private final ResourceType[] inputs;
    private final int baseInput;
    private final int pollution;
    private final int upkeep;

    private String name;
    private String bankString;
    private final int graphId;

    ResourceType(String name, String bankString, int graphId) {
        this(name, bankString, graphId, 0, 0, 0, 0);
    }

    ResourceType(String name, String bankString, int graphId, int upkeep, int pollution, double baseProduction, int cap) {
        this(name, bankString, graphId, upkeep, pollution, baseProduction, cap, 1, null, 0);
    }

    ResourceType(String name, String bankString, int graphId, int upkeep, int pollution, double baseProduction, int cap, double boostFactor, Supplier<Project> project, int baseInput, ResourceType... inputs) {
        this.name = name;
        this.bankString = bankString;
        this.baseProduction = baseProduction;
        this.baseProductionInverse = 1d / baseProduction;
        this.cap = cap;
        this.capInverse = 1d / (cap - 1d);
        this.boostFactor = boostFactor;
        this.baseInput = baseInput;
        this.pollution = pollution;
        this.upkeep = upkeep;
        this.project = project;
        this.inputs = inputs;
        this.graphId = graphId;
    }

    public int getGraphId() {
        return graphId;
    }

    public String url(Boolean isBuy, boolean shorten) {
        String url;
        if (shorten) {
            if (isBuy == Boolean.TRUE) {
                url = "https://tinyurl.com/qmm5ue7?resource1=%s";
            } else if (isBuy == Boolean.FALSE){
                url = "https://tinyurl.com/s2n7xp9?resource1=%s";
            } else {
                url = "https://tinyurl.com/26sxb2xb?resource1=%s";
            }
            url = String.format(url, name().toLowerCase());
        } else {
            url = "" + Settings.INSTANCE.PNW_URL() + "/index.php?id=90&display=world&resource1=%s&buysell=" + (isBuy ? "buy" : "sell") + "&ob=price&od=DEF";
            url = String.format(url, name().toLowerCase());
        }
        return url;
    }

    public boolean isRaw() {
        return inputs == null || inputs.length == 0 && cap > 0;
    }

    public boolean isManufactured() {
        return inputs != null && inputs.length > 0;
    }

    public int getPollution() {
        return pollution;
    }

    public int getUpkeep() {
        return upkeep;
    }

    public int getBaseInput() {
        return baseInput;
    }

    public double getBoostFactor() {
        return boostFactor;
    }

    public double getInput(Continent continent, double rads, Predicate<Project> hasProject, JavaCity city, int improvements) {
        if (inputs == null) return 0;

        double base = getBaseProduction(continent, rads, hasProject, city.getLand(), -1);
        base = (base * baseProductionInverse) * baseInput;

        return base * (1+0.5*((improvements - 1d) * capInverse)) * improvements;
    }

    public double getBaseProduction(Continent continent, double rads, Predicate<Project> hasProject, double land, long date) {
        double factor = 1;
        if (getProject() != null && hasProject.test(getProject())) {
            factor = boostFactor;
        }
        return baseProduction * factor;
    }

    public double getManufacturingMultiplier() {
        return baseProduction / baseInput;
    }

    public Project getProject() {
        return project == null ? null : project.get();
    }

    public int getCap() {
        return cap;
    }

    public double getProduction(Continent continent, double rads, Predicate<Project> hasProject, JavaCity city, int improvements, long date) {
        return getProduction(continent, rads, hasProject, city.getLand(), improvements, date);
    }

    public double getProduction(Continent continent, double rads, Predicate<Project> hasProject, double land, int improvements, long date) {
        double base = getBaseProduction(continent, rads, hasProject, land, date);
        return base * (1+0.5*((improvements - 1) * capInverse)) *improvements;
    }

    public double getMarketValue() {
        return Locutus.imp().getTradeManager().getLowAvg(this);
    }

    public ResourceType[] getInputs() {
        return inputs;
    }

    public String getName() {
        return name;
    }

    public String getBankString() {
        return bankString;
    }

    public static interface IResourceArray {
        public double[] get();

        public static IResourceArray create(double[] resources) {
            int numResources = 0;
            boolean noCredits = resources[CREDITS.ordinal()] == 0;
            ResourceType latestType = null;
            for (ResourceType type : ResourceType.values) {
                double amt = resources[type.ordinal()];
                if (amt > 0) {
                    numResources++;
                    latestType = type;
                }
            }
            switch (numResources) {
                case 0:
                    return new EmptyResources();
                case 1:
                    return new ResourceAmtCents(latestType, resources[latestType.ordinal()]);
                case 2, 3, 4, 5, 6, 7, 8, 9, 10:
                    return new VarArray(resources);
                default:
                    if (noCredits) {
                        return new ArrayNoCredits(resources);
                    } else {
                        return new VarArray(resources);
                    }
            }
        }
    }

    public static class EmptyResources implements IResourceArray{

        @Override
        public double[] get() {
            return ResourceType.getBuffer();
        }
    }

    public static class ArrayNoCredits implements IResourceArray {
        private final byte[] data;

        public ArrayNoCredits(double[] loot) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            for (ResourceType type : ResourceType.values) {
                if (type == ResourceType.CREDITS) continue;
                try {
                    IOUtil.writeVarLong(baos, (long) (loot[type.ordinal()] * 100));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            this.data = baos.toByteArray();
        }
        public double[] get() {
            double[] data = ResourceType.getBuffer();
            ByteArrayInputStream in = new ByteArrayInputStream(this.data);
            for (ResourceType type : ResourceType.values) {
                if (type == ResourceType.CREDITS) continue;
                try {
                    data[type.ordinal()] = IOUtil.readVarLong(in) / 100d;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            return data;
        }
    }

    public static class VarArray implements IResourceArray {
        private final byte[] data;

        public VarArray(double[] loot) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            for (ResourceType type : ResourceType.values) {
                long amtCents = (long) (loot[type.ordinal()] * 100);
                if (amtCents == 0) continue;
                try {
                    long pair = type.ordinal() + (amtCents << 4);
                    IOUtil.writeVarLong(baos, pair);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            this.data = baos.toByteArray();
        }

        public double[] get() {
            double[] data = ResourceType.getBuffer();
            ByteArrayInputStream in = new ByteArrayInputStream(this.data);
            while (in.available() > 0) {
                try {
                    long pair = IOUtil.readVarLong(in);
                    int type = (int) (pair & 0xF);
                    long amtCents = pair >> 4;
                    data[type] = amtCents / 100d;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            return data;
        }
    }

    public static class ResourceAmtCents implements IResourceArray {
        private final long data;

        public ResourceAmtCents(ResourceType type, double amount) {
            this.data = type.ordinal() + (Math.round(amount * 100d) << 4);
        }

        public ResourceType getType() {
            return ResourceType.values[(int) (data & 0xF)];
        }
        public long getAmountCents() {
            return data >> 4;
        }

        @Override
        public double[] get() {
            return getType().toArray(getAmountCents() / 100d);
        }
    }
}
