
https://onedrive.live.com/?id=EBE3EA8146B849E3%21s5f8b702387b745fcb9c52d626f39e2c5&cid=EBE3EA8146B849E3&redeem=aHR0cHM6Ly8xZHJ2Lm1zL2YvYy9lYmUzZWE4MTQ2Yjg0OWUzL0lnQWpjSXRmdDRmOFJibkZMV0p2T2VMRkFkOVBMVW1fVzRhdV81Q3NCSFpnM2Z3P2U9czVBUW5F


? extends T (upper bound) - read only 
? super T (lower bound) - write safely - safe to read only as OBJECT

PECS - producer extends (getting value), consumer super (putting value)

list.stresm().map(String::toUpperCase) is faster than 
list.stresm().map(s -> s.toUpperCase())
because there is no middle man, create additional object, use direct link

Parallel stram for CPU work not for network or disk IO - one blocking task can starve the entire application


