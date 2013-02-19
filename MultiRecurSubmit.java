/*
 * This is a simulation of the problem of excessive thread creation found here:
 * http://stackoverflow.com/questions/10797568/what-determines-the-number-of-threads-a-java-forkjoinpool-creates
 * 
 * It is now set up to expose the stall problem created by the patch to the original problem.
 * You will need the current jar or source code for jsr166e found at:
 * http://gee.cs.oswego.edu/dl/concurrency-interest/
 * 
 * To run the simulation for Java7 (original problem), comment the 
 *   import jsr166e.*; line and uncomment the 
 *   import java.util.concurrent.*;
 *   
 *      ---  options  ---
 *   
 * Change FJParallism to the number of threads you want for
 *   the FJPool. Set now to number of processors.
 *   
 * Change nbr_threads to the number of concurrent requests for
 *   submission. Each thread does a single invoke(). Set now
 *   to 2. The problem happens with only one submission. Multiple
 *   submissions just exacerbate the problem.
 *   
 * Change recur_count to the depth of recurrsion. Small numbers
 *   finish too fast to visualize the problem. Set now to 16 which
 *   clearly is excessive but lets the program run long enough to
 *   visualize in a profiler like VisualVM or JConsole. Set this
 *   higher to expand the stack on work threads since the framework
 *   loops in:
 * at jsr166e.ForkJoinTask.join(ForkJoinTask.java:673)
 * at MultiRecurSubmit$Something.compute(MultiRecurSubmit.java:108)
 * at MultiRecurSubmit$Something.compute(MultiRecurSubmit.java:1)
 * at jsr166e.RecursiveTask.exec(RecursiveTask.java:64)
 * at jsr166e.ForkJoinTask.doExec(ForkJoinTask.java:260)
 * at jsr166e.ForkJoinPool$WorkQueue.tryRemoveAndExec(ForkJoinPool.java:949)
 * at jsr166e.ForkJoinPool.awaitJoin(ForkJoinPool.java:2019)
 * at jsr166e.ForkJoinTask.doJoin(ForkJoinTask.java:345)
 * at jsr166e.ForkJoinTask.join(ForkJoinTask.java:673) 
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
//import java.util.concurrent.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Submit highly recursive requests to FJPool
 */
public class MultiRecurSubmit {
  
  // timing
  static final long NPS = (1000L * 1000 * 1000);
    
  // number of threads for FJPool
  static final int FJParallism = Runtime.getRuntime().availableProcessors();
  
  // number of concurrent requests submitted
  static final int nbr_threads = 2; 
  
  // depth of recurrsion
  static final int recur_count = 16;
  
  // count of number of user tasks created
  static final AtomicLong myCount = new AtomicLong(nbr_threads);
  
  // count of number doSomething() used
  static final AtomicLong myComputed = new AtomicLong(0);    
    
  /*
   * User task. Forks new tasks, count times, and then
   *   joins the forked tasks.
   * 
   */
  public class Something extends RecursiveTask<Void> {
    
    int count;
    
    // constructor
    Something(int count) { this.count = count; }
    
    @Override
    protected Void compute() {
      
      if  (count < 1) {
        
          doSomething();          
          return null;
      }
                  
      // array of tasks to fork
      Something[] stuff = new Something[count];
      
      // new tasks to create is 1 < current
      int new_count = count - 1;
      
      // create number of new tasks depending on count
      for (int i = 0; i < count; i++) {        
        stuff[i] = new Something(new_count);
        stuff[i].fork();
      }
      
      // accum total tasks created
      myCount.getAndAdd(count);
            
      // wait for all submitted tasks to complete
      for (int i = 0; i < count; i++) {     
        stuff[i].join();
      }
            
      return null;      
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
    
      Something S = new Something(recur_count);
      
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
    
    my_threads[i] = new Thd(fjpool, "MultiThread" + i, latch);
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
  
  MultiRecurSubmit worker = new MultiRecurSubmit();  
  worker.doWork();            
}
} // end-class
