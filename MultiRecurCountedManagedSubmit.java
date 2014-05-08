package calamity2;
/*
 * 
 * This demo is set up to expose the "compensation threads" problem when
 *   using a ForkJoinPool.managedBlock() in a CountedCompleter.
 * 
 *   
 *      ---  options  ---
 *   
 * The shipped version uses the common ForkJoinPool. You can
 *   use the instance version by changing FJParallism 
 *   to the number of threads you want for the FJPool, 
 *     set now to number of processors,
 *   AND adjust the commented code accordingly.
 *   
 * Change nbr_threads to the number of concurrent requests for
 *   submission. Each thread does a single invoke(). Set now
 *   to 1. The problem happens with only one submit on small
 *   systems. Change when running on large systems.
 *   
 * Change recur_count to the depth of recurrsion. Small numbers
 *   show the FJP Work Thread count but finish too fast to run out of memory. 
 *   Set now to 4 which is small. 
 *   Set to 16+ to run out of memory creating threads AND 
 *     comment the System.out.println(Thread.currentThread()); in method
 *     doSomething() to stop excessive printing.
 *     
 *    ---  methods  ---
 * 
 * doSomething() 
 *   Increment a counter. 
 *   Print the current thread, ForkJoinPool-1-worker-NN, where NN
 *     is the thread number. 
 *     Numbers significantly above either
 *       Runtime.getRuntime().availableProcessors() for the common FJP or
 *       FJParallism for the instance version 
 *     are "compensation threads" used in place of good task management.
 *     When the current thread is MultiThread-NN - this is an example of
 *     the appalling practice of using submitters for work threads.
 *   Issue ForkJoinPool.managedBlock()
 *     which will result in the callback method block() being called.
 *  
 * block() sleep 1ms to simulate waiting for an outside resource. 
 * 
 * isReleasable() always false.
 *   
 *       ------------
 *   
 *   myCount is just a total count of the number of user Tasks
 *   created.
 *   
 *   myComputed is just a total count of the number of times a Task
 *   called doSomething().
 * 
 */

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CountedCompleter;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Submit highly recursive requests to FJPool
 */
public class MultiRecurCountedManagedSubmit {
  
  // timing
  static final long NPS = (1000L * 1000 * 1000);
    
  // number of threads for FJPool
  static final int FJParallism = Runtime.getRuntime().availableProcessors();
  
  // number of concurrent requests submitted
  static final int nbr_threads = 1; 
  
  // depth of recurrsion
  static final int recur_count = 16;
    
  // count of number of user tasks created
  static final AtomicLong myCount = new AtomicLong(nbr_threads);
  
  // count of number doSomething() used
  static final AtomicLong myComputed = new AtomicLong(0);    
    
  /*
   * User task. Forks new tasks, count times, and then
   *   tryComplete();
   * 
   */
  public class Something 
         extends CountedCompleter<Void> 
         implements ForkJoinPool.ManagedBlocker {
    
    private final int count;
    private final Something prior;
    
    // constructor
    Something(Something p, int count) {
      
      super(p); 
      prior      = p;
      this.count = count;       
    }
    
    @Override
    public void compute() {
      
      if  (count < 1) {
        
          doSomething();          
          
          tryComplete();
          return;
      }
     
      // new tasks to create is 1 < current
      int new_count = count - 1;
            
      // create number of new tasks depending on count
      for (int i = 0; i < count; i++) {        
        
        addToPendingCount(1);
        
        new Something(this, new_count).fork();        
      }
      
      // accum total tasks created
      myCount.getAndAdd(count);
            
      tryComplete();    
    }  
    
    /*
     * Will Access outside resource 
     */
    private void doSomething() {        
      
      myComputed.incrementAndGet();
            
      if  (prior != null) {
        
          try {
            ForkJoinPool.managedBlock(prior);
          }
          catch (InterruptedException ignore) {}
      }   
    }

    /**
     * Access outside resource which results in a small
     * wait.
     */
    @Override
    public boolean block() throws InterruptedException {
      
      System.out.println(Thread.currentThread()); 
      
      try {Thread.sleep(1); } catch (InterruptedException ignore) {} 
         
      return true;
    }

    /**
     * is never releaseable for this test
     */
    @Override
    public boolean isReleasable() {
      
      return false;
    }
    
  } // end-inner-class
  
  /*
   * Just submit a new request
   * 
   */
  public class Thd extends Thread {
    
      //private ForkJoinPool   fjpool;
      private String         my_name;
      private CountDownLatch latch;
    
    // constructor
    public Thd( //ForkJoinPool fjpool, 
                String my_name, 
                CountDownLatch latch) {
      
      super(my_name);    
      
      //this.fjpool  = fjpool;
      this.my_name = my_name;
      this.latch   = latch;    
      
    } // end-constructor 
    
    @Override
    public void run() {      
    
      Something S = new Something(null, recur_count);
      
      long last = System.nanoTime();
        
      // submit one request
      //fjpool.invoke(S);
      ForkJoinPool.commonPool().invoke(S);
        
      System.out.printf("  " + my_name + " time: %7.9f\n", (double)(System.nanoTime() - last) / NPS);  
      
      // say done here
      latch.countDown();      
      
    } 
  } // end-inner class

  /**
   * do the actual work
   */
private void doWork() {
    
  //ForkJoinPool fjpool  = new ForkJoinPool(FJParallism);    
  CountDownLatch latch = new CountDownLatch(nbr_threads);
  
  Thd[] my_threads = new Thd[nbr_threads]; 
  
  for (int i = 0; i < nbr_threads; i++) {
    
    my_threads[i] = new Thd("MultiThread-" + i, latch);
    //my_threads[i] = new Thd(fjpool, "MultiThread-" + i, latch);
  }
  
  System.out.println("Starting threads");
  
  long last = System.nanoTime();
  
  for (int i = 0; i < nbr_threads; i++) {
    
    my_threads[i].start() ;
  }
  
  // wait for all submitted jobs to complete. does not use a timeout
  try {latch.await(); } catch(InterruptedException ignore) {}
  
  System.out.printf("  Total time: %7.9f\n", (double)(System.nanoTime() - last) / NPS);    
  System.out.println("  Total Tasks= " + myCount.get());
  System.out.println("  Total doSomething()= " + myComputed.get());
    
  System.out.println("Finished");      
  
} // end-method

public static void main(String[] args) throws Exception {
  
  MultiRecurCountedManagedSubmit worker = new MultiRecurCountedManagedSubmit();  
  worker.doWork();            
}
} // end-class
