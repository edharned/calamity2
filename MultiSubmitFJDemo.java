package calamity2;

// for current jdk1.8
// to use jdk1.7, run with that release
//
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

/**
 * This demo has the f/j pool being used by many concurrent requests. 
 * 
 * It creates threads. Each thread creates a long array to sum the arrays
 *     
 * It wakes up the threads. 
 * 
 * The threads submit the arrays for sum to the F/J pool.
 * 
 * The time to complete is printed. 
 * 
 * Change the size of the arrays, number of threads to submit array objects and number of threads in the pool as you wish.
 */
public class MultiSubmitFJDemo {
  
    static final long NPS = (1000L * 1000 * 1000);
    static final int THRESHOLD = 32768;
    
    // inner classes
      
    /**
     * Class that sums an array in parallel
     */
      private class Summer extends RecursiveTask<Long> {
        
        private final long[] array;
        private final int lo;
        private final int hi;
        
        Summer (long[] array, int lo, int hi) { 
          
          this.array = array;
          this.lo    = lo;
          this.hi    = hi;          
        }
        
        @Override
        public Long compute () {
          
          
          // When can be done sequentially
          if  ((hi - lo) <= THRESHOLD) {
               
               long sum = 0;
               
               // sum array
               for (int i = lo; i < hi; i++) 
                 sum += array[i];
                              
               return sum;
           }
           
          // split in two
           int mid = (lo + hi) >>> 1;           
             
           Summer left  = new Summer(array, lo, mid);
           Summer right = new Summer(array, mid, hi);
             
           // push to deque for another thread
           left.fork();
             
           // continue down stack
           Long rightBack = right.compute();
           
           // wait until complete
           Long leftBack = left.join();
           
           return rightBack.longValue() + leftBack.longValue();   
        }        
      } // end-inner class
  
      /**
       * Submission thread
       */
      public class Thd extends Thread {
        
        private final Random rng = new Random();
        private final long[]         array;
        private final ForkJoinPool   fjpool;
        private final CountDownLatch latch;
        private final Object         wait_object;
        
        private long sum;
        
        public Thd( int n, 
                    ForkJoinPool fjpool, 
                    Object wait_object, 
                    CountDownLatch latch) {         
         
          this.wait_object = wait_object;
          this.latch       = latch; 
          this.fjpool      = fjpool;
          
          // create the array for summing
          array = new long[n];      
          randomFill(array);   
          
          // sum sequentially for error checking
          for (int i = 0, l =array.length; i < l; i++)            
            sum += array[i];      
          
        } // end-constructor 
        
        @Override
        public void run() {
          
          // until all ready
          synchronized (wait_object) {
            
            try {
              wait_object.wait();
              
            } catch (InterruptedException ignore) {}        
          } // end-sync          
            
          // sum the array
          Long back = fjpool.invoke(new Summer(array, 0, array.length));
          
          // done with this thread     
          latch.countDown();  
         
          /* 
           * Optionally check for correctness
           * 
           */ 
          if  (sum != back.longValue()) System.out.println("Computed sum:" + sum + " not= returned sum:" + back.longValue());
          
        } // end-run
        
        private void randomFill(long[] a) {
          for (int i = 0; i < a.length; ++i) 
            a[i] = rng.nextLong();         
        }           
      } // end-inner class

/**
 * constructor
 */
public MultiSubmitFJDemo() {   
  
} // end-constructor

/**
 * the actual work
 */
private void doWork() {
  
  // size of array to sum
  int nArray = 1 << 20;  
  
  // number of threads to submit sum
  int nSums = 25;
  
  // ForkJoinPool size  *** adjust up to number of processors ***
  int nParallel = Runtime.getRuntime().availableProcessors();
  
  // object threads wait on before submitting sum
  Object wait_object = new Object();  
 
  ForkJoinPool   fjpool = new ForkJoinPool(nParallel);    
  CountDownLatch latch  = new CountDownLatch(nSums);
  
  // create the submitting threads
  for (int i = 0; i < nSums; i++) {
    
    new Thd(nArray,      // array size
            fjpool,      // F/J pool
            wait_object, // object to wait on before submit
            latch        // count down latch
           ).start();    // start thread             
  }
    
  System.out.println("Parallelizm=" + nParallel + " Concurrent sum=" + nSums);
  
  // start timing
  long last = System.nanoTime();   
  
  // wake up threads
  synchronized (wait_object) {
    
    wait_object.notifyAll();
    
  } // end-sync
  
  // wait until done
  try {latch.await(); } catch(InterruptedException ignore) {}
  
  double run_time = (double)(System.nanoTime() - last) / NPS;
  
  fjpool.shutdown();
  System.out.printf(" Finished with total runtime=  %7.9f\n", run_time);      
  
} // end-method

public static void main(String[] args) throws Exception {
  
  MultiSubmitFJDemo worker = new MultiSubmitFJDemo();
  
  worker.doWork();        
    
}
} // end-class
