package calamity2;
/*
 * 
 * This demo is set up to expose the falure of a CompletableFuture with a long
 *   completion chain.
 *
 * The code is from an example given by Paul Sandoz of Oracle in response to
 *  hanging problems with .thenCombine(). The full text can be found on the
 *  concurrency-interest list, title:
 *  Java 8 CompletableFuture.thenCombine() hangs. 
 *
 *  The grist of the problem is that the async task encounters a
 *    StackOverflowException. Since the F/J pool eats the exception, no further
 *    computing happens, hence the code hangs.
 *   
 * You will need the current JDK1.8 
 */

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static java.util.stream.Collectors.toList;

/**
 * Completable Future Failure
 */
public class CFFailure {

    static List<CompletableFuture<Integer>> tasks = new ArrayList<>();

    private Integer run(int n) {
        CountDownLatch cdl = new CountDownLatch(n - 1);

        CompletableFuture<Integer> last = CompletableFuture.supplyAsync(() -> {
            try {
                cdl.await();
            }
            catch (InterruptedException e) { }
            return 0;
        });

        tasks.add(last);
        for (int i = 1; i < n; i++) {
            final int v = i;
            last = CompletableFuture.supplyAsync(() -> {
                cdl.countDown();
                return v;
            }).thenCombine(last, Integer::max);
            tasks.add(last);
        }
        return last.join();
    }

    public static void main(String[] args) {
        CFFailure fail = new CFFailure();

        CountDownLatch cld = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            try {
                cld.await(2, TimeUnit.SECONDS);
            }
            catch (InterruptedException e) {
            }

            if (cld.getCount() == 1) {
                List<CompletableFuture<Integer>> it = tasks.stream().filter(task -> !task.isDone()).collect(toList());
                System.out.printf("%d tasks not completed\n", it.size());
            }
        });

        t.start();
        fail.run(10000);
        cld.countDown();
    }
}