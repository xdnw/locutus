package link.locutus.discord.gpt;

public class test {
    public class Intent {

        private final String query;

        public Intent(String query) {
            this.query = query;
        }

        public String getQuery() {
            return query;
        }
    }

    public Intent USE_COMMAND = new Intent("Do this task for me");

    public Intent INFO_TASK_COMMAND = new Intent("How do I perform this task using this command?");

    public Intent INFO_COMMAND = new Intent("How do I use this command?");

    public Intent FIND_COMMAND = new Intent("What is the command to do this task?");

    public Intent SEEK_INFORMATION = new Intent("Can you provide me historical information about this topic?");
/*
        test

        System.out.println("Use command: " + handler.getSimilarity(USE_COMMAND.query, input));
        System.out.println("Info task command: " + handler.getSimilarity(INFO_TASK_COMMAND.query, input));
        System.out.println("Info command: " + handler.getSimilarity(INFO_COMMAND.query, input));
        System.out.println("Find command: " + handler.getSimilarity(FIND_COMMAND.query, input));
        System.out.println("seek information: " + handler.getSimilarity(SEEK_INFORMATION.query, input));


             */


    //            String name = callable.getFullPath();
//            String desc = callable.simpleDesc();
//            RolePermission rolePerm = callable.getMethod().getAnnotation(RolePermission.class);
//            if (rolePerm != null && rolePerm.root()) continue;
//
//            String full = "Command " + name + ": " + desc.replaceAll("\n", " ");
////            if (full.toLowerCase().contains("locutus")) {
////                System.out.println(callable.getMethod().getDeclaringClass().getSimpleName() +" " + callable.getMethod().getName() + " has locutus");
////            }
//            double[] cmdEmbed = handler.getEmbedding(EmbeddingType.COMMAND.ordinal(), name, full, false);
//
//            double diff = ArrayUtil.cosineSimilarity(embedding, cmdEmbed);
//            closest.add(Map.entry(callable, diff));
//        }
//
//        closest.sort((o1, o2) -> Double.compare(o2.getValue(), o1.getValue()));
//
//        System.out.println("Closest");
//        // print top 15
//        for (int i = 0; i < 15; i++) {
//            Map.Entry<ParametricCallable, Double> entry = closest.get(i);
//            System.out.println(entry.getKey().getFullPath() + " " + entry.getValue());
//        }
}
