
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

/**
 * This demo simulates a thread pool used for summing
 * It creates threads to sort the arrays.
 * 
 * It creates the long[] and puts them into a queue.
 *  
 * It wakes up the threads. 
 * The threads fetch the long[] and sums the arrays 
 * 
 * The time to complete is printed. 
 * 
 * Change the size of the arrays, number of array objects and number of threads as you wish.
 */
public class MultiSubmitTPoolDemo {
    
  /** for time conversion */
  static final long NPS = (1000L * 1000 * 1000);  
  
  static final Random rng = new Random();
    
  // size of array to sum
  private final int nArray = 1 << 20;  
  
  // number of arrays to sum
  private final int nSums = 25;
  
  // number of parallel threads  *** adjust up to number of processors ***
  private final int nParallel = Runtime.getRuntime().availableProcessors();
    
    // inner classes
      
    // summing thread
    protected class Thd extends Thread {
    
        private final CountDownLatch latch;
        private final Object         wait_object;
        private final ConcurrentLinkedQueue<long[]> queue;   

        private long sum;
    
      // constructor
      public Thd( Object wait_object,
                  ConcurrentLinkedQueue<long[]> queue, 
                  CountDownLatch latch) {         
     
        this.wait_object = wait_object;
        this.latch       = latch; 
        this.queue       = queue;      
        
      } // end-constructor 
      
      @Override
      public void run() {
        
        // until all ready
        synchronized (wait_object) {
        
          try {
            wait_object.wait();
          
          } catch (InterruptedException ignore) {}        
        }    
        
        // do all in queue
        while (true) {
                            
          // array 
          long[] array = queue.poll();
          
           // When none, done
          if  (array == null) break;          
          
          // sum the array
          long sum = 0;                       
          for (int i = 0, l =array.length; i < l; i++)            
            sum += array[i];
                             
        } // end-while   
        
        // done with this thread     
        latch.countDown();        
              
      } // end-run       
  } // end-inner class  

/**
 * Main entry
 * @param args
 * @throws Exception
 */
public static void main(String[] args) throws Exception {
  
  MultiSubmitTPoolDemo demo = new MultiSubmitTPoolDemo();
    
  demo.doWork();
  
} // end-method

/**
 * do the work
 */
private void doWork() {  
  
  // used to hold objects to sum
  ConcurrentLinkedQueue<long[]> queue = new ConcurrentLinkedQueue<long[]>();
  
  // number of work threads
  CountDownLatch latch = new CountDownLatch(nParallel);
  
  // object threads wait on before doing sorting
  Object wait_object = new Object();  
  
  // create the summing threads
  for (int i = 0; i < nParallel; i++) {
    
    new Thd(wait_object, // object to wait on before work
            queue,       // holds arrays to sort
            latch        // count down latch
           ).start();    // start thread             
  }
  
  // fill up the queue
  for (int i = 0; i < nSums; i++) {
    
    long[] array = new long[nArray];
    
    // fill array with random numbers
    ranFill(array); 
    
    // add to queue
    queue.offer(array);   
    
  } // end-for 
  
  System.out.println("Parallelism=" + nParallel + " Total sums=" + nSums);
  
  // start timing
  long last = System.nanoTime();
  
  // wake up the threads
  synchronized (wait_object) {   
   
      wait_object.notifyAll();     
  }
  
  // wait until summing complete
  try {latch.await(); } catch(InterruptedException ignore) {}
     
  double run_time = (double)(System.nanoTime() - last) / NPS;
  
  System.out.printf(" Finished with total runtime= %7.9f\n", run_time);   
   
} // end-method

/**
 * fill array with random numbers
 * @param array
 */
static void ranFill(long[] array) {
    
  for (int i = 0; i < array.length; ++i)
    array[i] = rng.nextLong();
  
} // end-method
} // end-class
