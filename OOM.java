package calamity2;

/*
 * 
 * This demo is set up to expose the Out Of Memory error when
 *   using a stream.limit(n) in parallel mode.
 *   
 * You will need the current JDK1.8 for lambda found at:
 *  https://jdk8.java.net/download.html
 * You may also want an IDE to help run this program. NetBeans 7.4 has Java8 support
 *  
 * This demo was copied from a problem submitted by Christian Mallwitz to
 *  the lambda-dev@openjdk.java.net
 *
 * As of July, 2013 the OOME was fixed by making the parallel code run mostly
 *  sequentilly at a huge cost in performance. The parallel version now takes
 *  longer than the sequential version.
 *
 *
 * The second test may still produce an OOME on 32bit systems. When it doesn't
 *  it takes longer to run parallel than sequential.
 *  This is from a discussion on the core libraries: 
 *     RFR 8027316 Distinct operation
 *  Although using parallel for the example is questionable, it does
 *    show how badly designed the F/J Framework is when using iteration.
 */

import java.util.Optional;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public class OOM {
  
  private static final long NPS = (1000L * 1000 * 1000);
  private static final long start_millis = System.currentTimeMillis();
  private static long max_n_seen = 3_999_970L;

public static void main(String... ignored) {
  // first 283_146 primes are all < 4_000_000
  
  // sequential run   
  firstNPrimes(283_146, getIteratorBasedStream(false)); 
  
  // parallel run 
  firstNPrimes(283_146, getIteratorBasedStream(true)); 
  
  // OOME on 32 bit systems
  distinct();  
}

private static Stream<Long> getIteratorBasedStream(boolean parallel) {
	Stream<Long> s = LongStream.iterate(1L, n -> n + 1L).boxed();
	return parallel ? s.parallel() : s;
}

private static void firstNPrimes(int n, Stream<Long> stream) {
	System.out.println(String.format("firstNPrimes (%8d, %5b): %8d", 
          n, 
          stream.isParallel(),
          stream.filter(OOM::isPrime).limit(n) // limit after primality test
            .count()));
}

private static boolean isPrime(long n) {
	if (n >= max_n_seen) {
			System.out.println(String.format("%.3f: %d", 
              (System.currentTimeMillis() - start_millis)/1000.0, n));
      
			max_n_seen = n + (n/20L); // new max_n_seen 5% larger than current n
	}

	if (n <= 1) { return false; }
	if (n == 2) { return true; }
	if (n % 2 == 0) { return false; }
	for (int i = 3; i <= (int) Math.sqrt(n); i += 2) { if (n % i == 0) return false; }
	return true;
}

private static void distinct () {
  
  // sequential    
  long last = System.nanoTime(); 

  Optional<Integer> oi = 
          Stream.iterate(1, i -> i + 1)
                .unordered()
                .distinct()
                .findAny();

  double elapsed = (double)(System.nanoTime() - last) / NPS;
  System.out.printf("Stream.iterate sequential: %5.9f\n", elapsed);  
    
  // parallel   
  last = System.nanoTime(); 
  
  oi = Stream.iterate(1, i -> i + 1)
             .unordered()
             .parallel()
             .distinct()
             .findAny();
    
  elapsed = (double)(System.nanoTime() - last) / NPS;
  System.out.printf("Stream.iterate parallel  : %5.9f\n", elapsed); 
      
  // sequential   
  last = System.nanoTime(); 
  
  Integer one = 
          Stream.iterate(1, i -> i + 1)
                 .unordered()
                 .distinct()
                 .findAny()
                 .get();
  
  elapsed = (double)(System.nanoTime() - last) / NPS;
  System.out.printf("Integer sequential: %5.9f\n", elapsed); 
  
  // parallel
  last = System.nanoTime(); 
  
   one = Stream.iterate(1, i -> i + 1)
               .unordered()
               .parallel()
               .distinct()
               .findAny()
               .get();
   
  elapsed = (double)(System.nanoTime() - last) / NPS;
  System.out.printf("Integer parallel  : %5.9f\n", elapsed); 
  
}
}