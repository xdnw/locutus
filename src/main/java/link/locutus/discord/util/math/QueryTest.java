package link.locutus.discord.util.math;

public class QueryTest<T> {

//    private record ParseResult(List<Predicate<T>> predicates, List<T> numbers, AtomicBoolean hadNonFilter) {
//    }


//    public static List<T> getNumbers(String input) {
//        if (input.equals("x")) {
//            return List.of(13, 26, 39, 52, 65, 50, 100, 200);
//        }
//        if (input.equals("y")) {
//            return List.of(20, 30, 11, 21, 31);
//        }
//        if (input.equals("z")) {
//            return List.of(66, 666, 200, 31);
//        }
//        return List.of(Integer.parseInt(input));
//    }

    public static boolean isPrime(int num) {
        if (num <= 1) {
            return false;
        }
        if (num <= 3) {
            return true;
        }
        if (num % 2 == 0 || num % 3 == 0) {
            return false;
        }

        for (int i = 5; i * i <= num; i += 6) {
            if (num % i == 0 || num % (i + 2) == 0) {
                return false;
            }
        }

        return true;
    }

//    public static <T> Predicate<T> parseFilter(String input) {
//        if (input.equals("#isPrime=true")) {
//            return number -> isPrime(number.intValue());
//        }
//        if (input.equals("#isDivisible(2)=true")) {
//            return number -> number.intValue() % 2 == 0;
//        }
//        throw new IllegalArgumentException("Unknown filter " + input);
//    }


//    public static void main(String[] args) {
//        // Example usage:
//        String input = "1,(x,#isPrime=true),3,(y,#isDivisible(2)=true),z";
//
//        //
//        List<T> parsedTs = parseQuery(input);
//        System.out.println("Parsed Ts: " + parsedTs);
//    }
}
