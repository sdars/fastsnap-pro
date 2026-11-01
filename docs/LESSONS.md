# Lessons learned

1. Decompile with two engines. CFR and JADX agreed on control flow, but CFR
   preserved the exact constant `FastSnap Pro 3.2.1)` while a hand-written
   summary had silently "fixed" it. Constant-pool checks catch that class of bug.
2. Compile the recovered source and compare class hashes. Matching `App.class`
   and `Lic.class` byte-for-byte is stronger evidence than reading decompiler
   output and assuming it is complete.
3. Treat the checksum as a fixed-point problem. The last two body characters
   are part of the hashed input and also encode the expected value; naive
   generate-then-append loops never terminate. Expanding the recurrence and
   solving `15*d1 == -961*P (mod 65536)` gives a direct construction.
4. Test the original, not the decompiler. The shipped jar reports every valid
   key as expired because it compares a two-digit year with epoch milliseconds.
   Reproducing that bug in a `legacy` mode keeps compatibility honest while the
   default path remains usable.
5. Make hostile input non-fatal. The original crashes on non-hex checksum
   digits; the rebuild returns `invalid serial` and covers it with a test.
6. Pin the toolchain. This host only ships JDK 21; a Java 17 toolchain block
   fails before compilation. The build targets 21 and documents it.
7. Keep generated artifacts out of git and publish a reproducible jar from a
   clean tag. `preserveFileTimestamps = false` and `reproducibleFileOrder = true`
   make the archive stable.
