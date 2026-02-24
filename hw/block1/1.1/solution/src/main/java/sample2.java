public class sample2 {
    public static void main(String[] args) throws InterruptedException {

        Thread B = new Thread(() -> {
            System.out.println("B throws excpetion");
            throw new RuntimeException("exception from B");
        }, "B");

        System.out.println("A starting B");
        B.start();

        System.out.println("A joining B");
        B.join();
        System.out.println("B terminated. B.isAlive= " + B.isAlive());

        Thread C = new Thread(() -> {
            System.out.println("C joining B after B already terminated");

            try {
                B.join();
            } catch (InterruptedException e) {
                System.out.println("C interrupted during joining B");
                return;
            }

            System.out.println("B already terminated. thats why C join returned immediately");
        }, "C");

        System.out.println("A starting C");
        C.start();
        C.join();

        System.out.println("A done");
    }
}
