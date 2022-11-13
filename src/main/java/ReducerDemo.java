import java.util.ArrayList;

/* This class only used for practice reduce() method */
public class ReducerDemo {
    public static void main(String[] args) {
        ArrayList<Integer> numbers = new ArrayList<>();
        numbers.add(10);
        numbers.add(20);
        numbers.add(30);
        numbers.add(40);

//        numbers.stream().reduce((previous, current) -> {
//            System.out.println(previous);
//            System.out.println(current);
//            System.out.println("----------");
//            return current;
//        });

        System.out.println(numbers.stream().reduce((prev, crnt) -> prev+crnt));
    }
}
