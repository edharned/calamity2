/*
 * 
 * This demo is set up to expose the "continuation threads" problem when
 *   using a join() in a CountedCompleter.
 *   
 * You will need the current jar or source code for jsr166e found at:
 * http://gee.cs.oswego.edu/dl/concurrency-interest/
 * 
 *   
 *      ---  options  ---
 *   
 * Change FJParallism to the number of threads you want for
 *   the FJPool. Set now to number of processors.
 *   
 * Change nbr_threads to the number of concurrent requests for
 *   submission. Each thread does a single invoke(). Set now
 *   to 1. The problem happens with only one submit on small
 *   systems. Change when running on large systems.
 *   
 * Change recur_count to the depth of recurrsion. Small numbers
 *   finish too fast to visualize the problem or run out of memory. 
 *   Set now to 16 which clearly is excessive but lets the program 
 *   run long enough to visualize in a profiler like VisualVM or JConsole. 
 * 
 * Change NORMALLY to true to run normally without excessive thread creation,
 *   that is, without a join()    
 *   ALSO, adjust recur_count down to 12 or less so it finishes in a reasonable time.
 * 
 * doSomething() doesn't do anything now except increment a counter. 
 *  You can uncomment the code to burn up cpu time. 
 *  There is a commented sleep() to simulate I/O or some other blocking state.
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

import jsr166e.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Submit highly recursive requests to FJPool
 */
public class MultiRecurCountedSubmit {
  
  // timing
  static final long NPS = (1000L * 1000 * 1000);
    
  // number of threads for FJPool
  static final int FJParallism = Runtime.getRuntime().availableProcessors();
  
  // number of concurrent requests submitted
  static final int nbr_threads = 1; 
  
  // depth of recurrsion
  static final int recur_count = 16;
  
  // to run normally without join() = true; 
  // false shows the excessive thread creation problem
  static final boolean NORMALLY = false;
  
  // count of number of user tasks created
  static final AtomicLong myCount = new AtomicLong(nbr_threads);
  
  // count of number doSomething() used
  static final AtomicLong myComputed = new AtomicLong(0);    
    
  /*
   * User task. Forks new tasks, count times, and then
   *   tryComplete();
   * 
   */
  public class Something extends CountedCompleter<Void> {
    
    int count;
    
    // constructor
    Something(CountedCompleter<?> p, int count) {
      
      super(p);      
      this.count = count;       
    }
    
    @Override
    public void compute() {
      
      if  (count < 1) {
        
          doSomething();
          tryComplete();
          return;
      }
                  
      // array of tasks to fork
      Something[] stuff = new Something[count];
      
      // new tasks to create is 1 < current
      int new_count = count - 1;
            
      // create number of new tasks depending on count
      for (int i = 0; i < count; i++) {        
        
        stuff[i] = new Something(this, new_count);
        
        addToPendingCount(1);
        
        stuff[i].fork();
      }
      
      // accum total tasks created
      myCount.getAndAdd(count);
      
      /*
       *  this will cause excessive threads when the 
       *    static final int recur_count is high.
       *    
       *  to run normally, set NORMALLY to true
       */
      if  (!NORMALLY) {
          if  (new_count == 1) {
            
              tryComplete();
              
              // will cause excessive thread creation
              stuff[0].join();              
              return;
              
          } else {            
              tryComplete();
              return;
          }
      }
            
      tryComplete();    
    }  
    
    /*
     * Whatever you would like
     */
    private void doSomething() {        
      
      myComputed.incrementAndGet();
            
      //Thread.yield();
      
      //try {Thread.sleep(1); } catch (InterruptedException ignore) {}   
      
      // for non-trivial work
      //double temp = System.currentTimeMillis();
      //  double x = Math.sqrt(temp);
      //  double y = Math.tanh(temp);
      //  double z = Math.sinh(temp);    
    }
  } // end-inner-class
  
  /*
   * Just submit a new request
   * 
   */
  public class Thd extends Thread {
    
    private ForkJoinPool   fjpool;
    private String         my_name;
    private CountDownLatch latch;
    
    public Thd( ForkJoinPool fjpool, 
                String my_name, 
                CountDownLatch latch) {
      
      super(my_name);    
      
      this.fjpool  = fjpool;
      this.my_name = my_name;
      this.latch   = latch;    
      
    } // end-constructor 
    
    @Override
    public void run() {      
    
      Something S = new Something(null, recur_count);
      
      long last = System.nanoTime();
        
      // submit one request
      fjpool.invoke(S);
      
        
      System.out.printf("  " + my_name + " time: %7.9f\n", (double)(System.nanoTime() - last) / NPS);  
      
      // say done here
      latch.countDown();      
      
    } 
  } // end-inner class

  /**
   * do the actual work
   */
private void doWork() {
    
  ForkJoinPool fjpool  = new ForkJoinPool(FJParallism);    
  CountDownLatch latch = new CountDownLatch(nbr_threads);
  
  Thd[] my_threads = new Thd[nbr_threads]; 
  
  for (int i = 0; i < nbr_threads; i++) {
    
    my_threads[i] = new Thd(fjpool, "MultiThread-" + i, latch);
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
  
  fjpool.shutdown();
  
  System.out.println("Finished");      
  
} // end-method

public static void main(String[] args) throws Exception {
  
  MultiRecurCountedSubmit worker = new MultiRecurCountedSubmit();  
  worker.doWork();            
}
} // end-class
