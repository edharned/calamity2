package calamity2;
/*
 * 
 * This demo is set up to expose the awful results when
 *   using Arrays.stream(array) and Arrays.stream(array).parallel().
 *
 * Generally, splitting an array between two threads results in about 1.3%
 *  improvement in elapsed time. The sequentialSum/parallelSum proves this.
 *  Adding more threads improves performance when there are processors to
 *  handle the load. 
 *
 *  ParallelStreamSum doesn't improve by adding more threads
 *  since the processing is mostly being done sequentially.
 *   
 * You will need the current JDK1.8 
 *  
 * This demo was copied from a problem submitted by Sebastian Zarnekow to
 *  the lambda-dev@openjdk.java.net
 */

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

public class IntArraySum {
    
    static final long NPS = (1000L * 1000 * 1000); // nanoseconds -> seconds

    private static int length;    
    private static int reps;
    
    private static int[] array;
    
    private static Thd thread1;
    private static Thd thread2;
    private static final Object waitObject = new Object();
    private static final CountDownLatch latch = new CountDownLatch(2);
    
    /**
     * inner class to do the thread work
     */
    public static class Thd extends Thread {
    
        private final int low, high;
    
      public Thd(int lo, int hi) {
      
        low = lo;
        high = hi;        
      } 
    
      @Override
      public void run() {
        
        // until ready
        synchronized (waitObject) {
        
          try { waitObject.wait();
          
          } catch (InterruptedException ignore) {}        
        }    
        
        // just like SequentialSum
        int result = 0;
        for (int i = 0; i < reps; i++) {
            int j = 0;
            for(int k = low; k < high; k++) 
              j += k * 5 * i;
            
            result += j;
        }
        
        latch.countDown();            
      } 
    } // end-inner class
    
    public static void main(String[] args) throws Exception {
        
        length = 10_000_000;
        reps   = 100;
        setUp();
        
        /* pure sequential summation */        
        long last = System.nanoTime(); 
        
        SequentialSum();
        
        double elapsed = (double)(System.nanoTime() - last) / NPS;
        System.out.printf("SequentialSum    : %5.9f\n", elapsed);  
        
         /* summation using two threads */
        last = System.nanoTime(); 
        
        ThreadedSum();
        
        elapsed = (double)(System.nanoTime() - last) / NPS;
        System.out.printf("ThreadedSum      : %5.9f\n", elapsed);
        
         /* sequential stream summation */
        last = System.nanoTime(); 
        
        StreamSum();
        
        elapsed = (double)(System.nanoTime() - last) / NPS;
        System.out.printf("StreamSum        : %5.9f\n", elapsed);
        
        /* parallel stream summation */
        last = System.nanoTime(); 
        
        ParallelStreamSum();
        
        elapsed = (double)(System.nanoTime() - last) / NPS;
        System.out.printf("ParallelStreamSum: %5.9f\n", elapsed);         
    }

    static void setUp() {
        array = new int[length];
        for(int i = 0; i < length; i++) {
            array[i] = i;
        }     
        
        // create threads for threadedSum()
        thread1 = new Thd(0, length / 2);
        thread1.start();
        thread2 = new Thd(length / 2, length);
        thread2.start();
        
        System.out.println("array length= " + length + " reps= " + reps );
        System.out.println(" ");
    }

    static void SequentialSum() {
        int result = 0;
        for (int i = 0; i < reps; i++) {
            int j = 0;
            for(int k : array) {
                j += k * 5 * i;
            }
            result += j;
        }
    }
    
    static void ThreadedSum() {
      
      synchronized (waitObject) {
          
          waitObject.notifyAll();          
      }
      
      try { latch.await();
      } catch (InterruptedException ignore) {}        
    }

    static void StreamSum() {
        int result = 0;
        for (int i = 0; i < reps; i++) {
            final int i_ = i;
            result += Arrays.stream(array).map(e -> e * 5 * i_).sum();
        }
    }

    static void ParallelStreamSum() {
        int result = 0;
        for (int i = 0; i < reps; i++) {
            final int i_ = i;
            result += Arrays.stream(array).parallel().map(e -> e * 5 * i_).sum();
        }        
    }
}
