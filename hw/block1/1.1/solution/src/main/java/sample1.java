public class sample1 {
    public static void main(String[] args) throws InterruptedException {
        Thread b = new Thread(() -> {
            System.out.println("thread B started");
            throw new RuntimeException("exception from B");
        }, "B");

        System.out.println("thread A starting B");
        b.start();

        System.out.println("threa A joining B...");
        b.join();

        System.out.println("A join returned, B isAlive=" + b.isAlive());
    }
}
