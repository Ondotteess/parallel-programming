public class sample3 {
    public static void main(String[] args) throws InterruptedException {

        Thread A = new Thread(() -> {
            System.out.println("thread A started");

            Thread B = new Thread(() -> {
                System.out.println("thread B started");
                throw new RuntimeException("exception from B");
            }, "B");

            System.out.println("A starting B");
            B.start();

            System.out.println("A joining B");
            try {
                B.join();
            } catch (Exception e) {
                System.out.println("A interrupted during waiting for B. this should not normally happen");
                return;
            }

            System.out.println("thread A join returned, B.isAlive" + B.isAlive());

            System.out.println("thread A doing some work after B termination...");
            try {
                Thread.sleep(999);
            } catch (InterruptedException ignored) { }

            System.out.println("thread A finished");
        }, "A");

        Thread D = new Thread(() -> {
            System.out.println("thread D started");
            System.out.println("D joining A (D waits until A fully terminates)");
            try {
                A.join();
            } catch (InterruptedException e) {
                System.out.println("D interrupted while waiting for A");
                return;
            }
            System.out.println("thread D join returned, A.isAlive " + A.isAlive());
            System.out.println("thread D finished");
        }, "D");

        System.out.println("main starting A and D");
        A.start();
        D.start();

        A.join();
        D.join();

        System.out.println("done");
    }
}
