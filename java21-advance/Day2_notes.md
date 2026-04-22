
https://onedrive.live.com/?id=EBE3EA8146B849E3%21s5f8b702387b745fcb9c52d626f39e2c5&cid=EBE3EA8146B849E3&redeem=aHR0cHM6Ly8xZHJ2Lm1zL2YvYy9lYmUzZWE4MTQ2Yjg0OWUzL0lnQWpjSXRmdDRmOFJibkZMV0p2T2VMRkFkOVBMVW1fVzRhdV81Q3NCSFpnM2Z3P2U9czVBUW5F

Concurrency

Volatile

ConnectionPool - reduce the startup hook time
More thread does not means can work faster, it depends on the CPU bound

CPU bound (minimal context switching) (cores + 1) VS IO bound (scaling for blocking latency)

Executor.newFixedThreadPoolExecutor(cores+1)

the threshold 10,000 per code is ideal for the core to work

future.get() -> blocking

future.whenComplete() -> non blocking push model, use callback

virtual thread - good for IO, good for Web, able to switch taks non stop

bound to carries thead
block then will unmount (e.g. read buffer) - move to heap
re-mount

pinning - virtual thread cannot be unmounted from its carrier thread during blocking operation


synchronized like below will cause pinning, but JAVA 21 the latest version is fixed now
public synchronized void heavyIO (){
 String data = networkClient.fetch
}
