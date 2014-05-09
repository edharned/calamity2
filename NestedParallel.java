package calamity2;
/*
 * 
 * This demo is set up to expose the failure of nested parallel forEach loops.
 *
 * The code is from an example by Christian P. Fries posted originally on
 *   StackOverflow at:
 *     http://stackoverflow.com/questions/23489993/nested-java-8-parallel-foreach-loop-perform-poor-is-this-behavior-expected
 *  
 * The grist of the problem is that a parallel inner forEach loop can take as
 *   much as 2-3 times as long to run as a sequential inner forEach loop.
 *   The parallel version can create 10 times the number of worker threads with
 *   most of those threads simply in a wait state up to 88% of the time. Each
 *   outer loop thread cannot detach from processing the inner loop therefore,
 *   they get stuck and the framework creates new threads.
 *
 * You can run this as-is and see the time it takes to execute both in 
 *   parallel as well as sequential.
 * Running in a profiler (visualVM for one) shows just how awful the execution
 *   of parallel inner loops can be. A huge number of ForkJoinWorkerThreads
 *   are created with most of them sitting idle (awaitJoin()) for 80-90% of
 *   the time. Use the below option, USE_DELAY = true; to help establish the
 *   profiler linkage before execution. The number of iterations of both
 *   inner and outer is geared so execution takes long enough so you can see
 *   what is happening in a profiler. Adjust to your needs.
 * 
 * Options:
 *  isInnerStreamParallel -- run with parallel or sequential inner loop
 *  USE_DELAY -- delay the start of test to enable a profiler
 *  outerLoop -- iterations in outer loop
 *  innerLoop -- iterations in inner loop
 *  fjParallelism -- override default ForkJoinPool parallelism level
 *  burningCount -- Tasks need to do some work, this is how much.
 *  
 * You will need the current JDK1.8 
 */
import java.util.stream.IntStream;

/**
 * Test nested parallel forEach loops
 * 
 */
public class NestedParallel {
  
  static final long NPS = (1000L * 1000 * 1000); // for timing

	// Run with inner loop parallel=true, sequential=false
	static final boolean isInnerStreamParallel	 = true;
  
  // When using a profiler, it is sometimes best to delay formal execution until
  //   you've had a chance to establish linkage. Therefore, you can delay
  //   execution for 10 seconds with this option set to "true"
  static final boolean USE_DELAY = false;
	
	static final int outerLoop = 200;		// adjust for your needs
	static final int innerLoop = 50000;	// adjust for your needs
	static final int fjParallelism = 8;	// ForkJoinPool.common.parallelism
	
	static final long	burningCount = 800;	// useless work count
  
  // Thread message for each outer loop, will print at end of run
  static final Thread[] println = new Thread[outerLoop];

  /**
   * Start of application
   * @param args 
   */
	public static void main(String[] args) {
    
    if  (USE_DELAY) 
      try {Thread.sleep(10000);} catch (InterruptedException e) {}
    
		new NestedParallel().nestedLoops();
	}

  /**
   * Loops
   */
	public void nestedLoops() {

		System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism",
                        Integer.toString(fjParallelism));
		System.out.println("ForkJoinPool.common.parallelism: " 
                       + fjParallelism);
    String type = (isInnerStreamParallel)? "parallel" : "sequential";
    System.out.println("Using " + type + " inner loop");
    
		long start = System.nanoTime();
    
    // Outer loop always parallel    
		IntStream.range(0,outerLoop).parallel().forEach(i -> {
      
      // thread info, will print at end of run
      println[i] = Thread.currentThread();

      if(isInnerStreamParallel) {
        IntStream.range(0,innerLoop).parallel().forEach(j -> {
          uselessWork(10);
        });						
      }
      else {
        IntStream.range(0,innerLoop).sequential().forEach(j -> {
          uselessWork(10);
        });
      } 
		});

		long end = System.nanoTime();
    
    // print thread info
    for (int i = 0; i < outerLoop; i++)       
      System.out.println(i + "\t" + println[i]);    

    double elapsed = (double)(end - start) / NPS;
    System.out.printf("Elapsed time : %5.9f\n", elapsed);
		
	}
	
  /**
   * Simulate some real CPU work
   * @param millis
   * @return ignored
   */
	private double uselessWork(long millis) {
		
    double back = 0.0;
		long max = millis * burningCount;
		for(long i=0; i < max; i++) {
			back += Math.cos(0.0);
		}		
    return back;
	}
}
