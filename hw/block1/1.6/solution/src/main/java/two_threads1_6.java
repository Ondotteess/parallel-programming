public class two_threads1_6 {

    static Thread A;
    static Thread B;

    public static void main(String[] args) throws Exception {

        A = new Thread(() -> {
            try {
                B.join();
            } catch (InterruptedException e) {
            }
        });

        B = new Thread(() -> {
            try {
                A.join();
            } catch (InterruptedException e) {
            }
        });

        A.start();
        B.start();

        A.join();
        B.join();
    }
}