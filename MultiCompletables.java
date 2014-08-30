package calamity2;

/*
 * This demo is set up to expose the "compensation threads" problem when
 *   using CompleableFutures.get() with many tasks.
 *   
 *   Change recur_count to the depth of recurrsion. Small numbers
 *   finish too fast to visualize the problem. Set now to 16 which
 *   clearly is excessive but lets the program run long enough to
 *   visualize in a profiler like VisualVM or JConsole or just about
 *   any tool that shows thread counts. 
 *   The bigger recur_count, the more compensation threads. 
 *   
 */

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeoutException;

/**
 * Submit highly recursive requests to FJPool
 */
public class MultiCompletables {
  
  // timing
  static final long NPS = (1000L * 1000 * 1000);
  
  // depth of recurrsion
  static final int recur_count = 16;
  
  // When using a profiler, it is sometimes best to delay formal execution until
  //   you've had a chance to establish linkage. Therefore, you can delay
  //   execution for 5 seconds with this option set to "true"
  static final boolean USE_DELAY = false;
  
  /**
   * Dummy completer to simulate a dependent function
   */
  public class MyFuture extends CompletableFuture<Void> {
    
    // not used yet
    private Something obj;
    
    // constructor
    public MyFuture (Something obj) { this.obj = obj; }  
    
    public Something getS() { return obj; }
    
  } // end-inner-class
    
  /*
   * User task 
   */ 
  public class Something extends RecursiveAction {
    
    private static final long serialVersionUID = -6194557974354749779L;
    
    int count;
    
    // constructor
    Something(int count) { this.count   = count; }
    
    @Override
    protected void compute() {
      
      if  (count < 1)         
          return;
                  
      // array of tasks to fork
      Something[] stuff = new Something[count];  
      
      // completers
      MyFuture[] cf = new MyFuture[count];
      
      // new tasks to create is 1 < current
      int new_count = count - 1;
      
      // create number of new tasks and completers depending on count
      for (int i = 0; i < count; i++) {      
        
        stuff[i] = new Something(new_count);                
        cf[i]    = new MyFuture(stuff[i]);        
        
        stuff[i].fork();        
      } 
                  
      // wait for completers: to simulate a dependent function
      for (int i = 0; i < count; i++) {     
                        
        try {
          // forces a wait which will result in a compensation thread
          //  without the 1 second timeout, it would never end
          cf[i].get(1000, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException ignore ) {}
        catch (InterruptedException ignore ) {}
        catch (ExecutionException ex ) {
        
            System.out.println("Caught ExecutionException " + ex);
        }                  
      }          
    }      
  } // end-inner-class
  
  /**
   * do the actual work
   */
private void doWork() {    
  
  if  (USE_DELAY) 
      try {Thread.sleep(5000);} catch (InterruptedException e) {}
    
  System.out.println("Starting ");
  
  Something S = new Something(recur_count);
      
  long last = System.nanoTime();
    
  // submit one request  
  ForkJoinPool.commonPool().invoke(S); 
  
  System.out.printf("Finished with total time: %7.9f\n", 
                    (double)(System.nanoTime() - last) / NPS);    
  
} // end-method

public static void main(String[] args) throws Exception {
  
  MultiCompletables worker = new MultiCompletables();  
  worker.doWork();            
}
} // end-class
